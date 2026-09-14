package fr.ascendant.quarryguard.mixin.quarry;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yogpc.qp.machine.advquarry.AdvQuarryItem;
import com.yogpc.qp.machine.quarry.QuarryItem;
import fr.ascendant.quarryguard.GuardHooks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BlockItem.class, remap = false)
public abstract class BlockItemPlacementMixin {
    @Unique
    private static final ThreadLocal<Boolean> quarryguard$placing = new ThreadLocal<>();

    @Unique
    private boolean quarryguard$isQuarryItem() {
        Object item = this;
        return item instanceof QuarryItem || item instanceof AdvQuarryItem;
    }

    @WrapMethod(method = "place(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/InteractionResult;",
        remap = false, require = 1)
    private InteractionResult quarryguard$placementScope(BlockPlaceContext context,
                                                         Operation<InteractionResult> original) {
        if (!quarryguard$isQuarryItem() || context.getLevel().isClientSide()) {
            return original.call(context);
        }
        // Reject nesting without calling/clearing the outer attempt's policy token.
        if (Boolean.TRUE.equals(quarryguard$placing.get())) {
            return InteractionResult.FAIL;
        }
        quarryguard$placing.set(true);
        try {
            GuardHooks.afterPlace();
            return original.call(context);
        } finally {
            try {
                GuardHooks.afterPlace();
            } finally {
                quarryguard$placing.remove();
            }
        }
    }

    @Inject(method = "placeBlock(Lnet/minecraft/world/item/context/BlockPlaceContext;Lnet/minecraft/world/level/block/state/BlockState;)Z",
        at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void quarryguard$beforeActualPlacement(BlockPlaceContext context, BlockState state,
                                                   CallbackInfoReturnable<Boolean> cir) {
        if (quarryguard$isQuarryItem() && !context.getLevel().isClientSide()
            && (!Boolean.TRUE.equals(quarryguard$placing.get()) || !GuardHooks.beforePlace(context, state))) {
            cir.setReturnValue(false);
            if (context.getPlayer() instanceof ServerPlayer player) {
                // The client predicts BlockItem consumption before the server rejects the placement.
                player.inventoryMenu.sendAllDataToRemote();
            }
        }
    }

    @Inject(method = "place(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/InteractionResult;",
        at = @At("RETURN"), remap = false, require = 1)
    private void quarryguard$clearOnReturn(BlockPlaceContext context,
                                           CallbackInfoReturnable<InteractionResult> cir) {
        if (quarryguard$isQuarryItem() && !context.getLevel().isClientSide()) {
            GuardHooks.afterPlace();
        }
    }
}
