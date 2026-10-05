package dev.kollegen.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kollegen.client.presence.CosmeticText;
import dev.kollegen.client.presence.KollegenPresence;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(EntityRenderer.class)
public class NametagMixin {

    private static final java.util.Map<EntityRenderState, java.util.UUID> STATE_UUID = new java.util.WeakHashMap<>();
    private static final Identifier LOGO = Identifier.fromNamespaceAndPath("kollegen", "textures/gui/logo_mark.png");

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",
            at = @At("RETURN"))
    private void kollegen$capture(Entity entity, EntityRenderState state, float f, CallbackInfo ci) {
        boolean kollege = entity instanceof Player p && KollegenPresence.isKollegen(p.getUUID());
        KollegenPresence.markKollegen(state, kollege);
        if (entity instanceof Player p) {
            STATE_UUID.put(state, p.getUUID());
            kollegen$applyCosmetics(state, p.getUUID());
        }
    }

    private static void kollegen$applyCosmetics(EntityRenderState state, java.util.UUID id) {
        try {
            Component cur = state.nameTag;
            if (cur == null) return;
            Component next = CosmeticText.decorateNameLine(cur, id);
            if (next != cur) state.nameTag = next;
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "submitNameTag(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
            at = @At("HEAD"))
    private void kollegen$decorateEarly(EntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                                        CameraRenderState camera, CallbackInfo ci) {
        try {
            java.util.UUID id = STATE_UUID.get(state);
            if (id == null) return;
            kollegen$applyCosmetics(state, id);
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "submitNameTag(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
            at = @At("RETURN"))
    private void kollegen$extras(EntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                                 CameraRenderState camera, CallbackInfo ci) {
        if (!KollegenPresence.isKollegen(state)) return;
        try {
            if (state.nameTag == null) return;
            java.util.UUID id = STATE_UUID.get(state);
            if (id == null) return;
            Vec3 anchor = state.nameTagAttachment;
            if (anchor == null) return;
            Component title = CosmeticText.titleComponent(id);
            if (title != null) {
                collector.submitNameTag(poseStack,
                        new Vec3(anchor.x, anchor.y + 0.32, anchor.z),
                        0, title, !state.isDiscrete, state.lightCoords, state.distanceToCameraSq, camera);
            }
            Component level = CosmeticText.levelComponent(id);
            if (level != null) {
                collector.submitNameTag(poseStack,
                        new Vec3(anchor.x, anchor.y - 0.34, anchor.z),
                        0, level, !state.isDiscrete, state.lightCoords, state.distanceToCameraSq, camera);
            }
            kollegen$logo(state, poseStack, collector, anchor, camera.orientation);
        } catch (Throwable ignored) {
        }
    }

    private static void kollegen$logo(EntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, Vec3 anchor, org.joml.Quaternionf orientation) {
        try {
            Component nameTag = state.nameTag;
            if (nameTag == null || orientation == null) return;
            int tw = Minecraft.getInstance().font.width(nameTag);
            poseStack.pushPose();
            poseStack.translate(anchor.x, anchor.y, anchor.z);
            poseStack.mulPose(orientation);
            poseStack.scale(0.025F, -0.025F, 0.025F);
            int s = 8;
            float x = -tw / 2f - s - 2;
            float y = -s / 2f;
            collector.submitCustomGeometry(poseStack, RenderTypes.textSeeThrough(LOGO), (pose, vc) -> {
                vc.addVertex(pose, x, y + s, 0f).setColor(255, 255, 255, 255).setUv(0, 1);
                vc.addVertex(pose, x + s, y + s, 0f).setColor(255, 255, 255, 255).setUv(1, 1);
                vc.addVertex(pose, x + s, y, 0f).setColor(255, 255, 255, 255).setUv(1, 0);
                vc.addVertex(pose, x, y, 0f).setColor(255, 255, 255, 255).setUv(0, 0);
            });
            poseStack.popPose();
        } catch (Throwable ignored) {
        }
    }
}
