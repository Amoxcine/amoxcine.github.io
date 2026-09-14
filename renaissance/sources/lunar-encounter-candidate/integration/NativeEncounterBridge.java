package fr.ascendant.lunar.encounter;

import java.util.*;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Bounded lunar adapter. Rebind on server startup, not per arena. */
public final class NativeEncounterBridge {
    public interface CampaignPorts {
        // Must validate FTB rights, prebuilt arena, beneficiary and ALL ready players. No item escrow.
        // Return true only AFTER durable reservation; false MUST mean no reservation was performed.
        boolean reserveDurably(RaidMachine raid, ServerLevel level, BlockPos center);
        // Persist these exact actor UUIDs/positions before spawning; reconcile them on startup.
        boolean persistActorPlan(UUID run, Map<UUID, BlockPos> actors);
        // Must idempotently record the result; NO physical grant here or in commands.
        boolean settleDurably(RaidMachine raid, BlockPos center);
        boolean capable(ServerPlayer player);
        boolean sameTeam(ServerPlayer player, UUID beneficiary);
        boolean arenaStillAllowed(ServerLevel level, BlockPos center);
        boolean completionAllowed(ServerLevel level, BlockPos center);
        void serviceFailed(RuntimeException failure);
    }
    private record Actor(UUID run, RaidMachine.Token token, int task, boolean module, BlockPos position) {}
    private record Active(RaidMachine raid, BlockPos center, Map<UUID, Actor> actors) {}
    private static final int MAX_ARENAS = 1;
    private final Map<UUID, Active> active = new HashMap<>();
    private final Map<UUID, Actor> actors = new HashMap<>();
    private final Map<UUID, Integer> recentlyDead = new HashMap<>();
    private MinecraftServer server;
    private ServerLevel level;
    private CampaignPorts ports;
    private boolean healthy;

    public NativeEncounterBridge() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> guard(() -> tick(e)));
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock e) -> guard(() -> interact(e)));
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (LivingDamageEvent.Pre e) -> guard(() -> damage(e)));
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (LivingDeathEvent e) -> guard(() -> death(e)));
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (LivingDropsEvent e) -> guard(() -> drops(e)));
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (LivingExperienceDropEvent e) -> guard(() -> experience(e)));
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> guard(() -> stopping(e)));
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (LivingIncomingDamageEvent e) -> guard(() -> incoming(e)));
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (LivingChangeTargetEvent e) -> guard(() -> target(e)));
        NeoForge.EVENT_BUS.addListener((EntityTravelToDimensionEvent e) -> {
            if (actors.containsKey(e.getEntity().getUUID())) e.setCanceled(true);
        });
    }
    public void bind(MinecraftServer server, ServerLevel level, CampaignPorts ports) {
        if (!server.isSameThread()
                || !level.dimension().location().toString().equals(LunarConfig.DIMENSION)
                || level.getServer() != server || !active.isEmpty()) throw new IllegalStateException("Lunar bind refused");
        this.server = server; this.level = level; this.ports = Objects.requireNonNull(ports); healthy = true;
    }
    public boolean start(RaidMachine raid, BlockPos center) {
        requireThread();
        if (!healthy || active.size() >= MAX_ARENAS || active.containsKey(raid.view().run())
                || raid.view().generation() != 1 || raid.view().activeTicks() != 0
                || raid.view().remainingHealth() > 1000
                || Math.abs((long)center.getX()) > 29_999_900 || Math.abs((long)center.getZ()) > 29_999_900
                || center.getY() < level.getMinBuildHeight() + 2 || center.getY() > level.getMaxBuildHeight() - 10)
            return false;
        // At most 25 loaded chunks; no requests to generate or force-load them.
        for (int x = (center.getX() - 32) >> 4; x <= (center.getX() + 32) >> 4; x++)
            for (int z = (center.getZ() - 32) >> 4; z <= (center.getZ() + 32) >> 4; z++)
                if (!level.hasChunk(x, z)) return false;
        for (Active a : active.values()) if (Math.abs((long)a.center.getX() - center.getX()) < 65
                && Math.abs((long)a.center.getZ() - center.getZ()) < 65) return false;
        for (UUID id : raid.view().group().roster()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (!capable(p, center)) return false;
        }
        if (!durable(() -> ports.reserveDurably(raid, level, center))) return false;
        Active a = new Active(raid, center.immutable(), new HashMap<>());
        active.put(raid.view().run(), a);
        return EncounterSafety.contain(() -> announce(a), this::closeService) && healthy;
    }
    public RaidMachine.View view(UUID run) { requireThread(); return required(run).raid.view(); }
    public boolean ownsActive(UUID actor) { return actors.containsKey(actor); }
    public boolean healthy() { return healthy; }
    public void abort(UUID run) {
        requireThread(); Active a = required(run);
        if (!EncounterSafety.contain(() -> {a.raid.abort(); refresh(a, -1);}, this::closeService) || !healthy)
            throw new IllegalStateException("Abort cleanup/persistence failed; encounter service closed");
    }

    private void tick(ServerTickEvent.Post event) {
        if (event.getServer() != server || !healthy || server.getTickCount() % 20 != 0) return;
        recentlyDead.entrySet().removeIf(entry -> server.getTickCount() - entry.getValue() >= 0);
        for (Active a : List.copyOf(active.values())) {
            if (!healthy) break;
            int oldGeneration = a.raid.view().generation();
            if (!ports.arenaStillAllowed(level, a.center)) { a.raid.actorLost(); refresh(a, oldGeneration); continue; }
            Set<UUID> present = new HashSet<>();
            for (UUID id : a.raid.view().group().roster()) if (participant(a,server.getPlayerList().getPlayer(id))) present.add(id);
            for (UUID id : List.copyOf(a.actors.keySet())) {
                var entity = level.getEntity(id);
                if (!(entity instanceof LivingEntity living) || !living.isAlive()) { a.raid.actorLost(); break; }
                if (!a.raid.view().run().equals(marker(entity)) || !inside(a,entity)) { a.raid.actorLost(); break; }
                if (entity instanceof Mob mob && !a.actors.get(id).module) {
                    ServerPlayer target = present.isEmpty() ? null : server.getPlayerList().getPlayer(present.iterator().next());
                    mob.setTarget(target);
                }
            }
            a.raid.advance(20, present);
            if (!present.isEmpty() && a.raid.view().phase()==RaidMachine.Phase.ROUTING) {
                long elapsed=a.raid.view().phaseTicks();
                if(elapsed==200) message(a,"Surcharge dans 5 secondes: neutralisez les defenseurs actifs.");
                if(elapsed>=300) {
                    for(var signal:a.raid.view().signals()) if(!signal.defenderInterrupted()) {
                        a.raid.sabotageCompleted(a.raid.token(),signal.task());break;
                    }
                }
            }
            refresh(a, oldGeneration);
            if (!a.raid.terminal() && server.getTickCount()%100==0) markers(a);
        }
    }
    private void interact(PlayerInteractEvent.RightClickBlock event) {
        if (!healthy || event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player) || player.server != server
                || event.getHand() != InteractionHand.MAIN_HAND || player.serverLevel() != level) return;
        for (Active a : active.values()) {
            var view=a.raid.view();
            if (view.identity()!=RaidMachine.Identity.SEALED_GREENHOUSE || !view.group().roster().contains(player.getUUID())
                    || !capable(player,a.center) || !ports.sameTeam(player,view.group().beneficiary())) continue;
            for (int receiver=0;receiver<3;receiver++) {
                if (!event.getPos().equals(terminal(a.center,receiver)) || !atTerminal(player,a.center,receiver)) continue;
                if (!ports.arenaStillAllowed(level,a.center)) {a.raid.actorLost();refresh(a,-1);return;}
                event.setCanceled(true);
                showMenu(a,player,receiver);
                return;
            }
        }
    }
    /** Explicit player choice, with the submitted generation checked before any mutation. */
    public RaidMachine.Result choose(ServerPlayer player,RaidMachine.Token token,int task,int receiver,RaidMachine.Action action) {
        requireThread();
        Active a=token==null?null:active.get(token.run());
        if (!healthy || a==null || a.raid.terminal() || !a.raid.token().equals(token))
            return reject(player,"Choix p\u00e9rim\u00e9 : relisez le circuit. Aucune p\u00e9nalit\u00e9.");
        try {
            var view=a.raid.view();
            if (view.identity()!=RaidMachine.Identity.SEALED_GREENHOUSE || player==null || player.server!=server
                    || !view.group().roster().contains(player.getUUID()) || !capable(player,a.center)
                    || !ports.sameTeam(player,view.group().beneficiary()))
                return reject(player,"Ce circuit est r\u00e9serv\u00e9 aux participants aptes de l'\u00e9quipe inscrite.");
            if(task<0||task>=view.signals().size()||receiver<0||receiver>2||action==null)
                return reject(player,"Choix de circuit invalide.");
            if(!atTerminal(player,a.center,receiver))
                return reject(player,"Approchez le r\u00e9cepteur choisi \u00e0 trois blocs, en ligne de vue.");
            if(!ports.arenaStillAllowed(level,a.center)) {
                a.raid.actorLost();refresh(a,view.generation());
                return reject(player,"Site indisponible : op\u00e9ration interrompue.");
            }
            var result=a.raid.interact(player.getUUID(),token,task,receiver,action);
            player.displayClientMessage(Component.literal(switch(result) {
                case APPLIED -> "Circuit "+(task+1)+" valid\u00e9.";
                case WRONG_ROUTE -> "Mauvais routage : une marge perdue. Relisez les signaux.";
                case IGNORED -> "Confirmation ferm\u00e9e, circuit d\u00e9j\u00e0 valid\u00e9 ou d\u00e9fenseur encore actif.";
            }),true);
            if(result!=RaidMachine.Result.IGNORED)refresh(a,view.generation());
            return result;
        } catch(RuntimeException failure) {closeService(failure);return RaidMachine.Result.IGNORED;}
    }
    private RaidMachine.Result reject(ServerPlayer player,String reason) {
        if(player!=null)player.displayClientMessage(Component.literal(reason),true);
        return RaidMachine.Result.IGNORED;
    }
    private boolean atTerminal(ServerPlayer player,BlockPos center,int receiver) {
        BlockPos target=terminal(center,receiver);
        if(player.distanceToSqr(Vec3.atCenterOf(target))>9)return false;
        var hit=level.clip(new ClipContext(player.getEyePosition(),Vec3.atCenterOf(target),
                ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,player));
        return hit.getType()==HitResult.Type.BLOCK && hit.getBlockPos().equals(target);
    }
    private void showMenu(Active a,ServerPlayer player,int receiver) {
        var view=a.raid.view();
        for(String line:CircuitChoices.telegraphs(view))player.sendSystemMessage(Component.literal(line));
        player.sendSystemMessage(Component.literal("R\u00e9cepteur choisi : "+(char)('A'+receiver)));
        if(view.phase()!=RaidMachine.Phase.ROUTING)return;
        for(var signal:view.signals()) {
            if(signal.completed())continue;
            var row=Component.literal("Circuit "+(signal.task()+1)+" : ");
            for(var action:RaidMachine.Action.values()) {
                String command=CircuitChoices.command(a.raid.token(),signal.task(),receiver,action);
                row.append(Component.literal("["+CircuitChoices.label(action)+"] ").withStyle(style->
                        style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,command))));
            }
            player.sendSystemMessage(row);
        }
    }
    private void damage(LivingDamageEvent.Pre event) {
        if (!healthy || event.getEntity().level() != level) return;
        Actor actor = actors.get(event.getEntity().getUUID());
        if (actor == null || !actor.module) return;
        float amount = event.getNewDamage();
        event.setNewDamage(0);
        Active a = active.get(actor.run);
        if (a == null || !(event.getSource().getEntity() instanceof ServerPlayer p)
                || !participant(a,p) || !inside(a,event.getEntity())) return;
        int old = a.raid.view().generation();
        EncounterSafety.damageAuthorized(a.raid, p.getUUID(), actor.token, amount,
                () -> ports.arenaStillAllowed(level, a.center)
                    && (a.raid.view().phase() != RaidMachine.Phase.CORE_EXPOSED
                        || amount < a.raid.view().remainingHealth() || ports.completionAllowed(level, a.center)));
        if (a.raid.view().generation() == old) event.getEntity().setHealth((float) a.raid.view().remainingHealth());
        refresh(a, old);
    }
    private void death(LivingDeathEvent event) {
        if (!healthy || event.isCanceled() || event.getEntity().level() != level) return;
        Actor actor = actors.remove(event.getEntity().getUUID());
        if (actor == null) return;
        // Native drops/XP arrive after death; retain only a bounded short-lived ownership receipt.
        if (recentlyDead.size() >= 256) {
            event.setCanceled(true);
            throw new IllegalStateException("Death receipt capacity reached");
        }
        recentlyDead.put(event.getEntity().getUUID(), server.getTickCount() + 60);
        Active a = active.get(actor.run);
        if (a == null) return;
        a.actors.remove(event.getEntity().getUUID());
        int old = a.raid.view().generation();
        if (actor.module) a.raid.actorLost();
        else a.raid.defenderInterrupted(actor.token, actor.task);
        refresh(a, old);
    }
    private void drops(LivingDropsEvent event) {
        if (event.getEntity().level() == level && (actors.containsKey(event.getEntity().getUUID())
                || recentlyDead.containsKey(event.getEntity().getUUID()))) event.setCanceled(true);
    }
    private void experience(LivingExperienceDropEvent event) {
        if (event.getEntity().level() == level && (actors.containsKey(event.getEntity().getUUID())
                || recentlyDead.containsKey(event.getEntity().getUUID()))) event.setDroppedExperience(0);
    }
    private void refresh(Active a, int oldGeneration) {
        if (a.raid.terminal()) {
            if (!durable(() -> ports.settleDurably(a.raid, a.center))) {
                if (healthy) closeService(new IllegalStateException("Settlement refused"));
                return;
            }
            cleanup(a); active.remove(a.raid.view().run()); announce(a); return;
        }
        boolean changed = a.raid.view().generation() != oldGeneration;
        if (changed) cleanup(a);
        var phase = a.raid.view().phase();
        List<Mob> planned = new ArrayList<>();
        if (phase == RaidMachine.Phase.ROUTING) {
            for (var signal : a.raid.view().signals()) {
                if (signal.defenderInterrupted()) continue;
                if (a.raid.view().group().roster().size() == 1 && signal.task() > 0
                        && !a.raid.view().signals().get(signal.task() - 1).completed()) continue;
                if (a.actors.values().stream().anyMatch(actor -> actor.task == signal.task())) continue;
                var mob = EntityType.IRON_GOLEM.create(level);
                if (mob == null) throw new IllegalStateException("Defender creation failed");
                mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40);
                mob.setHealth(40);
                mob.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(4);
                prepare(a, mob, terminal(a.center, signal.receiver()).above(), signal.task(), false);
                planned.add(mob);
            }
        } else if (changed && (phase == RaidMachine.Phase.EXPOSED || phase == RaidMachine.Phase.CORE_EXPOSED)) {
            var mob = EntityType.IRON_GOLEM.create(level);
            if (mob == null) throw new IllegalStateException("Module creation failed");
            mob.setNoAi(true);
            var maxHealth = mob.getAttribute(Attributes.MAX_HEALTH);
            if (maxHealth == null) throw new IllegalStateException("Module health attribute missing");
            maxHealth.setBaseValue(a.raid.view().remainingHealth());
            mob.setHealth((float)a.raid.view().remainingHealth());
            prepare(a, mob, a.center.above(), -1, true); planned.add(mob);
        }
        if (!changed && planned.isEmpty()) return;
        Map<UUID, BlockPos> plan = new HashMap<>();
        for (var entry : a.actors.entrySet()) plan.put(entry.getKey(), entry.getValue().position);
        if (!durable(() -> ports.persistActorPlan(a.raid.view().run(), Map.copyOf(plan)))) {
            if (healthy) {a.raid.actorLost();refresh(a,-1);}
            return;
        }
        for (Mob mob : planned) if (!level.addFreshEntity(mob)) { a.raid.actorLost(); refresh(a, -1); return; }
        announce(a);
    }
    private void prepare(Active a, Mob mob, BlockPos position, int task, boolean module) {
        mob.getPersistentData().putUUID("ascendantLunarEncounterRun", a.raid.view().run());
        mob.moveTo(position.getX() + .5, position.getY(), position.getZ() + .5, 0, 0);
        mob.setPersistenceRequired(); mob.setCanPickUpLoot(false);
        mob.setCustomName(Component.literal(module ? "Module expose" : "Defenseur " + (task + 1)));
        mob.setCustomNameVisible(true);
        Actor actor = new Actor(a.raid.view().run(), a.raid.token(), task, module, position);
        actors.put(mob.getUUID(), actor); a.actors.put(mob.getUUID(), actor);
    }
    private void cleanup(Active a) {
        RuntimeException failure = null;
        for (UUID id : List.copyOf(a.actors.keySet())) {
            actors.remove(id);
            try { discardExact(server, id, a.raid.view().run()); }
            catch (RuntimeException ex) {
                if (failure == null) failure = ex;
                else if (failure != ex) failure.addSuppressed(ex);
            }
        }
        a.actors.clear();
        if (failure != null) throw failure;
    }
    static UUID marker(net.minecraft.world.entity.Entity entity) {
        var data = entity.getPersistentData();
        return data.hasUUID("ascendantLunarEncounterRun") ? data.getUUID("ascendantLunarEncounterRun") : null;
    }
    static void discardExact(MinecraftServer server, UUID actor, UUID run) {
        EncounterSafety.discardExact(actor, run, server.getAllLevels(), ServerLevel::getEntity,
                NativeEncounterBridge::marker, net.minecraft.world.entity.Entity::discard);
    }
    private void announce(Active a) {
        var messages=CircuitChoices.telegraphs(a.raid.view());
        for (UUID id : a.raid.view().group().roster()) {
            var player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                for(String message:messages)player.sendSystemMessage(Component.literal(message));
                if(capable(player,a.center))player.playNotifySound(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP,net.minecraft.sounds.SoundSource.BLOCKS,.35f,1f);
            }
        }
        if(a.raid.view().phase()==RaidMachine.Phase.SUCCESS)
            message(a,"Victoire enregistree. Consultez la premiere recompense dans FTB Quests. Pret puis start pour rejouer; aucun nouveau lot de cuivre par combat.");
        if(a.raid.view().phase()==RaidMachine.Phase.FAILED||a.raid.view().phase()==RaidMachine.Phase.ABORTED)
            message(a,"Aucun objet preleve. Revenez au site: /lunar_encounter ready puis /lunar_encounter start.");
    }
    private void message(Active a,String text) {
        for(UUID id:a.raid.view().group().roster()) {
            var p=server.getPlayerList().getPlayer(id);if(p!=null)p.sendSystemMessage(Component.literal(text));
        }
    }
    private void markers(Active a) {
        for(UUID id:a.raid.view().group().roster()) {
            var p=server.getPlayerList().getPlayer(id);if(!participant(a,p))continue;
            for(int i=0;i<3;i++) {
                var b=terminal(a.center,i);
                level.sendParticles(p,ParticleTypes.ELECTRIC_SPARK,true,b.getX()+.5,b.getY()+1.3,b.getZ()+.5,i+1,.15,.2,.15,0);
            }
            p.displayClientMessage(Component.literal("Relais | "+a.raid.view().phase()+" | Stabilite "+(3-a.raid.view().errors())+"/3 | A ouest, B est, C sud"),true);
        }
    }
    private boolean inside(Active a,net.minecraft.world.entity.Entity entity) {
        return entity.level()==level&&SiteBounds.contains(a.center.getX(),a.center.getY(),a.center.getZ(),entity.getX(),entity.getY(),entity.getZ());
    }
    private boolean participant(Active a,ServerPlayer p) {
        return capable(p,a.center)&&a.raid.view().group().roster().contains(p.getUUID())&&ports.sameTeam(p,a.raid.view().group().beneficiary());
    }
    private void target(LivingChangeTargetEvent e) {
        Actor actor=actors.get(e.getEntity().getUUID());if(actor==null)return;
        var proposed=e.getNewAboutToBeSetTarget();
        e.setNewAboutToBeSetTarget(null);
        Active a=active.get(actor.run);
        if(healthy&&a!=null&&proposed instanceof ServerPlayer p&&participant(a,p))e.setNewAboutToBeSetTarget(proposed);
    }
    private void incoming(LivingIncomingDamageEvent e) {
        if(e.isCanceled())return;
        Actor attacker=e.getSource().getEntity()==null?null:actors.get(e.getSource().getEntity().getUUID());
        Actor victim=actors.get(e.getEntity().getUUID());
        if(attacker==null&&victim==null)return;
        // Keep the event canceled if any authorization lookup throws.
        e.setCanceled(true);
        if(attacker!=null) {
            Active a=active.get(attacker.run);
            if(!healthy||a==null||!inside(a,e.getSource().getEntity())||!(e.getEntity() instanceof ServerPlayer p)
                ||!participant(a,p)||!ports.arenaStillAllowed(level,a.center))return;
        }
        if(victim!=null) {
            Active a=active.get(victim.run);
            if(!healthy||a==null||!inside(a,e.getEntity())||!victim.run.equals(marker(e.getEntity()))
                ||!ports.arenaStillAllowed(level,a.center)
                ||(e.getSource().getEntity() instanceof ServerPlayer p&&!participant(a,p)))return;
        }
        e.setCanceled(false);
    }
    private void stopping(ServerStoppingEvent event) {
        if (event.getServer() != server) return;
        try {
            for (Active a : List.copyOf(active.values())) {
                try {
                    a.raid.serverRestart();
                    if (healthy && !durable(() -> ports.settleDurably(a.raid, a.center))) healthy = false;
                } catch (RuntimeException failure) { closeService(failure); }
                finally { EncounterSafety.contain(() -> cleanup(a), this::reportCleanupFailure); }
            }
        } finally {
            active.clear(); actors.clear(); recentlyDead.clear(); healthy = false; server = null; level = null; ports = null;
        }
    }
    private boolean capable(ServerPlayer p, BlockPos center) {
        return p != null && p.serverLevel() == level && p.isAlive() && !p.isSpectator()
                && SiteBounds.contains(center.getX(),center.getY(),center.getZ(),p.getX(),p.getY(),p.getZ()) && ports.capable(p);
    }
    private static BlockPos terminal(BlockPos center, int receiver) {
        return switch (receiver) { case 0 -> center.offset(-12, 0, 0); case 1 -> center.offset(12, 0, 0); default -> center.offset(0, 0, 12); };
    }
    private void requireThread() { if (server == null || !server.isSameThread()) throw new IllegalStateException("Server thread required"); }
    private boolean durable(BooleanSupplier operation) {
        try { return operation.getAsBoolean(); }
        catch (RuntimeException failure) {
            closeService(failure);
            return false;
        }
    }
    private void guard(Runnable event) { EncounterSafety.contain(event, this::closeService); }
    public void closeService(RuntimeException failure) {
        healthy = false;
        // No persistence on this failure path. Durable RESERVED/CLAIMING states recover on restart.
        if (ports != null) EncounterSafety.contain(() -> ports.serviceFailed(failure), this::reportCleanupFailure);
        for (Active a : List.copyOf(active.values()))
            EncounterSafety.contain(() -> cleanup(a), this::reportCleanupFailure);
        active.clear(); actors.clear();
        EncounterSafety.contain(() -> reportCleanupFailure(failure), ignored -> {});
    }
    private void reportCleanupFailure(RuntimeException failure) {
        com.mojang.logging.LogUtils.getLogger().error("[LunarEncounter] service closed; exact actor tombstones retained for recovery", failure);
    }
    private Active required(UUID run) {
        Active a = active.get(run); if (a == null) throw new IllegalArgumentException("No active run"); return a;
    }
}
