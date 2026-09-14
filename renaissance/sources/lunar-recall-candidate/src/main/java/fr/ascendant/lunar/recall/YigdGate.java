package fr.ascendant.lunar.recall;

import com.b1n_ry.yigd.components.GraveComponent;
import com.b1n_ry.yigd.config.ExtraFeaturesConfig.ScrollConfig.ClickFunction;
import com.b1n_ry.yigd.config.YigdConfig;
import com.b1n_ry.yigd.data.DeathInfoManager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class YigdGate {
    private YigdGate() { }

    public static boolean deniesGrave(ServerPlayer player, GraveComponent grave, boolean teleport) {
        if (!LunarRecall.active(player)) return false;
        if (!LunarRecall.validThread(player) || grave == null || grave.getWorldRegistryKey() == null) return true;
        String target = grave.getWorldRegistryKey().location().toString();
        var world = grave.getWorld();
        // Use the authoritative grave's stored world, never the claim event's caller-supplied level.
        if (world == null || !world.dimension().equals(grave.getWorldRegistryKey())
            || world.getServer() != player.serverLevel().getServer()) return true;
        String source = player.level().dimension().location().toString();
        return teleport ? RecallPolicy.blocksTeleport(source, target) : RecallPolicy.blocksRestore(source, target);
    }

    public static boolean deniesScroll(ServerPlayer player, ItemStack stack) {
        if (!LunarRecall.active(player)) return false;
        if (!LunarRecall.validThread(player)) return true;
        try {
            var data = stack.get(DataComponents.CUSTOM_DATA);
            var tag = data == null ? null : data.copyTag();
            ClickFunction action = YigdConfig.getConfig().extraFeatures.deathScroll.clickFunction;
            if (tag != null && tag.contains("clickFunction") && !tag.getString("clickFunction").equals("default"))
                action = ClickFunction.valueOf(tag.getString("clickFunction"));
            if (action == ClickFunction.VIEW_CONTENTS) return false;
            if (action != ClickFunction.RESTORE_CONTENTS && action != ClickFunction.TELEPORT_TO_LOCATION) return true;
            if (tag == null || !tag.hasUUID("grave")) return true;
            var grave = DeathInfoManager.INSTANCE.getGrave(tag.getUUID("grave")).orElse(null);
            return deniesGrave(player, grave, action == ClickFunction.TELEPORT_TO_LOCATION);
        } catch (RuntimeException unavailable) {
            // Unknown action/data/provenance is a non-consuming refusal, not a fallback transfer.
            return true;
        }
    }
}
