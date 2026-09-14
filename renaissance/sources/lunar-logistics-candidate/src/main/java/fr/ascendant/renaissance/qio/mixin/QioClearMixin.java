package fr.ascendant.renaissance.qio.mixin;

import java.util.List;
import fr.ascendant.renaissance.qio.QioManualGate;
import fr.ascendant.renaissance.qio.QioManualSession;
import mekanism.common.content.qio.QIOCraftingWindow;
import mekanism.common.inventory.container.slot.HotBarSlot;
import mekanism.common.inventory.container.slot.MainInventorySlot;
import mekanism.common.network.to_server.qio.PacketQIOClearCraftingWindow;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = PacketQIOClearCraftingWindow.class, remap = false)
public abstract class QioClearMixin {
    @Redirect(method = "handle(Lnet/neoforged/neoforge/network/handling/IPayloadContext;)V",
        at = @At(value = "INVOKE", target = "Lmekanism/common/content/qio/QIOCraftingWindow;emptyTo(ZLjava/util/List;Ljava/util/List;)V"),
        require = 1, allow = 1)
    private void renaissance$localReturn(QIOCraftingWindow window, boolean toPlayer,
                                         List<HotBarSlot> hotbar, List<MainInventorySlot> inventory, IPayloadContext context) {
        QioManualSession session = QioManualGate.session(context.player());
        if (session != null && session.owns(window)) {
            window.emptyTo(toPlayer || !session.remoteAllowed(), hotbar, inventory);
        }
    }
}
