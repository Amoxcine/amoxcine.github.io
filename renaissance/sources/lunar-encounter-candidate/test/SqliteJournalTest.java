package fr.ascendant.lunar.encounter;

import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class SqliteJournalTest {
    private static int checks;
    private static final UUID PLAYER=new UUID(1,1),TEAM=new UUID(2,2),RUN=new UUID(3,3),ACTOR=new UUID(4,4),OP=new UUID(5,5);
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("V3 check "+checks);}
    @FunctionalInterface interface Attempt{void run()throws Exception;}
    private static void reject(Attempt action)throws Exception {checks++;try{action.run();throw new AssertionError("Expected refusal "+checks);}catch(IOException|IllegalArgumentException|IllegalStateException|SecurityException expected){}}
    static RaidMachine raid(UUID id){return new RaidMachine(id,RaidMachine.Identity.SEALED_GREENHOUSE,new RaidMachine.Group(TEAM,Set.of(PLAYER),List.of(PLAYER)),RaidMachine.Rules.prototype(100,200),17);}
    static void win(RaidMachine r){var present=Set.copyOf(r.view().group().roster());r.acknowledgeReading(present,r.token());for(var s:r.view().signals()){r.defenderInterrupted(r.token(),s.task());r.interact(PLAYER,r.token(),s.task(),s.receiver(),s.action());}r.damage(PLAYER,r.token(),1000);r.advance(60,present);r.damage(PLAYER,r.token(),1000);check(r.view().phase()==RaidMachine.Phase.SUCCESS);}
    static void available(SqliteRaidJournal j)throws Exception {var r=raid(RUN);j.reserve(r,0);j.actorPlan(RUN,Map.of(ACTOR,new DurableRaidJournal.Position(736,101,736)));win(r);j.settle(r);}
    private static void normal(Path root)throws Exception {
        Path dir=root.resolve("normal");
        try(var j=new SqliteRaidJournal(dir)){
            available(j);check(j.runCount()==1&&j.actorCount()==1&&j.healthy());
            reject(()->j.begin(RUN,UUID.randomUUID(),OP));check(j.entry(RUN).status()==RewardLedger.Status.AVAILABLE&&j.healthy());
            reject(()->j.reserve(raid(RUN),0));
            long bytes=j.sizeBytes();
            reject(()->EncounterSafety.beginAuthorized(j,RUN,PLAYER,OP,()->false));check(j.entry(RUN).status()==RewardLedger.Status.AVAILABLE);
            j.begin(RUN,PLAYER,OP);reject(()->j.begin(RUN,PLAYER,UUID.randomUUID()));reject(()->j.confirm(RUN,UUID.randomUUID()));j.confirm(RUN,OP);
            check(j.entry(RUN).status()==RewardLedger.Status.CLAIMED&&j.sizeBytes()>=bytes);
            j.checkpoint();check(j.walBytes()>=SqliteRaidJournal.WORKSPACE_BYTES&&j.walBytes()<=SqliteRaidJournal.WAL_LIMIT);
            reject(()->{try(var second=new SqliteRaidJournal(dir)){};});check(j.healthy());
        }
        try(var j=new SqliteRaidJournal(dir)){check(j.entry(RUN).status()==RewardLedger.Status.CLAIMED&&j.owns(ACTOR));reject(()->j.begin(RUN,PLAYER,OP));}
    }
    private static void economicReservation(Path root)throws Exception {
        try(var j=new SqliteRaidJournal(root.resolve("full-pages"))) {
            available(j);j.checkpoint();long pages=j.pageCountForTests();j.setPageLimitForTests(pages);
            boolean denied=false;int n=0;
            for(;n<1000;n++)try{j.reserve(raid(UUID.randomUUID()),0);}catch(SqliteRaidJournal.CapacityDenied expected){denied=true;break;}
            check(denied&&j.healthy());check(j.pageCountForTests()==pages);
            j.begin(RUN,PLAYER,OP);j.confirm(RUN,OP);j.checkpoint();
            check(j.entry(RUN).status()==RewardLedger.Status.CLAIMED&&j.healthy());check(j.pageCountForTests()==pages);
            check(j.walBytes()<=SqliteRaidJournal.WAL_LIMIT);
            System.out.println("Economic reservation PASS: DB pages locked="+pages+", extra admissions="+n+", begin/confirm/checkpoint succeeded without DB growth");
        }
    }
    private static void contracts(Path root)throws Exception {
        try(var j=new SqliteRaidJournal(root.resolve("contracts"))) {
            check(j.runCount()==0&&j.actorCount()==0);
            var r=raid(UUID.randomUUID());win(r);reject(()->j.reserve(r,0));check(j.runCount()==0&&j.healthy());
            var active=raid(RUN);j.reserve(active,0);check(j.entry(RUN).status()==RewardLedger.Status.RESERVED);
            var pos=new DurableRaidJournal.Position(736,101,736);j.actorPlan(RUN,Map.of(ACTOR,pos));
            long seq=JournalFence.read(root.resolve("contracts/ack.fence")).after().sequence();j.actorPlan(RUN,Map.of(ACTOR,pos));
            check(seq==JournalFence.read(root.resolve("contracts/ack.fence")).after().sequence());
            reject(()->j.actorPlan(RUN,Map.of(ACTOR,new DurableRaidJournal.Position(737,101,736))));
            var other=raid(UUID.randomUUID());j.reserve(other,0);reject(()->j.actorPlan(other.view().run(),Map.of(ACTOR,pos)));
            active.abort();j.settle(active);reject(()->j.actorPlan(RUN,Map.of(UUID.randomUUID(),pos)));j.begin(RUN,PLAYER,OP);j.confirm(RUN,OP);
            check(j.entry(RUN).status()==RewardLedger.Status.REFUNDED&&j.owns(ACTOR));
            for(var identity:RaidMachine.Identity.values()) {
                var group=new RaidMachine.Group(TEAM,Set.of(PLAYER),List.of(PLAYER));
                var model=new RaidMachine(UUID.randomUUID(),identity,group,RaidMachine.Rules.prototype(100,200),17);
                int coolant=identity==RaidMachine.Identity.REGULATOR?10:0;j.reserve(model,coolant);model.abort();j.settle(model);
                UUID op=UUID.randomUUID();j.begin(model.view().run(),PLAYER,op);j.confirm(model.view().run(),op);
                check(j.entry(model.view().run()).reservedCoolant()==coolant&&j.entry(model.view().run()).status()==RewardLedger.Status.REFUNDED);
            }
            Set<UUID> delegates=new HashSet<>();List<UUID> roster=new ArrayList<>();for(int i=0;i<64;i++)delegates.add(new UUID(8,i));for(int i=0;i<8;i++)roster.add(new UUID(8,i));
            var model=new RaidMachine(UUID.randomUUID(),RaidMachine.Identity.SEALED_GREENHOUSE,new RaidMachine.Group(TEAM,delegates,roster),RaidMachine.Rules.prototype(100,200),17);
            j.reserve(model,0);model.abort();j.settle(model);long pages=j.pageCountForTests();j.checkpoint();j.setPageLimitForTests(pages);
            UUID op=UUID.randomUUID();j.begin(model.view().run(),roster.getFirst(),op);j.confirm(model.view().run(),op);check(j.pageCountForTests()==pages);
        }
    }
    private static void crashChild(Path dir,SqliteRaidJournal.Point point,String action)throws Exception {
        if(action.equals("workspace")) {
            try(var j=new SqliteRaidJournal(dir)){available(j);j.begin(RUN,PLAYER,OP);j.confirm(RUN,OP);}
            try(var j=new SqliteRaidJournal(dir,null,null,p->{if(p==point)Runtime.getRuntime().halt(73);})){}
            throw new AssertionError("Workspace crash not reached");
        }
        if(action.equals("migration")) {
            MigrateJournal.migrate(dir.getParent().resolve("legacy.journal"),dir,p->{if(p==point)Runtime.getRuntime().halt(73);});
            throw new AssertionError("Migration crash not reached");
        }
        try(var j=new SqliteRaidJournal(dir)) {
            available(j);
            if(!action.equals("begin")){j.begin(RUN,PLAYER,OP);if(action.equals("checkpoint"))j.confirm(RUN,OP);}
            j.setFaultForTests(p->{if(p==point)Runtime.getRuntime().halt(73);});
            switch(action){case "begin"->j.begin(RUN,PLAYER,OP);case "confirm"->j.confirm(RUN,OP);case "checkpoint"->j.checkpoint();default->throw new AssertionError();}
        }
        throw new AssertionError("Crash boundary was not reached");
    }
    private static void child(Path dir,SqliteRaidJournal.Point point,String action)throws Exception {
        Process p=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),"-Xmx96m",
                "-Dorg.sqlite.tmpdir="+System.getProperty("org.sqlite.tmpdir"),"-cp",System.getProperty("java.class.path"),SqliteJournalTest.class.getName(),"crash",dir.toString(),point.name(),action).inheritIO().start();
        if(!p.waitFor(60,TimeUnit.SECONDS)){p.destroyForcibly();throw new AssertionError("Child timeout");}check(p.exitValue()==73);
    }
    private static void crashes(Path root)throws Exception {
        for(String action:List.of("begin","confirm"))for(var point:List.of(SqliteRaidJournal.Point.BEFORE_INTENT,SqliteRaidJournal.Point.AFTER_INTENT,SqliteRaidJournal.Point.BEFORE_COMMIT,SqliteRaidJournal.Point.AFTER_COMMIT,SqliteRaidJournal.Point.AFTER_ACK)) {
            Path dir=root.resolve("crash-"+action+"-"+point);child(dir,point,action);
            try(var j=new SqliteRaidJournal(dir)) {
                boolean committed=point==SqliteRaidJournal.Point.AFTER_COMMIT||point==SqliteRaidJournal.Point.AFTER_ACK;
                var expected=action.equals("begin")?(committed?RewardLedger.Status.REVIEW:RewardLedger.Status.AVAILABLE):(committed?RewardLedger.Status.CLAIMED:RewardLedger.Status.REVIEW);
                check(j.entry(RUN).status()==expected&&j.owns(ACTOR));
                if(expected!=RewardLedger.Status.AVAILABLE)reject(()->j.begin(RUN,PLAYER,UUID.randomUUID()));
            }
        }
        for(var point:List.of(SqliteRaidJournal.Point.BEFORE_CHECKPOINT,SqliteRaidJournal.Point.AFTER_CHECKPOINT)) {
            Path dir=root.resolve("crash-checkpoint-"+point);child(dir,point,"checkpoint");
            try(var j=new SqliteRaidJournal(dir)){check(j.entry(RUN).status()==RewardLedger.Status.CLAIMED&&j.owns(ACTOR));}
        }
        for(var point:List.of(SqliteRaidJournal.Point.BEFORE_WORKSPACE,SqliteRaidJournal.Point.AFTER_WORKSPACE)) {
            Path dir=root.resolve("crash-workspace-"+point);child(dir,point,"workspace");
            try(var j=new SqliteRaidJournal(dir)){check(j.entry(RUN).status()==RewardLedger.Status.CLAIMED&&j.owns(ACTOR));}
        }
    }
    private static void eventFaults(Path root)throws Exception {
        for(var point:List.of(SqliteRaidJournal.Point.BEFORE_INTENT,SqliteRaidJournal.Point.AFTER_INTENT,SqliteRaidJournal.Point.BEFORE_COMMIT,SqliteRaidJournal.Point.AFTER_COMMIT,SqliteRaidJournal.Point.AFTER_ACK,SqliteRaidJournal.Point.BEFORE_CHECKPOINT,SqliteRaidJournal.Point.AFTER_CHECKPOINT)) {
            Path dir=root.resolve("io-"+point);boolean[] cleanup={false};int[] grants={0};
            try(var j=new SqliteRaidJournal(dir)) {
                available(j);j.setFaultForTests(p->{if(p==point)throw new IOException("Injected "+point);});
                check(!EncounterSafety.contain(()->{try{if(point.name().contains("CHECKPOINT"))j.checkpoint();else j.begin(RUN,PLAYER,OP);grants[0]++;}catch(IOException ex){throw new UncheckedIOException(ex);}},ex->cleanup[0]=true));
                check(cleanup[0]&&grants[0]==0&&!j.healthy());reject(()->j.begin(RUN,PLAYER,OP));
            }
            try(var j=new SqliteRaidJournal(dir)){check(j.owns(ACTOR));if(j.entry(RUN).status()==RewardLedger.Status.REVIEW)reject(()->j.begin(RUN,PLAYER,OP));}
        }
    }
    private static void corruption(Path root)throws Exception {
        Path fence=root.resolve("bad-fence");try(var j=new SqliteRaidJournal(fence)){available(j);}
        byte[] bytes=Files.readAllBytes(fence.resolve("ack.fence"));bytes[40]^=1;Files.write(fence.resolve("ack.fence"),bytes);
        String dbHash=MigrateJournal.hash(fence.resolve("ledger.sqlite"));reject(()->{try(var j=new SqliteRaidJournal(fence)){};});check(dbHash.equals(MigrateJournal.hash(fence.resolve("ledger.sqlite"))));
        Path record=root.resolve("bad-record");try(var j=new SqliteRaidJournal(record)){available(j);}
        try(var c=new org.sqlite.JDBC().connect("jdbc:sqlite:"+record.resolve("ledger.sqlite"),new Properties());var p=c.prepareStatement("UPDATE runs SET data=? WHERE id=?")){
            byte[] invalid=new byte[JournalRecords.RUN_BYTES];p.setBytes(1,invalid);p.setString(2,RUN.toString());p.executeUpdate();
        }
        dbHash=MigrateJournal.hash(record.resolve("ledger.sqlite"));reject(()->{try(var j=new SqliteRaidJournal(record)){};});check(dbHash.equals(MigrateJournal.hash(record.resolve("ledger.sqlite"))));
        Path missing=root.resolve("lost-wal");child(missing,SqliteRaidJournal.Point.AFTER_ACK,"confirm");
        Files.move(missing.resolve("ledger.sqlite-wal"),missing.resolve("wal-evidence.bin"));
        reject(()->{try(var j=new SqliteRaidJournal(missing)){};});
        Path badWal=root.resolve("bad-wal");child(badWal,SqliteRaidJournal.Point.AFTER_ACK,"confirm");
        try(var f=FileChannel.open(badWal.resolve("ledger.sqlite-wal"),StandardOpenOption.WRITE)){f.write(java.nio.ByteBuffer.wrap(new byte[]{0,0,0,0}),0);f.force(true);}
        reject(()->{try(var j=new SqliteRaidJournal(badWal)){};});
        Path empty=root.resolve("empty-db");Files.createDirectory(empty);Files.createFile(empty.resolve("ledger.sqlite"));reject(()->{try(var j=new SqliteRaidJournal(empty)){};});
        Path directory=root.resolve("nonregular");Files.createDirectory(directory);Files.createDirectory(directory.resolve("ledger.sqlite"));reject(()->{try(var j=new SqliteRaidJournal(directory)){};});
        Path readOnly=root.resolve("readonly");try(var j=new SqliteRaidJournal(readOnly)){available(j);}
        Path db=readOnly.resolve("ledger.sqlite");Files.setAttribute(db,"dos:readonly",true);
        try{reject(()->{try(var j=new SqliteRaidJournal(readOnly)){};});}finally{Files.setAttribute(db,"dos:readonly",false);}
        try(var j=new SqliteRaidJournal(readOnly)){check(j.entry(RUN).status()==RewardLedger.Status.AVAILABLE);}
        Path semantic=root.resolve("semantic-record");byte[] encoded;
        try(var j=new SqliteRaidJournal(semantic)){j.reserve(raid(RUN),0);encoded=JournalRecords.run(j.entry(RUN));}
        byte[] from="RESERVED".getBytes(java.nio.charset.StandardCharsets.US_ASCII),to="CLAIMING".getBytes(java.nio.charset.StandardCharsets.US_ASCII);boolean changed=false;
        for(int i=0;i<encoded.length-from.length;i++)if(Arrays.equals(Arrays.copyOfRange(encoded,i,i+from.length),from)){System.arraycopy(to,0,encoded,i,to.length);changed=true;break;}
        check(changed);encoded=JournalRecords.seal(Arrays.copyOf(encoded,encoded.length-32),encoded.length);
        byte[] invalid=encoded;reject(()->JournalRecords.run(invalid));
        var stamp=JournalFence.read(semantic.resolve("ack.fence")).after();byte[] rootHash=JournalRecords.hash(encoded);
        try(var c=new org.sqlite.JDBC().connect("jdbc:sqlite:"+semantic.resolve("ledger.sqlite"),new Properties());var p=c.prepareStatement("UPDATE runs SET data=?")){p.setBytes(1,encoded);p.executeUpdate();try(var m=c.prepareStatement("UPDATE meta SET root=?")){m.setBytes(1,rootHash);m.executeUpdate();}}
        try(var ack=new JournalFence(semantic.resolve("ack.fence"),false)){ack.committed(new JournalFence.Stamp(stamp.sequence(),rootHash));}
        reject(()->{try(var j=new SqliteRaidJournal(semantic)){};});
    }
    private static void migration(Path root)throws Exception {
        Path source=root.resolve("legacy.journal"),target=root.resolve("migrated");
        try(var old=new DurableRaidJournal(source)) {
            var r=raid(RUN);old.reserve(r,0);old.actorPlan(RUN,Map.of(ACTOR,new DurableRaidJournal.Position(736,101,736)));win(r);old.settle(r);old.begin(RUN,PLAYER,OP);old.confirm(RUN,OP);
            old.reserve(raid(UUID.randomUUID()),0);
            reject(()->MigrateJournal.migrate(source,target));check(old.healthy());
        }
        String before=MigrateJournal.hash(source);MigrateJournal.migrate(source,target);check(before.equals(MigrateJournal.hash(source)));
        try(var j=new SqliteRaidJournal(target)){check(j.runCount()==2&&j.actorCount()==1&&j.entry(RUN).status()==RewardLedger.Status.CLAIMED&&j.owns(ACTOR));}
        Path incomplete=root.resolve("incomplete-migration");String hash=MigrateJournal.hash(source);
        try(var j=new SqliteRaidJournal(incomplete,hash);var old=DurableRaidJournal.readOnly(source)){j.importEntry(old.entries().getFirst());}
        reject(()->{try(var j=new SqliteRaidJournal(incomplete)){};});MigrateJournal.migrate(source,incomplete);
        try(var j=new SqliteRaidJournal(incomplete)){check(j.runCount()==2&&j.actorCount()==1);}
        check(before.equals(MigrateJournal.hash(source)));
        MigrateJournal.migrate(source,target);check(before.equals(MigrateJournal.hash(source)));
        for(var point:List.of(SqliteRaidJournal.Point.AFTER_INTENT,SqliteRaidJournal.Point.BEFORE_COMMIT,SqliteRaidJournal.Point.AFTER_COMMIT,SqliteRaidJournal.Point.AFTER_ACK,SqliteRaidJournal.Point.BEFORE_CHECKPOINT,SqliteRaidJournal.Point.AFTER_CHECKPOINT)) {
            Path partial=root.resolve("migration-crash-"+point);child(partial,point,"migration");MigrateJournal.migrate(source,partial);
            try(var j=new SqliteRaidJournal(partial)){check(j.runCount()==2&&j.actorCount()==1&&j.entry(RUN).status()==RewardLedger.Status.CLAIMED);}
        }
    }
    private static void volume(Path root)throws Exception {
        final int total=Integer.getInteger("encounter.volume",18000);Path dir=root.resolve("volume");
        long started=System.nanoTime(),maxWal=0;UUID firstActor=null,lastRun=null;List<Long> tail=new ArrayList<>();
        try(var j=new SqliteRaidJournal(dir)) {
            for(int i=0;i<total;i++) {
                long t=System.nanoTime();var roster=new ArrayList<UUID>();roster.add(PLAYER);for(int p=1;p<8;p++)roster.add(new UUID(1,p+1));
                var r=new RaidMachine(UUID.randomUUID(),RaidMachine.Identity.SEALED_GREENHOUSE,new RaidMachine.Group(TEAM,Set.of(PLAYER),roster),RaidMachine.Rules.prototype(100,200),17);lastRun=r.view().run();j.reserve(r,0);
                Map<UUID,DurableRaidJournal.Position> plan=new HashMap<>();for(int a=0;a<3;a++){UUID id=UUID.randomUUID();if(firstActor==null)firstActor=id;plan.put(id,new DurableRaidJournal.Position(736+a,101,736));}j.actorPlan(lastRun,plan);
                if(i%100==0){win(r);j.settle(r);UUID op=UUID.randomUUID();j.begin(lastRun,PLAYER,op);j.confirm(lastRun,op);}else{r.abort();j.settle(r);}
                if(i>=total-500)tail.add(System.nanoTime()-t);
                maxWal=Math.max(maxWal,j.walBytes());
                if(i%1000==0){maxWal=Math.max(maxWal,j.walBytes());System.out.println("V3 volume "+i+"/"+total+" db="+j.sizeBytes()+" wal="+j.walBytes());}
            }
            j.checkpoint();check(j.runCount()==total&&j.actorCount()==3L*total);check(maxWal<=SqliteRaidJournal.WAL_LIMIT);
            check(j.sizeBytes()<SqliteRaidJournal.WORKSPACE_BYTES+(long)total*10000);
            if(total>=18000)check(j.sizeBytes()>64L*1024*1024);
            Collections.sort(tail);System.out.println("V3 VOLUME PASS roster=8 runs="+total+" actors="+j.actorCount()+" dbBytes="+j.sizeBytes()+" maxWalBytes="+maxWal+" tailAttemptP95ms="+(tail.get(tail.size()*95/100)/1e6)+" elapsedSeconds="+((System.nanoTime()-started)/1e9));
        }
        try(var j=new SqliteRaidJournal(dir)){check(j.runCount()==total&&j.owns(firstActor));check(j.entry(lastRun)!=null&&j.page("",128).size()==Math.min(total,128));}
    }
    public static void main(String[] args)throws Exception {
        if(args[0].equals("crash")){crashChild(Path.of(args[1]),SqliteRaidJournal.Point.valueOf(args[2]),args[3]);return;}
        Path root=Path.of(args[0]);Files.createDirectories(root);normal(root);contracts(root);economicReservation(root);crashes(root);eventFaults(root);corruption(root);migration(root);volume(root);
        System.out.println("PASS indexed journal v3: "+checks+" assertions, subprocess hard crashes, corruption, writer/permission refusals, migration, volume; no Minecraft server");
    }
}
