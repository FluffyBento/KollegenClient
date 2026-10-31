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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<T extends Entity, S extends EntityRenderState> {

    @Unique
    private static final int KOLLEGEN$ENTITY_CACHE_SIZE = 4096;
    @Unique
    private static final int KOLLEGEN$ENTITY_MASK = KOLLEGEN$ENTITY_CACHE_SIZE - 1;
    @Unique
    private static final int[] KOLLEGEN$CACHED_IDS = new int[KOLLEGEN$ENTITY_CACHE_SIZE];
    @Unique
    private static final long[] KOLLEGEN$CACHED_TIMES = new long[KOLLEGEN$ENTITY_CACHE_SIZE];
    @Unique
    private static final boolean[] KOLLEGEN$CACHED_OCCLUDED = new boolean[KOLLEGEN$ENTITY_CACHE_SIZE];

    static {
        Arrays.fill(KOLLEGEN$CACHED_IDS, -1);
    }

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

            // 1. Purge invisible marker armor stands (server holograms & anchors)
            if (Performance.KollegenTricks.shouldPurgeMarkerArmorStands()) {
                if (entity instanceof net.minecraft.world.entity.decoration.ArmorStand as) {
                    if (as.isMarker() && as.isInvisible() && !as.hasCustomName()) {
                        cir.setReturnValue(false);
                        return;
                    }
                }
            }

            // 2. Hanging entity backface culling (Item frames & paintings facing away from camera)
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

            double dx = entity.getX() - camX;
            double dy = entity.getY() - camY;
            double dz = entity.getZ() - camZ;
            double distSq = dx * dx + dy * dy + dz * dz;

            // 3. Distant mob culling (> 48 blocks for passive/monster mobs; players, bosses & named pets stay full distance)
            if (Performance.KollegenTricks.shouldCullDistantMobs()) {
                if (distSq > 2304.0) {
                    if (!(entity instanceof net.minecraft.world.entity.player.Player)
                            && !(entity instanceof net.minecraft.world.entity.projectile.Projectile)
                            && !(entity instanceof EnderDragon)
                            && !(entity instanceof WitherBoss)) {
                        cir.setReturnValue(false);
                        return;
                    }
                }
            }

            // In close proximity (< 3.0 blocks): never cull to avoid clipping
            if (distSq <= 9.0) return;

            long now = net.minecraft.util.Util.getMillis();
            int id = entity.getId();
            int idx = (id ^ (id >>> 6)) & KOLLEGEN$ENTITY_MASK;

            if (KOLLEGEN$CACHED_IDS[idx] == id && (now - KOLLEGEN$CACHED_TIMES[idx]) < (KOLLEGEN$CACHED_OCCLUDED[idx] ? 350L : 180L)) {
                if (KOLLEGEN$CACHED_OCCLUDED[idx]) {
                    cir.setReturnValue(false);
                }
                return;
            }

            Level level = entity.level();
            if (level == null) return;

            Vec3 camPos = new Vec3(camX, camY, camZ);

            // Fast Check: Eye position (vast majority of visible entities are visible at eye level)
            Vec3 eyePos = entity.getEyePosition(tickDelta);
            BlockHitResult hitEye = level.clip(new ClipContext(camPos, eyePos, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, entity));
            if (hitEye.getType() != HitResult.Type.BLOCK) {
                KOLLEGEN$CACHED_IDS[idx] = id;
                KOLLEGEN$CACHED_TIMES[idx] = now;
                KOLLEGEN$CACHED_OCCLUDED[idx] = false;
                return;
            }

            // Check 2: Center position
            Vec3 centerPos = new Vec3(entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ());
            BlockHitResult hitCenter = level.clip(new ClipContext(camPos, centerPos, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, entity));
            if (hitCenter.getType() != HitResult.Type.BLOCK) {
                KOLLEGEN$CACHED_IDS[idx] = id;
                KOLLEGEN$CACHED_TIMES[idx] = now;
                KOLLEGEN$CACHED_OCCLUDED[idx] = false;
                return;
            }

            // Check 3: Feet position
            Vec3 feetPos = new Vec3(entity.getX(), entity.getY() + 0.15, entity.getZ());
            BlockHitResult hitFeet = level.clip(new ClipContext(camPos, feetPos, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, entity));
            if (hitFeet.getType() != HitResult.Type.BLOCK) {
                KOLLEGEN$CACHED_IDS[idx] = id;
                KOLLEGEN$CACHED_TIMES[idx] = now;
                KOLLEGEN$CACHED_OCCLUDED[idx] = false;
                return;
            }

            // Check 4 & 5: Left and right silhouette edges (avoids pop-in when peeking around corners)
            double halfWidth = entity.getBbWidth() * 0.45;
            double invDist = 1.0 / Math.sqrt(distSq);
            double perpX = -dz * invDist * halfWidth;
            double perpZ = dx * invDist * halfWidth;

            Vec3 leftPos = new Vec3(entity.getX() + perpX, entity.getY() + entity.getBbHeight() * 0.5, entity.getZ() + perpZ);
            BlockHitResult hitLeft = level.clip(new ClipContext(camPos, leftPos, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, entity));
            if (hitLeft.getType() != HitResult.Type.BLOCK) {
                KOLLEGEN$CACHED_IDS[idx] = id;
                KOLLEGEN$CACHED_TIMES[idx] = now;
                KOLLEGEN$CACHED_OCCLUDED[idx] = false;
                return;
            }

            Vec3 rightPos = new Vec3(entity.getX() - perpX, entity.getY() + entity.getBbHeight() * 0.5, entity.getZ() - perpZ);
            BlockHitResult hitRight = level.clip(new ClipContext(camPos, rightPos, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, entity));
            if (hitRight.getType() != HitResult.Type.BLOCK) {
                KOLLEGEN$CACHED_IDS[idx] = id;
                KOLLEGEN$CACHED_TIMES[idx] = now;
                KOLLEGEN$CACHED_OCCLUDED[idx] = false;
                return;
            }

            // All 5 points blocked by opaque blocks -> occluded behind wall!
            KOLLEGEN$CACHED_IDS[idx] = id;
            KOLLEGEN$CACHED_TIMES[idx] = now;
            KOLLEGEN$CACHED_OCCLUDED[idx] = true;
            cir.setReturnValue(false);
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
            // Shadows beyond 16 blocks (256 distSq) have 0 opacity in vanilla anyway
            if (state.distanceToCameraSq > 256.0) return;

            // Direct proximity to camera (< 3 blocks / 9 distSq): always keep to avoid near-plane edge cases
            if (state.distanceToCameraSq <= 9.0) return;

            if (mc == null || mc.gameRenderer == null) return;
            Camera camera = mc.gameRenderer.mainCamera();
            if (camera == null) return;
            Frustum frustum = camera.getCullFrustum();
            if (frustum == null) return;

            double r = state.shadowRadius > 0.0f ? state.shadowRadius : 1.0;
            // Bounding box enclosing the shadow area on the ground underneath the entity (+1.0 block margin)
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
