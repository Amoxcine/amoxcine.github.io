package fr.ascendant.lunar.travel.mixin;
import java.util.Map;
import io.wispforest.accessories.api.AccessoriesContainer;
import io.wispforest.accessories.impl.AccessoriesHolderImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(value = AccessoriesHolderImpl.class, remap = false)
public interface AccessoriesHolderAccessor {
    @Accessor("slotContainers") Map<String, AccessoriesContainer> lunar$all();
    @Accessor("loadedFromTag") boolean lunar$pendingLoad();
}

