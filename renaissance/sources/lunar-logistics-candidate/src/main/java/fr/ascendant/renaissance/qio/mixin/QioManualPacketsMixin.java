package fr.ascendant.renaissance.qio.mixin;

import fr.ascendant.renaissance.qio.QioManualGate;
import mekanism.common.network.to_server.qio.PacketQIOItemViewerSlotTake;
import mekanism.common.network.to_server.qio.PacketQIOItemViewerSlotShiftTake;
import mekanism.common.network.to_server.qio.PacketQIOItemViewerSlotPlace;
import mekanism.common.network.to_server.qio.PacketQIOFillCraftingWindow;
import mekanism.common.network.to_server.qio.PacketQIOClearCraftingWindow;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = {PacketQIOItemViewerSlotTake.class, PacketQIOItemViewerSlotShiftTake.class,
    PacketQIOItemViewerSlotPlace.class, PacketQIOFillCraftingWindow.class, PacketQIOClearCraftingWindow.class}, remap = false)
public abstract class QioManualPacketsMixin {
    @Inject(method = "handle(Lnet/neoforged/neoforge/network/handling/IPayloadContext;)V",
        at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void renaissance$currentMenu(IPayloadContext context, CallbackInfo ci) {
        if (QioManualGate.session(context.player()) == null) ci.cancel();
    }
}
