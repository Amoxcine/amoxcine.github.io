package fr.ascendant.renaissance.mekanism.mixin;

import fr.ascendant.renaissance.mekanism.MekEndpointGate;
import fr.ascendant.renaissance.mekanism.EndpointChemicalAccess;
import fr.ascendant.renaissance.mekanism.EndpointChemicalTanks;
import java.util.List;
import mekanism.api.chemical.IChemicalTank;
import mekanism.common.content.entangloporter.InventoryFrequency;
import mekanism.common.tile.TileEntityQuantumEntangloporter;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TileEntityQuantumEntangloporter.class, remap = false)
public abstract class QuantumEndpointMixin implements EndpointChemicalAccess {
    @Unique private boolean ascendant$permissionKnown;
    @Unique private boolean ascendant$lastPermission;
    @Unique private EndpointChemicalTanks ascendant$chemicalTanks;

    @Override
    public List<IChemicalTank> ascendant$chemicalViews(InventoryFrequency frequency, List<IChemicalTank> tanks) {
        var endpoint = (TileEntityQuantumEntangloporter) (Object) this;
        if (!MekEndpointGate.isRenaissance(endpoint) || endpoint.getLevel().isClientSide()) return tanks;
        if (ascendant$chemicalTanks == null) ascendant$chemicalTanks = new EndpointChemicalTanks(endpoint);
        return ascendant$chemicalTanks.views(frequency, tanks);
    }

    @Redirect(method = "lambda$new$2()Ljava/util/List;", at = @At(value = "INVOKE",
        target = "Lmekanism/common/content/entangloporter/InventoryFrequency;getChemicalTanks(Lnet/minecraft/core/Direction;)Ljava/util/List;"),
        require = 1, allow = 1)
    private List<IChemicalTank> ascendant$wrapChemicalSlotInfo(InventoryFrequency frequency, Direction side) {
        return ascendant$chemicalViews(frequency, frequency.getChemicalTanks(side));
    }

    @Redirect(method = "getBufferChemicalTank()Lmekanism/api/chemical/IChemicalTank;", at = @At(value = "INVOKE",
        target = "Lmekanism/common/content/entangloporter/InventoryFrequency;getChemicalTanks(Lnet/minecraft/core/Direction;)Ljava/util/List;"),
        require = 1, allow = 1)
    private List<IChemicalTank> ascendant$wrapComputerTank(InventoryFrequency frequency, Direction side) {
        return ascendant$chemicalViews(frequency, frequency.getChemicalTanks(side));
    }

    @Inject(method = "getFreq()Lmekanism/common/content/entangloporter/InventoryFrequency;",
        at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void ascendant$filterLocalFrequency(CallbackInfoReturnable<InventoryFrequency> callback) {
        if (!MekEndpointGate.allowed((TileEntityQuantumEntangloporter) (Object) this))
            callback.setReturnValue(null);
    }

    // Use the existing tile tick only for cache notifications; never cancel the base tick.
    @Inject(method = "onUpdateServer()Z", at = @At("HEAD"), require = 1, allow = 1)
    private void ascendant$notifyPermissionEdge(CallbackInfoReturnable<Boolean> callback) {
        var endpoint = (TileEntityQuantumEntangloporter) (Object) this;
        if (!MekEndpointGate.isRenaissance(endpoint)) return;
        boolean now = MekEndpointGate.allowed(endpoint);
        if (!ascendant$permissionKnown || now != ascendant$lastPermission) {
            ascendant$permissionKnown = true;
            ascendant$lastPermission = now;
            MekEndpointGate.authorizationChanged(endpoint);
        }
    }
}
