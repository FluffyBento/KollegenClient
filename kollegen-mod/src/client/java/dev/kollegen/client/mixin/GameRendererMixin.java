package dev.kollegen.client.mixin;

import dev.kollegen.client.mods.modules.Visual;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public class GameRendererMixin {

    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void kollegen$zoomCalculateFov(float partialTicks, CallbackInfoReturnable<Float> cir) {
        float divisor = Visual.getEffectiveFovDivisor(0.05f);
        if (divisor > 1.001f) {
            float fov = cir.getReturnValueF();
            if (fov > 0f) {
                cir.setReturnValue(Math.max(1.0f, fov / divisor));
            }
        }
    }

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void kollegen$zoomGetFov(CallbackInfoReturnable<Float> cir) {
        float divisor = Visual.getEffectiveFovDivisor(0.05f);
        if (divisor > 1.001f) {
            float fov = cir.getReturnValueF();
            if (fov > 0f) {
                cir.setReturnValue(Math.max(1.0f, fov / divisor));
            }
        }
    }
}
