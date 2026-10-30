package dev.kollegen.client.mixin;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.kollegen.client.presence.CosmeticData;
import dev.kollegen.client.presence.CosmeticText;
import dev.kollegen.client.presence.KollegenPresence;
import dev.kollegen.client.presence.PetRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(AvatarRenderer.class)
public class NametagMixin {

    private static final Identifier LOGO = Identifier.fromNamespaceAndPath("kollegen", "textures/gui/logo_mark.png");
    private static final net.minecraft.client.renderer.rendertype.RenderType LOGO_TYPE = RenderTypes.textSeeThrough(LOGO);

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("RETURN"))
    private void kollegen$track(net.minecraft.world.entity.Avatar entity, AvatarRenderState state, float f, CallbackInfo ci) {
        try {
            if (entity instanceof net.minecraft.world.entity.player.Player p) {
                state.id = p.getId();
                Minecraft mc = Minecraft.getInstance();
                boolean isSelf = mc.player != null && (p.getUUID().equals(mc.player.getUUID()) || p.getId() == mc.player.getId());
                if (dev.kollegen.client.mods.modules.Player.shouldHideNametag(isSelf)) {
                    state.nameTag = null;
                    state.scoreText = null;
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
            String capeStr = data.capeTexture.toLowerCase(java.util.Locale.ROOT);
            net.minecraft.resources.Identifier tex = net.minecraft.resources.Identifier.tryParse(capeStr);
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
            if (state.isDiscrete) {
                kollegen$dbg("skip-discrete");
                return;
            }
            java.util.UUID id = dev.kollegen.client.presence.RenderStateIds.get(state);
            if (id == null) id = kollegen$resolve(state);

            Minecraft mc = Minecraft.getInstance();
            boolean isSelf = mc.player != null && ((id != null && id.equals(mc.player.getUUID())) || (state.id != 0 && state.id == mc.player.getId()));

            if (dev.kollegen.client.mods.modules.Player.shouldHideNametag(isSelf)) {
                ci.cancel();
                return;
            }

            if (id == null) {
                kollegen$dbg("skip-no-id");
                return;
            }
            CosmeticData d = KollegenPresence.getCosmetics(id);
            boolean hasData = d != null && !d.isEmpty();
            if (!KollegenPresence.isKollegen(id) && !hasData) {
                kollegen$dbg("skip-nothing");
                return;
            }
            boolean useW = state.scoreText == null && state.nameTag != null;
            Component line = useW ? state.nameTag : state.scoreText;
            if (line == null) {
                kollegen$dbg("skip-no-line");
                return;
            }
            KollegenPresence.dbgRender = "render";
            if (hasData) {
                String probe = null;
                try {
                    probe = line.getString();
                } catch (Throwable ignored) {
                }
                if (probe == null || probe.startsWith(CosmeticText.LOGO_CHAR)) return;
                Component decorated = CosmeticText.decorateNameLine(line, id);
                MutableComponent withLogo = Component.empty();
                withLogo.append(CosmeticText.logoComponent());
                withLogo.append(Component.literal(" "));
                withLogo.append(decorated);
                if (useW) state.nameTag = withLogo;
                else state.scoreText = withLogo;
            }
            Vec3 anchor = state.nameTagAttachment;
            if (anchor == null) return;
            double nameY = anchor.y + (useW ? 0.259 : 0.0);
            Component title = (hasData && !dev.kollegen.client.mods.modules.Player.shouldHideTitle(isSelf)) ? CosmeticText.titleComponent(id) : null;
            if (title != null) {
                collector.submitNameTag(poseStack,
                        new Vec3(anchor.x, anchor.y + 0.50, anchor.z),
                        0, title, !state.isDiscrete, state.lightCoords, camera);
            }
            Component level = (hasData && !dev.kollegen.client.mods.modules.Player.shouldHideLevel(isSelf)) ? CosmeticText.levelComponent(id) : null;
            if (level != null) {
                collector.submitNameTag(poseStack,
                        new Vec3(anchor.x, anchor.y - 0.26, anchor.z),
                        0, level, !state.isDiscrete, state.lightCoords, camera);
            }
            if (false && KollegenPresence.isKollegen(id)) {
                Component nameLine = useW ? state.nameTag : state.scoreText;
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

    private static void kollegen$dbg(String s) {
        try {
            String cur = String.valueOf(KollegenPresence.dbgRender);
            if (!"render".equals(cur) && cur.indexOf('|') < 0) KollegenPresence.dbgRender = s;
        } catch (Throwable ignored) {
        }
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
                return kollegen$nameMatches(plain, profile.name());
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
            collector.submitCustomGeometry(poseStack, LOGO_TYPE, (pose, vc) -> {
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
