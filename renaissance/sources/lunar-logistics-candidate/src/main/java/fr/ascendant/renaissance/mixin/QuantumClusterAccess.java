package fr.ascendant.renaissance.mixin;

import appeng.me.cluster.implementations.QuantumCluster;
import appeng.me.service.helpers.ConnectionWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = QuantumCluster.class, remap = false)
public interface QuantumClusterAccess {
    @Accessor("connection") ConnectionWrapper renaissance$connection();
    @Accessor("otherSide") long renaissance$otherSide();
}
