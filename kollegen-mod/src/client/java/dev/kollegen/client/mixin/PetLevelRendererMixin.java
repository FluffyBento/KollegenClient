package dev.kollegen.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kollegen.client.presence.PetRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class PetLevelRendererMixin {

    @Inject(method = "renderLevel(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;FZ)V", at = @At("TAIL"))
    private void kollegen$renderPets(PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, float partialTick, boolean renderBlockOutline, CallbackInfo ci) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.level == null) return;
            PetRenderer.renderPets(mc, poseStack, collector, camera);
        } catch (Throwable ignored) {
        }
    }
}