package dev.kollegen.client.mixin;

import dev.kollegen.client.mods.modules.Performance;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;

@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {

    @Shadow
    public abstract <E extends BlockEntity, S extends BlockEntityRenderState> BlockEntityRenderer<E, S> getRenderer(E blockEntity);

    @Unique
    private static final int KOLLEGEN$BE_CACHE_SIZE = 2048;
    @Unique
    private static final int KOLLEGEN$BE_CACHE_MASK = KOLLEGEN$BE_CACHE_SIZE - 1;
    @Unique
    private static final long[] KOLLEGEN$BE_KEYS = new long[KOLLEGEN$BE_CACHE_SIZE];
    @Unique
    private static final long[] KOLLEGEN$BE_TIMES = new long[KOLLEGEN$BE_CACHE_SIZE];
    @Unique
    private static final boolean[] KOLLEGEN$BE_OCCLUDED = new boolean[KOLLEGEN$BE_CACHE_SIZE];

    static {
        Arrays.fill(KOLLEGEN$BE_KEYS, Long.MIN_VALUE);
    }

    @Inject(
            method = "tryExtractRenderState(Lnet/minecraft/world/level/block/entity/BlockEntity;FLnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;Z)Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;",
            at = @At("HEAD"),
            cancellable = true
    )
    private <E extends BlockEntity, S extends BlockEntityRenderState> void kollegen$cullOffScreenBlockEntity(
            E blockEntity,
            float tickDelta,
            ModelFeatureRenderer.CrumblingOverlay overlay,
            boolean bl,
            CallbackInfoReturnable<S> cir
    ) {
        if (!Performance.KollegenTricks.shouldCullBlockEntities()) return;
        if (blockEntity == null || overlay != null) return;

        try {
            BlockEntityRenderer<E, S> renderer = this.getRenderer(blockEntity);
            if (renderer == null || renderer.shouldRenderOffScreen()) return;

            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.gameRenderer == null) return;
            Camera camera = mc.gameRenderer.mainCamera();
            if (camera == null) return;
            Frustum frustum = camera.getCullFrustum();
            if (frustum == null) return;

            BlockPos pos = blockEntity.getBlockPos();
            Vec3 camPos = camera.position();
            double dx = (pos.getX() + 0.5) - camPos.x;
            double dy = (pos.getY() + 0.5) - camPos.y;
            double dz = (pos.getZ() + 0.5) - camPos.z;
            double distSq = dx * dx + dy * dy + dz * dz;

            // In direct proximity (within 3 blocks), never cull to avoid near-plane clipping
            if (distSq <= 9.0) return;

            // 1. Frustum Culling without allocating AABB object
            int frustumResult = ((FrustumAccessor) frustum).kollegen$cubeInFrustum(
                    pos.getX() - 0.75, pos.getY() - 0.75, pos.getZ() - 0.75,
                    pos.getX() + 1.75, pos.getY() + 1.75, pos.getZ() + 1.75
            );

            if (frustumResult != -2 && frustumResult != -1) {
                cir.setReturnValue(null);
                cir.cancel();
                return;
            }

            // 2. Wall / Occlusion Culling
            Level level = blockEntity.getLevel();
            if (level != null) {
                long beKey = pos.asLong();
                int beIdx = (int) (beKey ^ (beKey >>> 16)) & KOLLEGEN$BE_CACHE_MASK;
                long now = net.minecraft.util.Util.getMillis();

                if (KOLLEGEN$BE_KEYS[beIdx] == beKey && (now - KOLLEGEN$BE_TIMES[beIdx]) < (KOLLEGEN$BE_OCCLUDED[beIdx] ? 400L : 200L)) {
                    if (KOLLEGEN$BE_OCCLUDED[beIdx]) {
                        cir.setReturnValue(null);
                        cir.cancel();
                        return;
                    }
                } else {
                    Vec3 target = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                    BlockHitResult hit = level.clip(new ClipContext(camPos, target, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, (net.minecraft.world.entity.Entity) null));
                    boolean occluded = hit.getType() == HitResult.Type.BLOCK && !hit.getBlockPos().equals(pos);
                    KOLLEGEN$BE_KEYS[beIdx] = beKey;
                    KOLLEGEN$BE_TIMES[beIdx] = now;
                    KOLLEGEN$BE_OCCLUDED[beIdx] = occluded;
                    if (occluded) {
                        cir.setReturnValue(null);
                        cir.cancel();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
