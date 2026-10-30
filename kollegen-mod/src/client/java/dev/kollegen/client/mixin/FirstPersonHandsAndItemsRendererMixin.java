package dev.kollegen.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kollegen.client.mods.modules.ShieldSettings;
import dev.kollegen.client.mods.modules.ShieldStatusContext;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public class FirstPersonHandsAndItemsRendererMixin {

    @Unique
    private static boolean kollegen$isShield = false;

    @Inject(method = "submitArmWithItem", at = @At("HEAD"), require = 0)
    private void kollegen$onArmWithItemHead(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState handsState,
                                            float equippedProgress, float swingProgress, InteractionHand hand, float pt,
                                            ItemStack stack, float height, PoseStack poseStack,
                                            SubmitNodeCollector submitNodeCollector, int light, CallbackInfo ci) {
        if (stack != null && stack.is(Items.SHIELD)) {
            kollegen$isShield = true;
            ShieldStatusContext.setFirstPerson(true);
            if (ShieldSettings.isCustomTransformEnabled()) {
                ShieldSettings.applyFirstPersonTransform(poseStack, hand);
            }
        } else {
            kollegen$isShield = false;
            ShieldStatusContext.setFirstPerson(false);
        }
    }

    @Inject(method = "submitArmWithItem", at = @At("RETURN"), require = 0)
    private void kollegen$onArmWithItemReturn(CallbackInfo ci) {
        kollegen$isShield = false;
        ShieldStatusContext.clear();
    }

    @ModifyArg(method = "submitArmWithItem",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"),
            index = 4, require = 0)
    private int kollegen$shieldOutlineFirstPerson(int originalOutline) {
        if (kollegen$isShield && ShieldSettings.isStatusOutlineEnabled()) {
            int c = ShieldSettings.getSelfStatusColor();
            if (c != 0) return c;
        }
        return originalOutline;
    }
}
