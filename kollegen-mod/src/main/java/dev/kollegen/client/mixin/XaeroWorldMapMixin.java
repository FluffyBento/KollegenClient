package dev.kollegen.client.mixin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import xaero.map.MapLimiter;

/**
 * Xaero's World Map ruft in {@code MapLimiter.determineDriverType()} LWJGLs
 * {@code GL.getCapabilities()} auf, um den GPU-Treiber zu erkennen. Unter
 * VulkanMod existiert jedoch kein OpenGL-Context, sodass die Methode mit
 * {@code IllegalStateException: No GLCapabilities instance set} abstürzt und
 * Xaero beim Serverbeitritt den Spieler disconnectet.
 *
 * Wir leiten den determineDriverType()-Aufruf in updateAvailableVRAM() auf einen
 * sicheren Wrapper um: schlägt er fehl, setzen wir den Treibertyp auf "unbekannt"
 * (2), wodurch updateAvailableVRAM den GL-Pfad komplett überspringt – kein Crash,
 * kein Disconnect. Bei normalen (OpenGL-)Usern läuft determineDriverType()
 * unverändert, das VRAM-Limiting bleibt erhalten.
 */
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
