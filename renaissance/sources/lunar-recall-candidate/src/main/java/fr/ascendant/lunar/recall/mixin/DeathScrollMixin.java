package fr.ascendant.lunar.recall.mixin;

import com.b1n_ry.yigd.item.DeathScrollItem;
import fr.ascendant.lunar.recall.YigdGate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = DeathScrollItem.class, remap = false)
public abstract class DeathScrollMixin {
    // Cancelling only teleport()/claim() is too late: useAction shrinks even a FAIL result.
    @Inject(method = "useAction(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResultHolder;",
        at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void lunarRecall$beforeAction(Level level, Player player, InteractionHand hand,
                                         CallbackInfoReturnable<InteractionResultHolder<ItemStack>> ci) {
        if (player instanceof ServerPlayer serverPlayer && YigdGate.deniesScroll(serverPlayer, player.getItemInHand(hand)))
            ci.setReturnValue(InteractionResultHolder.fail(player.getItemInHand(hand)));
    }
}
