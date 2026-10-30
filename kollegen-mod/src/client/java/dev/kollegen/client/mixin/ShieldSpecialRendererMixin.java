package dev.kollegen.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kollegen.client.mods.modules.ShieldSettings;
import dev.kollegen.client.mods.modules.ShieldStatusContext;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.ShieldSpecialRenderer;
import net.minecraft.core.component.DataComponentMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ShieldSpecialRenderer.class)
public class ShieldSpecialRendererMixin {

    @ModifyArg(
            method = "submit(Lnet/minecraft/core/component/DataComponentMap;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IIZI)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;IIILnet/minecraft/client/resources/model/sprite/SpriteId;Lnet/minecraft/client/resources/model/sprite/SpriteGetter;I)V"),
            index = 5,
            require = 0
    )
    private int kollegen$tintShield(int originalTint) {
        if (ShieldSettings.isStatusOutlineEnabled()) {
            int c = ShieldStatusContext.getCurrentColor();
            if (c != 0) return c;
        }
        return originalTint;
    }

    @ModifyArg(
            method = "submit(Lnet/minecraft/core/component/DataComponentMap;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IIZI)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;IIILnet/minecraft/client/resources/model/sprite/SpriteId;Lnet/minecraft/client/resources/model/sprite/SpriteGetter;I)V"),
            index = 8,
            require = 0
    )
    private int kollegen$outlineShield(int originalOutline) {
        if (ShieldSettings.isStatusOutlineEnabled()) {
            int c = ShieldStatusContext.getCurrentColor();
            if (c != 0) return c;
        }
        return originalOutline;
    }

    @ModifyArg(
            method = "submit(Lnet/minecraft/core/component/DataComponentMap;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IIZI)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/UvMapping;I)V"),
            index = 6,
            require = 0
    )
    private int kollegen$tintShieldGlint(int originalTint) {
        if (ShieldSettings.isStatusOutlineEnabled()) {
            int c = ShieldStatusContext.getCurrentColor();
            if (c != 0) return c;
        }
        return originalTint;
    }

    @ModifyArg(
            method = "submit(Lnet/minecraft/core/component/DataComponentMap;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IIZI)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/UvMapping;I)V"),
            index = 8,
            require = 0
    )
    private int kollegen$outlineShieldGlint(int originalOutline) {
        if (ShieldSettings.isStatusOutlineEnabled()) {
            int c = ShieldStatusContext.getCurrentColor();
            if (c != 0) return c;
        }
        return originalOutline;
    }
}
