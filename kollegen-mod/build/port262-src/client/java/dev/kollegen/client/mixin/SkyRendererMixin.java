package dev.kollegen.client.mixin;

import dev.kollegen.client.mods.modules.Performance;
import dev.kollegen.client.mods.modules.WeatherState;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(SkyRenderer.class)
public class SkyRendererMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/client/multiplayer/ClientLevel;FLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/state/level/SkyRenderState;)V",
            at = @At("HEAD"))
    private void kollegen$caveSky(ClientLevel level, float f, Camera camera, SkyRenderState state, CallbackInfo ci) {
        if (!Performance.KollegenTricks.shouldCullCaveSky()) return;
        try {
            if (level != null && camera != null && state != null) {
                if (camera.position().y < 55 && !level.canSeeSky(camera.blockPosition())) {
                    state.starBrightness = 0.0f;
                    state.sunriseAndSunsetColor = null;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/multiplayer/ClientLevel;FLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/state/level/SkyRenderState;)V",
            at = @At("RETURN"))
    private void kollegen$sky(ClientLevel level, float f, Camera camera, SkyRenderState state, CallbackInfo ci) {
        if (WeatherState.mode < 4) return;
        try {
            if (state != null) {
                state.skyColor = switch (WeatherState.mode) {
                    case 4 -> new org.joml.Vector3f(0.8f, 0.8f, 0.8f);   
                    case 5 -> new org.joml.Vector3f(0.18f, 0.07f, 0.28f);  
                    case 6 -> new org.joml.Vector3f(0.29f, 0.12f, 0.06f);  
                    default -> state.skyColor;
                };
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
