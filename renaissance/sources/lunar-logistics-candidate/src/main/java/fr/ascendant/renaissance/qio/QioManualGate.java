package fr.ascendant.renaissance.qio;

import mekanism.common.content.qio.IQIOCraftingWindowHolder;
import mekanism.common.content.qio.QIOCraftingWindow;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.inventory.container.QIOItemViewerContainer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class QioManualGate {

    private QioManualGate() { }

    static boolean denied(ServerPlayer player, boolean valid, BlockEntity endpoint) {
        return QioScope.denyManual(false, player.level().dimension().location().toString(), valid,
            null, endpoint == null ? null : () -> !QioEndpointGate.denied(endpoint));
    }

    public static QioManualSession session(Player player) {
        if (player instanceof ServerPlayer && player.containerMenu instanceof QIOItemViewerContainer viewer
            && viewer instanceof QioSessionAccess access) {
            QioManualSession session = access.renaissance$qioSession();
            if (session != null && session.isCurrent(player)) return session;
        }
        return null;
    }

    public static QIOFrequency craftingFrequency(Player player, QIOCraftingWindow window,
                                                 IQIOCraftingWindowHolder holder) {
        if (player.level().isClientSide) return holder.getFrequency();
        QioManualSession session = session(player);
        return session != null && session.owns(window, holder) ? session.frequency() : null;
    }
}
