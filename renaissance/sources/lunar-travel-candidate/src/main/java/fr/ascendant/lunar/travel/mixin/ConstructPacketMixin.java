package fr.ascendant.lunar.travel.mixin;

import earth.terrarium.adastra.common.network.packets.ServerboundConstructSpaceStationPacket;
import fr.ascendant.lunar.travel.LunarTravel;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "earth.terrarium.adastra.common.network.packets.ServerboundConstructSpaceStationPacket$Type", remap = false)
public abstract class ConstructPacketMixin {
    @Inject(method = "lambda$handle$0", at = @At("HEAD"), cancellable = true, require = 1)
    private static void lunar$deny(ServerboundConstructSpaceStationPacket packet, Player player, CallbackInfo ci) {
        if (LunarTravel.active(player)) ci.cancel();
    }
}

