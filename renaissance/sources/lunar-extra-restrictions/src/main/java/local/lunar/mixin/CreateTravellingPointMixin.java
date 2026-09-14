package local.lunar.mixin;

import com.simibubi.create.content.trains.entity.TravellingPoint;
import local.lunar.TrackGuards;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = TravellingPoint.class, remap = false)
public abstract class CreateTravellingPointMixin {
    @ModifyVariable(method = "travel(Lcom/simibubi/create/content/trains/graph/TrackGraph;DLcom/simibubi/create/content/trains/entity/TravellingPoint$ITrackSelector;Lcom/simibubi/create/content/trains/entity/TravellingPoint$IEdgePointListener;Lcom/simibubi/create/content/trains/entity/TravellingPoint$ITurnListener;Lcom/simibubi/create/content/trains/entity/TravellingPoint$IPortalListener;)D",
            at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 1)
    private TravellingPoint.IPortalListener lunar$portalVeto(TravellingPoint.IPortalListener original) {
        return endpoints -> TrackGuards.crossing(endpoints.getFirst(), endpoints.getSecond()) || original.test(endpoints);
    }
}
