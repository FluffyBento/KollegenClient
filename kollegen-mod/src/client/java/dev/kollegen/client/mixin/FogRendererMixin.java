package dev.kollegen.client.mixin;

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
        if (WeatherState.mode < 4) return;
        try {
            FogData data = cir.getReturnValue();
            if (data == null) return;
            switch (WeatherState.mode) {
                case 4 -> data.color.set(0.80F, 0.80F, 0.82F, 1.0F);
                case 5 -> data.color.set(0.18F, 0.08F, 0.28F, 1.0F);
                case 6 -> data.color.set(0.65F, 0.28F, 0.12F, 1.0F);
                default -> {}
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
