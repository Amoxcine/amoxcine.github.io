package local.lunar.mixin;

import com.simibubi.create.content.trains.entity.Carriage;
import local.lunar.TrackGuards;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Carriage.class, remap = false)
public abstract class CreateCarriageMixin {
    @Inject(method = "manageEntities", at = @At("HEAD"), cancellable = true, require = 1)
    private void lunar$beforeRecreation(Level level, CallbackInfo ci) {
        if (TrackGuards.straddling((Carriage) (Object) this)) ci.cancel();
    }

    @Inject(method = "updateContraptionAnchors", at = @At("HEAD"), cancellable = true, require = 1)
    private void lunar$beforeAnchors(CallbackInfo ci) {
        if (TrackGuards.straddling((Carriage) (Object) this)) ci.cancel();
    }
}
