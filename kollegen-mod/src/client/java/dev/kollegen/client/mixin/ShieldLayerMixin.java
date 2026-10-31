package dev.kollegen.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kollegen.client.mods.modules.ShieldSettings;
import dev.kollegen.client.mods.modules.ShieldStatusContext;
import dev.kollegen.client.presence.CosmeticData;
import dev.kollegen.client.presence.KollegenPresence;
import dev.kollegen.client.presence.RenderStateIds;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.PlayerItemInHandLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(PlayerItemInHandLayer.class)
public class ShieldLayerMixin {

    @Unique
    private static final ThreadLocal<AvatarRenderState> kollegen$curState = new ThreadLocal<>();
    @Unique
    private static final ThreadLocal<Boolean> kollegen$curIsShield = new ThreadLocal<>();

    @Inject(method = "submitArmWithItem(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lnet/minecraft/client/renderer/item/ItemStackRenderState;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void kollegen$hideVanillaShield(AvatarRenderState state, ItemStackRenderState renderState, ItemStack stack, HumanoidArm arm,
                                            PoseStack poseStack, SubmitNodeCollector collector, int packedLight, CallbackInfo ci) {
        kollegen$curState.set(state);
        boolean isSh = stack != null && stack.getItem() == Items.SHIELD;
        kollegen$curIsShield.set(isSh);
        if (isSh) {
            ShieldStatusContext.setThirdPerson(state);
        }
    }

    @Inject(method = "submitArmWithItem(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lnet/minecraft/client/renderer/item/ItemStackRenderState;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
            at = @At("RETURN"), require = 0)
    private void kollegen$cleanState(CallbackInfo ci) {
        kollegen$curState.remove();
        kollegen$curIsShield.remove();
        ShieldStatusContext.clear();
    }
}
