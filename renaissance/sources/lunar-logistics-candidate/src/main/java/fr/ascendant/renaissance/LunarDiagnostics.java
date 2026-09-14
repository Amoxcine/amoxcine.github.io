package fr.ascendant.renaissance;

import appeng.blockentity.qnb.QuantumBridgeBlockEntity;
import ascendant.renaissance.TransportPolicy;
import fr.ascendant.renaissance.mekanism.MekEndpointGate;
import fr.ascendant.renaissance.mixin.QuantumClusterAccess;
import fr.ascendant.renaissance.powah.PowahGate;
import fr.ascendant.renaissance.qio.QioEndpointGate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import mekanism.api.security.SecurityMode;
import mekanism.common.lib.frequency.Frequency;
import mekanism.common.lib.frequency.FrequencyType;
import mekanism.common.tile.TileEntityQuantumEntangloporter;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Explicit disposable fixtures only; adapted from LabCommands/MekLabProbe air-halo and identity checks. */
public final class LunarDiagnostics {
    private static final String MARKER = "ascendant_lunar_fixture";
    private static Fixture fixture;
    private static boolean ready;
    private record Site(ServerLevel level, BlockPos center) { }
    private record Fixture(UUID id, Site outside, Site moon) { }
    private LunarDiagnostics() { }

    public static void clear() { fixture = null; ready = false; }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("lunarlogistics")
            .requires(source -> LunarMixinPlugin.ENABLED && source.hasPermission(4))
            .then(Commands.literal("prepare").then(Commands.argument("overworld", BlockPosArgument.blockPos())
                .then(Commands.argument("moon", BlockPosArgument.blockPos()).executes(ctx ->
                    run(ctx.getSource(), () -> prepare(ctx.getSource(), BlockPosArgument.getBlockPos(ctx, "overworld"),
                        BlockPosArgument.getBlockPos(ctx, "moon"), LunarConfig.DIMENSION))))))
            .then(Commands.literal("prepare_orbit").then(Commands.argument("overworld", BlockPosArgument.blockPos())
                .then(Commands.argument("orbit", BlockPosArgument.blockPos()).executes(ctx ->
                    run(ctx.getSource(), () -> prepare(ctx.getSource(), BlockPosArgument.getBlockPos(ctx, "overworld"),
                        BlockPosArgument.getBlockPos(ctx, "orbit"), LunarConfig.ORBIT))))))
            .then(Commands.literal("probe").executes(ctx -> run(ctx.getSource(), LunarDiagnostics::probe)))
            .then(Commands.literal("forget").executes(ctx -> run(ctx.getSource(), () -> {
                clear(); return "Memory cleared only. Blocks/frequencies retained. Next prepare requires NEW empty sites.";
            }))));
    }

    private static int run(CommandSourceStack source, Supplier<String> action) {
        try {
            require(LunarMixinPlugin.ENABLED && source.hasPermission(4) && source.getServer().isSameThread(),
                "Enabled candidate, OP4 and server thread required");
            String result = action.get();
            source.sendSuccess(() -> Component.literal(result), true);
            com.mojang.logging.LogUtils.getLogger().info("[Lunar diagnostic] {}", result);
            return 1;
        } catch (RuntimeException failure) {
            source.sendFailure(Component.literal("Lunar diagnostic REFUSED/FAILED: " + failure.getMessage()));
            return 0;
        }
    }

    private static String prepare(CommandSourceStack source, BlockPos ow, BlockPos moon, String dimension) {
        require(fixture == null, "Fixture already attempted; no attach, resume or cached PASS");
        ServerLevel lunar = source.getServer().getLevel(ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.parse(dimension)));
        require(lunar != null, "Protected dimension missing: " + dimension);
        Site outside = new Site(source.getServer().overworld(), ow.immutable());
        Site inside = new Site(lunar, moon.immutable());
        for (Site site : List.of(outside, inside)) {
            for (BlockPos pos : BlockPos.betweenClosed(site.center.offset(-3, -2, -2), site.center.offset(12, 2, 2))) {
                require(!site.level.isOutsideBuildHeight(pos) && site.level.getWorldBorder().isWithinBounds(pos)
                    && site.level.hasChunkAt(pos) && site.level.isEmptyBlock(pos) && site.level.getBlockEntity(pos) == null,
                    "Need loaded empty 16x5x5 volumes; refused at " + site.level.dimension().location() + " " + pos);
            }
        }
        UUID id = UUID.randomUUID();
        fixture = new Fixture(id, outside, inside); // A partial attempt is never reused silently.
        var singularityId = ResourceLocation.parse("ae2:quantum_entangled_singularity");
        require(BuiltInRegistries.ITEM.containsKey(singularityId), "Missing singularity");
        var singularity = new ItemStack(BuiltInRegistries.ITEM.get(singularityId));
        QuantumBridgeBlockEntity.assignFrequency(singularity);
        for (Site site : List.of(outside, inside)) {
            for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++)
                place(site, x, y, x == 0 && y == 0 ? "ae2:quantum_link" : "ae2:quantum_ring");
            place(site, 2, 0, "ae2:creative_energy_cell");
            var qe = (TileEntityQuantumEntangloporter) place(site, 6, 0, "mekanism:quantum_entangloporter");
            qe.setFrequency(FrequencyType.INVENTORY,
                new Frequency.FrequencyIdentity("lunar_fixture_" + id, SecurityMode.PRIVATE, id), id);
            place(site, 8, 0, "mekanism:qio_importer");
            place(site, 10, 0, "powah:ender_cell_starter");
            var bridge = (QuantumBridgeBlockEntity) site.level.getBlockEntity(site.center);
            bridge.getInternalInventory().setItemDirect(0, singularity.copy());
            bridge.getInternalInventory().sendChangeNotification(0);
            bridge.setChanged();
        }
        ready = true;
        return "FRESH fixture=" + id + " dimension=" + dimension + "; 26 blocks placed; paired powered AE2 bridges and private QE frequency. "
            + "No grant/journal access. Wait for native ticks, then /lunarlogistics probe. No stock transfer PASS yet.";
    }

    private static BlockEntity place(Site site, int x, int y, String name) {
        var key = ResourceLocation.parse(name);
        require(BuiltInRegistries.BLOCK.containsKey(key), "Missing block " + name);
        var pos = site.center.offset(x, y, 0);
        require(site.level.setBlock(pos, BuiltInRegistries.BLOCK.get(key).defaultBlockState(), 3),
            "Placement failed; partial fixture retained at " + pos);
        var endpoint = site.level.getBlockEntity(pos);
        require(endpoint != null, "Block entity missing: " + name);
        endpoint.getPersistentData().putUUID(MARKER, fixture.id);
        endpoint.setChanged();
        return endpoint;
    }

    private static BlockEntity live(Site site, int offset) {
        var pos = site.center.offset(offset, 0, 0);
        require(site.level.hasChunkAt(pos), "Fixture chunk unloaded; no force load");
        var endpoint = site.level.getBlockEntity(pos);
        require(endpoint != null && !endpoint.isRemoved() && endpoint.getPersistentData().hasUUID(MARKER)
            && fixture.id.equals(endpoint.getPersistentData().getUUID(MARKER)), "Fresh fixture identity lost/replaced");
        return endpoint;
    }

    private static String probe() {
        require(fixture != null && ready, "Prepare a NEW fixture in this JVM first; old completed fixtures are never attached");
        var moonBridge = (QuantumBridgeBlockEntity) live(fixture.moon, 0);
        var owBridge = (QuantumBridgeBlockEntity) live(fixture.outside, 0);
        require(moonBridge.getQEFrequency() == owBridge.getQEFrequency(), "AE2 fixture singularities no longer match");
        for (var bridge : List.of(moonBridge, owBridge)) {
            require(bridge.isFormed() && bridge.isPowered() && bridge.getQEFrequency() != 0
                && bridge.getCluster() != null && bridge.getCluster().getActionableNode() != null,
                "AE2 not formed/powered/paired; disconnected alone is NOT a PASS");
            var connection = ((QuantumClusterAccess) bridge.getCluster()).renaissance$connection();
            require(connection == null || connection.getConnection() == null, "Crossdim AE2 connection exists");
            long partnerKey = ((QuantumClusterAccess) bridge.getCluster()).renaissance$otherSide();
            var expectedPartner = bridge == moonBridge ? owBridge : moonBridge;
            require(partnerKey != 0 && appeng.api.features.Locatables.quantumNetworkBridges()
                .get(bridge.getLevel(), partnerKey) == expectedPartner.getCluster(),
                "AE2 partner not registered/ready; absence alone is NOT a PASS");
        }
        require(QuantumHooks.filter(moonBridge.getCluster(), owBridge.getCluster()) == null
            && QuantumHooks.filter(owBridge.getCluster(), moonBridge.getCluster()) == null, "AE2 boundary opened");
        var moonQe = (TileEntityQuantumEntangloporter) live(fixture.moon, 6);
        var owQe = (TileEntityQuantumEntangloporter) live(fixture.outside, 6);
        var raw = owQe.getFrequency(FrequencyType.INVENTORY);
        require(raw != null && raw == moonQe.getFrequency(FrequencyType.INVENTORY)
            && raw.isValid() && !raw.isRemoved() && fixture.id.equals(raw.getOwner()), "Fresh native QE frequency not ready");
        require(owQe.getFreq() == raw && moonQe.getFreq() == null, "Native QE getFreq not gated");
        for (Direction side : Direction.values()) {
            require(moonQe.getInventorySlots(side).isEmpty() && moonQe.getFluidTanks(side).isEmpty()
                && moonQe.getChemicalTanks(side).isEmpty() && moonQe.getEnergyContainers(side).isEmpty()
                && moonQe.getHeatCapacitors(side).isEmpty(), "QE lunar containers exposed");
        }
        require(!MekEndpointGate.allowed(moonQe) && MekEndpointGate.allowed(owQe), "QE gate mismatch");
        require(QioEndpointGate.denied(live(fixture.moon, 8)) && !QioEndpointGate.denied(live(fixture.outside, 8)), "QIO gate mismatch");
        require(!PowahGate.allowsEndpoint(live(fixture.moon, 10)) && PowahGate.allowsEndpoint(live(fixture.outside, 10)), "Powah gate mismatch");
        var policy = new TransportPolicy(LunarConfig.DIMENSIONS);
        var lunar = new TransportPolicy.Endpoint(fixture.moon.level.dimension().location().toString(), fixture.id);
        var earth = new TransportPolicy.Endpoint("minecraft:overworld", fixture.id);
        for (Set<UUID> grants : List.of(Set.<UUID>of(), Set.of(fixture.id))) {
            require(!policy.directLink(lunar, earth, grants).allowed() && !policy.directLink(earth, lunar, grants).allowed()
                && !policy.sharedPool(lunar, grants).allowed(), "Synthetic existing grant bypassed permanent policy");
        }
        return "BOUNDED DIAGNOSTIC PASS fixture=" + fixture.id + ": native AE2 disconnected while ready; native QE frequency/list denial; "
            + "QIO/Powah endpoint gate decisions; grant-independent policy. NOT stock-transfer, QIO drive, menu, transmitter, restart or total-pack proof.";
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
