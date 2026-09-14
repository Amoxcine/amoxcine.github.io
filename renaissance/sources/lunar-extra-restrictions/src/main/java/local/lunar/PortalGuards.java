package local.lunar;

import com.hollingsworth.arsnouveau.common.items.data.WarpScrollData;
import com.hollingsworth.arsnouveau.setup.registry.DataComponentRegistry;
import io.redspace.ironsspellbooks.capabilities.magic.PortalManager;
import java.util.UUID;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class PortalGuards {
    private PortalGuards() {}

    public static boolean ars(Level level, ItemStack stack) {
        if (!LunarExtraRestrictions.enabled(level)) return false;
        return ars(level, stack.get(DataComponentRegistry.WARP_SCROLL));
    }

    public static boolean ars(Level level, WarpScrollData data) {
        return LunarExtraRestrictions.nativePortal(level, data == null ? null : data.dimension());
    }

    public static boolean iron(Level level, UUID portal) {
        if (!LunarExtraRestrictions.enabled(level)) return false;
        if (LunarExtraRestrictions.shared(level)) return true;
        var data = PortalManager.INSTANCE.getPortalData(portal);
        if (data == null) return false; // Native missing link cannot resolve a destination.
        var destination = Rules.connectedTarget(portal, data.portalEntityId1, data.globalPos1, data.portalEntityId2, data.globalPos2);
        return destination != null && LunarExtraRestrictions.nativePortal(level, destination.dimension().location().toString());
    }
}
