package dev.kollegen.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kollegen.client.presence.CosmeticData;
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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(EntityRenderer.class)
public class NametagMixin {

    private static final Identifier ICON = Identifier.fromNamespaceAndPath("kollegen", "kollegen");
    private static final java.util.Map<EntityRenderState, java.util.UUID> STATE_UUID = new java.util.WeakHashMap<>();

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",
            at = @At("RETURN"))
    private void kollegen$capture(Entity entity, EntityRenderState state, float f, CallbackInfo ci) {
        boolean kollege = entity instanceof Player p && KollegenPresence.isKollegen(p.getUUID());
        KollegenPresence.markKollegen(state, kollege);
        if (entity instanceof Player p) {
            STATE_UUID.put(state, p.getUUID());
            kollegen$applyCosmetics(state, p.getUUID(), p.getDisplayName());
        }
    }

    private static void kollegen$applyCosmetics(EntityRenderState state, java.util.UUID id, Component fallback) {
        try {
            CosmeticData d = KollegenPresence.getCosmetics(id);
            if (d == null || d.isEmpty()) return;
            Component base = state.nameTag != null ? state.nameTag : fallback;
            if (base == null) return;
            String plain;
            try {
                plain = base.getString();
            } catch (Throwable ignored) {
                return;
            }
            if (plain == null || plain.isEmpty() || plain.contains(" · Lv ")) return;
            state.nameTag = CosmeticText.decorate(base, d);
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
            kollegen$applyCosmetics(state, id, null);
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "submitNameTag(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
            at = @At("RETURN"))
    private void kollegen$name(EntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                               CameraRenderState camera, CallbackInfo ci) {
        if (!KollegenPresence.isKollegen(state)) return;
        try {
            int s = 8;
            Component nameTag = state.nameTag;
            int tw = nameTag != null ? Minecraft.getInstance().font.width(nameTag) : 0;
            float x = -tw / 2f - s - 2;
            float y = -s / 2f;
            collector.submitCustomGeometry(poseStack, RenderTypes.textSeeThrough(ICON), (pose, vc) -> {
                vc.addVertex(pose, x, y + s, 0f).setColor(255, 255, 255, 255).setUv(0, 1);
                vc.addVertex(pose, x + s, y + s, 0f).setColor(255, 255, 255, 255).setUv(1, 1);
                vc.addVertex(pose, x + s, y, 0f).setColor(255, 255, 255, 255).setUv(1, 0);
                vc.addVertex(pose, x, y, 0f).setColor(255, 255, 255, 255).setUv(0, 0);
            });
        } catch (Throwable ignored) {
        }
    }
}
