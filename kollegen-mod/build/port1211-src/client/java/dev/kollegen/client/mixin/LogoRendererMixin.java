package dev.kollegen.client.mixin;

import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.kollegen.client.ui.LogoDraw;


@Mixin(net.minecraft.client.gui.components.LogoRenderer.class)
public class LogoRendererMixin {

    private void kollegen$drawCustomLogo(GuiGraphics gui, CallbackInfo ci) {
        ci.cancel();
        int targetW = Math.min(384, gui.guiWidth() - 20);
        int drawX = (gui.guiWidth() - targetW) / 2;
        LogoDraw.draw(gui, drawX, 16, targetW);
    }

    @Inject(method = "renderLogo(Lnet/minecraft/client/gui/GuiGraphics;IF)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void kollegen$renderLogoExtract(GuiGraphics gui, int x, float alpha, CallbackInfo ci) {
        kollegen$drawCustomLogo(gui, ci);
    }

    @Inject(method = "renderLogo(Lnet/minecraft/client/gui/GuiGraphics;IFI)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void kollegen$renderLogoExtractY(GuiGraphics gui, int x, float alpha, int y, CallbackInfo ci) {
        kollegen$drawCustomLogo(gui, ci);
    }
}
