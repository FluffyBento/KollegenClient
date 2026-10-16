package dev.kollegen.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kollegen.client.presence.CosmeticData;
import dev.kollegen.client.presence.KollegenPresence;
import dev.kollegen.client.presence.RenderStateIds;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(CapeLayer.class)
public class CapeLayerMixin {
    @Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/AvatarRenderState;FF)V",
            at = @At("HEAD"), cancellable = true)
    private void kollegen$hideVanillaCape(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                                          AvatarRenderState state, float limbSwing, float limbSwingAmount, CallbackInfo ci) {
        try {
            UUID id = RenderStateIds.get(state);
            if (id == null) return;
            CosmeticData data = KollegenPresence.getCosmetics(id);
            if (data != null && data.capeTexture != null) ci.cancel();
        } catch (Throwable ignored) {
        }
    }
}
