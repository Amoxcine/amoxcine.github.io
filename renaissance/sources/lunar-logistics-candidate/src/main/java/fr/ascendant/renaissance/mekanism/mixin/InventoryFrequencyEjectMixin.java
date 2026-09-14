package fr.ascendant.renaissance.mekanism.mixin;

import fr.ascendant.renaissance.mekanism.MekEndpointGate;
import mekanism.common.content.entangloporter.InventoryFrequency;
import mekanism.common.tile.TileEntityQuantumEntangloporter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = InventoryFrequency.class, remap = false)
public abstract class InventoryFrequencyEjectMixin {
    @Redirect(method = "handleEject(J)V", at = @At(value = "INVOKE",
        target = "Lmekanism/common/tile/TileEntityQuantumEntangloporter;canFunction()Z"),
        require = 1, allow = 1)
    private boolean ascendant$skipClosedEndpoint(TileEntityQuantumEntangloporter endpoint) {
        return endpoint.canFunction() && MekEndpointGate.allowed(endpoint);
    }
}
