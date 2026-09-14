package local.lunar.mixin;

import com.simibubi.create.api.contraption.train.PortalTrackProvider;
import com.simibubi.create.content.trains.track.AllPortalTracks;
import local.lunar.LunarExtraRestrictions;
import local.lunar.TrackGuards;
import net.createmod.catnip.math.BlockFace;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Portal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AllPortalTracks.class, remap = false)
public abstract class CreateAllPortalTracksMixin {
    @Inject(method = "fromPortal", at = @At("HEAD"), cancellable = true, require = 1)
    private static void lunar$beforeDestination(ServerLevel source, BlockFace face, ResourceKey<Level> worldA,
            ResourceKey<Level> worldB, Portal portal, CallbackInfoReturnable<PortalTrackProvider.Exit> cir) {
        if (LunarExtraRestrictions.nativePortal(source, worldA == null ? null : worldA.location().toString())
                || LunarExtraRestrictions.nativePortal(source, worldB == null ? null : worldB.location().toString())) {
            TrackGuards.rejectExit();
            cir.setReturnValue(null);
        }
    }
}
