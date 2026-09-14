package fr.ascendant.lunar.encounter;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import com.mojang.logging.LogUtils;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.*;
import team.creative.playerrevive.server.PlayerReviveServer;

@Mod("ascendant_lunar_encounter")
public final class LunarEncounterMod implements NativeEncounterBridge.CampaignPorts {
    private record Ready(UUID team, UUID leader, int tick) {}
    private final NativeEncounterBridge bridge = new NativeEncounterBridge();
    private final Map<UUID,Ready> ready = new HashMap<>();
    private AdmissionBudget admission = new AdmissionBudget();
    private SqliteRaidJournal journal;
    private MinecraftServer server;
    private ServerLevel level;
    private BlockPos center;
    private LunarConfig config;
    private String failure = "Not started";
    private static final String LAST_RUN="ascendantLunarLastRun";
    private static final String SUCCESS_RUN="ascendantLunarSuccessRun";
    public LunarEncounterMod() {
        NeoForge.EVENT_BUS.addListener((ServerAboutToStartEvent e) -> event(() -> opening(e)));
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent e) -> event(() -> started(e)));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> event(() -> stopped(e)));
        NeoForge.EVENT_BUS.addListener((EntityJoinLevelEvent e) -> event(() -> joined(e)));
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> event(() -> commands(e)));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if(e.getEntity() instanceof ServerPlayer p&&available()&&p.getPersistentData().hasUUID(SUCCESS_RUN))
                observeQuietly(p,p.getPersistentData().getUUID(SUCCESS_RUN));
        });
    }
    private void event(Runnable action) { EncounterSafety.contain(action, ex -> { serviceFailed(ex); bridge.closeService(ex); }); }
    @Override public void serviceFailed(RuntimeException ex) { failure=ex.toString(); }
    private void opening(ServerAboutToStartEvent e) {
        server=e.getServer();
        try {
            Path root=server.getWorldPath(LevelResource.ROOT);
            config=LunarConfig.load(root.resolve("serverconfig/lunar-encounter.properties"));
            Path data=root.resolve("data/ascendant-lunar-encounter-v1");
            if (!config.enabled()) {
                failure="Disabled: no admissions or success observation; existing actor joins suspended";
                return;
            }
            Files.createDirectories(data);
            if (!Files.isDirectory(data,LinkOption.NOFOLLOW_LINKS)) throw new IOException("Non-regular encounter data directory");
            config.seal(data);
            journal=new SqliteRaidJournal(data.resolve("ledger"));
            center=new BlockPos(config.x(),config.y(),config.z());
            admission=new AdmissionBudget(); failure="";
        } catch(Exception ex) { failure=ex.toString(); LogUtils.getLogger().error("[LunarEncounter] closed; files preserved",ex); }
    }
    private void started(ServerStartedEvent e) {
        if(e.getServer()!=server||journal==null||!journal.healthy()||!failure.isEmpty())return;
        level=server.getLevel(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(LunarConfig.DIMENSION)));
        if(level==null) {failure="ad_astra:moon dimension absent";return;}
        if(center.getY()<level.getMinBuildHeight()+2||center.getY()>level.getMaxBuildHeight()-10) {failure="Site Y outside build bounds";return;}
        EncounterSafety.dimensions(server.getAllLevels());
        bridge.bind(server,level,this);
        LunarEncounterApi.bind(this::completion);
        LogUtils.getLogger().info("[LunarEncounter] candidate enabled, site={} center={}; no terrain/oxygen edits",config.site(),center);
    }
    private void stopped(ServerStoppedEvent e) {
        if(e.getServer()!=server)return;
        try {if(journal!=null)journal.close();}
        finally {LunarEncounterApi.close();journal=null;server=null;level=null;center=null;config=null;ready.clear();failure="Stopped";}
    }
    private void joined(EntityJoinLevelEvent e) {
        if(!(e.getLevel() instanceof ServerLevel world)||world.getServer()!=server)return;
        var entity=e.getEntity();UUID id=entity.getUUID();
        boolean marked=entity.getPersistentData().contains("ascendantLunarEncounterRun");
        boolean active=bridge.ownsActive(id);
        EncounterSafety.recoverEntityJoin(marked,NativeEncounterBridge.marker(entity),active,
            world.dimension().location().toString().equals(LunarConfig.DIMENSION),journal!=null&&journal.healthy(),
            ()->{var owner=journal.owner(id);return owner==null?null:owner.run();},()->e.setCanceled(true),entity::discard);
    }
    private void commands(RegisterCommandsEvent e) {
        var root=Commands.literal("lunar_encounter");
        root.then(Commands.literal("status").requires(s->s.hasPermission(2)).executes(c->command(c.getSource(),()->{
            c.getSource().sendSuccess(()->Component.literal("[LunarEncounter] 0.1.2-candidate healthy="+available()+" site="+(config==null?"none":config.site())+" center="+center+" runs="+(journal==null?0:journal.runCount())+" failure="+failure+"; native FTB rewards only; runtime qualification pending"),false);return 1;})));
        root.then(Commands.literal("validate").requires(s->s.hasPermission(2)).executes(c->command(c.getSource(),()->{
            require();if(!terrain(center))throw new IllegalStateException("Site unloaded, claimed, obstructed or missing copper terminals");
            c.getSource().sendSuccess(()->Component.literal("Site geometry/claims currently valid; no terrain changed. Not a combat qualification."),false);return 1;})));
        root.then(Commands.literal("site").executes(c->command(c.getSource(),()->{
            require();var p=c.getSource().getPlayerOrException();if(!capable(p))throw new IllegalStateException("Real player required");
            p.sendSystemMessage(Component.literal("Relais lunaire "+config.site()+" | "+LunarConfig.DIMENSION+" | X="+center.getX()+" Y="+center.getY()+" Z="+center.getZ()+". Voyage normal vers la Lune; aucun teleport. Au site: /lunar_encounter ready puis start."));return 1;})));
        root.then(Commands.literal("preflight").requires(s->s.hasPermission(2))
            .then(Commands.argument("center",BlockPosArgument.blockPos()).executes(c->command(c.getSource(),()->{
                var moon=c.getSource().getServer().getLevel(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(LunarConfig.DIMENSION)));
                if(moon==null)throw new IllegalStateException("Moon dimension unavailable");
                var at=BlockPosArgument.getBlockPos(c,"center");var plan=NativeSiteBuilder.preflight(moon,at);
                c.getSource().sendSuccess(()->Component.literal("Preflight PASS at "+at+": "+plan.size()+" air-only additions; no world/data changes. Register these exact coordinates before first enable."),false);return 1;}))));
        root.then(Commands.literal("build_site").requires(s->s.hasPermission(2)).then(Commands.literal("confirm").executes(c->command(c.getSource(),()->{
            require();if(!config.allowSiteBootstrap()||journal.runCount()!=0)throw new IllegalStateException("Parent-only: allowSiteBootstrap=true and zero historical runs required");
            int placed=NativeSiteBuilder.build(level,center);
            if(!terrain(center))throw new IllegalStateException("Site changed during bootstrap; inspect retained additions before retry");
            c.getSource().sendSuccess(()->Component.literal("Registered site ready: "+center+"; "+placed+" air-only blocks added, nothing replaced. Players: /lunar_encounter site, ready, start. Disable allowSiteBootstrap on next restart."),false);return 1;}))));
        root.then(Commands.literal("ready").executes(c->command(c.getSource(),()->ready(c.getSource().getPlayerOrException(),c.getSource().getPlayerOrException())))
            .then(Commands.argument("leader",EntityArgument.player()).executes(c->command(c.getSource(),()->ready(c.getSource().getPlayerOrException(),EntityArgument.getPlayer(c,"leader"))))));
        root.then(Commands.literal("start").executes(c->command(c.getSource(),()->start(c.getSource().getPlayerOrException()))));
        root.then(Commands.literal("last").executes(c->command(c.getSource(),()->{
            require();var p=c.getSource().getPlayerOrException();UUID run=lastRun(p);var entry=journal.entry(run);
            if(!entry.group().roster().contains(p.getUUID()))throw new SecurityException("Not a participant");
            p.sendSystemMessage(Component.literal("Relais lunaire | "+run+" | "+entry.status()+" | recompense initiale geree par FTB Quests"));return 1;})));
        root.then(Commands.literal("observe").then(Commands.argument("run",UuidArgument.uuid()).executes(c->command(c.getSource(),()->{
            var p=c.getSource().getPlayerOrException();UUID run=UuidArgument.getUuid(c,"run");
            if(completion(p,run).isEmpty())throw new IllegalStateException("No authenticated durable victory");
            observe(p,run);p.sendSystemMessage(Component.literal("Victoire durable observee: "+run+". Aucun objet attribue par cette observation."));return 1;}))));
        root.then(Commands.literal("circuit").then(Commands.argument("run",UuidArgument.uuid())
            .then(Commands.argument("generation",IntegerArgumentType.integer(1))
            .then(Commands.argument("tache",IntegerArgumentType.integer(1,3))
            .then(Commands.argument("recepteur",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(List.of("A","B","C"),b))
            .then(Commands.argument("action",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(List.of("identifier","transferer","deriver"),b))
            .executes(c->command(c.getSource(),()->{
                require();var token=new RaidMachine.Token(UuidArgument.getUuid(c,"run"),IntegerArgumentType.getInteger(c,"generation"));
                return bridge.choose(c.getSource().getPlayerOrException(),token,IntegerArgumentType.getInteger(c,"tache")-1,
                    CircuitChoices.receiver(StringArgumentType.getString(c,"recepteur")),CircuitChoices.action(StringArgumentType.getString(c,"action")))==RaidMachine.Result.IGNORED?0:1;
            }))))))));
        root.then(Commands.literal("inspect").requires(s->s.hasPermission(2)).then(Commands.argument("run",UuidArgument.uuid()).executes(c->command(c.getSource(),()->{
            require();var entry=journal.entry(UuidArgument.getUuid(c,"run"));c.getSource().sendSuccess(()->Component.literal(entry.toString()),false);return 1;}))));
        root.then(Commands.literal("abort").executes(c->command(c.getSource(),()->{
            var p=c.getSource().getPlayerOrException();return abort(p,lastRun(p));}))
            .then(Commands.argument("run",UuidArgument.uuid()).executes(c->command(c.getSource(),()->abort(c.getSource().getPlayerOrException(),UuidArgument.getUuid(c,"run"))))));
        e.getDispatcher().register(root);
    }
    private UUID lastRun(ServerPlayer p) {
        if(!p.getPersistentData().hasUUID(LAST_RUN))throw new IllegalStateException("No last run; use the UUID printed at start");
        return p.getPersistentData().getUUID(LAST_RUN);
    }
    private int ready(ServerPlayer p,ServerPlayer leader) {
        require();ready.entrySet().removeIf(x->server.getTickCount()-x.getValue().tick>1200);
        if(!near(p)||!near(leader)||!team(p).equals(team(leader))||(!ready.containsKey(p.getUUID())&&ready.size()>=64))throw new IllegalStateException("Join at the lunar site, same team, maximum 64 pending consents");
        ready.put(p.getUUID(),new Ready(team(p),leader.getUUID(),server.getTickCount()));
        p.sendSystemMessage(Component.literal("Pret 60 secondes. Chef de rencontre: "+leader.getGameProfile().getName()+". Le chef lance /lunar_encounter start. Oxygene natif requis; aucun air fourni."));return 1;
    }
    private int start(ServerPlayer leader) throws IOException {
        require();UUID team=team(leader);
        if(!admission.attempt(leader.getUUID(),server.getTickCount()))throw new IllegalStateException("Start request cooldown");
        List<UUID> roster=ready.entrySet().stream().filter(x->validReady(x.getKey(),x.getValue(),leader.getUUID(),team)).map(Map.Entry::getKey).sorted().toList();
        if(!roster.contains(leader.getUUID())||roster.size()>8)throw new IllegalStateException("Consent required from 1..8 players, including leader");
        var raid=new RaidMachine(UUID.randomUUID(),RaidMachine.Identity.SEALED_GREENHOUSE,new RaidMachine.Group(team,Set.of(leader.getUUID()),roster),config.rules(),level.random.nextLong());
        if(!journal.admissionOpen()||!bridge.start(raid,center))throw new IllegalStateException("Start refused: occupied, unloaded, claimed or invalid site");
        roster.forEach(ready::remove);
        for(UUID id:roster){var p=server.getPlayerList().getPlayer(id);p.getPersistentData().putUUID(LAST_RUN,raid.view().run());p.sendSystemMessage(Component.literal("Relais lunaire commence | "+raid.view().run()+" | Premiere recompense dans FTB Quests, pas de lot par combat. A=(-12,0), B=(12,0), C=(0,12) depuis le centre. Echec: aucun objet preleve, pret puis start pour reessayer."));}
        return 1;
    }
    private int abort(ServerPlayer p,UUID run) {
        require();if(!journal.entry(run).group().delegates().contains(p.getUUID()))throw new SecurityException("Only the frozen leader may abort");
        bridge.abort(run);return 1;
    }
    @FunctionalInterface private interface Command {int run()throws Exception;}
    private int command(CommandSourceStack source,Command op){
        try{return op.run();}catch(Exception ex){
            if(journal!=null&&!journal.healthy())bridge.closeService(new IllegalStateException("Journal command failed",ex));
            source.sendFailure(Component.literal("[LunarEncounter] Refuse: "+ex.getMessage()));return 0;
        }
    }
    private boolean available(){return server!=null&&journal!=null&&journal.healthy()&&bridge.healthy()&&failure.isEmpty();}
    private void require(){if(!available()||!server.isSameThread())throw new IllegalStateException("Encounter unavailable: "+failure);}
    private UUID team(ServerPlayer p){if(!FTBTeamsAPI.api().isManagerLoaded())throw new IllegalStateException("Teams not loaded");return FTBTeamsAPI.api().getManager().getTeamForPlayer(p).orElseThrow().getTeamId();}
    @Override public boolean capable(ServerPlayer p){return !(p instanceof net.neoforged.neoforge.common.util.FakePlayer)&&server.getPlayerList().getPlayer(p.getUUID())==p&&!PlayerReviveServer.isBleeding(p)&&p.isAlive()&&!p.isSpectator();}
    @Override public boolean sameTeam(ServerPlayer p,UUID beneficiary){return FTBTeamsAPI.api().isManagerLoaded()&&team(p).equals(beneficiary);}
    @Override public boolean arenaStillAllowed(ServerLevel world,BlockPos c){
        if(!available()||world!=level||!c.equals(center)||!FTBChunksAPI.api().isManagerLoaded())return false;
        var claims=FTBChunksAPI.api().getManager();
        for(int x=(c.getX()-32)>>4;x<=(c.getX()+32)>>4;x++)for(int z=(c.getZ()-32)>>4;z<=(c.getZ()+32)>>4;z++)
            if(!level.hasChunk(x,z)||claims.getChunk(new ChunkDimPos(level.dimension(),x,z))!=null)return false;
        return level.getWorldBorder().isWithinBounds(c.offset(-32,0,-32))&&level.getWorldBorder().isWithinBounds(c.offset(32,0,32));
    }
    @Override public boolean completionAllowed(ServerLevel world,BlockPos c){return arenaStillAllowed(world,c)&&terrain(c);}
    private boolean near(ServerPlayer p){return center!=null&&p.serverLevel()==level&&capable(p)&&SiteBounds.contains(center.getX(),center.getY(),center.getZ(),p.getX(),p.getY(),p.getZ());}
    private boolean validReady(UUID id,Ready r,UUID leader,UUID team){var p=server.getPlayerList().getPlayer(id);return p!=null&&near(p)&&r.leader.equals(leader)&&r.team.equals(team)&&team(p).equals(team)&&server.getTickCount()-r.tick>=0&&server.getTickCount()-r.tick<=1200;}
    @Override public boolean reserveDurably(RaidMachine raid,ServerLevel world,BlockPos c){
        require();var v=raid.view();
        if(world!=level||!c.equals(center)||v.identity()!=RaidMachine.Identity.SEALED_GREENHOUSE||v.group().delegates().size()!=1||!terrain(c))return false;
        UUID leader=v.group().delegates().iterator().next();
        for(UUID id:v.group().roster()){Ready r=ready.get(id);if(r==null||!validReady(id,r,leader,v.group().beneficiary()))return false;}
        try{journal.reserve(raid,0);return true;}catch(SqliteRaidJournal.CapacityDenied refused){return false;}catch(IOException ex){throw new UncheckedIOException(ex);}
    }
    @Override public boolean persistActorPlan(UUID run,Map<UUID,BlockPos> actors){require();Map<UUID,DurableRaidJournal.Position> plan=new HashMap<>();actors.forEach((id,p)->plan.put(id,new DurableRaidJournal.Position(p.getX(),p.getY(),p.getZ())));try{journal.actorPlan(run,plan);return true;}catch(SqliteRaidJournal.CapacityDenied refused){return false;}catch(IOException e){throw new UncheckedIOException(e);}}
    @Override public boolean settleDurably(RaidMachine raid,BlockPos arena){
        require();if(raid.view().phase()==RaidMachine.Phase.SUCCESS)EncounterSafety.requireAuthorization(()->completionAllowed(level,arena));
        try{
            journal.settle(raid);
            if(raid.view().phase()==RaidMachine.Phase.SUCCESS)for(UUID id:raid.view().group().roster()) {
                var p=server.getPlayerList().getPlayer(id);if(p!=null)observeQuietly(p,raid.view().run());
            }
            return true;
        }catch(IOException e){throw new UncheckedIOException(e);}
    }
    private Optional<LunarEncounterApi.Completion> completion(ServerPlayer p,UUID run) {
        require();
        if(p==null||p.server!=server||!capable(p)||run==null)return Optional.empty();
        final RewardLedger.Entry entry;
        try{entry=journal.entry(run);}catch(IllegalArgumentException missing){return Optional.empty();}
        if(!entry.group().roster().contains(p.getUUID())||!sameTeam(p,entry.group().beneficiary())
            ||!Set.of(RewardLedger.Status.AVAILABLE,RewardLedger.Status.CLAIMING,RewardLedger.Status.CLAIMED,RewardLedger.Status.REVIEW).contains(entry.status()))return Optional.empty();
        return Optional.of(new LunarEncounterApi.Completion("renaissance_lunar",config.site(),"lunar_relay",run,entry.group().beneficiary(),entry.group().roster()));
    }
    private void observe(ServerPlayer p,UUID run) {
        if(completion(p,run).isEmpty())return;
        p.getPersistentData().putUUID(SUCCESS_RUN,run);
        var advancement=server.getAdvancements().get(ResourceLocation.parse(LunarEncounterApi.ADVANCEMENT));
        if(advancement==null)throw new IllegalStateException("Missing relay_complete advancement; no synthetic completion");
        p.getAdvancements().award(advancement,"durable_success");
    }
    private void observeQuietly(ServerPlayer p,UUID run) {
        try{observe(p,run);}catch(RuntimeException ex){LogUtils.getLogger().warn("[LunarEncounter] Presentation could not observe durable run {}; use observe after resolving error",run,ex);}
    }
    private boolean terrain(BlockPos c){
        if(!arenaStillAllowed(level,c))return false;
        Set<BlockPos> terminals=Set.of(c.offset(-12,0,0),c.offset(12,0,0),c.offset(0,0,12));
        for(int x=-32;x<=32;x++)for(int z=-32;z<=32;z++){
            BlockPos p=c.offset(x,0,z);if(!level.getBlockState(p.below()).isCollisionShapeFullBlock(level,p.below())||level.getBlockEntity(p.below())!=null)return false;
            if(terminals.contains(p)){if(!level.getBlockState(p).is(Blocks.COPPER_BLOCK))return false;}else if(!level.getBlockState(p).isAir())return false;
            for(int y=1;y<=3;y++)if(!level.getBlockState(p.above(y)).isAir())return false;
        }return true;
    }
}
