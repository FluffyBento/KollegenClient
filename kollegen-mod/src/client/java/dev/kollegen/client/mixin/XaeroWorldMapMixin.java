package dev.kollegen.client.mixin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;


@Pseudo
@Mixin(targets = "xaero.map.MapLimiter", remap = false)
public abstract class XaeroWorldMapMixin {

    private static final Logger LOG = LoggerFactory.getLogger("Kollegen/XaeroFix");

    @Redirect(
        method = "updateAvailableVRAM",
        require = 0,
        remap = false,
        at = @At(
            value = "INVOKE",
            target = "Lxaero/map/MapLimiter;determineDriverType()V"))
    private void kollegenSafeDetermineDriverType(Object instance) {
        try {
            java.lang.reflect.Method m = instance.getClass().getDeclaredMethod("determineDriverType");
            m.setAccessible(true);
            m.invoke(instance);
        } catch (Throwable t) {
            LOG.warn(
                "Xaero World Map VRAM-Erkennung uebersprungen (kein OpenGL-Context unter VulkanMod): {}",
                t.getMessage());
            try {
                java.lang.reflect.Field f = instance.getClass().getDeclaredField("driverType");
                f.setAccessible(true);
                f.setInt(instance, 2);
            } catch (Throwable ignored) {
            }
        }
    }
}
