package fr.ascendant.renaissance.qio.mixin;

import fr.ascendant.renaissance.qio.QioManualSession;
import fr.ascendant.renaissance.qio.QioSessionAccess;
import mekanism.common.inventory.container.item.PortableQIODashboardContainer;
import mekanism.common.inventory.container.tile.QIODashboardContainer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = {PortableQIODashboardContainer.class, QIODashboardContainer.class}, remap = false)
public abstract class QioMenuValidityMixin {
    @Inject(method = "stillValid(Lnet/minecraft/world/entity/player/Player;)Z", at = @At("HEAD"),
        cancellable = true, require = 1, allow = 1)
    private void renaissance$valid(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (!player.level().isClientSide) {
            QioManualSession session = ((QioSessionAccess) this).renaissance$qioSession();
            if (session == null || !session.isCurrent(player)) cir.setReturnValue(false);
        }
    }
}
