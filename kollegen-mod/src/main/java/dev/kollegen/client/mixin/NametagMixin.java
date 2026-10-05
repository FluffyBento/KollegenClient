package dev.kollegen.client.mixin;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.kollegen.client.presence.CosmeticData;
import dev.kollegen.client.presence.CosmeticText;
import dev.kollegen.client.presence.KollegenPresence;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(AvatarRenderer.class)
public class NametagMixin {

    private static final Identifier LOGO = Identifier.fromNamespaceAndPath("kollegen", "textures/gui/logo_mark.png");
    private static final java.util.Map<AvatarRenderState, java.util.UUID> STATE_ID = new java.util.WeakHashMap<>();

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("RETURN"))
    private void kollegen$track(net.minecraft.world.entity.Avatar entity, AvatarRenderState state, float f, CallbackInfo ci) {
        try {
            if (entity instanceof net.minecraft.world.entity.player.Player p) STATE_ID.put(state, p.getUUID());
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "submitNameTag(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
            at = @At("HEAD"))
    private void kollegen$extras(AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                                 CameraRenderState camera, CallbackInfo ci) {
        try {
            if (state.isDiscrete) return;
            java.util.UUID id = STATE_ID.get(state);
            if (id == null) id = kollegen$resolve(state);
            if (id == null) return;
            CosmeticData d = KollegenPresence.getCosmetics(id);
            boolean hasData = d != null && !d.isEmpty();
            if (!KollegenPresence.isKollegen(id) && !hasData) return;
            boolean matchedA = kollegen$matches(state.scoreText, id);
            boolean matchedW = !matchedA && kollegen$matches(state.nameTag, id);
            if (!matchedA && !matchedW) return;
            if (hasData) {
                if (matchedA) state.scoreText = CosmeticText.decorateNameLine(state.scoreText, id);
                else state.nameTag = CosmeticText.decorateNameLine(state.nameTag, id);
            }
            Vec3 anchor = state.nameTagAttachment;
            if (anchor == null) return;
            double nameY = anchor.y + (matchedW ? 0.259 : 0.0);
            Component title = hasData ? CosmeticText.titleComponent(id) : null;
            if (title != null) {
                collector.submitNameTag(poseStack,
                        new Vec3(anchor.x, anchor.y + 0.62, anchor.z),
                        0, title, !state.isDiscrete, state.lightCoords, state.distanceToCameraSq, camera);
            }
            Component level = hasData ? CosmeticText.levelComponent(id) : null;
            if (level != null) {
                collector.submitNameTag(poseStack,
                        new Vec3(anchor.x, anchor.y - 0.32, anchor.z),
                        0, level, !state.isDiscrete, state.lightCoords, state.distanceToCameraSq, camera);
            }
            if (KollegenPresence.isKollegen(id)) {
                Component nameLine = matchedW ? state.nameTag : state.scoreText;
                int tw = 60;
                try {
                    if (nameLine != null) tw = Minecraft.getInstance().font.width(nameLine);
                } catch (Throwable ignored) {
                }
                kollegen$logo(poseStack, collector, anchor, nameY, tw, camera.orientation);
            }
        } catch (Throwable ignored) {
        }
    }

    private static java.util.UUID kollegen$resolve(AvatarRenderState state) {
        try {
            String a = state.scoreText != null ? state.scoreText.getString() : null;
            String w = state.nameTag != null ? state.nameTag.getString() : null;
            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() == null) return null;
            for (PlayerInfo pi : mc.getConnection().getOnlinePlayers()) {
                GameProfile profile = pi.getProfile();
                if (profile == null) continue;
                String n = profile.name();
                if ((a != null && a.equals(n)) || (w != null && w.equals(n))) return profile.id();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static boolean kollegen$matches(Component line, java.util.UUID id) {
        try {
            if (line == null) return false;
            String plain = line.getString();
            if (plain == null || plain.isEmpty()) return false;
            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() == null) return false;
            for (PlayerInfo pi : mc.getConnection().getOnlinePlayers()) {
                GameProfile profile = pi.getProfile();
                if (profile == null) continue;
                if (!profile.id().equals(id)) continue;
                return plain.equals(profile.name());
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static void kollegen$logo(PoseStack poseStack, SubmitNodeCollector collector, Vec3 anchor, double nameY, int tw, org.joml.Quaternionf orientation) {
        try {
            if (orientation == null) return;
            poseStack.pushPose();
            poseStack.translate(anchor.x, nameY, anchor.z);
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
