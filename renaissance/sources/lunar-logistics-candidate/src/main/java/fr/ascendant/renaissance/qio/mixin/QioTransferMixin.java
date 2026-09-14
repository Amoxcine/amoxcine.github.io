package fr.ascendant.renaissance.qio.mixin;

import java.util.List;
import fr.ascendant.renaissance.qio.QioManualGate;
import it.unimi.dsi.fastutil.bytes.Byte2ObjectMap;
import mekanism.common.content.qio.QIOCraftingTransferHelper.SingularHashedItemSource;
import mekanism.common.content.qio.QIOServerCraftingTransferHandler;
import mekanism.common.inventory.container.QIOItemViewerContainer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.CraftingRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = QIOServerCraftingTransferHandler.class, remap = false)
public abstract class QioTransferMixin {
    @Inject(method = "tryTransfer(Lmekanism/common/inventory/container/QIOItemViewerContainer;BZLnet/minecraft/world/entity/player/Player;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/world/item/crafting/CraftingRecipe;Lit/unimi/dsi/fastutil/bytes/Byte2ObjectMap;)V",
        at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void renaissance$transfer(QIOItemViewerContainer menu, byte grid, boolean rejectToInventory,
        Player player, ResourceLocation recipeId, CraftingRecipe recipe,
        Byte2ObjectMap<List<SingularHashedItemSource>> sources, CallbackInfo ci) {
        if (player.containerMenu != menu || QioManualGate.session(player) == null || grid < 0 || grid >= 3
            || menu.getSelectedCraftingGrid(player.getUUID()) != grid) ci.cancel();
    }
}
