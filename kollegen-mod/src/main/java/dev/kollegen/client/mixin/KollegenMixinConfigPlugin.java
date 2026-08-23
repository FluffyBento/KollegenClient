package dev.kollegen.client.mixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Sorgt dafür, dass {@link XaeroWorldMapMixin} zur Laufzeit nur angewandt wird,
 * wenn Xaero's World Map tatsächlich geladen ist. Fehlt die Mod (oder die Klasse
 * xaero.map.MapLimiter), wird der Mixin übersprungen, statt mit einem
 * "target not found"-Fehler abzustürzen.
 */
public class KollegenMixinConfigPlugin implements IMixinConfigPlugin {

    private static final String XAERO_MIXIN = "dev.kollegen.client.mixin.XaeroWorldMapMixin";

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String mixinClassName, String targetClassName) {
        if (XAERO_MIXIN.equals(mixinClassName)) {
            try {
                Class.forName(targetClassName, false, getClass().getClassLoader());
                return true;
            } catch (Throwable t) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName,
            IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName,
            IMixinInfo mixinInfo) {
    }
}
