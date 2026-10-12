package dev.kollegen.client.presence;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CosmeticRenderer {
    private static final Map<Identifier, ModelPart> CACHED_MODELS = new ConcurrentHashMap<>();
    private static final Identifier CAPE_TEX = Identifier.fromNamespaceAndPath("minecraft", "textures/entity/player/cape.png");
    
    private CosmeticRenderer() {}

    public static void renderCapes(PoseStack poseStack, MultiBufferSource bufferSource, Player player, CosmeticData data, int packedLight) {
        if (data == null || data.capeTexture == null) return;
        Identifier tex = Identifier.tryParse(data.capeTexture);
        if (tex == null) return;
        
        Minecraft mc = Minecraft.getInstance();
        HumanoidModel<Player> model = mc.getEntityRenderDispatcher().getRenderer(player).getModel();
        if (model == null) return;
        
        poseStack.pushPose();
        poseStack.translate(0, 0, 0.125);
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityTranslucent(tex));
        model.cape.render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    public static void renderBackAccessory(PoseStack poseStack, MultiBufferSource bufferSource, Player player, CosmeticData data, int packedLight) {
        if (data == null || data.backAccessoryTexture == null) return;
        Identifier tex = Identifier.tryParse(data.backAccessoryTexture);
        if (tex == null) return;
        
        Minecraft mc = Minecraft.getInstance();
        HumanoidModel<Player> model = mc.getEntityRenderDispatcher().getRenderer(player).getModel();
        if (model == null) return;
        
        poseStack.pushPose();
        poseStack.translate(0, 0, 0.125);
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityTranslucent(tex));
        model.body.render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    public static void renderHeadAccessory(PoseStack poseStack, MultiBufferSource bufferSource, Player player, CosmeticData data, int packedLight) {
        if (data == null || data.headAccessoryTexture == null) return;
        Identifier tex = Identifier.tryParse(data.headAccessoryTexture);
        if (tex == null) return;
        
        Minecraft mc = Minecraft.getInstance();
        HumanoidModel<Player> model = mc.getEntityRenderDispatcher().getRenderer(player).getModel();
        if (model == null) return;
        
        poseStack.pushPose();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityTranslucent(tex));
        model.head.render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    public static void renderShield(PoseStack poseStack, MultiBufferSource bufferSource, Player player, CosmeticData data, int packedLight) {
        if (data == null || data.shieldTexture == null) return;
        ItemStack shieldStack = player.getItemBySlot(EquipmentSlot.OFFHAND);
        if (shieldStack.isEmpty()) return;
        
        Identifier tex = Identifier.tryParse(data.shieldTexture);
        if (tex == null) return;
        
        Minecraft mc = Minecraft.getInstance();
        HumanoidModel<Player> model = mc.getEntityRenderDispatcher().getRenderer(player).getModel();
        if (model == null) return;
        
        poseStack.pushPose();
        poseStack.translate(0, 0.25, 0.25);
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityTranslucent(tex));
        model.rightArm.render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }
}