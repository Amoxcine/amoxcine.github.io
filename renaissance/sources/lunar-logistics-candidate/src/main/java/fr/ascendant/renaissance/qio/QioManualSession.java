package fr.ascendant.renaissance.qio;

import mekanism.common.content.qio.IQIOCraftingWindowHolder;
import mekanism.common.content.qio.PortableQIODashboardInventory;
import mekanism.common.content.qio.QIOCraftingWindow;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.inventory.container.QIOItemViewerContainer;
import mekanism.common.inventory.container.item.PortableQIODashboardContainer;
import mekanism.common.inventory.container.tile.QIODashboardContainer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/** A menu owns its subscription independently of its current transaction permission. */
public final class QioManualSession {
    private final QIOItemViewerContainer menu;
    private final ServerPlayer player;
    private final ServerLevel openedLevel;
    private final IQIOCraftingWindowHolder holder;
    private final InteractionHand hand;
    private final ItemStack stack;
    private QIOFrequency subscribed;
    private boolean closed;

    public QioManualSession(QIOItemViewerContainer menu, ServerPlayer player, IQIOCraftingWindowHolder holder) {
        this.menu = menu;
        this.player = player;
        this.openedLevel = player.serverLevel();
        this.holder = holder;
        if (menu.getClass() == PortableQIODashboardContainer.class) {
            PortableQIODashboardContainer portable = (PortableQIODashboardContainer) menu;
            hand = portable.getHand();
            stack = portable.getStack();
        } else {
            hand = null;
            stack = null;
        }
    }

    public boolean isCurrent(Player actor) {
        try {
            return player.containerMenu == menu && validSession(actor);
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    private boolean validSession(Player actor) {
        if (closed || actor != player || !openedLevel.getServer().isSameThread()
            || !player.isAlive() || player.isRemoved() || player.hasDisconnected()
            || player.serverLevel() != openedLevel
            || holder == null || holder.getLevel() != player.level()
            || openedLevel.getServer().getPlayerList().getPlayer(player.getUUID()) != player) return false;
        if (menu.getClass() == PortableQIODashboardContainer.class) {
            PortableQIODashboardContainer portable = (PortableQIODashboardContainer) menu;
            if (!(holder instanceof PortableQIODashboardInventory) || stack == null || stack.isEmpty()
                || portable.getHand() != hand || portable.getStack() != stack
                || player.getItemInHand(hand) != stack) return false;
        } else if (menu.getClass() == QIODashboardContainer.class) {
            if (!((QIODashboardContainer) menu).getTileEntity().hasGui()) return false;
            BlockEntity tile = ((QIODashboardContainer) menu).getTileEntity();
            if (holder != tile || tile.isRemoved() || tile.getLevel() != player.level()
                || !openedLevel.hasChunkAt(tile.getBlockPos())
                || openedLevel.getBlockEntity(tile.getBlockPos()) != tile) return false;
        } else return false;
        return menu.canPlayerAccess(player);
    }

    public boolean owns(QIOCraftingWindow window, IQIOCraftingWindowHolder expected) {
        int index = window.getWindowIndex();
        return holder == expected && index >= 0 && index < 3 && menu.getCraftingWindow(index) == window;
    }

    public boolean owns(QIOCraftingWindow window) {
        return owns(window, holder);
    }

    public boolean remoteAllowed() {
        return !QioManualGate.denied(player, isCurrent(player), endpoint());
    }

    private BlockEntity endpoint() {
        return menu.getClass() == QIODashboardContainer.class
            ? ((QIODashboardContainer) menu).getTileEntity() : null;
    }

    /** Only the native constructor's openInventory call may precede containerMenu assignment. */
    public QIOFrequency openingFrequency() {
        try {
            if (!QioManualGate.denied(player, validSession(player), endpoint())) {
                subscribed = holder.getFrequency();
                return subscribed;
            }
        } catch (RuntimeException unavailable) {
            // No subscription was opened by the native caller yet.
        }
        return null;
    }

    public QIOFrequency frequency() {
        if (!remoteAllowed()) {
            unsubscribe();
            return null;
        }
        return holder.getFrequency();
    }

    /** Called from the existing menu broadcast, with no world/player scan. */
    public void refreshSubscription() {
        if (!openedLevel.getServer().isSameThread()) return;
        QIOFrequency next = frequency();
        if (next != subscribed) {
            unsubscribe();
            if (next != null) {
                next.openItemViewer(player);
                subscribed = next;
            }
        }
    }

    public void close() {
        closed = true;
        unsubscribe();
    }

    private void unsubscribe() {
        if (subscribed != null && openedLevel.getServer().isSameThread()) {
            subscribed.closeItemViewer(player);
            subscribed = null;
        }
    }
}
