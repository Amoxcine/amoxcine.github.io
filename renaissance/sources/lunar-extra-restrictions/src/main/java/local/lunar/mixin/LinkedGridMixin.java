package local.lunar.mixin;

import appeng.api.networking.IGrid;
import java.util.function.Consumer;
import local.lunar.LunarExtraRestrictions;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.pedroksl.advanced_ae.common.items.armors.QuantumArmorBase;
import net.pedroksl.ae2addonlib.api.IGridLinkedItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = IGridLinkedItem.class, remap = false)
public interface LinkedGridMixin {
    @Inject(method = "getLinkedGrid(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Ljava/util/function/Consumer;)Lappeng/api/networking/IGrid;",
            at = @At("HEAD"), cancellable = true, require = 1)
    default void lunar$beforeResolve(ItemStack stack, Level level, Consumer<Component> error,
            CallbackInfoReturnable<IGrid> cir) {
        if ((Object) this instanceof QuantumArmorBase
                && LunarExtraRestrictions.unprovenGrid(level, ((IGridLinkedItem) this).getLinkedPosition(stack))) {
            cir.setReturnValue(null);
        }
    }
}
