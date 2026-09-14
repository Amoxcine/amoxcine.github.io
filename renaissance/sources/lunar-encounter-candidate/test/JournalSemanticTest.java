package fr.ascendant.lunar.encounter;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

public final class JournalSemanticTest {
    private static int checks;
    private static final UUID PLAYER = UUID.randomUUID(), TEAM = UUID.randomUUID(), RUN = UUID.randomUUID(), OP = UUID.randomUUID();
    private static RewardLedger.Entry entry(RewardLedger.Status status) {
        boolean operation = switch (status) {case CLAIMING, CLAIMED, REFUNDING, REFUNDED, REVIEW -> true; default -> false;};
        return new RewardLedger.Entry(RUN, RaidMachine.Identity.SEALED_GREENHOUSE,
                new RaidMachine.Group(TEAM, Set.of(PLAYER), List.of(PLAYER)), 0, status, operation ? OP : null, operation ? PLAYER : null);
    }
    private static void uuid(DataOutputStream out, UUID id) throws IOException {
        out.writeLong(id.getMostSignificantBits()); out.writeLong(id.getLeastSignificantBits());
    }
    private static byte[] frame(long sequence, List<RewardLedger.Entry> entries, Map<UUID,UUID> actors) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeInt(0x4153524A); out.writeInt(1); out.writeLong(sequence); out.writeInt(entries.size());
            for (var e : entries) {
                uuid(out,e.run()); out.writeUTF(e.identity().name()); uuid(out,e.group().beneficiary());
                out.writeInt(e.group().delegates().size()); for (UUID id : e.group().delegates()) uuid(out,id);
                out.writeInt(e.group().roster().size()); for (UUID id : e.group().roster()) uuid(out,id);
                out.writeInt(e.reservedCoolant()); out.writeUTF(e.status().name()); out.writeBoolean(e.operation()!=null);
                if (e.operation()!=null) {uuid(out,e.operation()); uuid(out,e.claimant());}
            }
            out.writeInt(actors.size());
            for (var actor : actors.entrySet()) {uuid(out,actor.getKey()); uuid(out,actor.getValue()); out.writeInt(736); out.writeInt(101); out.writeInt(736);}
        }
        byte[] payload=bytes.toByteArray(); bytes.reset();
        try (var out=new DataOutputStream(bytes)) {out.writeInt(payload.length); out.write(payload); out.write(MessageDigest.getInstance("SHA-256").digest(payload));}
        return bytes.toByteArray();
    }
    private static void rejected(Path root, String name, byte[]... frames) throws Exception {
        var bytes=new ByteArrayOutputStream();for(byte[] frame:frames)bytes.write(frame);
        Path path=root.resolve(name+".journal");Files.write(path,bytes.toByteArray());
        boolean rejected=false;
        try(var journal=new DurableRaidJournal(path)) {} catch(IOException expected) {rejected=true;}
        if(!rejected)throw new AssertionError("Semantic frame accepted: "+name); checks++;
        if(!Arrays.equals(bytes.toByteArray(),Files.readAllBytes(path)))throw new AssertionError("Rejected journal was modified: "+name); checks++;
    }
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]);Files.createDirectories(root);
        byte[] genesis=frame(0,List.of(),Map.of());
        for(var status:RewardLedger.Status.values()) {
            var e=entry(status);
            rejected(root,"nonempty-genesis-"+status,frame(0,List.of(e),Map.of()));
            if(status!=RewardLedger.Status.RESERVED)
                rejected(root,"new-run-"+status,genesis,frame(1,List.of(e),Map.of()));
        }
        rejected(root,"genesis-actor",frame(0,List.of(entry(RewardLedger.Status.RESERVED)),Map.of(UUID.randomUUID(),RUN)));
        rejected(root,"genesis-orphan",frame(0,List.of(),Map.of(UUID.randomUUID(),RUN)));
        var reserved=entry(RewardLedger.Status.RESERVED);
        var available=entry(RewardLedger.Status.AVAILABLE);
        var claimed=entry(RewardLedger.Status.CLAIMED);
        rejected(root,"skip-intent",genesis,frame(1,List.of(reserved),Map.of()),frame(2,List.of(available),Map.of()),frame(3,List.of(claimed),Map.of()));
        rejected(root,"remove-run",genesis,frame(1,List.of(reserved),Map.of()),frame(2,List.of(),Map.of()));
        UUID actor=UUID.randomUUID();
        rejected(root,"remove-actor",genesis,frame(1,List.of(reserved),Map.of(actor,RUN)),frame(2,List.of(reserved),Map.of()));
        rejected(root,"bad-sequence",genesis,frame(2,List.of(reserved),Map.of()));
        Path good=root.resolve("valid-history.journal");
        var bytes=new ByteArrayOutputStream();bytes.write(genesis);
        long seq=1;
        for(var status:List.of(RewardLedger.Status.RESERVED,RewardLedger.Status.AVAILABLE,RewardLedger.Status.CLAIMING,RewardLedger.Status.CLAIMED))
            bytes.write(frame(seq++,List.of(entry(status)),Map.of()));
        Files.write(good,bytes.toByteArray());
        try(var j=new DurableRaidJournal(good)){if(j.entry(RUN).status()!=RewardLedger.Status.CLAIMED)throw new AssertionError();checks++;}
        try(var j=new DurableRaidJournal(good)){if(j.entry(RUN).status()!=RewardLedger.Status.CLAIMED)throw new AssertionError();checks++;}
        System.out.println("PASS semantic journal: "+checks+" assertions; checksum-valid impossible histories rejected without modification");
    }
}
