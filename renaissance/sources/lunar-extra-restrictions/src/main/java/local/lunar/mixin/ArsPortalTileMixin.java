package local.lunar.mixin;

import com.hollingsworth.arsnouveau.common.block.tile.PortalTile;
import com.hollingsworth.arsnouveau.common.items.data.WarpScrollData;
import local.lunar.LunarExtraRestrictions;
import local.lunar.PortalGuards;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PortalTile.class, remap = false)
public abstract class ArsPortalTileMixin {
    @Inject(method = "warp", at = @At("HEAD"), cancellable = true, require = 1)
    private void lunar$warp(Entity entity, CallbackInfo ci) {
        var tile = (PortalTile) (Object) this;
        if (LunarExtraRestrictions.nativePortal(entity.level(), tile.dimID)) ci.cancel();
    }

    @Inject(method = "setFromScroll", at = @At("HEAD"), cancellable = true, require = 1)
    private void lunar$bind(WarpScrollData data, CallbackInfo ci) {
        if (PortalGuards.ars(((PortalTile) (Object) this).getLevel(), data)) ci.cancel();
    }

    @Inject(method = "teleportEntityTo", at = @At("HEAD"), cancellable = true, require = 1)
    private static void lunar$beforeEntityMutation(Entity entity, Level target, BlockPos pos, Vec2 rotation, CallbackInfoReturnable<Entity> cir) {
        if (LunarExtraRestrictions.nativePortal(entity.level(), target == null ? null : target.dimension().location().toString())) cir.setReturnValue(null);
    }
}
