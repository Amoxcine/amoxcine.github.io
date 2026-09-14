package fr.ascendant.renaissance.mekanism.mixin;

import fr.ascendant.renaissance.mekanism.MekEndpointGate;
import mekanism.api.heat.ISidedHeatHandler;
import mekanism.common.capabilities.proxy.ProxyHeatHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ProxyHeatHandler.class, remap = false)
public abstract class CachedHeatProxyMixin {
    @Shadow @Final private ISidedHeatHandler heatHandler;

    @Inject(method = "getTotalInverseConduction()D", at = @At("HEAD"),
        cancellable = true, require = 1, allow = 1)
    private void ascendant$insulateCachedTotal(CallbackInfoReturnable<Double> callback) {
        if (MekEndpointGate.closedHeatHandler(heatHandler))
            callback.setReturnValue(Double.POSITIVE_INFINITY);
    }

    @Inject(method = "getInverseConduction(I)D", at = @At("HEAD"),
        cancellable = true, require = 1, allow = 1)
    private void ascendant$insulateCachedCapacitor(int capacitor, CallbackInfoReturnable<Double> callback) {
        if (MekEndpointGate.closedHeatHandler(heatHandler))
            callback.setReturnValue(Double.POSITIVE_INFINITY);
    }
}
