package fr.ascendant.lunar.encounter;

import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.sqlite.SQLiteConfig;

/** Indexed v3 checkpoint store. No historical run/actor count limit or full-history hot-path snapshots. */
public final class SqliteRaidJournal implements AutoCloseable, WithdrawalJournal {
    public static final int FORMAT=3, WORKSPACE_BYTES=4*1024*1024, CHECKPOINT_TRANSACTIONS=16;
    public static final long WAL_LIMIT=8L*1024*1024;
    private static final int APPLICATION_ID=0x414C4531;
    public enum Point { BEFORE_INTENT, AFTER_INTENT, BEFORE_COMMIT, AFTER_COMMIT, AFTER_ACK, BEFORE_CHECKPOINT, AFTER_CHECKPOINT, BEFORE_WORKSPACE, AFTER_WORKSPACE }
    @FunctionalInterface public interface Fault {void at(Point point)throws IOException;}
    public static final class CapacityDenied extends IOException {
        CapacityDenied(Throwable cause){super("New storage allocation refused; reserved receipt slots remain usable",cause);}
    }
    private record Change(String table,String id,String run,byte[] before,byte[] after) {}
    private record Meta(JournalFence.Stamp stamp,String state,String provenance) {}
    private record Audit(long runs,long actors) {}
    private final Path directory,database,fencePath;
    private FileChannel lockChannel;
    private FileLock lock;
    private Connection connection;
    private JournalFence fence;
    private boolean healthy;
    private long runs,actors;
    private int transactions;
    private Fault fault=point->{};
    private Meta meta;

    public SqliteRaidJournal(Path directory)throws IOException {this(directory,null);}
    SqliteRaidJournal(Path directory,String migrationHash)throws IOException {
        this(directory,migrationHash,null);
    }
    SqliteRaidJournal(Path directory,String migrationHash,String expectedSource)throws IOException {
        this(directory,migrationHash,expectedSource,point->{});
    }
    SqliteRaidJournal(Path directory,String migrationHash,String expectedSource,Fault fault)throws IOException {
        this.fault=Objects.requireNonNull(fault);
        this.directory=directory.toAbsolutePath().normalize();database=this.directory.resolve("ledger.sqlite");fencePath=this.directory.resolve("ack.fence");
        try {
            Path parent=this.directory.getParent();
            if(parent==null||!Files.isDirectory(parent,LinkOption.NOFOLLOW_LINKS))throw new IOException("Missing regular store parent");
            if(!Files.exists(this.directory,LinkOption.NOFOLLOW_LINKS))Files.createDirectory(this.directory);
            if(!Files.isDirectory(this.directory,LinkOption.NOFOLLOW_LINKS))throw new IOException("Non-regular store directory");
            for(String name:List.of("ledger.sqlite","ledger.sqlite-wal","ledger.sqlite-shm","ack.fence","writer.lock"))regularOrMissing(this.directory.resolve(name));
            lockChannel=FileChannel.open(this.directory.resolve("writer.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);
            lock=lockChannel.tryLock();if(lock==null)throw new IOException("Store writer already active");
            boolean exists=Files.exists(database,LinkOption.NOFOLLOW_LINKS);
            if(exists) {
                if(Files.size(database)<100)throw new IOException("Existing empty/truncated database refused");
                var ack=JournalFence.read(fencePath);
                // Inspect without checkpointing or repairing evidence on a rejected database.
                try(Connection read=connect(true)) {
                    configure(read,false);
                    meta=readMeta(read);
                    if(!ack.accepts(meta.stamp))throw new IOException("Database rolled back past durable acknowledgement");
                    validateMode(meta,migrationHash);
                    if(expectedSource!=null&&!meta.provenance.equals(expectedSource))throw new IOException("Legacy migration provenance mismatch");
                    Audit audit=audit(read,meta);runs=audit.runs;actors=audit.actors;
                }
                connection=connect(false);configure(connection,true);
                fence=new JournalFence(fencePath,false);
                fence.committed(meta.stamp);
            } else {
                if(expectedSource!=null&&!expectedSource.isEmpty())throw new IOException("Explicit offline migration required");
                if(Files.exists(fencePath,LinkOption.NOFOLLOW_LINKS)||Files.exists(this.directory.resolve("ledger.sqlite-wal"),LinkOption.NOFOLLOW_LINKS)
                        ||Files.exists(this.directory.resolve("ledger.sqlite-shm"),LinkOption.NOFOLLOW_LINKS))throw new IOException("Missing database with retained sidecars");
                connection=connect(false);configure(connection,true);initialize(migrationHash);
                fence=new JournalFence(fencePath,true);fence.committed(meta.stamp);
            }
            healthy=true;
            warmWorkspace();
            if(migrationHash==null)recoverPending();
        }catch(Exception|LinkageError ex){healthy=false;closeResources();throw asIo(ex);}
    }
    private static void regularOrMissing(Path file)throws IOException {
        if(Files.exists(file,LinkOption.NOFOLLOW_LINKS)&&!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))throw new IOException("Non-regular store file: "+file.getFileName());
    }
    private Connection connect(boolean readOnly)throws SQLException {
        SQLiteConfig config=new SQLiteConfig();config.setReadOnly(readOnly);
        return new org.sqlite.JDBC().connect("jdbc:sqlite:"+database,config.toProperties());
    }
    private static void configure(Connection c,boolean writable)throws SQLException,IOException {
        try(var s=c.createStatement()) {
            for(String sql:List.of("PRAGMA busy_timeout=0","PRAGMA cache_size=-4096","PRAGMA mmap_size=0","PRAGMA temp_store=MEMORY","PRAGMA trusted_schema=OFF","PRAGMA foreign_keys=ON","PRAGMA cell_size_check=ON"))s.execute(sql);
            if(writable) {
                try(var result=s.executeQuery("PRAGMA journal_mode=WAL")){if(!result.next()||!"wal".equalsIgnoreCase(result.getString(1)))throw new IOException("WAL unavailable");}
                s.execute("PRAGMA synchronous=FULL");s.execute("PRAGMA wal_autocheckpoint=0");s.execute("PRAGMA journal_size_limit="+WAL_LIMIT);
            } else s.execute("PRAGMA query_only=ON");
        }
    }
    private void initialize(String migrationHash)throws SQLException {
        try(var s=connection.createStatement()) {
            s.execute("PRAGMA application_id="+APPLICATION_ID);s.execute("PRAGMA user_version=3");
            s.execute("CREATE TABLE meta(id INTEGER PRIMARY KEY CHECK(id=1),seq INTEGER NOT NULL CHECK(seq>=0),root BLOB NOT NULL CHECK(length(root)=32),state TEXT NOT NULL,provenance TEXT NOT NULL)");
            s.execute("CREATE TABLE runs(id TEXT PRIMARY KEY NOT NULL,data BLOB NOT NULL CHECK(length(data)=2048))");
            s.execute("CREATE TABLE actors(id TEXT PRIMARY KEY NOT NULL,run TEXT NOT NULL REFERENCES runs(id),data BLOB NOT NULL CHECK(length(data)=128))");
            s.execute("CREATE TABLE workspace(id INTEGER PRIMARY KEY CHECK(id=1),data BLOB NOT NULL CHECK(length(data)="+WORKSPACE_BYTES+"))");
        }
        meta=new Meta(new JournalFence.Stamp(0,new byte[32]),migrationHash==null?"READY":"MIGRATING",migrationHash==null?"":migrationHash);
        try(var p=connection.prepareStatement("INSERT INTO meta VALUES(1,0,?,?,?)")){p.setBytes(1,meta.stamp.root());p.setString(2,meta.state);p.setString(3,meta.provenance);p.executeUpdate();}
        try(var s=connection.createStatement()){s.executeUpdate("INSERT INTO workspace VALUES(1,zeroblob("+WORKSPACE_BYTES+"))");}
    }
    private static long scalar(Connection c,String sql)throws SQLException {
        try(var s=c.createStatement();var r=s.executeQuery(sql)){if(!r.next())throw new SQLException("Missing scalar");return r.getLong(1);}
    }
    private static Meta readMeta(Connection c)throws SQLException,IOException {
        if(scalar(c,"PRAGMA application_id")!=APPLICATION_ID||scalar(c,"PRAGMA user_version")!=FORMAT)throw new IOException("Unknown store application/format");
        if(scalar(c,"SELECT count(*) FROM sqlite_schema WHERE type IN ('trigger','view')")!=0
                ||scalar(c,"SELECT count(*) FROM sqlite_schema WHERE type='table' AND name NOT LIKE 'sqlite_%'")!=4)throw new IOException("Unexpected store schema");
        try(var s=c.createStatement();var r=s.executeQuery("SELECT seq,root,state,provenance FROM meta WHERE id=1")) {
            if(!r.next())throw new IOException("Missing store metadata");
            return new Meta(new JournalFence.Stamp(r.getLong(1),r.getBytes(2)),r.getString(3),r.getString(4));
        }
    }
    private static void validateMode(Meta meta,String migrationHash)throws IOException {
        if(!Set.of("READY","MIGRATING").contains(meta.state))throw new IOException("Unknown migration state");
        if(migrationHash==null&&!meta.state.equals("READY"))throw new IOException("Migration incomplete; explicit offline resume required");
        if(migrationHash!=null&&!meta.provenance.equals(migrationHash))throw new IOException("Migration source hash mismatch");
    }
    private static void xor(byte[] root,byte[] data){byte[] h=JournalRecords.hash(data);for(int i=0;i<32;i++)root[i]^=h[i];}
    private static Audit audit(Connection c,Meta meta)throws SQLException,IOException {
        try(var s=c.createStatement();var r=s.executeQuery("PRAGMA integrity_check")){if(!r.next()||!"ok".equals(r.getString(1))||r.next())throw new IOException("SQLite integrity check failed");}
        try(var s=c.createStatement();var r=s.executeQuery("PRAGMA foreign_key_check")){if(r.next())throw new IOException("Orphan actor");}
        byte[] root=new byte[32];long runs=0,actors=0;
        try(var s=c.createStatement();var r=s.executeQuery("SELECT id,data FROM runs")) {
            while(r.next()){byte[] data=r.getBytes(2);if(!JournalRecords.run(data).run().toString().equals(r.getString(1)))throw new IOException("Run key mismatch");xor(root,data);runs++;}
        }
        try(var s=c.createStatement();var r=s.executeQuery("SELECT id,run,data FROM actors")) {
            while(r.next()){byte[] data=r.getBytes(3);var a=JournalRecords.actor(data);if(!a.id().toString().equals(r.getString(1))||!a.owner().run().toString().equals(r.getString(2)))throw new IOException("Actor key/owner mismatch");xor(root,data);actors++;}
        }
        if(!Arrays.equals(root,meta.stamp.root()))throw new IOException("Receipts/tombstones root mismatch");
        if(meta.stamp.sequence()<runs+(actors+2)/3)throw new IOException("Impossible genesis/transaction sequence");
        if(scalar(c,"SELECT count(*) FROM workspace WHERE id=1 AND length(data)="+WORKSPACE_BYTES)!=1)throw new IOException("Missing economic write workspace");
        return new Audit(runs,actors);
    }
    private void warmWorkspace()throws IOException,SQLException {
        fault.at(Point.BEFORE_WORKSPACE);
        try(var s=connection.createStatement()){s.executeUpdate("UPDATE workspace SET data=randomblob("+WORKSPACE_BYTES+") WHERE id=1");}
        fault.at(Point.AFTER_WORKSPACE);
        checkpoint();
        if(walBytes()<WORKSPACE_BYTES)throw new IOException("Economic WAL workspace not allocated");
    }
    public synchronized void checkpoint()throws IOException {
        writable();
        try {
            fault.at(Point.BEFORE_CHECKPOINT);
            try(var s=connection.createStatement();var r=s.executeQuery("PRAGMA wal_checkpoint(RESTART)")) {
                if(!r.next()||r.getInt(1)!=0||r.getInt(2)!=r.getInt(3))throw new IOException("Checkpoint could not finish; no eviction or truncation");
            }
            transactions=0;fault.at(Point.AFTER_CHECKPOINT);
        }catch(Exception ex){healthy=false;throw asIo(ex);}
    }
    private void writable()throws IOException {if(!healthy||connection==null)throw new IOException("Indexed journal closed after failure");}
    private static IOException asIo(Throwable ex){return ex instanceof IOException io?io:new IOException("Indexed journal failure",ex);}
    public synchronized void setFaultForTests(Fault fault){this.fault=Objects.requireNonNull(fault);}
    private byte[] data(String table,UUID id)throws SQLException {
        try(var p=connection.prepareStatement("SELECT data FROM "+table+" WHERE id=?")){p.setString(1,id.toString());try(var r=p.executeQuery()){return r.next()?r.getBytes(1):null;}}
    }
    private void mutate(List<Change> changes,boolean allocation)throws IOException {
        writable();if(changes.isEmpty())return;
        if(changes.size()>3)throw new IllegalArgumentException("Transaction actor bound");
        if(transactions>=CHECKPOINT_TRANSACTIONS)checkpoint();
        byte[] root=meta.stamp.root().clone();for(var c:changes){if(c.before!=null)xor(root,c.before);xor(root,c.after);}
        var before=meta.stamp;var next=new JournalFence.Stamp(Math.addExact(before.sequence(),1),root);
        boolean transaction=false,committed=false;
        try {
            fault.at(Point.BEFORE_INTENT);fence.write(new JournalFence.State(true,before,next));fault.at(Point.AFTER_INTENT);
            connection.setAutoCommit(false);transaction=true;
            for(var c:changes) {
                if(c.before==null) {
                    String sql=c.table.equals("runs")?"INSERT INTO runs(id,data) VALUES(?,?)":"INSERT INTO actors(id,data,run) VALUES(?,?,?)";
                    try(var p=connection.prepareStatement(sql)){p.setString(1,c.id);p.setBytes(2,c.after);if(c.table.equals("actors"))p.setString(3,c.run);p.executeUpdate();}
                } else {
                    try(var p=connection.prepareStatement("UPDATE "+c.table+" SET data=? WHERE id=?")){p.setBytes(1,c.after);p.setString(2,c.id);if(p.executeUpdate()!=1)throw new SQLException("Record disappeared");}
                }
            }
            try(var p=connection.prepareStatement("UPDATE meta SET seq=?,root=? WHERE id=1")){p.setLong(1,next.sequence());p.setBytes(2,next.root());p.executeUpdate();}
            fault.at(Point.BEFORE_COMMIT);connection.commit();committed=true;fault.at(Point.AFTER_COMMIT);
            fence.committed(next);fault.at(Point.AFTER_ACK);
            meta=new Meta(next,meta.state,meta.provenance);
            for(var c:changes)if(c.before==null){if(c.table.equals("runs"))runs++;else actors++;}
            transactions++;
        }catch(Exception ex) {
            boolean rolledBack=false;
            try{if(transaction){connection.rollback();connection.setAutoCommit(true);}rolledBack=true;}
            catch(SQLException rollback){
                ex.addSuppressed(rollback);
                // SQLITE_FULL can auto-rollback natively while JDBC still tracks a transaction.
                // BEGIN must succeed before treating that case as an already-completed rollback.
                if(allocation&&!committed&&ex instanceof SQLException sql&&(sql.getErrorCode()&255)==13) {
                    try(var s=connection.createStatement()) {
                        s.execute("BEGIN");connection.rollback();connection.setAutoCommit(true);rolledBack=true;
                    }catch(SQLException uncertain){ex.addSuppressed(uncertain);}
                }
            }
            if(allocation&&!committed&&rolledBack&&ex instanceof SQLException sql&&(sql.getErrorCode()&255)==13) {
                try {if(!readMeta(connection).stamp.matches(before))throw new IOException("Allocation rollback not proven");fence.committed(before);}
                catch(Exception failed){healthy=false;throw asIo(failed);}
                throw new CapacityDenied(ex);
            }
            healthy=false;throw asIo(ex);
        }finally {
            if(transaction&&healthy)try{connection.setAutoCommit(true);}catch(SQLException ex){healthy=false;throw asIo(ex);}
        }
    }
    public synchronized RewardLedger.Entry entry(UUID run) {
        try{byte[] b=data("runs",run);if(b==null)throw new IllegalArgumentException("Unregistered run");return JournalRecords.run(b);}
        catch(IOException|SQLException ex){healthy=false;throw new UncheckedIOException(asIo(ex));}
    }
    public synchronized DurableRaidJournal.OwnedActor owner(UUID actor) {
        try{byte[] b=data("actors",actor);return b==null?null:JournalRecords.actor(b).owner();}
        catch(IOException|SQLException ex){healthy=false;throw new UncheckedIOException(asIo(ex));}
    }
    public synchronized boolean owns(UUID actor){return owner(actor)!=null;}
    public synchronized boolean healthy(){return healthy;}
    public synchronized long runCount(){return runs;}
    public synchronized long actorCount(){return actors;}
    public synchronized long sizeBytes()throws IOException{return Files.size(database);}
    public synchronized long walBytes()throws IOException{Path wal=directory.resolve("ledger.sqlite-wal");return Files.exists(wal)?Files.size(wal):0;}
    public synchronized boolean admissionOpen()throws IOException{writable();return Files.getFileStore(database).getUsableSpace()>16L*1024*1024;}
    public synchronized void reserve(RaidMachine raid,int coolant)throws IOException {
        writable();if(!admissionOpen())throw new CapacityDenied(null);
        var ledger=new RewardLedger(1);ledger.reserve(raid,coolant);var e=ledger.get(raid.view().run());
        try{if(data("runs",e.run())!=null)throw new IllegalStateException("Run reused");}
        catch(SQLException ex){healthy=false;throw asIo(ex);}
        mutate(List.of(new Change("runs",e.run().toString(),null,null,JournalRecords.run(e))),true);
    }
    public synchronized void settle(RaidMachine raid)throws IOException {
        writable();var old=entry(raid.view().run());var ledger=RewardLedger.forEntry(old);
        if(ledger.settle(raid))replace(old,ledger.get(old.run()));
    }
    public synchronized RewardLedger.Entry begin(UUID run,UUID delegate,UUID operation)throws IOException {
        writable();var old=entry(run);var ledger=RewardLedger.forEntry(old);var next=ledger.beginWithdrawal(run,delegate,operation);replace(old,next);return next;
    }
    public synchronized void confirm(UUID run,UUID operation)throws IOException {
        writable();var old=entry(run);var ledger=RewardLedger.forEntry(old);ledger.confirmWithdrawal(run,operation);replace(old,ledger.get(run));
    }
    private void replace(RewardLedger.Entry before,RewardLedger.Entry after)throws IOException {
        mutate(List.of(new Change("runs",before.run().toString(),null,JournalRecords.run(before),JournalRecords.run(after))),false);
    }
    public synchronized void actorPlan(UUID run,Map<UUID,DurableRaidJournal.Position> plan)throws IOException {
        writable();if(plan.size()>3)throw new IllegalArgumentException("Too many live actors");
        if(entry(run).status()!=RewardLedger.Status.RESERVED)throw new IllegalStateException("Actor plan on terminal run");
        List<Change> changes=new ArrayList<>();
        for(var p:plan.entrySet()) {
            var next=new DurableRaidJournal.OwnedActor(run,p.getValue());var prior=owner(p.getKey());
            if(prior!=null){if(!prior.equals(next))throw new IllegalArgumentException("Actor ownership changed");continue;}
            changes.add(new Change("actors",p.getKey().toString(),run.toString(),null,JournalRecords.actor(p.getKey(),next)));
        }
        mutate(changes,true);
    }
    public synchronized List<RewardLedger.Entry> page(String after,int limit)throws IOException {
        if(limit<1||limit>128)throw new IllegalArgumentException("Page bound");
        try(var p=connection.prepareStatement("SELECT data FROM runs WHERE id>? ORDER BY id LIMIT ?")) {
            p.setString(1,after);p.setInt(2,limit);try(var r=p.executeQuery()){List<RewardLedger.Entry> result=new ArrayList<>();while(r.next())result.add(JournalRecords.run(r.getBytes(1)));return List.copyOf(result);}
        }catch(SQLException ex){healthy=false;throw asIo(ex);}
    }
    private void recoverPending()throws IOException {
        String cursor="";
        while(true){var page=page(cursor,128);if(page.isEmpty())break;for(var e:page){var recovered=RewardLedger.recover(1,List.of(e)).get(e.run());if(!recovered.equals(e))replace(e,recovered);}cursor=page.getLast().run().toString();}
    }
    synchronized void importEntry(RewardLedger.Entry entry)throws IOException {
        try{byte[] existing=data("runs",entry.run()),encoded=JournalRecords.run(entry);if(existing!=null){if(!Arrays.equals(existing,encoded))throw new IOException("Migration run conflict");return;}
            if(!meta.state.equals("MIGRATING"))throw new IOException("Completed migration missing source run");
            mutate(List.of(new Change("runs",entry.run().toString(),null,null,encoded)),true);
        }catch(SQLException ex){healthy=false;throw asIo(ex);}
    }
    synchronized void importActor(UUID id,DurableRaidJournal.OwnedActor owner)throws IOException {
        try{byte[] existing=data("actors",id),encoded=JournalRecords.actor(id,owner);if(existing!=null){if(!Arrays.equals(existing,encoded))throw new IOException("Migration actor conflict");return;}
            if(!meta.state.equals("MIGRATING"))throw new IOException("Completed migration missing source actor");
            mutate(List.of(new Change("actors",id.toString(),owner.run().toString(),null,encoded)),true);
        }catch(SQLException ex){healthy=false;throw asIo(ex);}
    }
    synchronized void finishMigration(long expectedRuns,long expectedActors)throws IOException {
        if(runs!=expectedRuns||actors!=expectedActors)throw new IOException("Migration counts mismatch");
        try {audit(connection,meta);try(var s=connection.createStatement()){s.executeUpdate("UPDATE meta SET state='READY' WHERE id=1");}meta=new Meta(meta.stamp,"READY",meta.provenance);checkpoint();}
        catch(SQLException ex){healthy=false;throw asIo(ex);}
    }
    synchronized void setPageLimitForTests(long pages)throws SQLException {try(var s=connection.createStatement()){s.execute("PRAGMA max_page_count="+pages);}}
    synchronized long pageCountForTests()throws SQLException{return scalar(connection,"PRAGMA page_count");}
    private void closeResources() {
        try{if(connection!=null)connection.close();}catch(SQLException ignored){}
        try{if(fence!=null)fence.close();}catch(IOException ignored){}
        try{if(lock!=null&&lock.isValid())lock.release();}catch(IOException ignored){}
        try{if(lockChannel!=null)lockChannel.close();}catch(IOException ignored){}
        connection=null;
    }
    public synchronized void close(){healthy=false;closeResources();}
}
