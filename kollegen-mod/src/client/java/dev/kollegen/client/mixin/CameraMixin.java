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
    @Shadow protected abstract void move(float x, float y, float z);
    @Shadow protected abstract float getMaxZoom(float maxZoom);
    @Shadow public abstract float getCameraEntityPartialTicks(DeltaTracker deltaTracker);
    @Shadow private net.minecraft.world.entity.Entity entity;
    @Shadow private float eyeHeight;
    @Shadow private float eyeHeightOld;

    @Inject(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V", shift = At.Shift.AFTER))
    private void kollegen_client$overrideFreecamUpdate(DeltaTracker deltaTracker, CallbackInfo ci) {
        if (Freecam.isActive()) {
            float pt = this.getCameraEntityPartialTicks(deltaTracker);
            this.setPosition(Freecam.getX(pt), Freecam.getY(pt), Freecam.getZ(pt));
            this.setRotation(Freecam.getYaw(pt), Freecam.getPitch(pt));
        } else if (dev.kollegen.client.mods.modules.Visual.isAfkCinematicActive()) {
            float pt = this.getCameraEntityPartialTicks(deltaTracker);
            net.minecraft.world.entity.Entity e = this.entity;
            if (e != null) {
                double x = net.minecraft.util.Mth.lerp((double) pt, e.xo, e.getX());
                double y = net.minecraft.util.Mth.lerp((double) pt, e.yo, e.getY()) + net.minecraft.util.Mth.lerp(pt, this.eyeHeightOld, this.eyeHeight);
                double z = net.minecraft.util.Mth.lerp((double) pt, e.zo, e.getZ());
                this.setPosition(x, y, z);
                this.setRotation(dev.kollegen.client.mods.modules.Visual.getAfkCameraYaw(), dev.kollegen.client.mods.modules.Visual.getAfkCameraPitch());
                this.move(-this.getMaxZoom(dev.kollegen.client.mods.modules.Visual.getAfkCameraDistance()), 0.0f, 0.0f);
            }
        }
    }

    @Inject(method = "isDetached", at = @At("HEAD"), cancellable = true)
    private void kollegen_client$overrideIsDetached(CallbackInfoReturnable<Boolean> cir) {
        if (Freecam.isActive() || dev.kollegen.client.mods.modules.Visual.isAfkCinematicActive()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void kollegen_client$overrideExtractRenderState(CameraRenderState state, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (Freecam.isActive() || dev.kollegen.client.mods.modules.Visual.isAfkCinematicActive()) {
            state.isFirstPerson = false;
            state.smartCull = false;
        }
        if (state.entityRenderState != null) {
            float intensity = dev.kollegen.client.mods.modules.Visual.getFightCameraIntensity();
            state.entityRenderState.hurtTime *= intensity;
        }
    }
}
