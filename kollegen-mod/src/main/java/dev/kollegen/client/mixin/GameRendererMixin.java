package dev.kollegen.client.mixin;

import dev.kollegen.client.mods.modules.Visual;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Zoom über einen FOV-Teiler am zentralen Punkt der FOV-Berechnung. Das alte
 * Verfahren (options.fov().set(...)) überschrieb dauerhaft die FOV-Einstellung
 * und reagierte nicht live auf Slider-Änderungen – hier bleibt die eingestellte
 * FOV unangetastet und der Zoom wirkt in jedem Frame sofort.
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void kollegen$zoomFov(Camera camera, float partialTick, boolean useFovSetting,
                                  CallbackInfoReturnable<Float> cir) {
        float divisor = Visual.zoomFovDivisor;
        if (divisor > 1.0f) {
            float fov = cir.getReturnValueF();
            if (fov > 0f) {
                cir.setReturnValue(Math.max(1.0f, fov / divisor));
            }
        }
    }
}
