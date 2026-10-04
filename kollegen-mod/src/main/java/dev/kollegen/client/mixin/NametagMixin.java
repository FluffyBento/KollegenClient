package dev.kollegen.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kollegen.client.presence.CosmeticText;
import dev.kollegen.client.presence.KollegenPresence;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(EntityRenderer.class)
public class NametagMixin {

    private static final java.util.Map<EntityRenderState, java.util.UUID> STATE_UUID = new java.util.WeakHashMap<>();

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
            Component next = CosmeticText.decoratePlayer(cur, id);
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
}
