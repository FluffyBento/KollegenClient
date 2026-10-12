package dev.kollegen.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kollegen.client.presence.CosmeticData;
import dev.kollegen.client.presence.CosmeticRenderer;
import dev.kollegen.client.presence.KollegenPresence;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public class PlayerRendererMixin {

    @Inject(method = "render(Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("RETURN"))
    private void kollegen$renderCosmetics(PlayerRenderState state, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, CallbackInfo ci) {
        try {
            UUID id = state.profile != null ? state.profile.id() : null;
            if (id == null) return;
            
            CosmeticData data = KollegenPresence.getCosmetics(id);
            if (data == null || data.isEmpty()) return;
            
            Minecraft mc = Minecraft.getInstance();
            Player player = mc.level != null ? mc.level.getPlayerByUUID(id) : null;
            if (player == null) return;
            
            CosmeticRenderer.renderCapes(poseStack, bufferSource, player, data, packedLight);
            CosmeticRenderer.renderBackAccessory(poseStack, bufferSource, player, data, packedLight);
            CosmeticRenderer.renderHeadAccessory(poseStack, bufferSource, player, data, packedLight);
            CosmeticRenderer.renderShield(poseStack, bufferSource, player, data, packedLight);
        } catch (Throwable ignored) {
        }
    }
}