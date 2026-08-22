package dev.kollegen.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.InputStream;

/**
 * Ersetzt das vanilla Minecraft-Logo im Hauptmenue durch das eigene
 * assets/kollegen/logo.png (Titelbildschirm-Overlay). Die Textur wird
 * seitenveraeltniskonform auf die gewuenschte Anzeigebreite skaliert.
 */
@Mixin(net.minecraft.client.gui.components.LogoRenderer.class)
public class LogoRendererMixin {

    private static final Identifier KOLLEGEN_LOGO = Identifier.tryParse("kollegen:logo.png");

    @Inject(method = "renderLogo(Lnet/minecraft/client/gui/GuiGraphics;IFI)V", at = @At("HEAD"), cancellable = true)
    private void kollegen$renderLogo(GuiGraphics gui, int x, float alpha, int y, CallbackInfo ci) {
        ci.cancel();
        int[] dim = logoDims();
        int targetW = Math.min(256, gui.guiWidth() - 20);
        float scale = (float) targetW / dim[0];
        int drawX = (gui.guiWidth() - targetW) / 2;

        var pose = gui.pose();
        pose.pushMatrix();
        pose.translate(drawX, 16.0F);
        pose.scale(scale, scale);
        gui.blit(RenderPipelines.GUI_TEXTURED, KOLLEGEN_LOGO, 0, 0, 0.0F, 0.0F, dim[0], dim[1], dim[0], dim[1]);
        pose.popMatrix();
    }

    private static int[] logoDims() {
        try {
            var res = Minecraft.getInstance().getResourceManager().getResource(KOLLEGEN_LOGO);
            if (res.isPresent()) {
                try (InputStream in = res.get().open()) {
                    byte[] hdr = new byte[24];
                    int r = 0, n;
                    while (r < 24 && (n = in.read(hdr, r, 24 - r)) > 0) r += n;
                    if (r == 24) {
                        int w = ((hdr[16] & 0xff) << 24) | ((hdr[17] & 0xff) << 16) | ((hdr[18] & 0xff) << 8) | (hdr[19] & 0xff);
                        int h = ((hdr[20] & 0xff) << 24) | ((hdr[21] & 0xff) << 16) | ((hdr[22] & 0xff) << 8) | (hdr[23] & 0xff);
                        if (w > 0 && h > 0) return new int[]{w, h};
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return new int[]{1200, 400};
    }
}
