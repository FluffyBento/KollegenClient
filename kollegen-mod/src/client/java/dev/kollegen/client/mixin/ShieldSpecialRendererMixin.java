package dev.kollegen.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kollegen.client.mods.modules.ShieldSettings;
import dev.kollegen.client.mods.modules.ShieldStatusContext;
import dev.kollegen.client.presence.BbModel;
import dev.kollegen.client.presence.CosmeticData;
import dev.kollegen.client.presence.CosmeticRenderLayer;
import dev.kollegen.client.presence.KollegenPresence;
import dev.kollegen.client.render.ShieldOutlineRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.special.ShieldSpecialRenderer;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(ShieldSpecialRenderer.class)
public class ShieldSpecialRendererMixin {

    @Inject(
            method = "submit(Lnet/minecraft/core/component/DataComponentMap;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IIZI)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void kollegen$renderCustomShield(DataComponentMap components, PoseStack poseStack, SubmitNodeCollector collector,
                                              int light, int overlay, boolean glint, int outlineColor, CallbackInfo ci) {
        try {
            UUID id = ShieldStatusContext.getCurrentPlayerUUID();
            if (id == null) return;
            CosmeticData data = KollegenPresence.getCosmetics(id);
            if (data == null || data.shieldTexture == null) return;

            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return;

            Identifier tex = CosmeticRenderLayer.getId(data.shieldTexture);
            if (tex == null) return;

            BbModel bb = null;
            if (data.shieldModel != null) {
                Identifier modelId = CosmeticRenderLayer.getId(data.shieldModel);
                if (modelId != null) {
                    bb = BbModel.load(mc.getResourceManager(), modelId);
                }
            }
            if (bb == null) return;

            int tintColor = -1;
            if (ShieldSettings.isStatusTintEnabled()) {
                int sc = ShieldStatusContext.getCurrentColor();
                if (sc != 0) tintColor = sc;
            }

            RenderType type = CosmeticRenderLayer.getCustomRenderType(tex);
            poseStack.pushPose();
            poseStack.scale(1.0F, -1.0F, -1.0F);
            poseStack.translate(-bb.cx, -bb.cy, -bb.cz);

            double timeSec = (System.currentTimeMillis() % 1000000L) / 1000.0;
            CosmeticRenderLayer.renderModelDirect(poseStack, collector, type, bb, light, timeSec, tintColor);

            if (ShieldSettings.isStatusOutlineEnabled()) {
                int sc = ShieldStatusContext.getCurrentColor();
                if (sc != 0) {
                    ShieldOutlineRenderer.renderOutline(poseStack, collector,
                            bb.minX - bb.cx, bb.minY - bb.cy, bb.minZ - bb.cz,
                            bb.maxX - bb.cx, bb.maxY - bb.cy, bb.maxZ - bb.cz, sc);
                }
            }

            poseStack.popPose();
            ci.cancel();
        } catch (Throwable ignored) {
        }
    }

    @Inject(
            method = "submit(Lnet/minecraft/core/component/DataComponentMap;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IIZI)V",
            at = @At("TAIL"),
            require = 0
    )
    private void kollegen$renderVanillaShieldOutline(DataComponentMap components, PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, boolean hasGlint, int outlineColor, CallbackInfo ci) {
        if (!ShieldSettings.isStatusOutlineEnabled()) return;
        int sc = ShieldStatusContext.getCurrentColor();
        if (sc == 0) return;

        poseStack.pushPose();
        float minX = -6.0F / 16.0F;
        float maxX = 6.0F / 16.0F;
        float minY = -11.0F / 16.0F;
        float maxY = 11.0F / 16.0F;
        float minZ = -2.0F / 16.0F;
        float maxZ = -1.0F / 16.0F;

        ShieldOutlineRenderer.renderOutline(poseStack, collector, minX, minY, minZ, maxX, maxY, maxZ, sc);
        poseStack.popPose();
    }

    @ModifyArg(
            method = "submit(Lnet/minecraft/core/component/DataComponentMap;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IIZI)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;IIILnet/minecraft/client/resources/model/sprite/SpriteId;Lnet/minecraft/client/resources/model/sprite/SpriteGetter;I)V"),
            index = 5,
            require = 0
    )
    private int kollegen$tintShield(int originalTint) {
        if (ShieldSettings.isStatusTintEnabled()) {
            int c = ShieldStatusContext.getCurrentColor();
            if (c != 0) return c;
        }
        return originalTint;
    }

    @ModifyArg(
            method = "submit(Lnet/minecraft/core/component/DataComponentMap;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IIZI)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/UvMapping;I)V"),
            index = 6,
            require = 0
    )
    private int kollegen$tintShieldGlint(int originalTint) {
        if (ShieldSettings.isStatusTintEnabled()) {
            int c = ShieldStatusContext.getCurrentColor();
            if (c != 0) return c;
        }
        return originalTint;
    }
}
