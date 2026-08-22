package dev.kollegen.client.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ersetzt das vanilla Minecraft-Logo im Hauptmenue durch das eigene
 * assets/kollegen/logo.png (Titelbildschirm-Overlay).
 */
@Mixin(net.minecraft.client.gui.components.LogoRenderer.class)
public class LogoRendererMixin {

    private static final Identifier KOLLEGEN_LOGO = Identifier.tryParse("kollegen:logo.png");
    private static final int LOGO_W = 256;
    private static final int LOGO_H = 64;

    @Inject(method = "renderLogo(Lnet/minecraft/client/gui/GuiGraphics;IFI)V", at = @At("HEAD"), cancellable = true)
    private void kollegen$renderLogo(GuiGraphics gui, int x, float alpha, int y, CallbackInfo ci) {
        ci.cancel();
        int drawX = (gui.guiWidth() - LOGO_W) / 2;
        gui.blit(RenderPipelines.GUI_TEXTURED, KOLLEGEN_LOGO, drawX, 16, 0.0F, 0.0F, LOGO_W, LOGO_H, LOGO_W, LOGO_H);
    }
}
