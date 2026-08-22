package dev.kollegen.client.mixin;

import dev.kollegen.client.mods.modules.WeatherState;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Faerbt den Nebel fuer die Atmosphaeren-Modi (Nebel / End-Blitz / Basalt-Delta)
 * entsprechend der aktuellen Wetter-Einstellung ein.
 */
@Mixin(FogRenderer.class)
public class FogRendererMixin {

    @Inject(method = "setupFog(Lnet/minecraft/client/Camera;ILnet/minecraft/client/DeltaTracker;FLnet/minecraft/client/multiplayer/ClientLevel;)Lorg/joml/Vector4f;",
            at = @At("RETURN"))
    private void kollegen$fog(Camera camera, int i, DeltaTracker delta, float f, ClientLevel level, CallbackInfoReturnable<Vector4f> cir) {
        if (WeatherState.mode < 4) return;
        try {
            cir.setReturnValue(switch (WeatherState.mode) {
                case 4 -> new Vector4f(0.80F, 0.80F, 0.82F, 1.0F);   // Nebel: hellgrau
                case 5 -> new Vector4f(0.18F, 0.08F, 0.28F, 1.0F);   // End-Blitz: dunkles Violett
                case 6 -> new Vector4f(0.65F, 0.28F, 0.12F, 1.0F);   // Basalt-Delta: orange-rot
                default -> cir.getReturnValue();
            });
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
