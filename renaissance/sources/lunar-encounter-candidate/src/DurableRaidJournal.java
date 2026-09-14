package fr.ascendant.lunar.encounter;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Single-writer, checksummed append journal. Corrupt/incomplete tails fail closed, never truncate. */
public final class DurableRaidJournal implements AutoCloseable, WithdrawalJournal {
    public static final int CAPACITY = 4096, MAX_ACTORS = 32768, MAX_FRAME = 4 * 1024 * 1024;
    public static final long MAX_FILE = 64L * 1024 * 1024;
    public static final long ADMISSION_CEILING = 48L * 1024 * 1024;
    private static final int MAGIC = 0x4153524A, VERSION = 1;
    public enum Point { BEFORE_APPEND, AFTER_LENGTH, BEFORE_FORCE, AFTER_FORCE }
    @FunctionalInterface public interface Fault { void at(Point point) throws IOException; }
    public record Position(int x, int y, int z) {
        public Position { if (Math.abs((long)x) > 30_000_000 || Math.abs((long)z) > 30_000_000 || y < -4096 || y > 4096) throw new IllegalArgumentException("Actor bounds"); }
    }
    public record OwnedActor(UUID run, Position position) { public OwnedActor { Objects.requireNonNull(run); Objects.requireNonNull(position); } }
    private final FileChannel channel;
    private final long fileLimit, admissionLimit;
    private final boolean readOnly;
    private FileLock lock;
    private RewardLedger ledger;
    private final Map<UUID, OwnedActor> actors = new HashMap<>();
    private boolean healthy = true;
    private long sequence;
    private Fault fault = point -> {};

    public DurableRaidJournal(Path path) throws IOException {
        this(path, MAX_FILE, ADMISSION_CEILING);
    }
    // Smaller limits exercise the real exhaustion path in tests, never configurable by players.
    DurableRaidJournal(Path path, long fileLimit, long admissionLimit) throws IOException {
        this(path,fileLimit,admissionLimit,false);
    }
    static DurableRaidJournal readOnly(Path path)throws IOException {
        return new DurableRaidJournal(path,MAX_FILE,ADMISSION_CEILING,true);
    }
    private DurableRaidJournal(Path path,long fileLimit,long admissionLimit,boolean readOnly)throws IOException {
        this.readOnly=readOnly;
        if(fileLimit<4096||fileLimit>MAX_FILE||admissionLimit<60||admissionLimit>fileLimit)
            throw new IllegalArgumentException("Invalid journal limits");
        this.fileLimit=fileLimit;this.admissionLimit=admissionLimit;
        Path parent = path.toAbsolutePath().normalize().getParent();
        if (parent == null || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Missing regular journal directory");
        boolean exists = Files.exists(path, LinkOption.NOFOLLOW_LINKS);
        if(readOnly&&!exists)throw new IOException("Legacy source absent");
        if (exists && !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Non-regular journal file");
        channel = FileChannel.open(path, readOnly?Set.of(StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS):exists
                ? Set.of(StandardOpenOption.READ, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)
                : Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.READ, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS));
        try {
            lock = channel.tryLock(0,Long.MAX_VALUE,readOnly); if (lock == null) throw new IOException("Journal already open");
            if (exists) read(); else ledger = new RewardLedger(CAPACITY);
            // Persist recovery (RESERVED -> refundable; uncertain output -> REVIEW) before serving commands.
            if(!readOnly)append();
        } catch (Throwable failure) {
            healthy = false; channel.close();
            if (failure instanceof IOException io) throw io;
            throw new IOException("Journal rejected", failure);
        }
    }
    public synchronized void setFaultForTests(Fault fault) { this.fault = Objects.requireNonNull(fault); }
    public synchronized void reserve(RaidMachine raid, int coolant) throws IOException {
        writable();
        if (!admissionOpen()) throw new IllegalStateException("Admission capacity reached; existing withdrawals remain enabled");
        ledger.reserve(raid, coolant); append();
    }
    public synchronized boolean admissionOpen() throws IOException {
        writable();
        return channel.size() < admissionLimit && ledger.snapshot().size() < CAPACITY;
    }
    public synchronized long sizeBytes() throws IOException { return channel.size(); }
    public synchronized void settle(RaidMachine raid) throws IOException { writable(); if (ledger.settle(raid)) append(); }
    public synchronized RewardLedger.Entry begin(UUID run, UUID delegate, UUID operation) throws IOException {
        writable(); var e = ledger.beginWithdrawal(run, delegate, operation); append(); return e;
    }
    public synchronized void confirm(UUID run, UUID operation) throws IOException { writable(); ledger.confirmWithdrawal(run, operation); append(); }
    public synchronized RewardLedger.Entry entry(UUID run) { return ledger.get(run); }
    public synchronized List<RewardLedger.Entry> entries() { return ledger.snapshot(); }
    public synchronized Map<UUID, OwnedActor> actors() { return Map.copyOf(actors); }
    public synchronized boolean owns(UUID actor) { return actors.containsKey(actor); }
    public synchronized OwnedActor owner(UUID actor) { return actors.get(actor); }
    public synchronized boolean healthy() { return healthy; }
    public synchronized void actorPlan(UUID run, Map<UUID, Position> plan) throws IOException {
        writable(); ledger.get(run);
        if (plan.size() > 3) throw new IllegalArgumentException("Too many live actors");
        for (var entry : plan.entrySet()) {
            OwnedActor prior = actors.get(entry.getKey());
            OwnedActor next = new OwnedActor(run, entry.getValue());
            if (prior != null && !prior.equals(next)) throw new IllegalArgumentException("Actor ownership changed");
        }
        long newCount = plan.keySet().stream().filter(id -> !actors.containsKey(id)).count();
        if (actors.size() + newCount > MAX_ACTORS) throw new IOException("Actor tombstone capacity reached");
        for (var entry : plan.entrySet()) actors.put(entry.getKey(), new OwnedActor(run, entry.getValue()));
        append();
    }
    private void writable() throws IOException { if (readOnly || !healthy || !channel.isOpen()) throw new IOException("Journal locked/read-only"); }
    private void append() throws IOException {
        writable();
        try {
            byte[] payload = encode();
            long frameSize = 4L + payload.length + 32;
            if (payload.length > MAX_FRAME || channel.size() + frameSize > fileLimit) throw new IOException("Journal full; no tombstone eviction");
            fault.at(Point.BEFORE_APPEND);
            channel.position(channel.size());
            write(ByteBuffer.allocate(4).putInt(payload.length).flip()); fault.at(Point.AFTER_LENGTH);
            write(ByteBuffer.wrap(payload)); write(ByteBuffer.wrap(hash(payload)));
            fault.at(Point.BEFORE_FORCE); channel.force(true); fault.at(Point.AFTER_FORCE);
            sequence++;
        } catch (IOException | RuntimeException failure) { healthy = false; throw failure; }
    }
    private void write(ByteBuffer buffer) throws IOException { while (buffer.hasRemaining()) channel.write(buffer); }
    private void readExact(ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) if (channel.read(buffer) < 0) throw new EOFException("Incomplete frame; manual review required");
        buffer.flip();
    }
    private void read() throws IOException {
        if (channel.size() == 0 || channel.size() > fileLimit) throw new IOException("Invalid journal size");
        channel.position(0);
        List<RewardLedger.Entry> last = null;
        while (channel.position() < channel.size()) {
            ByteBuffer length = ByteBuffer.allocate(4); readExact(length); int size = length.getInt();
            if (size < 16 || size > MAX_FRAME || channel.size() - channel.position() < size + 32L) throw new IOException("Invalid/incomplete frame length");
            ByteBuffer data = ByteBuffer.allocate(size), checksum = ByteBuffer.allocate(32);
            readExact(data); readExact(checksum);
            if (!MessageDigest.isEqual(hash(data.array()), checksum.array())) throw new IOException("Journal checksum mismatch");
            var decoded = decode(data.array());
            if (last == null) {
                if (!decoded.isEmpty() || !actors.isEmpty()) throw new IOException("Journal genesis must be empty");
            } else validateProgress(last, decoded);
            last = decoded; sequence++;
        }
        if (last == null) throw new IOException("No journal frame");
        ledger = RewardLedger.recover(CAPACITY, last);
    }
    private byte[] encode() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(MAGIC); out.writeInt(VERSION); out.writeLong(sequence);
            var entries = ledger.snapshot(); out.writeInt(entries.size());
            for (var e : entries) {
                uuid(out, e.run()); out.writeUTF(e.identity().name()); uuid(out, e.group().beneficiary());
                out.writeInt(e.group().delegates().size()); for (UUID d : e.group().delegates()) uuid(out,d);
                out.writeInt(e.group().roster().size()); for (UUID p : e.group().roster()) uuid(out,p);
                out.writeInt(e.reservedCoolant()); out.writeUTF(e.status().name());
                out.writeBoolean(e.operation() != null); if (e.operation() != null) { uuid(out,e.operation()); uuid(out,e.claimant()); }
            }
            out.writeInt(actors.size());
            for (var e : actors.entrySet()) { uuid(out,e.getKey()); uuid(out,e.getValue().run());
                var p = e.getValue().position(); out.writeInt(p.x()); out.writeInt(p.y()); out.writeInt(p.z()); }
        }
        return bytes.toByteArray();
    }
    private List<RewardLedger.Entry> decode(byte[] bytes) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != MAGIC || in.readInt() != VERSION || in.readLong() != sequence) throw new IOException("Journal header/sequence invalid");
            int count = count(in, CAPACITY); List<RewardLedger.Entry> entries = new ArrayList<>(count); Set<UUID> runs = new HashSet<>();
            for (int i = 0; i < count; i++) {
                UUID run=uuid(in); if (!runs.add(run)) throw new IOException("Duplicate run");
                var identity = RaidMachine.Identity.valueOf(in.readUTF()); UUID beneficiary = uuid(in);
                int nd=count(in,64); Set<UUID> delegates=new HashSet<>();
                for (int j=0;j<nd;j++) if (!delegates.add(uuid(in))) throw new IOException("Duplicate delegate");
                int np=count(in,8); List<UUID> roster=new ArrayList<>(); for(int j=0;j<np;j++) roster.add(uuid(in));
                int coolant=in.readInt(); var status=RewardLedger.Status.valueOf(in.readUTF());
                boolean operation=in.readBoolean(); UUID op=operation?uuid(in):null, claimant=operation?uuid(in):null;
                entries.add(new RewardLedger.Entry(run,identity,new RaidMachine.Group(beneficiary,delegates,roster),coolant,status,op,claimant));
            }
            int actorCount=count(in,MAX_ACTORS); Map<UUID,OwnedActor> decoded=new HashMap<>();
            for(int i=0;i<actorCount;i++) {
                UUID id=uuid(in),run=uuid(in); if (!runs.contains(run)) throw new IOException("Orphan actor");
                var actor=new OwnedActor(run,new Position(in.readInt(),in.readInt(),in.readInt()));
                if(decoded.put(id,actor)!=null) throw new IOException("Duplicate actor");
            }
            if(in.available()!=0) throw new IOException("Trailing frame data");
            // A complete frame may add tombstones, but must never forget an earlier actor/run.
            if (!decoded.entrySet().containsAll(actors.entrySet())) throw new IOException("Actor tombstones removed");
            actors.clear(); actors.putAll(decoded);
            return entries;
        } catch (RuntimeException invalid) { throw new IOException("Invalid journal data",invalid); }
    }
    private static int count(DataInputStream in,int max) throws IOException { int n=in.readInt(); if(n<0||n>max) throw new IOException("Invalid count"); return n; }
    private static void validateProgress(List<RewardLedger.Entry> old, List<RewardLedger.Entry> next) throws IOException {
        Map<UUID,RewardLedger.Entry> index=new HashMap<>();for(var e:next)index.put(e.run(),e);
        Set<UUID> previousRuns=new HashSet<>();for(var e:old)previousRuns.add(e.run());
        for(var e:next)if(!previousRuns.contains(e.run())&&e.status()!=RewardLedger.Status.RESERVED)
            throw new IOException("New run must start RESERVED");
        for(var a:old){var b=index.get(a.run());
            if(b==null||a.identity()!=b.identity()||!a.group().equals(b.group())||a.reservedCoolant()!=b.reservedCoolant())throw new IOException("Run tombstone changed/removed");
            if(a.equals(b))continue;
            boolean allowed=switch(a.status()){
                case RESERVED -> b.status()==RewardLedger.Status.AVAILABLE||b.status()==RewardLedger.Status.REFUNDABLE;
                case AVAILABLE -> b.status()==RewardLedger.Status.CLAIMING;
                case REFUNDABLE -> b.status()==RewardLedger.Status.REFUNDING;
                case CLAIMING -> b.status()==RewardLedger.Status.CLAIMED||b.status()==RewardLedger.Status.REVIEW;
                case REFUNDING -> b.status()==RewardLedger.Status.REFUNDED||b.status()==RewardLedger.Status.REVIEW;
                default -> false;};
            if(!allowed||(a.operation()!=null&&(!a.operation().equals(b.operation())||!a.claimant().equals(b.claimant()))))throw new IOException("Reward history regressed");
        }
    }
    private static void uuid(DataOutputStream out,UUID id) throws IOException { out.writeLong(id.getMostSignificantBits()); out.writeLong(id.getLeastSignificantBits()); }
    private static UUID uuid(DataInputStream in) throws IOException { return new UUID(in.readLong(),in.readLong()); }
    private static byte[] hash(byte[] bytes) { try { return MessageDigest.getInstance("SHA-256").digest(bytes); } catch(NoSuchAlgorithmException e) { throw new AssertionError(e); } }
    @Override public synchronized void close() throws IOException { healthy=false; if(lock!=null&&lock.isValid()) lock.release(); channel.close(); }
}
