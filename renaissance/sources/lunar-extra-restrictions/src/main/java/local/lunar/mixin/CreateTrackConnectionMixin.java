package local.lunar.mixin;

import com.simibubi.create.api.contraption.train.PortalTrackProvider;
import com.simibubi.create.content.trains.track.TrackBlock;
import com.simibubi.create.content.trains.track.TrackShape;
import local.lunar.LunarExtraRestrictions;
import local.lunar.TrackGuards;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(value = TrackBlock.class, remap = false)
public abstract class CreateTrackConnectionMixin {
    @Inject(method = "connectToPortal", at = @At("HEAD"), cancellable = true, require = 1)
    private void lunar$source(ServerLevel level, BlockPos pos, BlockState state, CallbackInfo ci) {
        TrackGuards.beginAttempt();
        if (LunarExtraRestrictions.shared(level)) ci.cancel();
    }

    @Inject(method = "connectToPortal", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/api/contraption/train/PortalTrackProvider$Exit;level()Lnet/minecraft/server/level/ServerLevel;", ordinal = 0),
            locals = LocalCapture.CAPTURE_FAILHARD, cancellable = true, require = 1)
    private void lunar$resolved(ServerLevel level, BlockPos pos, BlockState state, CallbackInfo ci,
            TrackShape shape, Direction.Axis axis, boolean found, String failure, BlockPos failedPos,
            Direction[] directions, int length, int index, Direction direction, BlockPos portalPos,
            BlockState portalState, PortalTrackProvider.Exit exit) {
        if (LunarExtraRestrictions.nativePortal(level, exit.level().dimension().location().toString())) {
            TrackGuards.consumeRejectedExit();
            ci.cancel();
        }
    }

    @Inject(method = "connectToPortal", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;destroyBlock(Lnet/minecraft/core/BlockPos;Z)Z"),
            cancellable = true, require = 1)
    private void lunar$preserveEntrance(ServerLevel level, BlockPos pos, BlockState state, CallbackInfo ci) {
        if (TrackGuards.consumeRejectedExit()) ci.cancel();
    }

    @Inject(method = "connectToPortal", at = @At("RETURN"), require = 1)
    private void lunar$cleanup(ServerLevel level, BlockPos pos, BlockState state, CallbackInfo ci) {
        TrackGuards.consumeRejectedExit();
    }
}
