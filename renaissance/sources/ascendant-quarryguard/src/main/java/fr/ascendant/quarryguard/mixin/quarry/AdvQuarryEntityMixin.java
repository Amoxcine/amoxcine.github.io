package fr.ascendant.quarryguard.mixin.quarry;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yogpc.qp.machine.Area;
import com.yogpc.qp.machine.PickIterator;
import com.yogpc.qp.machine.WorkResult;
import com.yogpc.qp.machine.advquarry.AdvQuarryEntity;
import com.yogpc.qp.machine.misc.BlockBreakEventResult;
import fr.ascendant.quarryguard.GuardHooks;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AdvQuarryEntity.class, remap = false)
public abstract class AdvQuarryEntityMixin {
    @Unique
    private static final ThreadLocal<BlockEntity> quarryguard$collector = new ThreadLocal<>();

    @Shadow private PickIterator<BlockPos> targetIterator;
    @Shadow BlockPos targetPos;
    @Shadow boolean searchEnergyConsumed;

    @Unique
    private BlockEntity quarryguard$machine() {
        return (BlockEntity) (Object) this;
    }

    @WrapMethod(method = "breakBlocks(II)Lcom/yogpc/qp/machine/WorkResult;", remap = false, require = 1)
    private WorkResult quarryguard$collectionScope(int x, int z, Operation<WorkResult> original) {
        if (!GuardHooks.withinArea(quarryguard$machine(), x, z, true)
            || !GuardHooks.mayWork(quarryguard$machine())) return WorkResult.FAIL_EVENT;
        // The pinned XP query lives in a static lambda with no machine argument.
        BlockEntity previous = quarryguard$collector.get();
        quarryguard$collector.set(quarryguard$machine());
        try {
            return original.call(x, z);
        } finally {
            if (previous == null) {
                quarryguard$collector.remove();
            } else {
                quarryguard$collector.set(previous);
            }
        }
    }

    @Inject(method = "serverTick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lcom/yogpc/qp/machine/advquarry/AdvQuarryEntity;)V",
        at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private static void quarryguard$beforeTick(Level level, BlockPos pos, BlockState state,
                                               AdvQuarryEntity machine, CallbackInfo ci) {
        if (!GuardHooks.mayWork(machine)) {
            ci.cancel();
        }
    }

    @Inject(method = {"waiting()V", "startQuarryWork()V", "makeFrame()V", "breakBlock()V", "cleanUp()V",
        "removeFluidAt(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/block/state/BlockState;)V",
        "removeEdgeFluid(IILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;)V",
        "removeFluidAtXZ(IILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;)V"},
        at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void quarryguard$beforeWork(CallbackInfo ci) {
        if (!GuardHooks.mayWork(quarryguard$machine())) {
            ci.cancel();
        }
    }

    @Inject(method = {"breakOneBlock(Lnet/minecraft/core/BlockPos;)Lcom/yogpc/qp/machine/WorkResult;",
        "breakBlocks(II)Lcom/yogpc/qp/machine/WorkResult;", "cleanUpFluid(II)Lcom/yogpc/qp/machine/WorkResult;",
        "breakBlockModuleOverride(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;F)Lcom/yogpc/qp/machine/WorkResult;"},
        at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void quarryguard$beforeWorkResult(CallbackInfoReturnable<WorkResult> cir) {
        if (!GuardHooks.mayWork(quarryguard$machine())) {
            cir.setReturnValue(WorkResult.FAIL_EVENT);
        }
    }

    @Inject(method = "breakOneBlock(Lnet/minecraft/core/BlockPos;)Lcom/yogpc/qp/machine/WorkResult;",
        at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void quarryguard$checkBlockTarget(BlockPos pos, CallbackInfoReturnable<WorkResult> cir) {
        if (!GuardHooks.withinArea(quarryguard$machine(), pos.getX(), pos.getZ(), false)) cir.setReturnValue(WorkResult.FAIL_EVENT);
    }

    @Inject(method = "cleanUpFluid(II)Lcom/yogpc/qp/machine/WorkResult;",
        at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void quarryguard$checkCleanupTarget(int x, int z, CallbackInfoReturnable<WorkResult> cir) {
        if (!GuardHooks.withinArea(quarryguard$machine(), x, z, false)) cir.setReturnValue(WorkResult.FAIL_EVENT);
    }

    @Inject(method = "afterBreak(Lnet/minecraft/world/level/Level;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Ljava/util/List;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/block/state/BlockState;)Lcom/yogpc/qp/machine/misc/BlockBreakEventResult;",
        at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void quarryguard$beforeAfterBreak(CallbackInfoReturnable<BlockBreakEventResult> cir) {
        if (!GuardHooks.mayWork(quarryguard$machine())) {
            cir.setReturnValue(BlockBreakEventResult.CANCELED);
        }
    }

    @Inject(method = "setState(Lcom/yogpc/qp/machine/advquarry/AdvQuarryState;Lnet/minecraft/world/level/block/state/BlockState;)V",
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
        String currentState = ((AdvQuarryEntity) (Object) this)
            .toClientTag(new CompoundTag(), machine.getLevel().registryAccess()).getString("state");
        if ((!currentState.equals("FINISHED") && !currentState.equals("WAITING"))
            || !GuardHooks.maySetArea(machine, proposed)) {
            ci.cancel();
            return;
        }
        targetIterator = null;
        targetPos = null;
        searchEnergyConsumed = false;
    }

    @Redirect(method = "makeFrame()V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z",
        remap = false), remap = false, require = 1)
    private boolean quarryguard$beforeFrameWrite(Level level, BlockPos pos, BlockState state, int flags) {
        return GuardHooks.mayWrite(quarryguard$machine(), pos) && level.setBlock(pos, state, flags);
    }

    @Redirect(method = {"breakBlocks(II)Lcom/yogpc/qp/machine/WorkResult;", "cleanUpFluid(II)Lcom/yogpc/qp/machine/WorkResult;",
        "breakBlockModuleOverride(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;F)Lcom/yogpc/qp/machine/WorkResult;"},
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z",
            remap = false), remap = false, require = 1)
    private boolean quarryguard$beforeColumnWrite(ServerLevel level, BlockPos pos, BlockState state, int flags) {
        return GuardHooks.mayWrite(quarryguard$machine(), pos) && level.setBlock(pos, state, flags);
    }

    @Redirect(method = "breakBlocks(II)Lcom/yogpc/qp/machine/WorkResult;",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/BucketPickup;pickupBlock(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/item/ItemStack;",
            remap = false), remap = false, require = 1)
    private ItemStack quarryguard$beforeBucketPickup(BucketPickup bucket, Player player, LevelAccessor level,
                                                     BlockPos pos, BlockState state) {
        return GuardHooks.mayWrite(quarryguard$machine(), pos)
            ? bucket.pickupBlock(player, level, pos, state) : ItemStack.EMPTY;
    }

    @Redirect(method = "breakBlocks(II)Lcom/yogpc/qp/machine/WorkResult;",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;",
            remap = false), remap = false, require = 1)
    private <T extends Entity> List<T> quarryguard$filterCollection(ServerLevel level, Class<T> type,
                                                                  AABB bounds, Predicate<? super T> predicate) {
        List<T> entities = new ArrayList<>(level.getEntitiesOfClass(type, bounds, predicate));
        BlockEntity machine = quarryguard$machine();
        entities.removeIf(entity -> !GuardHooks.mayCollect(machine, entity));
        return entities;
    }

    @Redirect(method = "breakBlocks(II)Lcom/yogpc/qp/machine/WorkResult;",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;",
            remap = false), remap = false, require = 2)
    private <T extends Entity> List<T> quarryguard$filterCollectionWithoutPredicate(ServerLevel level,
                                                                                  Class<T> type, AABB bounds) {
        List<T> entities = new ArrayList<>(level.getEntitiesOfClass(type, bounds));
        BlockEntity machine = quarryguard$machine();
        entities.removeIf(entity -> !GuardHooks.mayCollect(machine, entity));
        return entities;
    }

    @Redirect(method = "lambda$breakBlocks$6(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/AABB;Lcom/yogpc/qp/machine/exp/ExpModule;)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;",
            remap = false), remap = false, require = 1)
    private static <T extends Entity> List<T> quarryguard$filterExperienceCollection(ServerLevel level,
                                                                                   Class<T> type, AABB bounds,
                                                                                   Predicate<? super T> predicate) {
        BlockEntity machine = quarryguard$collector.get();
        if (machine == null || machine.getLevel() != level) {
            return new ArrayList<>();
        }
        List<T> entities = new ArrayList<>(level.getEntitiesOfClass(type, bounds, predicate));
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
