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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {

    @Shadow
    public abstract <E extends BlockEntity, S extends BlockEntityRenderState> BlockEntityRenderer<E, S> getRenderer(E blockEntity);

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

            if (distSq <= 9.0) return;

            int frustumResult = ((FrustumAccessor) frustum).kollegen$cubeInFrustum(
                    pos.getX() - 0.75, pos.getY() - 0.75, pos.getZ() - 0.75,
                    pos.getX() + 1.75, pos.getY() + 1.75, pos.getZ() + 1.75
            );

            if (frustumResult != -2 && frustumResult != -1) {
                cir.setReturnValue(null);
                cir.cancel();
            }
        } catch (Throwable ignored) {
        }
    }
}
