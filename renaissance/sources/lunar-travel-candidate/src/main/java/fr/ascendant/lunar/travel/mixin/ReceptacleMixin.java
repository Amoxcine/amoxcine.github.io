package fr.ascendant.lunar.travel.mixin;

import com.brandon3055.draconicevolution.blocks.tileentity.TileDislocatorReceptacle;
import com.brandon3055.brandonscore.utils.TargetPos;
import fr.ascendant.lunar.travel.LunarTravel;
import net.minecraft.world.entity.Entity;
import java.util.List;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TileDislocatorReceptacle.class, remap = false)
public abstract class ReceptacleMixin {
    @Shadow private List<Entity> teleportQ;
    @Shadow private TargetPos getTargetPos() { throw new AssertionError(); }

    @Inject(method = "handleEntityTeleport", at = @At("HEAD"), cancellable = true, require = 1)
    private void lunar$queue(Entity entity, CallbackInfo ci) {
        if (!LunarTravel.active(entity)) return;
        TargetPos target = getTargetPos();
        if (LunarTravel.blocked(entity, target == null ? null : target.getDimension())) ci.cancel();
    }
    @Inject(method = "tick", at = @At("HEAD"), require = 1)
    private void lunar$pending(CallbackInfo ci) {
        if (teleportQ.isEmpty() || teleportQ.stream().noneMatch(LunarTravel::active)) return;
        TargetPos target = getTargetPos();
        // Remove only denied pending requests, never entities or their inventories. Recheck bound targets.
        teleportQ.removeIf(entity -> LunarTravel.blocked(entity, target == null ? null : target.getDimension()));
    }
}
