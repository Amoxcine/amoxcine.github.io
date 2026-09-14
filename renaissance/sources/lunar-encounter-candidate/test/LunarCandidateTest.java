package fr.ascendant.lunar.encounter;

import java.nio.file.*;
import java.util.*;

public final class LunarCandidateTest {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("Lunar check "+checks);}
    @FunctionalInterface interface Work { void run() throws Exception; }
    private static void rejected(Work work)throws Exception {try{work.run();throw new AssertionError("Accepted invalid input");}catch(java.io.IOException|IllegalArgumentException expected){checks++;}}
    private static RaidMachine model(int count) {
        List<UUID> roster=new ArrayList<>();for(int i=0;i<count;i++)roster.add(UUID.randomUUID());
        UUID leader=roster.isEmpty()?UUID.randomUUID():roster.getFirst();
        return new RaidMachine(UUID.randomUUID(),RaidMachine.Identity.SEALED_GREENHOUSE,
            new RaidMachine.Group(UUID.randomUUID(),Set.of(leader),roster),LunarConfig.defaults(true).rules(),31);
    }
    private static void win(RaidMachine raid) {
        UUID p=raid.view().group().roster().getFirst();var present=Set.of(p);
        check(raid.damage(p,raid.token(),1e9)==RaidMachine.Result.IGNORED);
        raid.advance(160,present);
        for(var signal:raid.view().signals()) {
            check(raid.interact(p,raid.token(),signal.task(),signal.receiver(),signal.action())==RaidMachine.Result.IGNORED);
            raid.defenderInterrupted(raid.token(),signal.task());
            check(raid.interact(p,raid.token(),signal.task(),signal.receiver(),signal.action())==RaidMachine.Result.APPLIED);
        }
        check(raid.view().phase()==RaidMachine.Phase.EXPOSED);
        raid.damage(p,raid.token(),1e9);
        check(raid.view().phase()==RaidMachine.Phase.FINAL_WARNING);
        check(raid.damage(p,raid.token(),1e9)==RaidMachine.Result.IGNORED);
        raid.advance(100,present);raid.damage(p,raid.token(),1e9);
        check(raid.view().phase()==RaidMachine.Phase.SUCCESS);
    }
    public static void main(String[] args)throws Exception {
        Path root=Path.of(args[0]);Files.createDirectories(root);
        Path config=root.resolve("config.properties");check(!LunarConfig.load(config).enabled());check(!Files.exists(config));
        Files.writeString(config,"enabled=true\nsite=test_site\nx=-4096\ny=100\nz=4096\n");
        var c=LunarConfig.load(config);check(c.enabled()&&c.x()==-4096&&c.z()==4096);
        check(!c.allowSiteBootstrap());
        Files.writeString(config,"enabled=false\nallowSiteBootstrap=true\n");check(LunarConfig.load(config).allowSiteBootstrap());
        for(String bad:List.of("enabled=TRUE", "enabled=true", "enabled=false\nunknown=42", "enabled=false\nenabled=true", "enabled=false\nx=NaN", "enabled=false\nsite=../escape", "enabled=false\nx=2147483647", "enabled=false\nreadingTicks=1", "enabled=false\nabsenceTicks=99999")) {
            Files.writeString(config,bad);rejected(()->LunarConfig.load(config));
        }
        c.seal(root);byte[] identity=Files.readAllBytes(root.resolve("site.identity"));c.seal(root);
        rejected(()->LunarConfig.defaults(true).seal(root));check(Arrays.equals(identity,Files.readAllBytes(root.resolve("site.identity"))));
        check(SiteBounds.contains(0,100,0,-32,100,32));check(SiteBounds.contains(0,100,0,0,108,0));
        int[] reads={0};var plan=SiteBlueprint.plan((x,y,z)->{reads[0]++;return SiteBlueprint.Cell.AIR;});
        check(reads[0]==21125&&plan.size()==4228);
        check(plan.stream().filter(p->p.material()==SiteBlueprint.Material.COPPER).count()==3);
        check(new HashSet<>(plan.stream().map(SiteBlueprint.Placement::position).toList()).size()==4228);
        check(plan.stream().allMatch(p->Math.abs(p.position().x())<=32&&Math.abs(p.position().z())<=32&&p.position().y()>=-1&&p.position().y()<=0));
        check(SiteBlueprint.plan((x,y,z)->y==-1?SiteBlueprint.Cell.SOLID_FLOOR:SiteBlueprint.Cell.AIR).size()==3);
        check(SiteBlueprint.plan((x,y,z)->y==-1?SiteBlueprint.Cell.SOLID_FLOOR:y==0&&SiteBlueprint.terminal(x,z)?SiteBlueprint.Cell.COPPER:SiteBlueprint.Cell.AIR).isEmpty());
        for(int obstructionY=-1;obstructionY<=3;obstructionY++) {
            int obstruction=obstructionY;
            try{SiteBlueprint.plan((x,y,z)->x==0&&z==0&&y==obstruction?SiteBlueprint.Cell.OBSTRUCTION:SiteBlueprint.Cell.AIR);throw new AssertionError("Obstruction accepted");}
            catch(IllegalStateException expected){checks++;}
        }
        for(double[] pos:new double[][]{{32.01,100,0},{0,99.9,0},{0,108.01,0},{0,100,-32.01},{Double.NaN,100,0},{0,Double.POSITIVE_INFINITY,0}})
            check(!SiteBounds.contains(0,100,0,pos[0],pos[1],pos[2]));
        rejected(()->model(0));rejected(()->model(9));
        for(int size=1;size<=8;size++){var r=model(size);check(r.view().signals().size()==(size==1?1:size<=4?2:3));win(r);}
        var raid=model(1);UUID id=raid.view().run(),leader=raid.view().group().delegates().iterator().next(),op=UUID.randomUUID();
        Path store=root.resolve("reward-store");
        try(var journal=new SqliteRaidJournal(store)) {
            journal.reserve(raid,0);win(raid);journal.settle(raid);journal.settle(raid);
            check(journal.runCount()==1);check(journal.entry(id).lot().equals(new RewardLedger.Lot("minecraft:copper_ingot",8)));
            try{journal.begin(id,UUID.randomUUID(),op);throw new AssertionError("Owner spoof");}catch(SecurityException expected){checks++;}
            journal.begin(id,leader,op);journal.confirm(id,op);
        }
        try(var journal=new SqliteRaidJournal(store)) {
            check(journal.entry(id).status()==RewardLedger.Status.CLAIMED);
            try{journal.begin(id,leader,UUID.randomUUID());throw new AssertionError("Duplicate reward");}catch(IllegalStateException expected){checks++;}
        }
        var pending=model(1);UUID pendingId=pending.view().run();
        try(var journal=new SqliteRaidJournal(store)) {journal.reserve(pending,0);win(pending);journal.settle(pending);journal.begin(pendingId,pending.view().group().delegates().iterator().next(),UUID.randomUUID());}
        try(var journal=new SqliteRaidJournal(store)){check(journal.entry(pendingId).status()==RewardLedger.Status.REVIEW);}
        var restart=model(1);try(var journal=new SqliteRaidJournal(store)){journal.reserve(restart,0);}
        try(var journal=new SqliteRaidJournal(store)){check(journal.entry(restart.view().run()).status()==RewardLedger.Status.REFUNDABLE);}
        System.out.println("PASS lunar candidate: "+checks+" assertions; config, identity seal, bounds, 1..8 loop, spoof/replay/crash quarantine. Pure model/store tests, NOT native gameplay.");
    }
}
