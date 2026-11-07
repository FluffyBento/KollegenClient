package dev.kollegen.client.feed;

import com.mojang.blaze3d.platform.NativeImage;
import dev.kollegen.client.ui.Glass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class FeedImageManager {

    private static final Map<String, Identifier> TEXTURE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, int[]> DIMS_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> LOADING = ConcurrentHashMap.newKeySet();

    private FeedImageManager() {
    }

    public static File getScreenshotsDir() {
        Minecraft mc = Minecraft.getInstance();
        return new File(mc.gameDirectory, "screenshots");
    }

    public static File getLatestScreenshot() {
        File dir = getScreenshotsDir();
        if (!dir.exists() || !dir.isDirectory()) return null;
        File[] files = dir.listFiles((d, name) -> name.toLowerCase(Locale.ROOT).endsWith(".png"));
        if (files == null || files.length == 0) return null;
        File newest = null;
        long newestTime = -1;
        for (File f : files) {
            long t = f.lastModified();
            if (t > newestTime) {
                newestTime = t;
                newest = f;
            }
        }
        return newest;
    }

    public static List<File> getRecentScreenshots(int maxCount) {
        List<File> list = new ArrayList<>();
        File dir = getScreenshotsDir();
        if (!dir.exists() || !dir.isDirectory()) return list;
        File[] files = dir.listFiles((d, name) -> name.toLowerCase(Locale.ROOT).endsWith(".png"));
        if (files == null || files.length == 0) return list;
        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (int i = 0; i < Math.min(files.length, maxCount); i++) {
            list.add(files[i]);
        }
        return list;
    }

    public static Identifier getOrLoadTexture(String path) {
        if (path == null || path.trim().isEmpty()) return null;
        String clean = path.trim();
        Identifier cached = TEXTURE_CACHE.get(clean);
        if (cached != null) return cached;

        File f = new File(clean);
        if (!f.exists() || !f.isFile()) {
            File inScreenshots = new File(getScreenshotsDir(), clean);
            if (inScreenshots.exists() && inScreenshots.isFile()) {
                f = inScreenshots;
            } else {
                return null;
            }
        }

        final File fileToLoad = f;
        if (!LOADING.add(clean)) return null;

        Thread t = new Thread(() -> {
            try (InputStream in = new FileInputStream(fileToLoad)) {
                NativeImage img = NativeImage.read(in);
                if (img != null) {
                    int w = img.getWidth();
                    int h = img.getHeight();
                    DIMS_CACHE.put(clean, new int[]{w, h});

                    String safeName = fileToLoad.getName().replaceAll("[^a-zA-Z0-9_.-]", "_");
                    Identifier id = Identifier.tryParse("kollegen:feed_img_" + safeName.toLowerCase(Locale.ROOT));

                    Minecraft.getInstance().execute(() -> {
                        try {
                            DynamicTexture tex = new DynamicTexture(() -> "feed_" + safeName, img);
                            Minecraft.getInstance().getTextureManager().register(id, tex);
                            TEXTURE_CACHE.put(clean, id);
                        } catch (Throwable ignored) {
                        }
                    });
                }
            } catch (Throwable ignored) {
            } finally {
                LOADING.remove(clean);
            }
        }, "kollegen-feed-img-loader");
        t.setDaemon(true);
        t.start();

        return null;
    }

    public static void drawImage(GuiGraphicsExtractor gui, String path, int x, int y, int targetW, int targetH, float radius) {
        if (path == null || path.trim().isEmpty()) return;
        Identifier id = getOrLoadTexture(path);

        Glass.fillRound(gui, x, y, targetW, targetH, (int) radius, 0xFF0D0E15);

        if (id != null) {
            int[] dims = DIMS_CACHE.getOrDefault(path.trim(), new int[]{targetW, targetH});
            int origW = Math.max(1, dims[0]);
            int origH = Math.max(1, dims[1]);

            Matrix3x2fStack pose = gui.pose();
            pose.pushMatrix();
            pose.translate((float) x, (float) y);
            float scaleX = (float) targetW / (float) origW;
            float scaleY = (float) targetH / (float) origH;
            pose.scale(scaleX, scaleY);
            gui.blit(RenderPipelines.GUI_TEXTURED, id, 0, 0, 0.0f, 0.0f, origW, origH, origW, origH);
            pose.popMatrix();

            Glass.ring(gui, x, y, targetW, targetH, radius, 1.0f, 0x44FFFFFF);
        } else {
            Glass.ring(gui, x, y, targetW, targetH, radius, 1.0f, 0x22FFFFFF);
            Glass.textC(gui, Minecraft.getInstance().font, "Lade Bild…", x + targetW / 2, y + (targetH - 8) / 2, 0xFF888888, false);
        }
    }
}
