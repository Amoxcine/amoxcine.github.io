package fr.ascendant.renaissance.mixin;

import appeng.api.features.Locatables;
import appeng.me.cluster.implementations.QuantumCluster;
import fr.ascendant.renaissance.QuantumHooks;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = QuantumCluster.class, remap = false)
public abstract class QuantumClusterMixin {
    @Redirect(method = "updateStatus(Z)V", at = @At(value = "INVOKE",
        target = "Lappeng/api/features/Locatables$Type;get(Lnet/minecraft/world/level/Level;J)Ljava/lang/Object;"),
        remap = false, require = 1, allow = 1)
    private Object renaissance$filterPartner(Locatables.Type<?> registry, Level level, long frequency) {
        Object partner = registry.get(level, frequency);
        return QuantumHooks.filter((QuantumCluster) (Object) this, partner);
    }
}
