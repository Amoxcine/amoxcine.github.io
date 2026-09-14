package fr.ascendant.lunar.travel.mixin;

import com.brandon3055.draconicevolution.items.tools.DislocatorAdvanced;
import com.brandon3055.brandonscore.utils.TargetPos;
import fr.ascendant.lunar.travel.LunarTravel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value = DislocatorAdvanced.class, remap = false)
public abstract class AdvancedDislocatorMixin {
    @Shadow public abstract TargetPos getTargetPos(ItemStack stack, Level level);

    @Inject(method = "handleTeleport", at = @At("HEAD"), cancellable = true, require = 1)
    private void lunar$beforeFuel(Player player, ItemStack stack, TargetPos target, boolean showFuel, CallbackInfo ci) {
        if (LunarTravel.blocked(player, target == null ? null : target.getDimension())) ci.cancel();
    }
    @Inject(method = "handleBlink", at = @At("HEAD"), cancellable = true, require = 1)
    private void lunar$blink(ServerPlayer player, ItemStack stack, boolean showFuel, CallbackInfo ci) {
        if (LunarTravel.moon(player)) ci.cancel();
    }
    @Inject(method = "onLeftClickEntity", at = @At("HEAD"), cancellable = true, require = 1)
    private void lunar$hit(ItemStack stack, Player player, Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!LunarTravel.active(player)) return;
        TargetPos target = getTargetPos(stack, player.level());
        if (LunarTravel.moon(player) || LunarTravel.blocked(entity, target == null ? null : target.getDimension()))
            cir.setReturnValue(true);
    }
}
