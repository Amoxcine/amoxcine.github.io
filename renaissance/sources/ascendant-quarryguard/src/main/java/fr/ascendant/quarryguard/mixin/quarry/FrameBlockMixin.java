package fr.ascendant.quarryguard.mixin.quarry;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yogpc.qp.machine.misc.FrameBlock;
import fr.ascendant.quarryguard.GuardHooks;
import java.util.function.BiPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = FrameBlock.class, remap = false)
public abstract class FrameBlockMixin {
    @Shadow private boolean breaking;
    @Shadow @Final private static BiPredicate<Level, BlockPos> HAS_NEIGHBOUR_LIQUID;
    @Unique private static final ThreadLocal<GuardHooks.FrameCleanup> quarryguard$cleanup = new ThreadLocal<>();

    @WrapMethod(method = "onRemove(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)V",
        remap = false, require = 1)
    private void quarryguard$restoreRecursionGuard(BlockState oldState, Level level, BlockPos pos,
                                                  BlockState newState, boolean moving, Operation<Void> original) {
        boolean previous = breaking;
        try {
            original.call(oldState, level, pos, newState, moving);
        } finally {
            breaking = previous;
        }
    }

    @WrapMethod(method = "breakChain(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V",
        remap = false, require = 1)
    private void quarryguard$cleanupScope(Level level, BlockPos origin, Operation<Void> original) {
        if (level.isClientSide()) return;
        GuardHooks.FrameCleanup scope = GuardHooks.beginFrameCleanup(level, origin);
        if (scope == null) return;
        GuardHooks.FrameCleanup previous = quarryguard$cleanup.get();
        quarryguard$cleanup.set(scope);
        try {
            original.call(level, origin);
        } finally {
            if (previous == null) quarryguard$cleanup.remove();
            else quarryguard$cleanup.set(previous);
        }
    }

    @Redirect(method = "breakChain(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"),
        remap = false, require = 1, expect = 1, allow = 1)
    private BlockState quarryguard$stopAtBoundary(Level level, BlockPos pos) {
        return GuardHooks.mayCleanFrame(quarryguard$cleanup.get(), level, pos)
            ? level.getBlockState(pos) : Blocks.AIR.defaultBlockState();
    }

    @Redirect(method = "lambda$breakChain$3(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"),
        remap = false, require = 1, expect = 1, allow = 1)
    private static boolean quarryguard$recheckRemoval(Level level, BlockPos pos, boolean moving) {
        GuardHooks.FrameCleanup scope = quarryguard$cleanup.get();
        if (!GuardHooks.mayCleanFrame(scope, level, pos)
            || HAS_NEIGHBOUR_LIQUID.test(level, pos)
            || !(level.getBlockState(pos).getBlock() instanceof FrameBlock)) return false;
        // Reading a block/fluid can invoke other mods; check authority again before mutation.
        return GuardHooks.mayCleanFrame(scope, level, pos) && level.removeBlock(pos, moving);
    }
}
