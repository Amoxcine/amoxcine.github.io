package fr.ascendant.renaissance.powah;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Ender pools are global; a transmitter is a separately verified direct source/recipient link. */
public final class PowahGate {

    private PowahGate() { }

    public static boolean allowsCharge(BlockEntity endpoint, net.minecraft.world.entity.player.Player player) {
        if (endpoint == null || !(player instanceof net.minecraft.server.level.ServerPlayer recipient)) return false;
        Level level = endpoint.getLevel();
        if (!currentEndpoint(endpoint, level) || !(level instanceof ServerLevel source)
            || recipient.serverLevel().getServer() != source.getServer()
            || !recipient.isAlive() || recipient.isRemoved() || recipient.hasDisconnected()
            || source.getServer().getPlayerList().getPlayer(recipient.getUUID()) != recipient) return false;
        return PowahPolicy.allowsDirectCharge(source.dimension().location().toString(),
            recipient.level().dimension().location().toString());
    }

    public static boolean allowsEndpoint(BlockEntity endpoint) {
        if (endpoint == null) return false;
        try {
            Level level = endpoint.getLevel();
            if (level == null) return false;
            return PowahPolicy.allows(level.isClientSide, level.dimension().location().toString(),
                () -> currentEndpoint(endpoint, level), null);
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    private static boolean currentEndpoint(BlockEntity endpoint, Level level) {
        if (!(level instanceof ServerLevel serverLevel)
            || !serverLevel.getServer().isSameThread() || endpoint.isRemoved()
            || endpoint.getLevel() != level
            || serverLevel.getServer().getLevel(serverLevel.dimension()) != serverLevel) return false;
        // Check presence before lookup: a retained adapter must not load a chunk or reach a replacement.
        return serverLevel.hasChunkAt(endpoint.getBlockPos())
            && serverLevel.getBlockEntity(endpoint.getBlockPos()) == endpoint;
    }

}
