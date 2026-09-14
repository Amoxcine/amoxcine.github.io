package fr.ascendant.renaissance.powah.mixin;

import fr.ascendant.renaissance.powah.PowahGate;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import owmii.powah.block.ender.AbstractEnderTile;

@Mixin(value = AbstractEnderTile.class, remap = false)
public abstract class EnderTileMixin {
    @Inject(method = "receiveEnergy(JZLnet/minecraft/core/Direction;)J",
        at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void renaissance$receive(long amount, boolean simulate, Direction side, CallbackInfoReturnable<Long> ci) {
        if (!PowahGate.allowsEndpoint((BlockEntity) (Object) this)) ci.setReturnValue(0L);
    }

    @Inject(method = "extractEnergy(JZLnet/minecraft/core/Direction;)J",
        at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void renaissance$extract(long amount, boolean simulate, Direction side, CallbackInfoReturnable<Long> ci) {
        if (!PowahGate.allowsEndpoint((BlockEntity) (Object) this)) ci.setReturnValue(0L);
    }

    @Inject(method = "canReceiveEnergy(Lnet/minecraft/core/Direction;)Z",
        at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void renaissance$canReceive(Direction side, CallbackInfoReturnable<Boolean> ci) {
        if (!PowahGate.allowsEndpoint((BlockEntity) (Object) this)) ci.setReturnValue(false);
    }

    @Inject(method = "canExtractEnergy(Lnet/minecraft/core/Direction;)Z",
        at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void renaissance$canExtract(Direction side, CallbackInfoReturnable<Boolean> ci) {
        if (!PowahGate.allowsEndpoint((BlockEntity) (Object) this)) ci.setReturnValue(false);
    }

    @Inject(method = "canInsert(ILnet/minecraft/world/item/ItemStack;)Z",
        at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void renaissance$insertExtender(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> ci) {
        if (slot == 0 && !PowahGate.allowsEndpoint((BlockEntity) (Object) this)) ci.setReturnValue(false);
    }

    // Leave the native prelude intact. In 6.2.10 this point is inside the slot-0 branch,
    // before extender callbacks, shared Energy access, mutation, shrink, or sound.
    @Inject(method = "onSlotChanged(I)V", at = @At(value = "INVOKE",
        target = "Lowmii/powah/block/ender/AbstractEnderTile;isExtender()Z"),
        cancellable = true, require = 1, allow = 1)
    private void renaissance$absorbExtender(int slot, CallbackInfo ci) {
        if (!PowahGate.allowsEndpoint((BlockEntity) (Object) this)) ci.cancel();
    }
}
