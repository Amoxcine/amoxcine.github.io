package fr.ascendant.lunar.travel.mixin;

import earth.terrarium.adastra.common.network.packets.ServerboundLandPacket;
import fr.ascendant.lunar.travel.NativeFlight;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.*;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "earth.terrarium.adastra.common.network.packets.ServerboundLandPacket$Type", remap = false)
public abstract class LandPacketMixin {
    @Redirect(method = "lambda$handle$0", at = @At(value = "INVOKE",
        target = "Learth/terrarium/adastra/common/handlers/LaunchingDimensionHandler;addSpawnLocation(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/server/level/ServerLevel;)V"), require = 1)
    private static void lunar$deferHistory(Player player, ServerLevel source) {
        NativeFlight.packetHistory(player, source);
    }
    @Inject(method = "lambda$handle$0", at = @At("HEAD"), cancellable = true, require = 1)
    private static void lunar$validate(ServerboundLandPacket packet, Player player, CallbackInfo ci) {
        if (NativeFlight.rejectPacket(player, packet.dimension(), packet.tryPreviousLocation())) ci.cancel();
    }
    @Redirect(method = "lambda$handle$0", at = @At(value = "INVOKE",
        target = "Learth/terrarium/adastra/common/utils/ModUtils;land(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;)V"), require = 1)
    private static void lunar$land(ServerPlayer player, ServerLevel target, Vec3 pos) {
        NativeFlight.landFromPacket(player, target, pos);
    }
}
