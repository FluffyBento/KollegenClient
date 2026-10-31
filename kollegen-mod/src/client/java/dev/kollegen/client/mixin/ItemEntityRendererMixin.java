package dev.kollegen.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.kollegen.client.mods.modules.ItemPhysics;
import dev.kollegen.client.render.KollegenItemPhysicsState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin {

    @Shadow @Final private RandomSource random;

    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;F)V",
            at = @At("TAIL")
    )
    private void kollegen$extractItemPhysics(ItemEntity entity, ItemEntityRenderState state, float partialTick, CallbackInfo ci) {
        if (state instanceof KollegenItemPhysicsState physicsState) {
            Vec3 delta = entity.getDeltaMovement();
            boolean stationary = Math.abs(delta.y) < 0.01 && delta.horizontalDistanceSqr() < 0.005;
            boolean onGround = entity.onGround() || entity.verticalCollision || stationary;
            physicsState.kollegen$setOnGround(onGround);

            ItemStack stack = entity.getItem();
            boolean isBlock = stack != null && !stack.isEmpty() && stack.getItem() instanceof BlockItem;
            physicsState.kollegen$setBlock(isBlock);

            float yaw = (float) ((Math.abs(entity.getUUID().getLeastSignificantBits()) % 3600) / 10.0);
            physicsState.kollegen$setYaw(yaw);
        }
    }

    @Inject(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void kollegen$submitItemPhysics(ItemEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraRenderState, CallbackInfo ci) {
        if (!ItemPhysics.isItemPhysicsEnabled()) return;
        if (state.item == null || state.item.isEmpty()) return;

        boolean onGround = (state instanceof KollegenItemPhysicsState ps) && ps.kollegen$isOnGround();
        boolean isBlockItem = (state instanceof KollegenItemPhysicsState ps) && ps.kollegen$isBlock();
        float yaw = (state instanceof KollegenItemPhysicsState ps) ? ps.kollegen$getYaw() : (float) (Math.abs(state.seed * 37) % 360);

        AABB aabb = state.item.getModelBoundingBox();
        boolean isFullCube = isBlockItem && aabb.getXsize() > 0.4 && aabb.getYsize() > 0.4 && aabb.getZsize() > 0.4;

        poseStack.pushPose();

        if (onGround) {
            if (!isFullCube && ItemPhysics.shouldLieFlat()) {
                float halfThick = (float) Math.max(aabb.getZsize() * 0.5, 0.015);
                float yOff = halfThick + 0.005F;
                poseStack.translate(0.0F, yOff, 0.0F);
                poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
                poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            } else {
                float yOff = (float) -aabb.minY + 0.005F;
                poseStack.translate(0.0F, yOff, 0.0F);
                poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
            }
        } else {
            float speed = ItemPhysics.getRotationSpeed();
            if (speed > 0.01F) {
                float tumble = (state.ageInTicks * 12.0F * speed) % 360.0F;
                float yOff = (float) -aabb.minY * 0.5F + 0.08F;
                poseStack.translate(0.0F, yOff, 0.0F);
                poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
                poseStack.mulPose(Axis.XP.rotationDegrees(tumble));
            } else {
                float yOff = (float) -aabb.minY + 0.02F;
                poseStack.translate(0.0F, yOff, 0.0F);
                poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
                if (!isFullCube && ItemPhysics.shouldLieFlat()) {
                    poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
                }
            }
        }

        ItemEntityRenderer.submitMultipleFromCount(poseStack, collector, state.lightCoords, state, this.random, aabb);
        poseStack.popPose();
        ci.cancel();
    }
}

