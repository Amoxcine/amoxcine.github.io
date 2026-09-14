package fr.ascendant.renaissance.qio.mixin;

import fr.ascendant.renaissance.qio.QioEndpointGate;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "me.ramidzkh.mekae2.qio.QioStorageAdapter", remap = false)
public abstract class AppliedQioStorageMixin {
    @Shadow @Final private BlockEntity dashboard;

    @Inject(method = "insert(Lappeng/api/stacks/AEKey;JLappeng/api/config/Actionable;Lappeng/api/networking/security/IActionSource;)J",
        at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void renaissanceQio$insert(CallbackInfoReturnable<Long> cir) {
        if (QioEndpointGate.denied(dashboard)) cir.setReturnValue(0L);
    }

    @Inject(method = "extract(Lappeng/api/stacks/AEKey;JLappeng/api/config/Actionable;Lappeng/api/networking/security/IActionSource;)J",
        at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void renaissanceQio$extract(CallbackInfoReturnable<Long> cir) {
        if (QioEndpointGate.denied(dashboard)) cir.setReturnValue(0L);
    }

    @Inject(method = "getAvailableStacks(Lappeng/api/stacks/KeyCounter;)V",
        at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void renaissanceQio$list(CallbackInfo ci) {
        if (QioEndpointGate.denied(dashboard)) ci.cancel();
    }
}
