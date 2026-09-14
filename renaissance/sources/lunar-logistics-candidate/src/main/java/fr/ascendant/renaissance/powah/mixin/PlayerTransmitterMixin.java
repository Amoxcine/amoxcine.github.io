package fr.ascendant.renaissance.powah.mixin;

import fr.ascendant.renaissance.powah.PowahGate;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import owmii.powah.block.transmitter.PlayerTransmitterTile;
import owmii.powah.util.ChargeUtil;

@Mixin(value = PlayerTransmitterTile.class, remap = false)
public abstract class PlayerTransmitterMixin {
    // The source and recipient are checked immediately before credit; native debit receives zero on denial.
    @Redirect(method = "postTick(Lnet/minecraft/world/level/Level;)I", at = @At(value = "INVOKE",
        target = "Lowmii/powah/util/ChargeUtil;chargeItemsInPlayerInv(Lnet/minecraft/world/entity/player/Player;JJ)J"),
        require = 1, allow = 1)
    private long lunar$charge(Player player, long rate, long stored) {
        return PowahGate.allowsCharge((PlayerTransmitterTile) (Object) this, player)
            ? ChargeUtil.chargeItemsInPlayerInv(player, rate, stored) : 0L;
    }
}
