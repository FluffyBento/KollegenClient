package dev.kollegen.client.mixin;

import dev.kollegen.client.mods.modules.Performance;
import dev.kollegen.client.mods.modules.WeatherState;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public class FogRendererMixin {

    @Inject(method = "setupFog(Lnet/minecraft/client/Camera;ILnet/minecraft/client/DeltaTracker;FLnet/minecraft/client/multiplayer/ClientLevel;)Lnet/minecraft/client/renderer/fog/FogData;",
            at = @At("RETURN"))
    private void kollegen$fog(Camera camera, int i, DeltaTracker delta, float f, ClientLevel level, CallbackInfoReturnable<FogData> cir) {
        try {
            FogData data = cir.getReturnValue();
            if (data == null) return;

            if (dev.kollegen.client.mods.modules.Visual.noFogEnabled) {
                data.renderDistanceStart = 100000.0f;
                data.renderDistanceEnd = 100000.0f;
            }

            if (Performance.KollegenDistance.isDistanceEnabled()) {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                int renderDist = Performance.KollegenDistance.getMaxChunkDistance();
                if (mc != null && mc.options != null) {
                    renderDist = Math.max(renderDist, mc.options.renderDistance().get());
                }
                float extendedDistance = Math.max(renderDist, 16) * 16.0f;
                if (data.renderDistanceEnd < extendedDistance) {
                    data.renderDistanceEnd = extendedDistance;
                    data.renderDistanceStart = extendedDistance * 0.75f;
                }
            }

            if (WeatherState.mode >= 4) {
                switch (WeatherState.mode) {
                    case 4 -> data.color.set(0.80F, 0.80F, 0.82F, 1.0F);
                    case 5 -> data.color.set(0.18F, 0.08F, 0.28F, 1.0F);
                    case 6 -> data.color.set(0.65F, 0.28F, 0.12F, 1.0F);
                    default -> {}
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
