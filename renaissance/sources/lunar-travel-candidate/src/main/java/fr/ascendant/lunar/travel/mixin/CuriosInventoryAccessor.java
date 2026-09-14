package fr.ascendant.lunar.travel.mixin;
import java.util.Map;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import top.theillusivec4.curios.common.capability.CurioInventory;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
@Mixin(value = CurioInventory.class, remap = false)
public interface CuriosInventoryAccessor {
    @Accessor("curios") Map<String, ICurioStacksHandler> lunar$all();
    @Accessor("invalidStacks") NonNullList<ItemStack> lunar$invalid();
    @Accessor("markDeserialized") boolean lunar$pendingLoad();
}

