package fr.ascendant.renaissance.qio.mixin;

import fr.ascendant.renaissance.qio.QioEndpointGate;
import mekanism.common.tile.qio.TileEntityQIOImporter;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TileEntityQIOImporter.class, remap = false)
public abstract class QioImporterMixin {
    @Inject(method = "tryImport(Lmekanism/common/content/qio/QIOFrequency;)V",
        at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void renaissanceQio$import(CallbackInfo ci) {
        if (QioEndpointGate.denied((BlockEntity) (Object) this)) ci.cancel();
    }
}
