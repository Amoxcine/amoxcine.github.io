package fr.ascendant.lunar.encounter;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class EncounterSafetyTest {
    private static int checks;
    private static final UUID PLAYER=UUID.randomUUID(), TEAM=UUID.randomUUID();
    private static void check(boolean value) {checks++;if(!value)throw new AssertionError("Safety check "+checks);}
    private static RaidMachine raid() {return new RaidMachine(UUID.randomUUID(),RaidMachine.Identity.SEALED_GREENHOUSE,
            new RaidMachine.Group(TEAM,Set.of(PLAYER),List.of(PLAYER)),RaidMachine.Rules.prototype(100,200),17);}
    private static void core(RaidMachine r) {
        r.acknowledgeReading(Set.of(PLAYER),r.token());var s=r.view().signals().getFirst();
        r.defenderInterrupted(r.token(),0);r.interact(PLAYER,r.token(),0,s.receiver(),s.action());
        r.damage(PLAYER,r.token(),1000);r.advance(60,Set.of(PLAYER));
        check(r.view().phase()==RaidMachine.Phase.CORE_EXPOSED);
    }
    private static final class Entity {
        final UUID run; boolean discarded; boolean broken;
        Entity(UUID run) {this.run=run;}
        void discard(){if(broken)throw new IllegalStateException("Injected native discard failure");discarded=true;}
    }
    private static void ownership() {
        UUID id=UUID.randomUUID(), run=UUID.randomUUID(), unrelated=UUID.randomUUID();
        Entity migrated=new Entity(run), bystander=new Entity(run);
        var home=new HashMap<UUID,Entity>();var other=new HashMap<UUID,Entity>();
        other.put(id,migrated);other.put(unrelated,bystander);int[] lookups={0};
        EncounterSafety.discardExact(id,run,List.of(home,other),(world,uuid)->{lookups[0]++;return world.get(uuid);},e->e.run,Entity::discard);
        check(migrated.discarded&&!bystander.discarded&&lookups[0]==2);
        check(EncounterSafety.recoverJoin(run,run,false,true));
        check(EncounterSafety.recoverJoin(run,run,false,false));
        check(EncounterSafety.recoverJoin(run,run,true,false));
        check(!EncounterSafety.recoverJoin(run,run,true,true));
        check(!EncounterSafety.recoverJoin(null,run,false,false));
        Entity mismatch=new Entity(UUID.randomUUID());home.put(id,mismatch);migrated.discarded=false;
        boolean[] closed={false};
        check(!EncounterSafety.contain(()->EncounterSafety.discardExact(id,run,List.of(home,other),Map::get,e->e.run,Entity::discard),e->closed[0]=true));
        check(closed[0]&&!mismatch.discarded&&migrated.discarded);
        Entity broken=new Entity(run);broken.broken=true;home.put(id,broken);migrated.discarded=false;
        check(!EncounterSafety.contain(()->EncounterSafety.discardExact(id,run,List.of(home,other),Map::get,e->e.run,Entity::discard),e->{throw new IllegalStateException("Injected cleanup reporter failure");}));
        check(migrated.discarded);
        int[] visits={0};
        Iterable<Integer> infinite=()->new Iterator<>(){public boolean hasNext(){return true;}public Integer next(){return visits[0]++;}};
        check(!EncounterSafety.contain(()->EncounterSafety.dimensions(infinite),e->{}));check(visits[0]==65);
        check(EncounterSafety.dimensions(Collections.nCopies(64,1)).size()==64);
    }
    private static void persistenceFaults(Path root) throws Exception {
        for(var point:DurableRaidJournal.Point.values())for(boolean settlement:new boolean[]{false,true}) {
            Path path=root.resolve("event-"+point+"-"+settlement+".journal");RaidMachine r=raid();
            UUID id=UUID.randomUUID();Entity actor=new Entity(r.view().run());var world=Map.of(id,actor);
            boolean[] closed={false};int[] passed={0};
            try(var journal=new DurableRaidJournal(path)) {
                journal.reserve(r,0);journal.actorPlan(r.view().run(),Map.of(id,new DurableRaidJournal.Position(736,101,736)));
                journal.setFaultForTests(p->{if(p==point)throw new IOException("Injected "+p);});
                if(settlement)r.abort();
                check(!EncounterSafety.contain(()->{
                    try {if(settlement)journal.settle(r);else journal.actorPlan(r.view().run(),Map.of(id,new DurableRaidJournal.Position(736,101,736)));}
                    catch(IOException e){throw new UncheckedIOException(e);}
                    passed[0]++;
                },failure->{closed[0]=true;EncounterSafety.discardExact(id,r.view().run(),List.of(world),Map::get,e->e.run,Entity::discard);}));
                check(closed[0]&&actor.discarded&&passed[0]==0&&!journal.healthy());
                check(journal.owns(id));
            }
        }
    }
    private static void authorization(Path root) throws Exception {
        try(var journal=new DurableRaidJournal(root.resolve("revoked-success.journal"))) {
            // Reservation must precede the real model transitions.
            var r=raid();journal.reserve(r,0);core(r);
            EncounterSafety.damageAuthorized(r,PLAYER,r.token(),1000,()->false);
            journal.settle(r);check(r.view().phase()==RaidMachine.Phase.ABORTED);
            check(journal.entry(r.view().run()).status()==RewardLedger.Status.REFUNDABLE);
        }
        var win=raid();UUID run=win.view().run(),op=UUID.randomUUID();
        Path path=root.resolve("revoked-claim.journal");
        try(var journal=new DurableRaidJournal(path)) {
            journal.reserve(win,0);core(win);EncounterSafety.damageAuthorized(win,PLAYER,win.token(),1000,()->true);journal.settle(win);
            long size=journal.sizeBytes();
            check(!EncounterSafety.contain(()->{
                try{EncounterSafety.beginAuthorized(journal,run,PLAYER,op,()->false);}catch(IOException e){throw new UncheckedIOException(e);}
            },e->{}));
            check(journal.sizeBytes()==size&&journal.entry(run).status()==RewardLedger.Status.AVAILABLE&&journal.healthy());
            EncounterSafety.beginAuthorized(journal,run,PLAYER,op,()->true);
            check(journal.entry(run).status()==RewardLedger.Status.CLAIMING);
            check(!EncounterSafety.contain(()->{try{EncounterSafety.beginAuthorized(journal,run,PLAYER,UUID.randomUUID(),()->true);}catch(IOException e){throw new UncheckedIOException(e);}},e->{}));
        }
        try(var journal=new DurableRaidJournal(path)){check(journal.entry(run).status()==RewardLedger.Status.REVIEW);}
        check(!EncounterSafety.contain(()->EncounterSafety.requireAuthorization(()->false),e->{}));
        check(!EncounterSafety.contain(()->EncounterSafety.requireAuthorization(()->{throw new IllegalStateException("FTB unavailable");}),e->{}));
    }
    private static void restartOwnership(Path root) throws Exception {
        Path path=root.resolve("migrated-actor.journal");var r=raid();UUID id=UUID.randomUUID(),run=r.view().run();
        try(var journal=new DurableRaidJournal(path)) {
            journal.reserve(r,0);journal.actorPlan(run,Map.of(id,new DurableRaidJournal.Position(736,101,736)));
        }
        try(var journal=new DurableRaidJournal(path)) {
            check(journal.entry(run).status()==RewardLedger.Status.REFUNDABLE);
            Entity actor=new Entity(run);var home=new HashMap<UUID,Entity>();var destination=Map.of(id,actor);
            for(var owned:journal.actors().entrySet())
                EncounterSafety.discardExact(owned.getKey(),owned.getValue().run(),List.of(home,destination),Map::get,e->e.run,Entity::discard);
            check(actor.discarded&&journal.owns(id));
            // A later load is still owned even after the first exact cleanup.
            check(EncounterSafety.recoverJoin(journal.owner(id).run(),run,false,false));
        }
    }
    private static void capacity(Path root) throws Exception {
        try(var journal=new DurableRaidJournal(root.resolve("headroom.journal"),4096,512)) {
            var earned=raid();journal.reserve(earned,0);core(earned);earned.damage(PLAYER,earned.token(),1000);journal.settle(earned);
            var early=raid();journal.reserve(early,0);early.abort();journal.settle(early);
            check(!journal.admissionOpen());long before=journal.sizeBytes();
            check(!EncounterSafety.contain(()->{try{journal.reserve(raid(),0);}catch(IOException e){throw new UncheckedIOException(e);}},e->{}));
            check(journal.healthy()&&journal.sizeBytes()==before);
            UUID op=UUID.randomUUID();EncounterSafety.beginAuthorized(journal,earned.view().run(),PLAYER,op,()->true);journal.confirm(earned.view().run(),op);
            check(journal.entry(earned.view().run()).status()==RewardLedger.Status.CLAIMED);
        }
        Path full=root.resolve("full.journal");
        try(var journal=new DurableRaidJournal(full,4096,4096)) {
            var r=raid();journal.reserve(r,0);UUID id=UUID.randomUUID();var plan=Map.of(id,new DurableRaidJournal.Position(736,101,736));
            boolean[] closed={false};Entity actor=new Entity(r.view().run());int writes=0;
            while(!closed[0]&&writes++<100) {
                EncounterSafety.contain(()->{try{journal.actorPlan(r.view().run(),plan);}catch(IOException e){throw new UncheckedIOException(e);}},
                    e->{closed[0]=true;actor.discard();});
            }
            check(closed[0]&&actor.discarded&&!journal.healthy());check(writes<100&&journal.sizeBytes()<=4096&&journal.owns(id));
        }
        byte[] before=Files.readAllBytes(full);
        try(var journal=new DurableRaidJournal(full,4096,4096)) {check(journal.healthy());}
        catch(IOException fullRecovery){check(Arrays.equals(before,Files.readAllBytes(full)));}
    }
    private static void admissions(Path root) throws Exception {
        var budget=new AdmissionBudget();
        for(int i=0;i<8;i++)check(budget.attempt(PLAYER,0));check(!budget.attempt(PLAYER,0));
        check(budget.attempt(PLAYER,200));
        for(int i=1;i<64;i++)check(budget.attempt(UUID.randomUUID(),200));
        check(!budget.attempt(UUID.randomUUID(),200));check(budget.attempt(UUID.randomUUID(),400));
        // Repeated immediate abandon patterns expire without operator intervention.
        for(int window=3;window<100;window++) {
            for(int i=0;i<8;i++)check(budget.attempt(PLAYER,window*200L));
            check(!budget.attempt(PLAYER,window*200L));
        }
        check(new AdmissionBudget().attempt(PLAYER,0));
    }
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]);Files.createDirectories(root);
        ownership();restartOwnership(root);persistenceFaults(root);authorization(root);admissions(root);capacity(root);
        System.out.println("PASS encounter safety: "+checks+" assertions; real journal/model, event containment, exact dimension lookups, revocation and admission budgets; no native server started");
    }
}
