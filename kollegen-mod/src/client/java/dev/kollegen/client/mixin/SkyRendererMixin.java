package dev.kollegen.client.mixin;

import dev.kollegen.client.mods.modules.WeatherState;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.SkyRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(SkyRenderer.class)
public class SkyRendererMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/client/multiplayer/ClientLevel;FLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/state/SkyRenderState;)V",
            at = @At("RETURN"))
    private void kollegen$sky(ClientLevel level, float f, Camera camera, SkyRenderState state, CallbackInfo ci) {
        if (WeatherState.mode < 4) return;
        try {
            if (state != null) {
                state.skyColor = switch (WeatherState.mode) {
                    case 4 -> 0xFF_CCCC_CC;   
                    case 5 -> 0xFF_2E_13_48;  
                    case 6 -> 0xFF_4A_1E_10;  
                    default -> state.skyColor;
                };
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
