package dev.kollegen.client.ui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.Window;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.joml.Matrix3x2fStack;

public final class LogoDraw {
    private static final Identifier KOLLEGEN_LOGO = Identifier.tryParse("kollegen:logo.png");
    private static final Map<String, int[]> CACHE = new HashMap<String, int[]>();

    private LogoDraw() {
    }

    public static int[] dims() {
        return dimsOf(KOLLEGEN_LOGO, 1200, 400);
    }

    public static int[] dimsOf(Identifier id, int fallbackW, int fallbackH) {
        if (id == null) {
            return new int[]{fallbackW, fallbackH};
        }
        String key = id.toString();
        int[] hit = CACHE.get(key);
        if (hit != null) {
            return hit;
        }
        int[] out = new int[]{fallbackW, fallbackH};
        boolean found = false;
        try {
            Optional res = Minecraft.getInstance().getResourceManager().getResource(id);
            if (res.isPresent()) {
                try (InputStream in = ((Resource) res.get()).open()) {
                    byte[] hdr = new byte[24];
                    int r = 0;
                    int n;
                    while (r < 24 && (n = in.read(hdr, r, 24 - r)) > 0) {
                        r += n;
                    }
                    if (r == 24) {
                        int w = (hdr[16] & 0xFF) << 24 | (hdr[17] & 0xFF) << 16 | (hdr[18] & 0xFF) << 8 | hdr[19] & 0xFF;
                        int h = (hdr[20] & 0xFF) << 24 | (hdr[21] & 0xFF) << 16 | (hdr[22] & 0xFF) << 8 | hdr[23] & 0xFF;
                        if (w > 0 && h > 0) {
                            out = new int[]{w, h};
                            found = true;
                        }
                    }
                }
            } else {
                found = true;
            }
        } catch (Exception exception) {
        }
        if (found) {
            CACHE.put(key, out);
        }
        return out;
    }

    public static boolean exists(Identifier id) {
        try {
            Optional res = Minecraft.getInstance().getResourceManager().getResource(id);
            return res.isPresent();
        } catch (Exception e) {
            return false;
        }
    }

    public static void draw(GuiGraphicsExtractor gui, int x, int y, int targetW) {
        drawId(gui, KOLLEGEN_LOGO, x, y, targetW);
    }

    public static void drawId(GuiGraphicsExtractor gui, Identifier id, int x, int y, int targetW) {
        if (id == null) {
            return;
        }
        try {
            int[] dim = dimsOf(id, targetW, targetW);
            float scale = (float) targetW / (float) dim[0];
            Matrix3x2fStack pose = gui.pose();
            pose.pushMatrix();
            pose.translate((float) x, (float) y);
            pose.scale(scale, scale);
            gui.blit(RenderPipelines.GUI_TEXTURED, id, 0, 0, 0.0f, 0.0f, dim[0], dim[1], dim[0], dim[1]);
            pose.popMatrix();
        } catch (Exception exception) {
        }
    }
}
