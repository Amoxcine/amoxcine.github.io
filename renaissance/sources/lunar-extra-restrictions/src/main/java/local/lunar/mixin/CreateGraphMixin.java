package local.lunar.mixin;

import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNodeLocation.DiscoveredLocation;
import com.simibubi.create.content.trains.track.BezierConnection;
import local.lunar.TrackGuards;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TrackGraph.class, remap = false)
public abstract class CreateGraphMixin {
    @Inject(method = "connectNodes", at = @At("HEAD"), cancellable = true, require = 1)
    private void lunar$beforeEdges(LevelAccessor level, DiscoveredLocation first, DiscoveredLocation second,
            BezierConnection curve, CallbackInfo ci) {
        if (TrackGuards.crossing(first, second)) ci.cancel();
    }
}
