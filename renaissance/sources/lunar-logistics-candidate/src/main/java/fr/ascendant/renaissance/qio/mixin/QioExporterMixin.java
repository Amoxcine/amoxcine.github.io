package fr.ascendant.renaissance.qio.mixin;

import fr.ascendant.renaissance.qio.QioEndpointGate;
import mekanism.common.lib.inventory.TransitRequest;
import mekanism.common.tile.qio.TileEntityQIOExporter;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TileEntityQIOExporter.class, remap = false)
public abstract class QioExporterMixin {
    @Inject(method = "tryEject(Lmekanism/common/content/qio/QIOFrequency;)V",
        at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void renaissanceQio$export(CallbackInfo ci) {
        if (QioEndpointGate.denied((BlockEntity) (Object) this)) ci.cancel();
    }

    @Inject(method = "canSendHome(Lnet/minecraft/world/item/ItemStack;)Z",
        at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void renaissanceQio$simulateReturn(CallbackInfoReturnable<Boolean> cir) {
        if (QioEndpointGate.denied((BlockEntity) (Object) this)) cir.setReturnValue(false);
    }

    @Inject(method = "sendHome(Lmekanism/common/lib/inventory/TransitRequest;)Lmekanism/common/lib/inventory/TransitRequest$TransitResponse;",
        at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void renaissanceQio$return(TransitRequest request,
            CallbackInfoReturnable<TransitRequest.TransitResponse> cir) {
        if (QioEndpointGate.denied((BlockEntity) (Object) this)) {
            cir.setReturnValue(request.getEmptyResponse());
        }
    }
}
