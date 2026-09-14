package local.lunar.mixin;

import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import com.hollingsworth.arsnouveau.api.spell.SpellStats;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectEnderChest;
import local.lunar.LunarExtraRestrictions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EffectEnderChest.class, remap = false)
public abstract class ArsEnderChestMixin {
    @Inject(method = "onResolveEntity", at = @At("HEAD"), cancellable = true, require = 1)
    private void lunar$beforeMenu(EntityHitResult hit, Level level, LivingEntity caster, SpellStats stats,
            SpellContext context, SpellResolver resolver, CallbackInfo ci) {
        if (LunarExtraRestrictions.shared(caster.level())) ci.cancel();
    }
}
