package fr.ascendant.lunar.encounter;
import java.io.*;
import java.nio.file.*;
import java.util.*;
public final class DurableRaidJournalTest {
    private static int checks;
    private static final UUID DELEGATE=UUID.randomUUID();
    private static void check(boolean x){checks++;if(!x)throw new AssertionError("Journal check "+checks);}
    @FunctionalInterface private interface Attempt{void run()throws Exception;}
    private static void reject(Attempt a)throws Exception{checks++;try{a.run();throw new AssertionError("Expected reject "+checks);}catch(IOException|IllegalArgumentException|IllegalStateException expected){}}
    private static RaidMachine raid(){return new RaidMachine(UUID.randomUUID(),RaidMachine.Identity.SEALED_GREENHOUSE,new RaidMachine.Group(UUID.randomUUID(),Set.of(DELEGATE),List.of(DELEGATE)),RaidMachine.Rules.prototype(100,200),17);}
    private static void win(RaidMachine r){while(!r.terminal())switch(r.view().phase()){
        case READING->r.acknowledgeReading(Set.of(DELEGATE),r.token());
        case ROUTING->{var s=r.view().signals().getFirst();r.defenderInterrupted(r.token(),0);r.interact(DELEGATE,r.token(),0,s.receiver(),s.action());}
        case EXPOSED,CORE_EXPOSED->r.damage(DELEGATE,r.token(),10000);
        case FINAL_WARNING->r.advance(60,Set.of(DELEGATE));
        default->throw new AssertionError();}}
    public static void main(String[] args)throws Exception{
        Path root=Path.of(args[0]);Files.createDirectories(root);
        Path normal=root.resolve("normal.journal");RaidMachine r=raid();UUID run=r.view().run(),actor=UUID.randomUUID(),op=UUID.randomUUID();
        try(var j=new DurableRaidJournal(normal)){j.reserve(r,0);j.actorPlan(run,Map.of(actor,new DurableRaidJournal.Position(736,101,736)));win(r);j.settle(r);check(j.entry(run).status()==RewardLedger.Status.AVAILABLE);j.begin(run,DELEGATE,op);j.confirm(run,op);}
        try(var j=new DurableRaidJournal(normal)){check(j.entry(run).status()==RewardLedger.Status.CLAIMED);check(j.owns(actor));reject(()->j.begin(run,DELEGATE,UUID.randomUUID()));reject(()->j.reserve(new RaidMachine(run,r.view().identity(),r.view().group(),RaidMachine.Rules.prototype(100,200),17),0));}
        for(var point:DurableRaidJournal.Point.values()){
            Path path=root.resolve("begin-"+point+".journal");RaidMachine q=raid();UUID id=q.view().run();int[] inserted={0};
            try(var j=new DurableRaidJournal(path)){j.reserve(q,0);win(q);j.settle(q);j.setFaultForTests(p->{if(p==point)throw new IOException("Injected "+p);});reject(()->{j.begin(id,DELEGATE,UUID.randomUUID());inserted[0]++;});check(!j.healthy());reject(()->j.begin(id,DELEGATE,UUID.randomUUID()));}
            byte[] before=Files.readAllBytes(path);
            try(var j=new DurableRaidJournal(path)){
                check(point!=DurableRaidJournal.Point.AFTER_LENGTH);
                check(j.entry(id).status()==(point==DurableRaidJournal.Point.BEFORE_APPEND?RewardLedger.Status.AVAILABLE:RewardLedger.Status.REVIEW));
                if(j.entry(id).status()==RewardLedger.Status.REVIEW)reject(()->j.begin(id,DELEGATE,UUID.randomUUID()));
            }catch(IOException invalid){check(point==DurableRaidJournal.Point.AFTER_LENGTH);check(Arrays.equals(before,Files.readAllBytes(path)));}
            check(inserted[0]==0);
            Path post=root.resolve("confirm-"+point+".journal");q=raid();UUID rid=q.view().run(),oid=UUID.randomUUID();
            try(var j=new DurableRaidJournal(post)){j.reserve(q,0);win(q);j.settle(q);j.begin(rid,DELEGATE,oid);inserted[0]++;j.setFaultForTests(p->{if(p==point)throw new IOException("Injected");});reject(()->j.confirm(rid,oid));}
            try(var j=new DurableRaidJournal(post)){check(j.entry(rid).status()==RewardLedger.Status.REVIEW||j.entry(rid).status()==RewardLedger.Status.CLAIMED);reject(()->j.begin(rid,DELEGATE,UUID.randomUUID()));}catch(IOException invalid){check(point==DurableRaidJournal.Point.AFTER_LENGTH);}
            check(inserted[0]==1);
        }
        Path pending=root.resolve("pending.journal");RaidMachine pendingRaid=raid();try(var j=new DurableRaidJournal(pending)){j.reserve(pendingRaid,0);}try(var j=new DurableRaidJournal(pending)){check(j.entry(pendingRaid.view().run()).status()==RewardLedger.Status.REFUNDABLE);}
        byte[] source=Files.readAllBytes(normal);
        for(int cut=1;cut<Math.min(150,source.length);cut++){
            Path p=root.resolve("tail-"+cut+".journal");byte[] truncated=Arrays.copyOf(source,source.length-cut);Files.write(p,truncated);
            // A cut exactly at a complete frame boundary is indistinguishable from a valid older backup.
            // Arbitrary backup rollback is outside this journal's threat model; retained snapshots need coordinated backups.
            try(var j=new DurableRaidJournal(p)){check(j.healthy());}catch(IOException expected){check(Arrays.equals(truncated,Files.readAllBytes(p)));}
        }
        Path bad=root.resolve("corrupt.journal");source[source.length-1]^=1;Files.write(bad,source);reject(()->{try(var j=new DurableRaidJournal(bad)){};});check(Arrays.equals(source,Files.readAllBytes(bad)));
        Path exclusive=root.resolve("exclusive.journal");try(var first=new DurableRaidJournal(exclusive)){reject(()->{try(var second=new DurableRaidJournal(exclusive)){};});check(first.healthy());}
        Path empty=root.resolve("empty.journal");Files.createFile(empty);reject(()->{try(var j=new DurableRaidJournal(empty)){};});check(Files.size(empty)==0);
        System.out.println("PASS durable journal: "+checks+" assertions; forced append/fault/recovery/corrupt-tail; no physical Minecraft inventory proof");
    }
}
