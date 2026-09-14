package local.lunar.mixin;

import io.redspace.ironsspellbooks.block.portal_frame.PortalFrameBlock;
import io.redspace.ironsspellbooks.block.portal_frame.PortalFrameBlockEntity;
import local.lunar.LunarExtraRestrictions;
import local.lunar.PortalGuards;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PortalFrameBlock.class, remap = false)
public abstract class IronFrameActivationMixin {
    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true, require = 1)
    private void lunar$beforeActivation(BlockState state, Level level, BlockPos pos, Entity entity, CallbackInfo ci) {
        if (!LunarExtraRestrictions.enabled(level)) return;
        if (LunarExtraRestrictions.shared(level)
                || level.getBlockEntity(pos) instanceof PortalFrameBlockEntity frame && PortalGuards.iron(level, frame.getUUID())) ci.cancel();
    }
}
