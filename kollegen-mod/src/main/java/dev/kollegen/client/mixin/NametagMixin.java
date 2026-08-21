package dev.kollegen.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
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

/**
 * Zeichnet das "kollegen.png"-Badge links neben dem Namensschild eines
 * Kollegen-Client-Nutzers (erkennbar ueber die Backend-Präsenzliste).
 *
 * 1.21.11-Architektur: EntityRenderer.extractRenderState(T, S, float) liefert
 * uns das Entity (-> UUID), EntityRenderer.submitNameTag(S, ...) das ist der
 * Punkt, an dem das Namensschild gezeichnet wird.
 */
@Mixin(EntityRenderer.class)
public class NametagMixin {

    private static final Identifier ICON = Identifier.fromNamespaceAndPath("kollegen", "kollegen");

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",
            at = @At("RETURN"))
    private void kollegen$capture(Entity entity, EntityRenderState state, float f, CallbackInfo ci) {
        boolean kollege = entity instanceof Player p && KollegenPresence.isKollegen(p.getUUID());
        KollegenPresence.markKollegen(state, kollege);
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
