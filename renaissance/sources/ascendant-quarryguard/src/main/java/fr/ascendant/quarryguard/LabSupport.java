package fr.ascendant.quarryguard;

import com.mojang.authlib.GameProfile;
import com.yogpc.qp.machine.Area;
import com.yogpc.qp.machine.quarry.QuarryEntity;
import com.yogpc.qp.machine.advquarry.AdvQuarryEntity;
import dev.ftb.mods.ftbteams.data.TeamManagerImpl;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.network.TextFilter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** No public test entry point may mutate a world before this boundary check. */
final class LabSupport {
    private LabSupport() { }

    static Path requireLab(MinecraftServer server) throws Exception {
        check(Boolean.getBoolean("ascendant.quarryguard.lab") && server.isSameThread(), "lab/server thread required");
        check(server.getPlayerCount() == 0 && "127.0.0.1".equals(server.getLocalIp()), "empty loopback server required");
        Path world = server.getWorldPath(LevelResource.ROOT).toRealPath();
        String profile = world.getParent().getFileName().toString();
        check(world.getFileName().toString().equals("quarryguard-lab-world")
            && (profile.equals("runtime") || profile.equals("full-runtime"))
            && world.getParent().getParent().getFileName().toString().equals("quarryguard-lab"), "not a disposable laboratory path");
        check(server.overworld().getChunkSource().getGenerator() instanceof FlatLevelSource, "flat world required");
        check(GuardHooks.status().contains("ready=true"), "guard not ready");
        return world;
    }

    static ServerPlayer actor(MinecraftServer server, TeamManagerImpl teams, String name) {
        UUID id = UUID.randomUUID();
        teams.playerLoggedIn(null, id, name);
        teams.getPersonalTeamForPlayerID(id).setOnline(false);
        ServerPlayer actor = new ServerPlayer(server, server.overworld(), new GameProfile(id, name), ClientInformation.createDefault()) {
            @Override public TextFilter getTextFilter() { return TextFilter.DUMMY; }
        };
        actor.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), actor,
            CommonListenerCookie.createInitial(actor.getGameProfile(), false)) {
            @Override public void send(Packet<?> packet) { }
            @Override public void send(Packet<?> packet, PacketSendListener listener) { }
        };
        return actor;
    }

    static BlockEntity place(ServerLevel level, ServerPlayer actor, BlockPos pos, String id) {
        return place(level, actor, pos, id, true);
    }

    static BlockEntity place(ServerLevel level, ServerPlayer actor, BlockPos pos, String id, boolean expectGuard) {
        check(level.getBlockState(pos).isAir() && level.getBlockState(pos.below()).isAir(), "fixture already occupied at " + pos);
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        actor.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() - 2.5);
        actor.setYRot(0);
        actor.setXRot(0);
        var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse("quarryplus:" + id));
        check(item instanceof BlockItem, "missing block item " + id);
        ItemStack stack = new ItemStack(item, 1);
        actor.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var hit = new BlockHitResult(Vec3.atBottomCenterOf(pos), Direction.UP, pos.below(), false);
        var result = ((BlockItem)item).place(new BlockPlaceContext(actor, InteractionHand.MAIN_HAND, stack, hit));
        check(result.consumesAction() && stack.isEmpty(), "native placement failed " + id);
        BlockEntity machine = level.getBlockEntity(pos);
        check(GuardHooks.isQuarry(machine), "native machine missing");
        check(machine.getPersistentData().hasUUID("ascendant_quarryguard_owner") == expectGuard, "wrong guarded/baseline artifact");
        if (expectGuard) check(GuardHooks.mayWork(machine), "native machine not authorized");
        return machine;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static void tick(BlockEntity machine) {
        var level = machine.getLevel();
        var state = machine.getBlockState();
        BlockEntityTicker<BlockEntity> ticker = ((EntityBlock)state.getBlock()).getTicker(level, state, (BlockEntityType)machine.getType());
        check(ticker != null, "native ticker unavailable");
        ticker.tick(level, machine.getBlockPos(), state, machine);
    }

    static Area area(BlockEntity machine) {
        return machine instanceof QuarryEntity quarry ? quarry.getArea() : ((AdvQuarryEntity)machine).getArea();
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
