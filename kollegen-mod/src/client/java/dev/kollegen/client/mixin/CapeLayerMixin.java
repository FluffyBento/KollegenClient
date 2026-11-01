package dev.kollegen.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kollegen.client.menu.SkinCapeTextureManager;
import dev.kollegen.client.mods.modules.Visual;
import dev.kollegen.client.presence.CosmeticData;
import dev.kollegen.client.presence.KollegenPresence;
import dev.kollegen.client.presence.RenderStateIds;
import dev.kollegen.client.render.WaveyCapeRenderer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(CapeLayer.class)
public abstract class CapeLayerMixin {
    @Shadow
    private HumanoidModel<AvatarRenderState> model;

    @Shadow
    protected abstract boolean hasLayer(ItemStack stack, EquipmentClientInfo.LayerType type);

    @Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/AvatarRenderState;FF)V",
            at = @At("HEAD"), cancellable = true)
    private void kollegen$submitCape(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                                     AvatarRenderState state, float limbSwing, float limbSwingAmount, CallbackInfo ci) {
        try {
            if (state == null || state.isInvisible || !state.showCape) return;

            ItemStack chest = state.chestEquipment;
            boolean elytra = chest != null && chest.getItem() == Items.ELYTRA;
            if (elytra) return;
            boolean hasArmor = chest != null && !chest.isEmpty() && chest.getItem() != Items.AIR;

            UUID id = RenderStateIds.get(state);
            CosmeticData data = id != null ? KollegenPresence.getCosmetics(id) : null;
            Identifier capeTexture = null;

            if (data != null && data.capeTexture != null) {
                // If it has a 3D BbModel (e.g. store creator capes), CosmeticRenderLayer renders it
                if (data.capeModel != null && !data.capeModel.isEmpty()) {
                    ci.cancel();
                    return;
                }
                capeTexture = SkinCapeTextureManager.getOrLoadCape(data.capeTexture, data.capeTexture);
                if (capeTexture == null) {
                    capeTexture = Identifier.tryParse(data.capeTexture.toLowerCase(java.util.Locale.ROOT));
                }
            } else if (state.skin != null && state.skin.cape() != null) {
                capeTexture = state.skin.cape().texturePath();
            }

            if (capeTexture == null) return;

            if (Visual.WaveyCapes.enabled) {
                WaveyCapeRenderer.renderWaveyCape(poseStack, collector, capeTexture, packedLight, state, hasArmor);
                ci.cancel();
            } else if (data != null && data.capeTexture != null) {
                // Render flat cape with capeTexture using humanoid cape model
                poseStack.pushPose();
                if (this.hasLayer(state.chestEquipment, EquipmentClientInfo.LayerType.HUMANOID)) {
                    poseStack.translate(0.0F, -0.053125F, 0.06875F);
                }
                collector.submitModel(this.model, state, poseStack,
                        RenderTypes.entitySolid(capeTexture),
                        packedLight, OverlayTexture.NO_OVERLAY, state.outlineColor);
                poseStack.popPose();
                ci.cancel();
            }
        } catch (Throwable ignored) {
        }
    }
}
