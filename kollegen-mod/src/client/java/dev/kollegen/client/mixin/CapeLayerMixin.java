package dev.kollegen.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kollegen.client.mods.modules.Visual;
import dev.kollegen.client.presence.CosmeticData;
import dev.kollegen.client.presence.KollegenPresence;
import dev.kollegen.client.presence.RenderStateIds;
import dev.kollegen.client.render.WaveyCapeRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(CapeLayer.class)
public class CapeLayerMixin {
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
                capeTexture = Identifier.tryParse(data.capeTexture.toLowerCase(java.util.Locale.ROOT));
            } else if (state.skin != null && state.skin.cape() != null) {
                capeTexture = state.skin.cape().texturePath();
            }

            if (capeTexture == null) return;

            if (Visual.WaveyCapes.enabled) {
                WaveyCapeRenderer.renderWaveyCape(poseStack, collector, capeTexture, packedLight, state, hasArmor);
                ci.cancel();
            } else if (data != null && data.capeTexture != null) {
                // If Wavey Capes is disabled but custom/community cape is equipped, cancel vanilla cape submit
                ci.cancel();
            }
        } catch (Throwable ignored) {
        }
    }
}
