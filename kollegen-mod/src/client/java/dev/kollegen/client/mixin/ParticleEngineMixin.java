package dev.kollegen.client.mixin;

import dev.kollegen.client.mods.modules.Performance;
import dev.kollegen.client.mods.modules.Visual;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleEngine.class)
public class ParticleEngineMixin {

    @Inject(method = "add(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"), cancellable = true)
    private void kollegen$handleParticles(Particle particle, CallbackInfo ci) {
        if (Visual.particleCancelAll) {
            ci.cancel();
            return;
        }

        if (particle == null || !Performance.KollegenTricks.shouldCullParticles()) return;

        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.gameRenderer == null) return;
            Camera camera = mc.gameRenderer.mainCamera();
            if (camera == null) return;
            Frustum frustum = camera.getCullFrustum();
            if (frustum == null) return;

            AABB bb = particle.getBoundingBox();
            if (bb == null) return;

            Vec3 camPos = camera.position();
            double cx = (bb.minX + bb.maxX) * 0.5;
            double cy = (bb.minY + bb.maxY) * 0.5;
            double cz = (bb.minZ + bb.maxZ) * 0.5;
            double dx = cx - camPos.x;
            double dy = cy - camPos.y;
            double dz = cz - camPos.z;
            double distSq = dx * dx + dy * dy + dz * dz;

            if (distSq <= 16.0) return;

            if (distSq > 1296.0) {
                ci.cancel();
                return;
            }

            if (frustum instanceof FrustumAccessor fa) {
                int res = fa.kollegen$cubeInFrustum(
                        bb.minX - 1.0, bb.minY - 1.0, bb.minZ - 1.0,
                        bb.maxX + 1.0, bb.maxY + 1.0, bb.maxZ + 1.0
                );
                if (res >= 0) {
                    ci.cancel();
                    return;
                }
            } else {
                AABB inflated = bb.inflate(1.0);
                if (!frustum.isVisible(inflated)) {
                    ci.cancel();
                    return;
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
