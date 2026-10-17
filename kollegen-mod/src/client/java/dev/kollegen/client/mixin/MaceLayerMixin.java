package dev.kollegen.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(PlayerItemInHandLayer.class)
public class MaceLayerMixin {
    private static boolean kollegen$loggedHideMace;
    private static boolean kollegen$loggedMaceKept;

    @Inject(method = "submitArmWithItem(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lnet/minecraft/client/renderer/item/ItemStackRenderState;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
            at = @At("HEAD"), cancellable = true)
    private void kollegen$hideVanillaMace(AvatarRenderState state, ItemStackRenderState renderState, ItemStack stack, HumanoidArm arm,
                                         PoseStack poseStack, SubmitNodeCollector collector, int packedLight, CallbackInfo ci) {
        try {
            if (stack == null || stack.getItem() != Items.MACE) return;
            UUID id = RenderStateIds.get(state);
            if (id == null) return;
            CosmeticData data = KollegenPresence.getCosmetics(id);
            if (data != null && data.maceTexture != null) {
                if (!kollegen$loggedHideMace) {
                    kollegen$loggedHideMace = true;
                    System.out.println("KOLLEGENDBG hide-vanilla-mace " + id);
                }
                ci.cancel();
            } else if (!kollegen$loggedMaceKept) {
                kollegen$loggedMaceKept = true;
                System.out.println("KOLLEGENDBG vanilla-mace-kept nodata=" + (data == null));
            }
        } catch (Throwable ignored) {
        }
    }
}