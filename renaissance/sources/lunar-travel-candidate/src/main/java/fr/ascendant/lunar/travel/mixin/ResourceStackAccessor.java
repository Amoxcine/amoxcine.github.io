package fr.ascendant.lunar.travel.mixin;
import earth.terrarium.common_storage_lib.resources.ResourceStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(value = ResourceStack.class, remap = false)
public interface ResourceStackAccessor {
    @Accessor("amount") long lunar$rawAmount();
}
