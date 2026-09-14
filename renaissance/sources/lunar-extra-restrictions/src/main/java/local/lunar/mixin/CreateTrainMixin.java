package local.lunar.mixin;

import com.simibubi.create.content.trains.entity.Train;
import local.lunar.TrackGuards;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Train.class, remap = false)
public abstract class CreateTrainMixin {
    @Inject(method = "earlyTick", at = @At("HEAD"), cancellable = true, require = 1)
    private void lunar$early(Level level, CallbackInfo ci) {
        if (TrackGuards.straddling((Train) (Object) this)) ci.cancel();
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, require = 1)
    private void lunar$tick(Level level, CallbackInfo ci) {
        if (TrackGuards.straddling((Train) (Object) this)) ci.cancel();
    }
}
