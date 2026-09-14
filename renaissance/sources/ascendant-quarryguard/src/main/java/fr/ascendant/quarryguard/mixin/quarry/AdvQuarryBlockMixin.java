package fr.ascendant.quarryguard.mixin.quarry;

import com.yogpc.qp.machine.advquarry.AdvQuarryBlock;
import com.yogpc.qp.machine.marker.QuarryMarker;
import fr.ascendant.quarryguard.GuardHooks;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AdvQuarryBlock.class, remap = false)
public abstract class AdvQuarryBlockMixin {
    @Inject(method = "setPlacedBy(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)V",
        at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void quarryguard$confirmPlacement(Level level, BlockPos pos, BlockState state,
                                              LivingEntity placer, ItemStack stack, CallbackInfo ci) {
        if (!level.isClientSide() && !GuardHooks.confirmPlacement(level, pos, state, placer)) {
            ci.cancel();
        }
    }

    @Redirect(method = "setPlacedBy(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)V",
        at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;findAny()Ljava/util/Optional;",
            remap = false), remap = false, require = 1)
    private Optional<QuarryMarker.Link> quarryguard$useApprovedLink(Stream<QuarryMarker.Link> ignored) {
        // Never let orElseGet compute an unapproved fallback, including default areas.
        return Optional.of(GuardHooks.approvedLink().orElseThrow(
            () -> new IllegalStateException("Missing approved advanced Quarry placement link")));
    }
}
