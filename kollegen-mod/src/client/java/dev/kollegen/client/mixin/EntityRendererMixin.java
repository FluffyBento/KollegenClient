package dev.kollegen.client.mixin;

import dev.kollegen.client.mods.modules.Performance;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<T extends Entity, S extends EntityRenderState> {

    @Inject(
            method = "shouldRender(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDDF)Z",
            at = @At("RETURN"),
            cancellable = true
    )
    private void kollegen$cullOccludedEntity(
            T entity,
            Frustum frustum,
            double camX,
            double camY,
            double camZ,
            float tickDelta,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!cir.getReturnValueZ()) return;
        if (!Performance.KollegenTricks.shouldCullOccludedEntities()) return;
        if (entity == null) return;

        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.player != null) {
                if (entity == mc.player) return;
                if (entity.hasPassenger(mc.player) || mc.player.isPassengerOfSameVehicle(entity)) return;
            }

            if (entity.isCurrentlyGlowing() || entity.hasCustomName()) return;
            if (entity instanceof EnderDragon || entity instanceof WitherBoss) return;
            if (entity instanceof net.minecraft.world.entity.monster.warden.Warden) return;
            if (entity instanceof net.minecraft.world.entity.monster.ElderGuardian) return;
            if (entity instanceof net.minecraft.world.entity.TamableAnimal tamed && tamed.isTame()) return;
            if (entity instanceof net.minecraft.world.entity.Mob mob && mob.isLeashed()) return;

            if (Performance.KollegenTricks.shouldPurgeMarkerArmorStands()) {
                if (entity instanceof net.minecraft.world.entity.decoration.ArmorStand as) {
                    if (as.isMarker() && as.isInvisible() && !as.hasCustomName()) {
                        cir.setReturnValue(false);
                        return;
                    }
                }
            }

            if (Performance.KollegenTricks.shouldCullHanging()) {
                if (entity instanceof net.minecraft.world.entity.decoration.HangingEntity hanging) {
                    net.minecraft.core.Direction dir = hanging.getDirection();
                    if (dir != null) {
                        double nx = dir.getStepX();
                        double ny = dir.getStepY();
                        double nz = dir.getStepZ();
                        double vx = camX - hanging.getX();
                        double vy = camY - hanging.getY();
                        double vz = camZ - hanging.getZ();
                        if (nx * vx + ny * vy + nz * vz < -0.05) {
                            cir.setReturnValue(false);
                            return;
                        }
                    }
                }
            }

            if (Performance.KollegenTricks.shouldCullDistantMobs()) {
                double dx = entity.getX() - camX;
                double dy = entity.getY() - camY;
                double dz = entity.getZ() - camZ;
                double distSq = dx * dx + dy * dy + dz * dz;
                if (distSq > 2304.0) {
                    if (!(entity instanceof net.minecraft.world.entity.player.Player)
                            && !(entity instanceof net.minecraft.world.entity.projectile.Projectile)
                            && !(entity instanceof EnderDragon)
                            && !(entity instanceof WitherBoss)) {
                        cir.setReturnValue(false);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    @Inject(
            method = "extractShadow(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/client/Minecraft;Lnet/minecraft/world/level/Level;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void kollegen$cullOffScreenShadow(
            EntityRenderState state,
            Minecraft mc,
            Level level,
            CallbackInfo ci
    ) {
        if (!Performance.KollegenTricks.shouldCullShadows()) return;
        if (state == null) return;

        try {
            if (state.distanceToCameraSq > 256.0) return;
            if (state.distanceToCameraSq <= 9.0) return;

            if (mc == null || mc.gameRenderer == null) return;
            Camera camera = mc.gameRenderer.mainCamera();
            if (camera == null) return;
            Frustum frustum = camera.getCullFrustum();
            if (frustum == null) return;

            double r = state.shadowRadius > 0.0f ? state.shadowRadius : 1.0;
            AABB shadowAABB = new AABB(
                    state.x - r - 1.0, state.y - 4.0, state.z - r - 1.0,
                    state.x + r + 1.0, state.y + 1.5, state.z + r + 1.0
            );

            if (!frustum.isVisible(shadowAABB)) {
                ci.cancel();
            }
        } catch (Throwable ignored) {
        }
    }
}
