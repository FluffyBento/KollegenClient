package dev.kollegen.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.kollegen.client.mods.modules.ShieldSettings;
import dev.kollegen.client.mods.modules.ShieldStatusContext;
import dev.kollegen.client.mods.modules.Visual;
import dev.kollegen.client.presence.CosmeticData;
import dev.kollegen.client.presence.CosmeticRenderLayer;
import dev.kollegen.client.presence.KollegenPresence;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public class FirstPersonHandsAndItemsRendererMixin {

    @Unique
    private static boolean kollegen$isShield = false;
    @Unique
    private static boolean kollegen$isMace = false;
    @Unique
    private static InteractionHand kollegen$maceHand = null;

    @Inject(method = "submitArmWithItem", at = @At("HEAD"), require = 0)
    private void kollegen$onArmWithItemHead(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState handsState,
                                            float equippedProgress, float swingProgress, InteractionHand hand, float pt,
                                            ItemStack stack, float height, PoseStack poseStack,
                                            SubmitNodeCollector submitNodeCollector, int light, CallbackInfo ci) {
        if (stack != null && stack.is(Items.SHIELD)) {
            kollegen$isShield = true;
            ShieldStatusContext.setFirstPerson(true);
            if (Visual.LowShield.isLowShieldEnabled()) {
                poseStack.translate(0.0f, (float) Visual.LowShield.getHeight(), 0.0f);
                float sc = (float) Visual.LowShield.getScale();
                if (sc != 1.0f) {
                    poseStack.scale(sc, sc, sc);
                }
            } else if (ShieldSettings.isCustomTransformEnabled()) {
                ShieldSettings.applyFirstPersonTransform(poseStack, hand);
            }
        } else {
            kollegen$isShield = false;
            ShieldStatusContext.setFirstPerson(false);
        }

        if (stack != null && stack.is(Items.MACE)) {
            UUID self = KollegenPresence.getSelfId();
            CosmeticData data = self != null ? KollegenPresence.getCosmetics(self) : null;
            if (data != null && data.maceTexture != null) {
                kollegen$isMace = true;
                kollegen$maceHand = hand;
            } else {
                kollegen$isMace = false;
                kollegen$maceHand = null;
            }
        } else {
            kollegen$isMace = false;
            kollegen$maceHand = null;
        }
    }

    @Inject(method = "submitArmWithItem", at = @At("RETURN"), require = 0)
    private void kollegen$onArmWithItemReturn(CallbackInfo ci) {
        kollegen$isShield = false;
        kollegen$isMace = false;
        kollegen$maceHand = null;
        ShieldStatusContext.clear();
    }

    @WrapOperation(method = "submitArmWithItem",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"),
            require = 0)
    private void kollegen$wrapSubmit(ItemStackRenderState instance, PoseStack poseStack, SubmitNodeCollector collector,
                                     int light, int overlay, int outline, Operation<Void> original) {
        if (kollegen$isMace) {
            if (CosmeticRenderLayer.renderFirstPersonMace(poseStack, collector, kollegen$maceHand, light)) {
                return;
            }
        }
        int effectiveOutline = outline;
        if (kollegen$isShield && ShieldSettings.isStatusOutlineEnabled()) {
            int c = ShieldSettings.getSelfStatusColor();
            if (c != 0) effectiveOutline = c;
        }
        original.call(instance, poseStack, collector, light, overlay, effectiveOutline);
    }
}
