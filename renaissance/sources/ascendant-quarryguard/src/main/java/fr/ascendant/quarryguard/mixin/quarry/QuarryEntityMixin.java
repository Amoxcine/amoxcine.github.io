package fr.ascendant.quarryguard.mixin.quarry;

import com.yogpc.qp.machine.Area;
import com.yogpc.qp.machine.PickIterator;
import com.yogpc.qp.machine.WorkResult;
import com.yogpc.qp.machine.misc.BlockBreakEventResult;
import com.yogpc.qp.machine.quarry.QuarryEntity;
import fr.ascendant.quarryguard.GuardHooks;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = QuarryEntity.class, remap = false)
public abstract class QuarryEntityMixin {
    @Shadow private PickIterator<BlockPos> targetIterator;
    @Shadow private Set<BlockPos> skipped;
    @Shadow BlockPos targetPos;
    @Shadow public Vec3 head;
    @Shadow public Vec3 targetHead;

    @Unique
    private BlockEntity quarryguard$machine() {
        return (BlockEntity) (Object) this;
    }

    @Inject(method = "serverTick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lcom/yogpc/qp/machine/quarry/QuarryEntity;)V",
        at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private static void quarryguard$beforeTick(Level level, BlockPos pos, BlockState state,
                                               QuarryEntity machine, CallbackInfo ci) {
        if (!GuardHooks.mayWork(machine)) {
            ci.cancel();
        }
    }

    @Inject(method = {"waiting()V", "breakInsideFrame()V", "makeFrame()V", "moveHead()V",
        "breakBlock()V", "removeFluid()V",
        "removeFluidAt(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/block/state/BlockState;)V"},
        at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void quarryguard$beforeWork(CallbackInfo ci) {
        if (!GuardHooks.mayWork(quarryguard$machine())) {
            ci.cancel();
        }
    }

    @Inject(method = {"breakBlock(Lnet/minecraft/core/BlockPos;)Lcom/yogpc/qp/machine/WorkResult;",
        "breakBlockModuleOverride(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;F)Lcom/yogpc/qp/machine/WorkResult;"},
        at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void quarryguard$beforeWorkResult(CallbackInfoReturnable<WorkResult> cir) {
        if (!GuardHooks.mayWork(quarryguard$machine())) {
            cir.setReturnValue(WorkResult.FAIL_EVENT);
        }
    }

    @Inject(method = "breakBlock(Lnet/minecraft/core/BlockPos;)Lcom/yogpc/qp/machine/WorkResult;",
        at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void quarryguard$checkBlockTarget(BlockPos pos, CallbackInfoReturnable<WorkResult> cir) {
        if (!GuardHooks.withinArea(quarryguard$machine(), pos.getX(), pos.getZ(), false)) cir.setReturnValue(WorkResult.FAIL_EVENT);
    }

    @Inject(method = "afterBreak(Lnet/minecraft/world/level/Level;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Ljava/util/List;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/block/state/BlockState;)Lcom/yogpc/qp/machine/misc/BlockBreakEventResult;",
        at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void quarryguard$beforeAfterBreak(CallbackInfoReturnable<BlockBreakEventResult> cir) {
        if (!GuardHooks.mayWork(quarryguard$machine())) {
            cir.setReturnValue(BlockBreakEventResult.CANCELED);
        }
    }

    @Inject(method = "setState(Lcom/yogpc/qp/machine/quarry/QuarryState;Lnet/minecraft/world/level/block/state/BlockState;)V",
        at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void quarryguard$beforeState(@Coerce Object proposed, BlockState state, CallbackInfo ci) {
        BlockEntity machine = quarryguard$machine();
        if (!(proposed instanceof Enum<?> next)) {
            ci.cancel();
            return;
        }
        if (!next.name().equals("FINISHED") && (machine.getLevel() == null
            || (!machine.getLevel().isClientSide() && !GuardHooks.mayWork(machine)))) {
            ci.cancel();
        }
    }

    @Inject(method = "setArea(Lcom/yogpc/qp/machine/Area;)V",
        at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void quarryguard$beforeArea(Area proposed, CallbackInfo ci) {
        BlockEntity machine = quarryguard$machine();
        if (machine.getLevel() != null && machine.getLevel().isClientSide()) {
            return;
        }
        if (machine.getLevel() == null) {
            ci.cancel();
            return;
        }
        String currentState = ((QuarryEntity) (Object) this)
            .toClientTag(new CompoundTag(), machine.getLevel().registryAccess()).getString("state");
        if ((!currentState.equals("FINISHED") && !currentState.equals("WAITING"))
            || !GuardHooks.maySetArea(machine, proposed)) {
            ci.cancel();
            return;
        }
        targetIterator = null;
        targetPos = null;
        skipped.clear();
        head = Vec3.atBottomCenterOf(machine.getBlockPos());
        targetHead = head;
    }

    @Redirect(method = {"makeFrame()V",
        "removeFluidAt(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/block/state/BlockState;)V"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z",
        remap = false), remap = false, require = 1)
    private boolean quarryguard$beforeFrameWrite(Level level, BlockPos pos, BlockState state, int flags) {
        return GuardHooks.mayWrite(quarryguard$machine(), pos) && level.setBlock(pos, state, flags);
    }

    @Redirect(method = "breakBlockModuleOverride(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;F)Lcom/yogpc/qp/machine/WorkResult;",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z",
            remap = false), remap = false, require = 1)
    private boolean quarryguard$beforeModuleWrite(ServerLevel level, BlockPos pos, BlockState state, int flags) {
        return GuardHooks.mayWrite(quarryguard$machine(), pos) && level.setBlock(pos, state, flags);
    }

    @Redirect(method = "breakBlock(Lnet/minecraft/core/BlockPos;)Lcom/yogpc/qp/machine/WorkResult;",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;",
            remap = false), remap = false, require = 3)
    private <T extends Entity> List<T> quarryguard$filterCollection(ServerLevel level, Class<T> type,
                                                                  AABB bounds, Predicate<? super T> predicate) {
        List<T> entities = new ArrayList<>(level.getEntitiesOfClass(type, bounds, predicate));
        BlockEntity machine = quarryguard$machine();
        entities.removeIf(entity -> !GuardHooks.mayCollect(machine, entity));
        return entities;
    }

    @Inject(method = "loadAdditional(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)V",
        at = @At("RETURN"), remap = false, require = 1)
    private void quarryguard$loadOwner(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        GuardHooks.loadOwner(quarryguard$machine(), tag);
    }

    @Inject(method = "saveAdditional(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)V",
        at = @At("HEAD"), remap = false, require = 1)
    private void quarryguard$saveOwner(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        GuardHooks.saveOwner(quarryguard$machine(), tag);
    }
}
