package fr.ascendant.lunar.travel.mixin;

import com.brandon3055.draconicevolution.blocks.tileentity.TileDislocatorPedestal;
import com.brandon3055.draconicevolution.items.tools.Dislocator;
import fr.ascendant.lunar.travel.LunarTravel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TileDislocatorPedestal.class, remap = false)
public abstract class PedestalMixin {
    @Inject(method = "useWithoutItem", at = @At("HEAD"), cancellable = true, require = 1)
    private void lunar$beforeArrival(BlockState state, Player player, BlockHitResult hit,
            CallbackInfoReturnable<InteractionResult> cir) {
        if (!LunarTravel.active(player) || player.isShiftKeyDown()) return;
        var tile = (TileDislocatorPedestal) (Object) this;
        var stack = tile.itemHandler.getStackInSlot(0);
        if (!(stack.getItem() instanceof Dislocator item)) return;
        var target = item.getTargetPos(stack, tile.getLevel());
        if (LunarTravel.blocked(player, target == null ? null : target.getDimension()))
            cir.setReturnValue(InteractionResult.FAIL);
    }
}
