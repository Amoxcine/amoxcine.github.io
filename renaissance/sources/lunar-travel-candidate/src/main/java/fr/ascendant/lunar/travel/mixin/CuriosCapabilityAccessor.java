package fr.ascendant.lunar.travel.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import top.theillusivec4.curios.common.capability.*;
@Mixin(value = CurioInventoryCapability.class, remap = false)
public interface CuriosCapabilityAccessor {
    @Accessor("curioInventory") CurioInventory lunar$inventory();
}

