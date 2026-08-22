package dev.kollegen.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.io.InputStream;

/**
 * Zentraler Helfer zum Zeichnen des eigenen Logos (assets/kollegen/logo.png),
 * seitenveraeltniskonform skaliert. Wird vom Titel-Screen (LogoRendererMixin)
 * und vom Inventar (InventoryScreenMixin) genutzt.
 */
public final class LogoDraw {
    private static final Identifier KOLLEGEN_LOGO = Identifier.tryParse("kollegen:logo.png");
    private static int[] DIMS;

    public static int[] dims() {
        if (DIMS != null) return DIMS;
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
                        if (w > 0 && h > 0) {
                            DIMS = new int[]{w, h};
                            return DIMS;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        DIMS = new int[]{1200, 400};
        return DIMS;
    }

    public static void draw(GuiGraphics gui, int x, int y, int targetW) {
        int[] dim = dims();
        float scale = (float) targetW / dim[0];
        var pose = gui.pose();
        pose.pushMatrix();
        pose.translate(x, y, 0.0F);
        pose.scale(scale, scale, 1.0F);
        gui.blit(RenderPipelines.GUI_TEXTURED, KOLLEGEN_LOGO, 0, 0, 0.0F, 0.0F, dim[0], dim[1], dim[0], dim[1]);
        pose.popMatrix();
    }
}
