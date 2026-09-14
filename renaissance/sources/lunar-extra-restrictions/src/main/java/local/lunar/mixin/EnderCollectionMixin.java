package local.lunar.mixin;

import com.brandon3055.brandonscore.api.power.IOPStorage;
import com.brandon3055.draconicevolution.api.modules.entities.EnderCollectionEntity;
import java.util.Collection;
import java.util.List;
import local.lunar.LunarExtraRestrictions;
import local.lunar.Rules;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EnderCollectionEntity.class, remap = false)
public abstract class EnderCollectionMixin {
    @Inject(method = "insertStacks", at = @At("HEAD"), cancellable = true, require = 1)
    private void lunar$bulk(Player player, Collection<ItemStack> stacks, IOPStorage energy,
            CallbackInfoReturnable<List<ItemStack>> cir) {
        if (LunarExtraRestrictions.shared(player.level())) cir.setReturnValue(Rules.unchangedRemainder(stacks));
    }

    @Inject(method = "insertStack", at = @At("HEAD"), cancellable = true, require = 1)
    private void lunar$single(Player player, ItemStack stack, IOPStorage energy, CallbackInfoReturnable<Integer> cir) {
        if (LunarExtraRestrictions.shared(player.level())) cir.setReturnValue(stack.getCount());
    }
}
