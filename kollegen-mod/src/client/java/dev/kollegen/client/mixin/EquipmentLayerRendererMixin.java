package dev.kollegen.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kollegen.client.mods.modules.ArmorTransparency;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAsset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EquipmentLayerRenderer.class)
public class EquipmentLayerRendererMixin {

    @Unique
    private static final ThreadLocal<Float> kollegen$curOpacity = new ThreadLocal<>();

    @Inject(
        method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;II)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private <S> void kollegen$onRenderLayers9Head(
        EquipmentClientInfo.LayerType layerType,
        ResourceKey<EquipmentAsset> assetKey,
        Model<? super S> model,
        S state,
        ItemStack stack,
        PoseStack poseStack,
        SubmitNodeCollector submitNodeCollector,
        int packedLight,
        int outlineColor,
        CallbackInfo ci
    ) {
        float opacity = ArmorTransparency.getOpacity(state, stack);
        if (opacity <= 0.05f) {
            ci.cancel();
        }
    }

    @Inject(
        method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private <S> void kollegen$onRenderLayersHead(
        EquipmentClientInfo.LayerType layerType,
        ResourceKey<EquipmentAsset> assetKey,
        Model<? super S> model,
        S state,
        ItemStack stack,
        PoseStack poseStack,
        SubmitNodeCollector submitNodeCollector,
        int packedLight,
        Identifier playerTexture,
        int outlineColor,
        int order,
        CallbackInfo ci
    ) {
        float opacity = ArmorTransparency.getOpacity(state, stack);
        if (opacity <= 0.05f) {
            ci.cancel();
            return;
        }
        kollegen$curOpacity.set(opacity);
    }

    @Inject(
        method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
        at = @At("RETURN")
    )
    private void kollegen$onRenderLayersReturn(CallbackInfo ci) {
        kollegen$curOpacity.remove();
    }

    @Redirect(
        method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/rendertype/RenderTypes;armorCutoutNoCull(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;")
    )
    private RenderType kollegen$redirectArmorCutout(Identifier id) {
        Float op = kollegen$curOpacity.get();
        if (op != null && op < 0.98f) {
            return RenderTypes.entityTranslucent(id);
        }
        return RenderTypes.armorCutoutNoCull(id);
    }

    @Redirect(
        method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/rendertype/RenderTypes;armorCutoutNoCullGlint(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;")
    )
    private RenderType kollegen$redirectArmorGlint(Identifier id) {
        Float op = kollegen$curOpacity.get();
        if (op != null && op < 0.98f) {
            return RenderTypes.entityTranslucent(id);
        }
        return RenderTypes.armorCutoutNoCullGlint(id);
    }

    @Redirect(
        method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/rendertype/RenderTypes;armorTrim(Lnet/minecraft/resources/Identifier;Z)Lnet/minecraft/client/renderer/rendertype/RenderType;")
    )
    private RenderType kollegen$redirectArmorTrim(Identifier id, boolean decal) {
        Float op = kollegen$curOpacity.get();
        if (op != null && op < 0.98f) {
            return RenderTypes.entityTranslucent(id);
        }
        return RenderTypes.armorTrim(id, decal);
    }

    @ModifyArg(
        method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/OrderedSubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/UvMapping;I)V"),
        index = 6
    )
    private int kollegen$applyArmorAlpha(int color) {
        Float op = kollegen$curOpacity.get();
        if (op != null && op < 0.98f) {
            int a = Math.max(1, Math.min(255, (int) Math.round(op * 255.0)));
            int rgb = color & 0x00FFFFFF;
            if (rgb == 0 && color == 0) {
                rgb = 0x00FFFFFF;
            }
            return (a << 24) | rgb;
        }
        return color;
    }
}
