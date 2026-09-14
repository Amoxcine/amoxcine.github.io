package fr.ascendant.renaissance.mekanism.mixin;

import fr.ascendant.renaissance.mekanism.EndpointChemicalAccess;
import java.util.List;
import mekanism.api.chemical.IChemicalTank;
import mekanism.common.capabilities.holder.QuantumEntangloporterConfigHolder;
import mekanism.common.capabilities.holder.chemical.QuantumEntangloporterChemicalTankHolder;
import mekanism.common.tile.TileEntityQuantumEntangloporter;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = QuantumEntangloporterChemicalTankHolder.class, remap = false)
public abstract class QuantumChemicalHolderMixin extends QuantumEntangloporterConfigHolder<IChemicalTank> {
    protected QuantumChemicalHolderMixin(TileEntityQuantumEntangloporter endpoint) { super(endpoint); }

    @Inject(method = "getTanks(Lnet/minecraft/core/Direction;)Ljava/util/List;",
        at = @At("RETURN"), cancellable = true, require = 1, allow = 1)
    private void ascendant$wrapTanks(Direction side, CallbackInfoReturnable<List<IChemicalTank>> callback) {
        callback.setReturnValue(((EndpointChemicalAccess) entangloporter)
            .ascendant$chemicalViews(entangloporter.getFreq(), callback.getReturnValue()));
    }
}
