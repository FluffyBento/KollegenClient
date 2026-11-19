package dev.kollegen.client.mixin;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.kollegen.client.menu.SkinCapeTextureManager;
import dev.kollegen.client.presence.CosmeticData;
import dev.kollegen.client.presence.CosmeticText;
import dev.kollegen.client.presence.KollegenPresence;
import dev.kollegen.client.presence.PetRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
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
            boolean hasTier = id != null && dev.kollegen.client.mods.modules.PvPTiers.isTiersActive() && dev.kollegen.client.mods.modules.PvPTiers.getPlayerTierBadge(id) != null;
            boolean isKollegen = isSelf || (id != null && KollegenPresence.isKollegen(id)) || hasData;

            if (!isKollegen && !hasTier) {
                return;
            }

            boolean useW = state.scoreText == null;
            Component line = useW ? state.nameTag : state.scoreText;
            if (line == null && isSelf && mc.player != null) {
                line = mc.player.getDisplayName();
            }
            if (line == null) {
                return;
            }

            if (hasData || isSelf) {
                Component decorated = CosmeticText.decorateNameLine(line, id);
                if (useW) state.nameTag = decorated;
                else state.scoreText = decorated;
            }

            if (hasTier) {
                Component cur = useW ? state.nameTag : state.scoreText;
                Component decorated = dev.kollegen.client.mods.modules.PvPTiers.decorateNametag(cur, id);
                if (useW) state.nameTag = decorated;
                else state.scoreText = decorated;
            }

            Vec3 anchor = state.nameTagAttachment;
            if (anchor == null) {
                if (isSelf && mc.player != null) {
                    anchor = new Vec3(0.0, mc.player.getBbHeight() + 0.5, 0.0);
                    state.nameTagAttachment = anchor;
                } else {
                    return;
                }
            }

            Component title = (hasData && !dev.kollegen.client.mods.modules.Player.shouldHideTitle(isSelf)) ? CosmeticText.titleComponent(id) : null;
            if (title != null) {
                double titleY = anchor.y + (useW ? 0.26 : 0.52);
                collector.submitNameTag(poseStack,
                        new Vec3(anchor.x, titleY, anchor.z),
                        0, title, !state.isDiscrete, state.lightCoords, camera);
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
