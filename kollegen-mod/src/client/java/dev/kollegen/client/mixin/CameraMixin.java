package dev.kollegen.client.mixin;

import dev.kollegen.client.mods.modules.Freecam;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow protected abstract void setPosition(double x, double y, double z);
    @Shadow protected abstract void setRotation(float yRot, float xRot);
    @Shadow public abstract float getCameraEntityPartialTicks(DeltaTracker deltaTracker);

    @Inject(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V", shift = At.Shift.AFTER))
    private void kollegen_client$overrideFreecamUpdate(DeltaTracker deltaTracker, CallbackInfo ci) {
        if (Freecam.isActive()) {
            float pt = this.getCameraEntityPartialTicks(deltaTracker);
            this.setPosition(Freecam.getX(pt), Freecam.getY(pt), Freecam.getZ(pt));
            this.setRotation(Freecam.getYaw(pt), Freecam.getPitch(pt));
        }
    }

    @Inject(method = "isDetached", at = @At("HEAD"), cancellable = true)
    private void kollegen_client$overrideIsDetached(CallbackInfoReturnable<Boolean> cir) {
        if (Freecam.isActive()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void kollegen_client$overrideExtractRenderState(CameraRenderState state, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (Freecam.isActive()) {
            state.isFirstPerson = false;
            state.smartCull = false;
        }
    }
}
