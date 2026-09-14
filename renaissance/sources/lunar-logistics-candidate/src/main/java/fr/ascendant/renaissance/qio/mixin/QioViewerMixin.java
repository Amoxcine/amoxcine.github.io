package fr.ascendant.renaissance.qio.mixin;

import fr.ascendant.renaissance.qio.QioManualSession;
import fr.ascendant.renaissance.qio.QioSessionAccess;
import mekanism.common.content.qio.IQIOCraftingWindowHolder;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.inventory.container.QIOItemViewerContainer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = QIOItemViewerContainer.class, remap = false)
public abstract class QioViewerMixin implements QioSessionAccess {
    @Shadow @Final protected IQIOCraftingWindowHolder craftingWindowHolder;
    @Unique private QioManualSession renaissance$session;

    @Override
    public QioManualSession renaissance$qioSession() { return renaissance$session; }

    // Construction precedes containerMenu assignment; this path only prepares the native subscription.
    @Redirect(method = "openInventory(Lnet/minecraft/world/entity/player/Inventory;)V",
        at = @At(value = "INVOKE", target = "Lmekanism/common/inventory/container/QIOItemViewerContainer;getFrequency()Lmekanism/common/content/qio/QIOFrequency;"),
        require = 1, allow = 1)
    private QIOFrequency renaissance$open(QIOItemViewerContainer menu, Inventory inv) {
        if (inv.player instanceof ServerPlayer player) {
            renaissance$session = new QioManualSession(menu, player, craftingWindowHolder);
            return renaissance$session.openingFrequency();
        }
        return menu.getFrequency();
    }

    @Inject(method = "getFrequency()Lmekanism/common/content/qio/QIOFrequency;", at = @At("HEAD"),
        cancellable = true, require = 1, allow = 1)
    private void renaissance$frequency(CallbackInfoReturnable<QIOFrequency> cir) {
        if (renaissance$session != null) cir.setReturnValue(renaissance$session.frequency());
        else if (craftingWindowHolder == null || craftingWindowHolder.getLevel() == null
            || !craftingWindowHolder.getLevel().isClientSide) cir.setReturnValue(null);
    }

    @Inject(method = "broadcastChanges()V", at = @At("HEAD"), require = 1, allow = 1)
    private void renaissance$broadcast(CallbackInfo ci) {
        if (renaissance$session != null) renaissance$session.refreshSubscription();
    }

    @Inject(method = "quickMoveStack(Lnet/minecraft/world/entity/player/Player;I)Lnet/minecraft/world/item/ItemStack;",
        at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void renaissance$quickMove(Player player, int slot, CallbackInfoReturnable<ItemStack> cir) {
        if (!player.level().isClientSide && (renaissance$session == null || !renaissance$session.isCurrent(player)))
            cir.setReturnValue(ItemStack.EMPTY);
    }

    @Inject(method = "doDoubleClickTransfer(Lnet/minecraft/world/entity/player/Player;)V",
        at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void renaissance$doubleClick(Player player, CallbackInfo ci) {
        if (!player.level().isClientSide && (renaissance$session == null || !renaissance$session.isCurrent(player))) ci.cancel();
    }

    // Do not ask the masked getter for the old subscription. Keep native super/block close calls intact.
    @Redirect(method = "closeInventory(Lnet/minecraft/world/entity/player/Player;)V",
        at = @At(value = "INVOKE", target = "Lmekanism/common/inventory/container/QIOItemViewerContainer;getFrequency()Lmekanism/common/content/qio/QIOFrequency;"),
        require = 1, allow = 1)
    private QIOFrequency renaissance$close(QIOItemViewerContainer menu, Player player) {
        if (renaissance$session != null) renaissance$session.close();
        return null;
    }
}
