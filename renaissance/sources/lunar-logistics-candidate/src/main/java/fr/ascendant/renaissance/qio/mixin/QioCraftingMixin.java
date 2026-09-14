package fr.ascendant.renaissance.qio.mixin;

import java.util.List;
import fr.ascendant.renaissance.qio.QioManualGate;
import fr.ascendant.renaissance.qio.QioManualSession;
import mekanism.common.content.qio.IQIOCraftingWindowHolder;
import mekanism.common.content.qio.QIOCraftingWindow;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.inventory.container.slot.HotBarSlot;
import mekanism.common.inventory.container.slot.MainInventorySlot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = QIOCraftingWindow.class, remap = false)
public abstract class QioCraftingMixin {
    @Redirect(method = "performCraft(Lnet/minecraft/world/entity/player/Player;Ljava/util/List;Ljava/util/List;)V",
        at = @At(value = "INVOKE", target = "Lmekanism/common/content/qio/IQIOCraftingWindowHolder;getFrequency()Lmekanism/common/content/qio/QIOFrequency;"),
        require = 1, allow = 1)
    private QIOFrequency renaissance$shiftCraft(IQIOCraftingWindowHolder holder, Player player,
                                               List<HotBarSlot> hotbar, List<MainInventorySlot> inventory) {
        return QioManualGate.craftingFrequency(player, (QIOCraftingWindow) (Object) this, holder);
    }

    @Redirect(method = "performCraft(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;I)Lnet/minecraft/world/item/ItemStack;",
        at = @At(value = "INVOKE", target = "Lmekanism/common/content/qio/IQIOCraftingWindowHolder;getFrequency()Lmekanism/common/content/qio/QIOFrequency;"),
        require = 1, allow = 1)
    private QIOFrequency renaissance$craft(IQIOCraftingWindowHolder holder, Player player, ItemStack result, int amount) {
        return QioManualGate.craftingFrequency(player, (QIOCraftingWindow) (Object) this, holder);
    }

    // Pickup must be rejected before the native output is handed out, not by cancelling onTake.
    @Inject(method = "canViewRecipe(Lnet/minecraft/server/level/ServerPlayer;)Z", at = @At("HEAD"),
        cancellable = true, require = 1, allow = 1)
    private void renaissance$recipeSession(ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
        QioManualSession session = QioManualGate.session(player);
        if (session == null || !session.owns((QIOCraftingWindow) (Object) this)) cir.setReturnValue(false);
    }
}
