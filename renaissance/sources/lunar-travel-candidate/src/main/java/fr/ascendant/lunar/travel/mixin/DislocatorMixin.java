package fr.ascendant.lunar.travel.mixin;

import com.brandon3055.draconicevolution.items.tools.Dislocator;
import com.brandon3055.brandonscore.utils.TargetPos;
import fr.ascendant.lunar.travel.LunarTravel;
import net.minecraft.world.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value = Dislocator.class, remap = false)
public abstract class DislocatorMixin {
    @Shadow public abstract TargetPos getTargetPos(ItemStack stack, Level level);

    @Inject(method = "dislocateEntity", at = @At("HEAD"), cancellable = true, require = 1)
    private void lunar$entity(ItemStack stack, Entity user, Entity entity, TargetPos target, CallbackInfoReturnable<Entity> cir) {
        if (LunarTravel.blocked(entity, target == null ? null : target.getDimension()) || LunarTravel.moon(user))
            cir.setReturnValue(entity);
    }
    @Inject(method = "use", at = @At("HEAD"), cancellable = true, require = 1)
    private void lunar$use(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        if (!LunarTravel.active(player)) return;
        ItemStack stack = player.getItemInHand(hand);
        TargetPos target = getTargetPos(stack, level);
        if (LunarTravel.moon(player) || target != null && LunarTravel.blocked(player, target.getDimension()))
            cir.setReturnValue(InteractionResultHolder.fail(stack));
    }
    @Inject(method = "onLeftClickEntity", at = @At("HEAD"), cancellable = true, require = 1)
    private void lunar$hit(ItemStack stack, Player player, Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!LunarTravel.active(player)) return;
        TargetPos target = getTargetPos(stack, player.level());
        if (LunarTravel.moon(player) || LunarTravel.blocked(entity, target == null ? null : target.getDimension()))
            cir.setReturnValue(true);
    }
}
