package dev.kollegen.client.mixin;

import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.kollegen.client.mods.modules.KollegenShader;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.client.renderer.oit.OitRenderPassProvider;
import net.minecraft.client.renderer.oit.OitStage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CloudRenderer.class)
public class CloudRendererMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void kollegen$skipVanillaClouds(CloudStatus status, RenderPass pass, CallbackInfo ci) {
        if (KollegenShader.shouldHideVanillaClouds()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderOit", at = @At("HEAD"), cancellable = true)
    private void kollegen$skipVanillaCloudsOit(
            CloudStatus status,
            OitStage stage,
            GpuTextureView target,
            OitRenderPassProvider.Parameters params,
            CallbackInfo ci
    ) {
        if (KollegenShader.shouldHideVanillaClouds()) {
            ci.cancel();
        }
    }
}