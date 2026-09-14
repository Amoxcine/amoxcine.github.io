package fr.ascendant.etrionic.mixin;

import earth.terrarium.adastra.common.blockentities.machines.EtrionicBlastFurnaceBlockEntity;
import earth.terrarium.common_storage_lib.storage.base.ValueStorage;
import fr.ascendant.etrionic.EtrionicGuard;
import fr.ascendant.etrionic.GuardPolicy;
import net.minecraft.world.item.crafting.BlastingRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=EtrionicBlastFurnaceBlockEntity.class, remap=false)
public abstract class EtrionicBlastingMixin {
    @Inject(method="recipeTick(Learth/terrarium/common_storage_lib/storage/base/ValueStorage;)V",
        at=@At("HEAD"), cancellable=true, require=1, expect=1, allow=1)
    private void ascendant$blockBlastingTick(ValueStorage energy, CallbackInfo ci) {
        var machine=(EtrionicBlastFurnaceBlockEntity)(Object)this;
        if(GuardPolicy.cancelTick(EtrionicGuard.active(machine), machine.mode()==EtrionicBlastFurnaceBlockEntity.Mode.ALLOYING)) {
            EtrionicGuard.recordTickVeto();
            ci.cancel();
        }
    }
    // This native method is blasting-only; the alloying completion has a separate method.
    @Inject(method="craft(Lnet/minecraft/world/item/crafting/BlastingRecipe;I)V",
        at=@At("HEAD"), cancellable=true, require=1, expect=1, allow=1)
    private void ascendant$blockBlastingCompletion(BlastingRecipe recipe, int slot, CallbackInfo ci) {
        if(GuardPolicy.cancelBlastingCraft(EtrionicGuard.active((EtrionicBlastFurnaceBlockEntity)(Object)this))) {
            EtrionicGuard.recordCraftVeto();
            ci.cancel();
        }
    }
}
