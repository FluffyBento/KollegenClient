package dev.kollegen.client.mixin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import xaero.map.MapLimiter;


@Mixin(value = MapLimiter.class, remap = false)
public abstract class XaeroWorldMapMixin {

    private static final Logger LOG = LoggerFactory.getLogger("Kollegen/XaeroFix");

    @Shadow
    private int driverType;

    @Shadow
    private void determineDriverType() {
    }

    @Redirect(
        method = "updateAvailableVRAM",
        at = @At(
            value = "INVOKE",
            target = "Lxaero/map/MapLimiter;determineDriverType()V"))
    private void kollegenSafeDetermineDriverType(MapLimiter instance) {
        try {
            this.determineDriverType();
        } catch (Throwable t) {
            LOG.warn(
                "Xaero World Map VRAM-Erkennung uebersprungen (kein OpenGL-Context unter VulkanMod): {}",
                t.getMessage());
            this.driverType = 2;
        }
    }
}
