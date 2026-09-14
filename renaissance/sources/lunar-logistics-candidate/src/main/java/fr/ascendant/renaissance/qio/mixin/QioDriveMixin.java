package fr.ascendant.renaissance.qio.mixin;

import fr.ascendant.renaissance.qio.QioEndpointGate;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.content.qio.QIODriveData.QIODriveKey;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Stop lunar drives entering the global index, without clearing or rewriting their items. */
@Mixin(value = QIOFrequency.class, remap = false)
public abstract class QioDriveMixin {
    @Inject(method = "update(Lnet/minecraft/world/level/block/entity/BlockEntity;)Z",
        at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void lunar$register(BlockEntity endpoint, CallbackInfoReturnable<Boolean> ci) {
        if (QioEndpointGate.denied(endpoint)) ci.setReturnValue(false);
    }

    @Inject(method = "addDrive(Lmekanism/common/content/qio/QIODriveData$QIODriveKey;)V",
        at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void lunar$addDrive(QIODriveKey key, CallbackInfo ci) {
        if (!(key.holder() instanceof BlockEntity endpoint) || QioEndpointGate.denied(endpoint)) ci.cancel();
    }
}
