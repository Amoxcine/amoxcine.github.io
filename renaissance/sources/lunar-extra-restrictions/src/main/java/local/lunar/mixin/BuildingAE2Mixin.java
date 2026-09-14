package local.lunar.mixin;

import com.direwolf20.buildinggadgets2.integration.AE2Methods;
import java.util.List;
import local.lunar.LunarExtraRestrictions;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AE2Methods.class, remap = false)
public abstract class BuildingAE2Mixin {
    @Inject(method = "checkAE2ForItems", at = @At("HEAD"), cancellable = true, require = 1)
    private static void lunar$extractItems(GlobalPos bound, Player player, List<ItemStack> items, boolean simulate, CallbackInfo ci) {
        if (LunarExtraRestrictions.crossing(player.level(), bound)) ci.cancel();
    }

    @Inject(method = "checkAE2ForFluids", at = @At("HEAD"), cancellable = true, require = 1)
    private static void lunar$extractFluid(GlobalPos bound, Player player, FluidStack fluid, boolean simulate, CallbackInfo ci) {
        if (LunarExtraRestrictions.crossing(player.level(), bound)) ci.cancel();
    }

    @Inject(method = "insertIntoAE2", at = @At("HEAD"), cancellable = true, require = 1)
    private static void lunar$insertItems(Player player, GlobalPos bound, ItemStack stack, CallbackInfo ci) {
        if (LunarExtraRestrictions.crossing(player.level(), bound)) ci.cancel();
    }

    @Inject(method = "insertFluidIntoAE2", at = @At("HEAD"), cancellable = true, require = 1)
    private static void lunar$insertFluid(Player player, GlobalPos bound, FluidStack stack, CallbackInfo ci) {
        if (LunarExtraRestrictions.crossing(player.level(), bound)) ci.cancel();
    }
}
