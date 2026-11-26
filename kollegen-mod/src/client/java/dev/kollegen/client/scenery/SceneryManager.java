package dev.kollegen.client.scenery;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import dev.kollegen.client.KollegenMod;
import dev.kollegen.client.ui.GuiCustomizationSettings;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.texture.CubeMapTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureContents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;

import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class SceneryManager {

    private static final Path SCENERIES_DIR = FabricLoader.getInstance().getGameDir().resolve("kollegen-sceneries");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final DateTimeFormatter FOLDER_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final int CAPTURE_RES = 1024;
    private static final int THUMB_RES = 160;

    private static final String[] SUFFIX_FACES = {
            "panorama_1.png",
            "panorama_3.png",
            "panorama_5.png",
            "panorama_4.png",
            "panorama_0.png",
            "panorama_2.png"
    };

    private static final Map<String, Identifier> THUMB_CACHE = new ConcurrentHashMap<>();

    private static volatile boolean captureActive = false;
    private static int captureFace = 0;
    private static int captureSettleTicks = 0;
    private static boolean capturePendingReadback = false;
    private static Path captureDir = null;
    private static String captureFolderName = null;
    private static String captureFriendlyName = null;
    private static String captureDimension = null;
    private static float savedXRot = 0.0f;
    private static float savedYRot = 0.0f;
    private static int savedFov = 70;
    private static boolean savedBobView = true;
    private static CameraType savedCameraType = CameraType.FIRST_PERSON;
    private static final AtomicInteger CAPTURE_SAVED_COUNT = new AtomicInteger(0);
    private static volatile boolean captureWriteFailed = false;
    private static volatile String captureErrorReason = null;
    private static volatile long captureReadbackStartMs = 0L;

    public record SceneryEntry(String id, String displayName, Path directory, long timestamp, String dimension) {
    }

    private SceneryManager() {
    }

    public static Path getSceneriesDir() {
        try {
            if (!Files.exists(SCENERIES_DIR)) {
                Files.createDirectories(SCENERIES_DIR);
            }
        } catch (Throwable ignored) {
        }
        return SCENERIES_DIR;
    }

    public static boolean isCapturing() {
        return captureActive;
    }

    public static List<SceneryEntry> listSceneries() {
        List<SceneryEntry> result = new ArrayList<>();
        Path root = getSceneriesDir();
        if (!Files.isDirectory(root)) {
            return result;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root, Files::isDirectory)) {
            for (Path dir : stream) {
                promoteNestedScreenshots(dir);
                if (!hasValidPanoramaFiles(dir)) {
                    pruneEmptyBrokenScenery(dir);
                    continue;
                }
                String id = dir.getFileName().toString();
                String displayName = id;
                String dimension = "";
                long timestamp = 0;

                Path metaPath = dir.resolve("meta.json");
                if (Files.exists(metaPath)) {
                    try (Reader reader = Files.newBufferedReader(metaPath)) {
                        JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                        if (obj.has("name")) {
                            displayName = obj.get("name").getAsString();
                        }
                        if (obj.has("created")) {
                            timestamp = obj.get("created").getAsLong();
                        }
                        if (obj.has("dimension")) {
                            dimension = obj.get("dimension").getAsString();
                        }
                    } catch (Throwable ignored) {
                    }
                }

                if (timestamp == 0) {
                    try {
                        timestamp = Files.getLastModifiedTime(dir).toMillis();
                    } catch (Throwable ignored) {
                        timestamp = System.currentTimeMillis();
                    }
                }

                result.add(new SceneryEntry(id, displayName, dir, timestamp, dimension));
            }
        } catch (Throwable ignored) {
        }

        result.sort(Comparator.comparingLong(SceneryEntry::timestamp).reversed());
        return result;
    }

    private static void promoteNestedScreenshots(Path dir) {
        Path nested = dir.resolve("screenshots");
        if (!Files.isDirectory(nested)) {
            return;
        }
        boolean hasNested = true;
        for (int i = 0; i < 6; i++) {
            if (!Files.isRegularFile(nested.resolve("panorama_" + i + ".png"))) {
                hasNested = false;
                break;
            }
        }
        if (hasNested) {
            for (int i = 0; i < 6; i++) {
                Path src = nested.resolve("panorama_" + i + ".png");
                Path dst = dir.resolve("panorama_" + i + ".png");
                try {
                    Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
                } catch (Throwable ignored) {
                }
            }
            try {
                Files.deleteIfExists(nested);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void pruneEmptyBrokenScenery(Path dir) {
        if (captureActive && captureDir != null && captureDir.equals(dir)) {
            return;
        }
        boolean anyPng = false;
        for (int i = 0; i < 6; i++) {
            if (Files.exists(dir.resolve("panorama_" + i + ".png"))) {
                anyPng = true;
                break;
            }
        }
        if (!anyPng) {
            try {
                Path nested = dir.resolve("screenshots");
                if (Files.isDirectory(nested)) {
                    try (DirectoryStream<Path> s = Files.newDirectoryStream(nested)) {
                        if (s.iterator().hasNext()) {
                            return;
                        }
                    }
                    Files.deleteIfExists(nested);
                }
                Files.deleteIfExists(dir.resolve("meta.json"));
                Files.deleteIfExists(dir);
            } catch (Throwable ignored) {
            }
        }
    }

    private static boolean hasValidPanoramaFiles(Path dir) {
        for (int i = 0; i < 6; i++) {
            try {
                Path p = dir.resolve("panorama_" + i + ".png");
                if (!Files.isRegularFile(p) || Files.size(p) <= 0) {
                    return false;
                }
            } catch (Throwable t) {
                return false;
            }
        }
        return true;
    }

    public static boolean hasActiveCustomScenery() {
        String selected = GuiCustomizationSettings.getSelectedScenery();
        if (selected == null || selected.isEmpty() || "STANDARD".equalsIgnoreCase(selected)) {
            return false;
        }
        Path dir = getSceneriesDir().resolve(selected);
        promoteNestedScreenshots(dir);
        return hasValidPanoramaFiles(dir);
    }

    public static TextureContents loadActiveSceneryTextureContents() {
        String selected = GuiCustomizationSettings.getSelectedScenery();
        if (selected == null || "STANDARD".equalsIgnoreCase(selected)) {
            return null;
        }
        Path dir = getSceneriesDir().resolve(selected);
        promoteNestedScreenshots(dir);
        if (!hasValidPanoramaFiles(dir)) {
            return null;
        }

        try {
            NativeImage[] faces = new NativeImage[6];
            int targetSize = 0;
            for (int i = 0; i < 6; i++) {
                try (InputStream in = Files.newInputStream(dir.resolve(SUFFIX_FACES[i]))) {
                    faces[i] = NativeImage.read(in);
                }
                if (i == 0) {
                    targetSize = Math.max(64, Math.min(faces[0].getWidth(), faces[0].getHeight()));
                }
            }

            NativeImage atlas = new NativeImage(targetSize, targetSize * 6, false);
            for (int i = 0; i < 6; i++) {
                NativeImage face = faces[i];
                if (face.getWidth() == targetSize && face.getHeight() == targetSize) {
                    face.copyRect(atlas, 0, 0, 0, i * targetSize, targetSize, targetSize, false, true);
                } else {
                    NativeImage scaled = resizeAndFlipY(face, targetSize, targetSize, true);
                    scaled.copyRect(atlas, 0, 0, 0, i * targetSize, targetSize, targetSize, false, false);
                    scaled.close();
                }
                face.close();
            }

            return new TextureContents(atlas, null);
        } catch (Throwable t) {
            return null;
        }
    }

    public static void reloadPanorama() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getTextureManager() != null) {
            try {
                Identifier panoramaId = Identifier.withDefaultNamespace("textures/gui/title/background/panorama");
                mc.getTextureManager().registerAndLoad(panoramaId, new CubeMapTexture(panoramaId));
            } catch (Throwable ignored) {
            }
        }
    }

    public static boolean renameScenery(String id, String newName) {
        if (id == null || id.isEmpty() || newName == null || newName.trim().isEmpty()) {
            return false;
        }
        Path dir = getSceneriesDir().resolve(id);
        if (!Files.isDirectory(dir)) {
            return false;
        }
        Path metaPath = dir.resolve("meta.json");
        JsonObject obj = new JsonObject();
        if (Files.exists(metaPath)) {
            try (Reader reader = Files.newBufferedReader(metaPath)) {
                obj = JsonParser.parseReader(reader).getAsJsonObject();
            } catch (Throwable ignored) {
            }
        }
        obj.addProperty("name", newName.trim());
        if (!obj.has("created")) {
            obj.addProperty("created", System.currentTimeMillis());
        }
        try (Writer writer = Files.newBufferedWriter(metaPath)) {
            writer.write(obj.toString());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean deleteScenery(String id) {
        if (id == null || id.isEmpty() || "STANDARD".equalsIgnoreCase(id)) {
            return false;
        }
        Path dir = getSceneriesDir().resolve(id);
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Throwable ignored) {
                }
            });
            Identifier oldThumb = THUMB_CACHE.remove(id);
            if (oldThumb != null) {
                Minecraft mc = Minecraft.getInstance();
                if (mc != null && mc.getTextureManager() != null) {
                    mc.getTextureManager().release(oldThumb);
                }
            }
            if (id.equals(GuiCustomizationSettings.getSelectedScenery())) {
                GuiCustomizationSettings.setSelectedScenery("STANDARD");
                reloadPanorama();
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static Identifier getOrLoadThumbnail(SceneryEntry entry) {
        if (entry == null || entry.id() == null) {
            return null;
        }
        Identifier cached = THUMB_CACHE.get(entry.id());
        if (cached != null) {
            return cached;
        }
        Path thumbFile = entry.directory().resolve("panorama_0.png");
        if (!Files.isRegularFile(thumbFile)) {
            return null;
        }
        try (InputStream in = Files.newInputStream(thumbFile)) {
            NativeImage raw = NativeImage.read(in);
            NativeImage thumb = resizeAndFlipY(raw, THUMB_RES, THUMB_RES, false);
            raw.close();
            String safeId = entry.id().toLowerCase().replaceAll("[^a-z0-9_.-]", "_");
            Identifier id = Identifier.tryParse("kollegen:scenery_thumb_" + safeId);
            if (id == null) {
                thumb.close();
                return null;
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getTextureManager() != null) {
                mc.getTextureManager().register(id, new DynamicTexture(() -> "scenery_thumb_" + safeId, thumb));
                THUMB_CACHE.put(entry.id(), id);
                return id;
            }
            thumb.close();
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static boolean captureCurrentScenery() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null || mc.options == null) {
            return false;
        }
        if (captureActive) {
            return true;
        }

        LocalDateTime now = LocalDateTime.now();
        List<SceneryEntry> existing = listSceneries();
        int sceneryNumber = existing.size() + 1;
        String folderName = "szenerie_" + now.format(FOLDER_FORMATTER);
        Path targetDir = getSceneriesDir().resolve(folderName);
        try {
            Files.createDirectories(targetDir);
        } catch (Throwable t) {
            KollegenMod.LOGGER.error("[Szenerie] Ordner konnte nicht erstellt werden: {}", targetDir, t);
            return false;
        }

        if (mc.gui != null && mc.gui.screen() != null) {
            mc.setScreenAndShow(null);
        }

        String userFriendlyName = "Szenerie " + sceneryNumber + " (" + now.format(TIME_FORMATTER) + ")";

        savedXRot = mc.player.getXRot();
        savedYRot = mc.player.getYRot();
        savedFov = mc.options.fov().get();
        savedBobView = mc.options.bobView().get();
        savedCameraType = mc.options.getCameraType();

        captureDir = targetDir;
        captureFolderName = folderName;
        captureFriendlyName = userFriendlyName;
        captureDimension = mc.level.dimension() != null ? mc.level.dimension().identifier().toString() : "";
        captureFace = 0;
        captureSettleTicks = 2;
        capturePendingReadback = false;
        CAPTURE_SAVED_COUNT.set(0);
        captureWriteFailed = false;
        captureErrorReason = null;
        captureReadbackStartMs = 0L;

        if (mc.gameRenderer != null) {
            mc.gameRenderer.setRenderBlockOutline(false);
            if (mc.gameRenderer.mainCamera() != null) {
                mc.gameRenderer.mainCamera().enablePanoramicMode();
            }
        }
        mc.options.bobView().set(false);
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.options.fov().set(90);

        captureActive = true;
        KollegenMod.LOGGER.info("[Szenerie] Aufnahme gestartet -> {}", targetDir);
        return true;
    }

    public static void onRenderFrameStart() {
        if (!captureActive) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) {
            captureErrorReason = "Welt oder Spieler nicht mehr geladen";
            abortCapture();
            return;
        }
        applyFaceAngles(mc, captureFace);
        if (capturePendingReadback && captureReadbackStartMs > 0L && System.currentTimeMillis() - captureReadbackStartMs > 8000L) {
            captureErrorReason = "GPU-Readback Timeout bei Seite " + captureFace;
            KollegenMod.LOGGER.error("[Szenerie] {}", captureErrorReason);
            abortCapture();
        }
    }

    public static void onRenderFrameEnd(boolean renderLevel) {
        if (!captureActive || !renderLevel || capturePendingReadback) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.gameRenderer == null || mc.gameRenderer.mainRenderTarget() == null) {
            captureErrorReason = "RenderTarget nicht verfügbar";
            abortCapture();
            return;
        }
        if (captureSettleTicks > 0) {
            captureSettleTicks--;
            return;
        }

        final int faceIndex = captureFace;
        final Path outFile = captureDir.resolve("panorama_" + faceIndex + ".png");
        capturePendingReadback = true;
        captureReadbackStartMs = System.currentTimeMillis();

        try {
            Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), raw -> {
                Util.ioPool().execute(() -> {
                    try (raw) {
                        NativeImage square = cropCenterSquareAndScale(raw, CAPTURE_RES);
                        try (square) {
                            if (outFile.getParent() != null) {
                                Files.createDirectories(outFile.getParent());
                            }
                            square.writeToFile(outFile);
                        }
                        CAPTURE_SAVED_COUNT.incrementAndGet();
                    } catch (Throwable t) {
                        KollegenMod.LOGGER.error("[Szenerie] Fehler beim Verarbeiten/Speichern von Seite {}", faceIndex, t);
                        captureErrorReason = t.getClass().getSimpleName() + (t.getMessage() != null ? ": " + t.getMessage() : "");
                        captureWriteFailed = true;
                    }
                    mc.execute(() -> advanceCapture(mc));
                });
            });
        } catch (Throwable t) {
            KollegenMod.LOGGER.error("[Szenerie] takeScreenshot fehlgeschlagen bei Seite {}", faceIndex, t);
            captureErrorReason = t.getClass().getSimpleName() + (t.getMessage() != null ? ": " + t.getMessage() : "");
            captureWriteFailed = true;
            abortCapture();
        }
    }

    private static void advanceCapture(Minecraft mc) {
        if (!captureActive) {
            return;
        }
        capturePendingReadback = false;
        if (captureWriteFailed) {
            abortCapture();
            return;
        }
        captureFace++;
        if (captureFace >= 6) {
            finishCapture(mc);
        } else {
            captureSettleTicks = 1;
        }
    }

    private static void finishCapture(Minecraft mc) {
        restorePlayerAndOptions(mc);
        captureActive = false;

        Path dir = captureDir;
        String folder = captureFolderName;
        String friendly = captureFriendlyName;
        String dim = captureDimension;
        captureDir = null;

        if (dir == null || !hasValidPanoramaFiles(dir)) {
            if (dir != null) {
                pruneEmptyBrokenScenery(dir);
            }
            sendChat(mc, "§c[Szenerie] Aufnahme fehlgeschlagen: Panorama-Bilder konnten nicht gespeichert werden.");
            return;
        }

        try {
            JsonObject meta = new JsonObject();
            meta.addProperty("name", friendly);
            meta.addProperty("created", System.currentTimeMillis());
            if (dim != null && !dim.isEmpty()) {
                meta.addProperty("dimension", dim);
            }
            try (Writer writer = Files.newBufferedWriter(dir.resolve("meta.json"))) {
                writer.write(meta.toString());
            }

            GuiCustomizationSettings.setSelectedScenery(folder);
            reloadPanorama();

            sendChat(mc, "§6[Szenerie] §a360°-Szenerie '" + friendly + "' aufgenommen! Im Hauptmenü aktiv.");
            if (mc.player != null) {
                mc.player.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            }
        } catch (Throwable t) {
            sendChat(mc, "§c[Szenerie] Fehler beim Abschließen der Szenerie: " + t.getMessage());
        }
    }

    private static void abortCapture() {
        Minecraft mc = Minecraft.getInstance();
        String detail = captureErrorReason;
        if (detail != null && !detail.isEmpty()) {
            KollegenMod.LOGGER.error("[Szenerie] Aufnahme abgebrochen: {}", detail);
        } else {
            KollegenMod.LOGGER.warn("[Szenerie] Aufnahme abgebrochen (ohne Detailgrund)");
        }
        if (mc != null) {
            restorePlayerAndOptions(mc);
            String msg = detail != null && !detail.isEmpty()
                    ? "§c[Szenerie] Aufnahme abgebrochen (" + detail + ")."
                    : "§c[Szenerie] Aufnahme abgebrochen.";
            sendChat(mc, msg);
        }
        captureActive = false;
        capturePendingReadback = false;
        captureErrorReason = null;
        captureReadbackStartMs = 0L;
        if (captureDir != null) {
            pruneEmptyBrokenScenery(captureDir);
            captureDir = null;
        }
    }

    private static void restorePlayerAndOptions(Minecraft mc) {
        if (mc.player != null) {
            mc.player.setXRot(savedXRot);
            mc.player.setYRot(savedYRot);
            mc.player.xRotO = savedXRot;
            mc.player.yRotO = savedYRot;
        }
        if (mc.gameRenderer != null) {
            mc.gameRenderer.setRenderBlockOutline(true);
            if (mc.gameRenderer.mainCamera() != null) {
                mc.gameRenderer.mainCamera().disablePanoramicMode();
            }
        }
        if (mc.options != null) {
            mc.options.bobView().set(savedBobView);
            mc.options.setCameraType(savedCameraType);
            mc.options.fov().set(savedFov);
        }
    }

    private static void applyFaceAngles(Minecraft mc, int face) {
        float yaw = savedYRot;
        float pitch = 0.0f;
        switch (face) {
            case 0 -> {
                yaw = savedYRot;
                pitch = 0.0f;
            }
            case 1 -> {
                yaw = (savedYRot + 90.0f) % 360.0f;
                pitch = 0.0f;
            }
            case 2 -> {
                yaw = (savedYRot + 180.0f) % 360.0f;
                pitch = 0.0f;
            }
            case 3 -> {
                yaw = (savedYRot - 90.0f) % 360.0f;
                pitch = 0.0f;
            }
            case 4 -> {
                yaw = savedYRot;
                pitch = -90.0f;
            }
            case 5 -> {
                yaw = savedYRot;
                pitch = 90.0f;
            }
            default -> {
            }
        }
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
        mc.player.yRotO = yaw;
        mc.player.xRotO = pitch;
    }

    private static NativeImage cropCenterSquareAndScale(NativeImage src, int targetSize) {
        int sw = src.getWidth();
        int sh = src.getHeight();
        int side = Math.min(sw, sh);
        int offX = (sw - side) / 2;
        int offY = (sh - side) / 2;
        int outSize = Math.min(targetSize, side);
        NativeImage out = new NativeImage(outSize, outSize, false);
        if (side == outSize) {
            src.copyRect(out, offX, offY, 0, 0, outSize, outSize, false, false);
            return out;
        }
        for (int dy = 0; dy < outSize; dy++) {
            float syF = ((dy + 0.5f) * side) / outSize - 0.5f;
            int y0 = Math.max(0, Math.min(side - 1, (int) Math.floor(syF)));
            int y1 = Math.max(0, Math.min(side - 1, y0 + 1));
            float fy = Math.max(0.0f, syF - y0);
            for (int dx = 0; dx < outSize; dx++) {
                float sxF = ((dx + 0.5f) * side) / outSize - 0.5f;
                int x0 = Math.max(0, Math.min(side - 1, (int) Math.floor(sxF)));
                int x1 = Math.max(0, Math.min(side - 1, x0 + 1));
                float fx = Math.max(0.0f, sxF - x0);
                int c00 = src.getPixel(offX + x0, offY + y0);
                int c10 = src.getPixel(offX + x1, offY + y0);
                int c01 = src.getPixel(offX + x0, offY + y1);
                int c11 = src.getPixel(offX + x1, offY + y1);
                out.setPixel(dx, dy, bilerpArgb(c00, c10, c01, c11, fx, fy) | 0xFF000000);
            }
        }
        return out;
    }

    private static NativeImage resizeAndFlipY(NativeImage src, int outW, int outH, boolean flipY) {
        int sw = src.getWidth();
        int sh = src.getHeight();
        NativeImage out = new NativeImage(outW, outH, false);
        for (int dy = 0; dy < outH; dy++) {
            float syF = ((dy + 0.5f) * sh) / outH - 0.5f;
            int y0 = Math.max(0, Math.min(sh - 1, (int) Math.floor(syF)));
            int y1 = Math.max(0, Math.min(sh - 1, y0 + 1));
            float fy = Math.max(0.0f, syF - y0);
            int destY = flipY ? (outH - 1 - dy) : dy;
            for (int dx = 0; dx < outW; dx++) {
                float sxF = ((dx + 0.5f) * sw) / outW - 0.5f;
                int x0 = Math.max(0, Math.min(sw - 1, (int) Math.floor(sxF)));
                int x1 = Math.max(0, Math.min(sw - 1, x0 + 1));
                float fx = Math.max(0.0f, sxF - x0);
                int c00 = src.getPixel(x0, y0);
                int c10 = src.getPixel(x1, y0);
                int c01 = src.getPixel(x0, y1);
                int c11 = src.getPixel(x1, y1);
                out.setPixel(dx, destY, bilerpArgb(c00, c10, c01, c11, fx, fy));
            }
        }
        return out;
    }

    private static int bilerpArgb(int c00, int c10, int c01, int c11, float fx, float fy) {
        int a = bilerpChannel((c00 >>> 24) & 0xFF, (c10 >>> 24) & 0xFF, (c01 >>> 24) & 0xFF, (c11 >>> 24) & 0xFF, fx, fy);
        int r = bilerpChannel((c00 >>> 16) & 0xFF, (c10 >>> 16) & 0xFF, (c01 >>> 16) & 0xFF, (c11 >>> 16) & 0xFF, fx, fy);
        int g = bilerpChannel((c00 >>> 8) & 0xFF, (c10 >>> 8) & 0xFF, (c01 >>> 8) & 0xFF, (c11 >>> 8) & 0xFF, fx, fy);
        int b = bilerpChannel(c00 & 0xFF, c10 & 0xFF, c01 & 0xFF, c11 & 0xFF, fx, fy);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int bilerpChannel(int v00, int v10, int v01, int v11, float fx, float fy) {
        float top = v00 + (v10 - v00) * fx;
        float bot = v01 + (v11 - v01) * fx;
        return Math.max(0, Math.min(255, Math.round(top + (bot - top) * fy)));
    }

    private static void sendChat(Minecraft mc, String msg) {
        if (mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
            mc.gui.hud.getChat().addClientSystemMessage(Component.literal(msg));
        }
    }
}
