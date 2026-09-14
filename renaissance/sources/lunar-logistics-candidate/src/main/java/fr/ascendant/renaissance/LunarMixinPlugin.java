package fr.ascendant.renaissance;

import java.util.List;
import java.util.Set;
import net.neoforged.fml.loading.FMLPaths;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** All hook sets use the same immutable decision, before Minecraft target loading. */
public final class LunarMixinPlugin implements IMixinConfigPlugin {
    public static final boolean ENABLED = LunarConfig.read(FMLPaths.CONFIGDIR.get().resolve(LunarConfig.FILE));
    @Override public void onLoad(String mixinPackage) { }
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String target, String mixin) { return ENABLED; }
    @Override public void acceptTargets(Set<String> ours, Set<String> others) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) { }
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) { }
}
