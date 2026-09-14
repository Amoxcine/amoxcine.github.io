package local.lunar.mixin;

import com.direwolf20.buildinggadgets2.util.BuildingUtils;
import local.lunar.LunarExtraRestrictions;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BuildingUtils.class, remap = false)
public abstract class BuildingBoundMixin {
    @Inject(method = "getHandlerFromBound", at = @At("HEAD"), cancellable = true, require = 1)
    private static void lunar$beforeLookup(Player player, GlobalPos bound, Direction side,
            CallbackInfoReturnable<IItemHandler> cir) {
        if (LunarExtraRestrictions.crossing(player.level(), bound)) cir.setReturnValue(null);
    }
}
