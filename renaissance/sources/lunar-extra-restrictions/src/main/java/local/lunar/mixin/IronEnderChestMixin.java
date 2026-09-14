package local.lunar.mixin;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.spells.ender.SummonEnderChestSpell;
import local.lunar.LunarExtraRestrictions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SummonEnderChestSpell.class, remap = false)
public abstract class IronEnderChestMixin {
    @Inject(method = "onCast", at = @At("HEAD"), cancellable = true, require = 1)
    private void lunar$beforeScrollAndMenu(Level level, int spellLevel, LivingEntity caster, CastSource source,
            MagicData magic, CallbackInfo ci) {
        if (LunarExtraRestrictions.shared(caster.level())) ci.cancel();
    }
}
