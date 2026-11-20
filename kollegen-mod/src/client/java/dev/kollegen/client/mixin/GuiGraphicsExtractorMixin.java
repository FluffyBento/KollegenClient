package dev.kollegen.client.mixin;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.kollegen.client.ui.GuiCustomizationRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsExtractorMixin {

    @Inject(method = "blit(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIII)V",
            at = @At("HEAD"), cancellable = true)
    private void kollegen$onBlitContainer(RenderPipeline pipeline, Identifier texture, int x, int y, float u, float v, int width, int height, int texW, int texH, CallbackInfo ci) {
        if (GuiCustomizationRenderer.shouldCancelContainerBlit(texture)) {
            ci.cancel();
        }
    }

    @Inject(method = "blit(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIII)V",
            at = @At("HEAD"), cancellable = true)
    private void kollegen$onBlitContainerFull(RenderPipeline pipeline, Identifier texture, int x, int y, float u, float v, int width, int height, int pWidth, int pHeight, int texW, int texH, CallbackInfo ci) {
        if (GuiCustomizationRenderer.shouldCancelContainerBlit(texture)) {
            ci.cancel();
        }
    }
}
