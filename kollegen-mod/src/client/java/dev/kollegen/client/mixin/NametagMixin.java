package dev.kollegen.client.mixin;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.kollegen.client.menu.SkinCapeTextureManager;
import dev.kollegen.client.presence.CosmeticData;
import dev.kollegen.client.presence.CosmeticText;
import dev.kollegen.client.presence.KollegenPresence;
import dev.kollegen.client.presence.PetRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AvatarRenderer.class)
public class NametagMixin {

    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/Avatar;D)Z", at = @At("HEAD"), cancellable = true)
    private void kollegen$showSelfName(net.minecraft.world.entity.Avatar entity, double d, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (entity instanceof net.minecraft.world.entity.player.Player p) {
                Minecraft mc = Minecraft.getInstance();
                boolean isSelf = mc.player != null && (p.getUUID().equals(mc.player.getUUID()) || p.getId() == mc.player.getId());
                if (isSelf) {
                    if (dev.kollegen.client.mods.modules.Player.shouldHideNametag(true)) {
                        cir.setReturnValue(false);
                    } else {
                        cir.setReturnValue(true);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("RETURN"))
    private void kollegen$track(net.minecraft.world.entity.Avatar entity, AvatarRenderState state, float f, CallbackInfo ci) {
        try {
            if (entity instanceof net.minecraft.world.entity.player.Player p) {
                state.id = p.getId();
                Minecraft mc = Minecraft.getInstance();
                boolean isSelf = mc.player != null && (p.getUUID().equals(mc.player.getUUID()) || p.getId() == mc.player.getId());
                if (isSelf) {
                    if (state.nameTag == null) {
                        state.nameTag = p.getDisplayName();
                    }
                    if (state.nameTagAttachment == null) {
                        state.nameTagAttachment = new Vec3(0.0, p.getBbHeight() + 0.5, 0.0);
                    }
                }
                if (dev.kollegen.client.mods.modules.Player.shouldHideNametag(isSelf)) {
                    state.nameTag = null;
                    state.scoreText = null;
                    state.nameTagAttachment = null;
                }
                dev.kollegen.client.presence.RenderStateIds.put(state, p.getUUID());
                PetRenderer.notePosition(p.getUUID(), p.position(), p.getYRot());
                kollegen$cape(state, p.getUUID());
            }
        } catch (Throwable ignored) {
        }
    }

    private static void kollegen$cape(AvatarRenderState state, java.util.UUID id) {
        try {
            if (state == null || id == null) return;
            CosmeticData data = KollegenPresence.getCosmetics(id);
            if (data == null || data.capeTexture == null) return;
            Identifier tex = SkinCapeTextureManager.getOrLoadCape(data.capeTexture, data.capeTexture);
            if (tex == null) {
                tex = Identifier.tryParse(data.capeTexture.toLowerCase(java.util.Locale.ROOT));
            }
            if (tex == null) return;
            state.showCape = true;
            try {
                net.minecraft.world.entity.player.PlayerSkin skin = state.skin;
                if (skin != null) {
                    state.skin = new net.minecraft.world.entity.player.PlayerSkin(skin.body(),
                            new net.minecraft.core.ClientAsset.ResourceTexture(tex), skin.elytra(), skin.model(), skin.secure());
                }
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void kollegen$extras(AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                                 CameraRenderState camera, CallbackInfo ci) {
        try {
            if (state == null || poseStack == null || collector == null || camera == null) return;
            if (state.isDiscrete && state.distanceToCameraSq > 1024.0) {
                return;
            }
            java.util.UUID id = dev.kollegen.client.presence.RenderStateIds.get(state);
            if (id == null) id = kollegen$resolve(state);

            Minecraft mc = Minecraft.getInstance();
            boolean isSelf = mc.player != null && ((id != null && id.equals(mc.player.getUUID())) || (state.id != 0 && state.id == mc.player.getId()));
            if (isSelf && id == null && mc.player != null) {
                id = mc.player.getUUID();
            }

            if (isSelf && mc.options.getCameraType().isFirstPerson()) {
                ci.cancel();
                return;
            }

            if (dev.kollegen.client.mods.modules.Player.shouldHideNametag(isSelf)) {
                ci.cancel();
                return;
            }

            CosmeticData d = id != null ? KollegenPresence.getCosmetics(id) : null;
            boolean hasData = d != null && !d.isEmpty();
            boolean isKollegen = isSelf || (id != null && KollegenPresence.isKollegen(id)) || hasData;

            if (!isKollegen) {
                return;
            }

            Vec3 anchor = state.nameTagAttachment;
            if (anchor == null) {
                if (isSelf && mc.player != null) {
                    anchor = new Vec3(0.0, mc.player.getBbHeight() + 0.5, 0.0);
                } else {
                    return;
                }
            }

            Font font = mc.font;
            if (font == null) return;

            boolean showTitle = hasData && d.titleText != null && !d.titleText.trim().isEmpty() && !dev.kollegen.client.mods.modules.Player.shouldHideTitle(isSelf);
            Component titleComp = null;
            int titleWidth = 0;
            if (showTitle) {
                int nameColor = CosmeticText.parseColor(d.nameColor, 0xFFFFFF);
                int tColor = CosmeticText.titleColor(d, nameColor);
                titleComp = Component.literal(d.titleText.trim()).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(tColor)).withBold(true));
                titleWidth = font.width(titleComp);
            }

            String plainName = state.nameTag != null ? state.nameTag.getString() : (mc.player != null && isSelf ? mc.player.getName().getString() : "?");
            Component nameComp = (hasData || isSelf) && id != null ? CosmeticText.decorateNameLine(Component.literal(plainName), id) : (state.nameTag != null ? state.nameTag : Component.literal(plainName));
            int nameWidth = font.width(nameComp);

            int maxTextW = Math.max(nameWidth, titleWidth);
            float padX = 7.0F;
            float x0 = -maxTextW / 2.0F - padX;
            float x1 = maxTextW / 2.0F + padX;

            float y0, y1, titleY, nameY;
            if (showTitle) {
                y0 = -13.0F;
                y1 = 13.0F;
                titleY = -10.0F;
                nameY = 2.0F;
            } else {
                y0 = -6.5F;
                y1 = 8.5F;
                titleY = 0.0F;
                nameY = -3.5F;
            }

            float tX = -titleWidth / 2.0F;
            float nX = -nameWidth / 2.0F;

            poseStack.pushPose();
            poseStack.translate(anchor.x, anchor.y + 0.5, anchor.z);
            poseStack.mulPose(camera.orientation);
            poseStack.scale(0.025F, -0.025F, 0.025F);

            int bgAlpha = state.isDiscrete ? 75 : 166;
            int borderAlpha = state.isDiscrete ? 15 : 34;
            kollegen$renderStageCard(poseStack, collector, x0, y0, x1, y1, bgAlpha, borderAlpha);

            poseStack.translate(0.0F, 0.0F, -0.05F);

            FormattedCharSequence nameSeq = nameComp.getVisualOrderText();
            collector.submitText(poseStack, nX, nameY, nameSeq, true, Font.DisplayMode.NORMAL, -1, 0, state.lightCoords, 0);

            if (showTitle) {
                FormattedCharSequence titleSeq = titleComp.getVisualOrderText();
                collector.submitText(poseStack, tX, titleY, titleSeq, true, Font.DisplayMode.NORMAL, -1, 0, state.lightCoords, 0);
            }

            if (!state.isDiscrete) {
                int seeThroughColor = (0x80 << 24) | 0xFFFFFF;
                collector.submitText(poseStack, nX, nameY, nameSeq, false, Font.DisplayMode.SEE_THROUGH, seeThroughColor, 0, state.lightCoords, 0);
                if (showTitle) {
                    FormattedCharSequence titleSeq = titleComp.getVisualOrderText();
                    collector.submitText(poseStack, tX, titleY, titleSeq, false, Font.DisplayMode.SEE_THROUGH, seeThroughColor, 0, state.lightCoords, 0);
                }
            }

            if (state.scoreText != null) {
                int scoreW = font.width(state.scoreText);
                float sX = -scoreW / 2.0F;
                float sY = y1 + 4.0F;
                FormattedCharSequence scoreSeq = state.scoreText.getVisualOrderText();
                collector.submitText(poseStack, sX, sY, scoreSeq, true, Font.DisplayMode.NORMAL, -1, 0, state.lightCoords, 0);
            }

            poseStack.popPose();
            ci.cancel();
        } catch (Throwable ignored) {
        }
    }

    private static void kollegen$renderStageCard(PoseStack poseStack, SubmitNodeCollector collector, float x0, float y0, float x1, float y1, int bgAlpha, int borderAlpha) {
        try {
            collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, vc) -> {
                Matrix4f mat = pose.pose();
                float rad = 2.0F;

                kollegen$emitRoundedBox(vc, mat, x0, y0 + 1.2F, x1, y1 + 2.0F, rad, 0, 0, 0, Math.min(bgAlpha, 60), 0.02F);

                kollegen$emitRoundedBox(vc, mat, x0, y0, x1, y1, rad, 8, 9, 12, bgAlpha, 0.0F);

                kollegen$emitRoundedBorder(vc, mat, x0, y0, x1, y1, rad, 255, 255, 255, borderAlpha, -0.01F);
            });
        } catch (Throwable ignored) {
        }
    }

    private static void kollegen$emitRoundedBox(VertexConsumer vc, Matrix4f mat, float x0, float y0, float x1, float y1, float r, int cr, int cg, int cb, int ca, float z) {
        kollegen$emitQuad(vc, mat, x0 + r, y0, x1 - r, y1, cr, cg, cb, ca, z);
        kollegen$emitQuad(vc, mat, x0, y0 + r, x0 + r, y1 - r, cr, cg, cb, ca, z);
        kollegen$emitQuad(vc, mat, x1 - r, y0 + r, x1, y1 - r, cr, cg, cb, ca, z);

        kollegen$emitQuad(vc, mat, x0, y0 + r, x0 + r, y0, x0 + r, y0 + r, x0 + r, y0 + r, cr, cg, cb, ca, z);
        kollegen$emitQuad(vc, mat, x1 - r, y0, x1, y0 + r, x1 - r, y0 + r, x1 - r, y0 + r, cr, cg, cb, ca, z);
        kollegen$emitQuad(vc, mat, x1, y1 - r, x1 - r, y1, x1 - r, y1 - r, x1 - r, y1 - r, cr, cg, cb, ca, z);
        kollegen$emitQuad(vc, mat, x0 + r, y1, x0, y1 - r, x0 + r, y1 - r, x0 + r, y1 - r, cr, cg, cb, ca, z);
    }

    private static void kollegen$emitRoundedBorder(VertexConsumer vc, Matrix4f mat, float x0, float y0, float x1, float y1, float r, int cr, int cg, int cb, int ca, float z) {
        float b = 0.8F;
        kollegen$emitQuad(vc, mat, x0 + r, y0 - b, x1 - r, y0, cr, cg, cb, ca, z);
        kollegen$emitQuad(vc, mat, x0 + r, y1, x1 - r, y1 + b, cr, cg, cb, ca, z);
        kollegen$emitQuad(vc, mat, x0 - b, y0 + r, x0, y1 - r, cr, cg, cb, ca, z);
        kollegen$emitQuad(vc, mat, x1, y0 + r, x1 + b, y1 - r, cr, cg, cb, ca, z);

        kollegen$emitQuad(vc, mat, x0 - b, y0 + r, x0 + r, y0 - b, x0 + r, y0, x0, y0 + r, cr, cg, cb, ca, z);
        kollegen$emitQuad(vc, mat, x1 - r, y0 - b, x1 + b, y0 + r, x1, y0 + r, x1 - r, y0, cr, cg, cb, ca, z);
        kollegen$emitQuad(vc, mat, x1, y1 - r, x1 + b, y1 - r, x1 - r, y1 + b, x1 - r, y1, cr, cg, cb, ca, z);
        kollegen$emitQuad(vc, mat, x0 - b, y1 - r, x0, y1 - r, x0 + r, y1, x0 + r, y1 + b, cr, cg, cb, ca, z);
    }

    private static void kollegen$emitQuad(VertexConsumer vc, Matrix4f mat, float x0, float y0, float x1, float y1, int r, int g, int b, int a, float z) {
        vc.addVertex(mat, x0, y1, z).setColor(r, g, b, a);
        vc.addVertex(mat, x1, y1, z).setColor(r, g, b, a);
        vc.addVertex(mat, x1, y0, z).setColor(r, g, b, a);
        vc.addVertex(mat, x0, y0, z).setColor(r, g, b, a);

        vc.addVertex(mat, x0, y0, z).setColor(r, g, b, a);
        vc.addVertex(mat, x1, y0, z).setColor(r, g, b, a);
        vc.addVertex(mat, x1, y1, z).setColor(r, g, b, a);
        vc.addVertex(mat, x0, y1, z).setColor(r, g, b, a);
    }

    private static void kollegen$emitQuad(VertexConsumer vc, Matrix4f mat, float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4, int r, int g, int b, int a, float z) {
        vc.addVertex(mat, x1, y1, z).setColor(r, g, b, a);
        vc.addVertex(mat, x2, y2, z).setColor(r, g, b, a);
        vc.addVertex(mat, x3, y3, z).setColor(r, g, b, a);
        vc.addVertex(mat, x4, y4, z).setColor(r, g, b, a);

        vc.addVertex(mat, x4, y4, z).setColor(r, g, b, a);
        vc.addVertex(mat, x3, y3, z).setColor(r, g, b, a);
        vc.addVertex(mat, x2, y2, z).setColor(r, g, b, a);
        vc.addVertex(mat, x1, y1, z).setColor(r, g, b, a);
    }

    private static java.util.UUID kollegen$resolve(AvatarRenderState state) {
        try {
            String a = state.scoreText != null ? state.scoreText.getString() : null;
            String w = state.nameTag != null ? state.nameTag.getString() : null;
            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() == null) return null;
            if (mc.player != null) {
                String self = mc.player.getName().getString();
                if ((a != null && kollegen$nameMatches(a, self)) || (w != null && kollegen$nameMatches(w, self))) {
                    return mc.player.getUUID();
                }
            }
            for (PlayerInfo pi : mc.getConnection().getOnlinePlayers()) {
                GameProfile profile = pi.getProfile();
                if (profile == null) continue;
                String n = profile.name();
                if (n == null || n.isEmpty()) continue;
                if ((a != null && kollegen$nameMatches(a, n)) || (w != null && kollegen$nameMatches(w, n))) return profile.id();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static boolean kollegen$nameMatches(String line, String name) {
        try {
            if (line == null || name == null || name.isEmpty()) return false;
            if (line.equals(name)) return true;
            String stripped = line.replaceAll("(?i)\\u00A7[0-9A-FK-OR]", "");
            return stripped.equals(name);
        } catch (Throwable ignored) {
        }
        return false;
    }
}
