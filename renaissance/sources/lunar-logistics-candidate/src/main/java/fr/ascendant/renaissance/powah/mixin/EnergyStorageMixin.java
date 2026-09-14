package fr.ascendant.renaissance.powah.mixin;

import fr.ascendant.renaissance.powah.PowahGate;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import owmii.powah.block.ender.AbstractEnderTile;
import owmii.powah.lib.block.AbstractEnergyStorage;

@Mixin(value = AbstractEnergyStorage.class, remap = false)
public abstract class EnergyStorageMixin {
    @Inject(method = "chargeItems(II)J", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void renaissance$chargeEnderSlots(int first, int end, CallbackInfoReturnable<Long> ci) {
        if ((Object) this instanceof AbstractEnderTile<?> endpoint && !PowahGate.allowsEndpoint(endpoint)) {
            ci.setReturnValue(0L);
        }
    }

    // Native canExtract runs earlier; capability lookup can reenter before this credit.
    // Once the receiver is called, native credit/debit is not a transaction we can roll back.
    @Redirect(method = "extractFromSides(Lnet/minecraft/world/level/Level;)J", at = @At(value = "INVOKE",
        target = "Lnet/neoforged/neoforge/energy/IEnergyStorage;receiveEnergy(IZ)I"), require = 1, allow = 1)
    private int renaissance$beforeNeighborCredit(IEnergyStorage receiver, int amount, boolean simulate) {
        if ((Object) this instanceof AbstractEnderTile<?> endpoint && !PowahGate.allowsEndpoint(endpoint)) return 0;
        return receiver.receiveEnergy(amount, simulate);
    }
}
