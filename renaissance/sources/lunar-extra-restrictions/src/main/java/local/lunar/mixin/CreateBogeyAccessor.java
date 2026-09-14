package local.lunar.mixin;

import com.simibubi.create.content.trains.entity.CarriageBogey;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import net.createmod.catnip.data.Couple;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = CarriageBogey.class, remap = false)
public interface CreateBogeyAccessor {
    @Accessor("points")
    Couple<TravellingPoint> lunar$points();
}
