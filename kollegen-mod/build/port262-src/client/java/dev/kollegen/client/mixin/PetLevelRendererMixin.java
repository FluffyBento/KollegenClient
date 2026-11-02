package dev.kollegen.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kollegen.client.presence.PetRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class PetLevelRendererMixin {

    @Inject(method = "submitEntities(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/state/level/LevelRenderState;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V", at = @At("TAIL"))
    private void kollegen$submitPets(PoseStack poseStack, LevelRenderState state, SubmitNodeCollector collector, CallbackInfo ci) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.level == null) return;
            PetRenderer.submitDragons(mc, poseStack, state, collector);
        } catch (Throwable ignored) {
        }
    }
}
