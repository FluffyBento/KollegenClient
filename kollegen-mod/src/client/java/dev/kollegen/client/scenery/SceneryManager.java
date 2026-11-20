package dev.kollegen.client.scenery;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import dev.kollegen.client.ui.GuiCustomizationSettings;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.CubeMapTexture;
import net.minecraft.client.renderer.texture.TextureContents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SceneryManager {

    private static final Path SCENERIES_DIR = FabricLoader.getInstance().getGameDir().resolve("kollegen-sceneries");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final DateTimeFormatter FOLDER_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private static final String[] SUFFIX_FACES = {
            "panorama_1.png",
            "panorama_3.png",
            "panorama_5.png",
            "panorama_4.png",
            "panorama_0.png",
            "panorama_2.png"
    };

    public record SceneryEntry(String id, String displayName, Path directory, long timestamp) {
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

    public static List<SceneryEntry> listSceneries() {
        List<SceneryEntry> result = new ArrayList<>();
        Path root = getSceneriesDir();
        if (!Files.isDirectory(root)) {
            return result;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root, Files::isDirectory)) {
            for (Path dir : stream) {
                if (!hasValidPanoramaFiles(dir)) {
                    continue;
                }
                String id = dir.getFileName().toString();
                String displayName = id;
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

                result.add(new SceneryEntry(id, displayName, dir, timestamp));
            }
        } catch (Throwable ignored) {
        }

        result.sort(Comparator.comparingLong(SceneryEntry::timestamp).reversed());
        return result;
    }

    private static boolean hasValidPanoramaFiles(Path dir) {
        for (int i = 0; i < 6; i++) {
            if (!Files.isRegularFile(dir.resolve("panorama_" + i + ".png"))) {
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
        return hasValidPanoramaFiles(dir);
    }

    public static TextureContents loadActiveSceneryTextureContents() {
        String selected = GuiCustomizationSettings.getSelectedScenery();
        if (selected == null || "STANDARD".equalsIgnoreCase(selected)) {
            return null;
        }
        Path dir = getSceneriesDir().resolve(selected);
        if (!hasValidPanoramaFiles(dir)) {
            return null;
        }

        try {
            Path face0Path = dir.resolve(SUFFIX_FACES[0]);
            NativeImage face0;
            try (InputStream in = Files.newInputStream(face0Path)) {
                face0 = NativeImage.read(in);
            }

            int w = face0.getWidth();
            int h = face0.getHeight();
            NativeImage atlas = new NativeImage(w, h * 6, false);
            face0.copyRect(atlas, 0, 0, 0, 0, w, h, false, true);
            face0.close();

            for (int i = 1; i < 6; i++) {
                Path facePath = dir.resolve(SUFFIX_FACES[i]);
                NativeImage face;
                try (InputStream in = Files.newInputStream(facePath)) {
                    face = NativeImage.read(in);
                }
                face.copyRect(atlas, 0, 0, 0, i * h, w, h, false, true);
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

    public static boolean captureCurrentScenery() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        String folderName = "szenerie_" + now.format(FOLDER_FORMATTER);
        Path targetDir = getSceneriesDir().resolve(folderName);
        try {
            Files.createDirectories(targetDir);
        } catch (Throwable t) {
            return false;
        }

        List<SceneryEntry> existing = listSceneries();
        int sceneryNumber = existing.size() + 1;
        String userFriendlyName = "Szenerie " + sceneryNumber + " (" + now.format(TIME_FORMATTER) + ")";

        try {
            Component result = mc.grabPanoramixScreenshot(targetDir.toFile());

            JsonObject meta = new JsonObject();
            meta.addProperty("name", userFriendlyName);
            meta.addProperty("created", System.currentTimeMillis());
            if (mc.level != null && mc.level.dimension() != null) {
                meta.addProperty("dimension", mc.level.dimension().identifier().toString());
            }

            try (Writer writer = Files.newBufferedWriter(targetDir.resolve("meta.json"))) {
                writer.write(meta.toString());
            }

            GuiCustomizationSettings.setSelectedScenery(folderName);
            reloadPanorama();

            if (mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
                mc.gui.hud.getChat().addClientSystemMessage(
                        Component.literal("§6[Szenerie] §a360°-Szenerie '" + userFriendlyName + "' aufgenommen! Im Hauptmenü aktiv.")
                );
            }
            if (mc.player != null) {
                mc.player.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            }
            return true;
        } catch (Throwable t) {
            if (mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
                mc.gui.hud.getChat().addClientSystemMessage(
                        Component.literal("§c[Szenerie] Fehler beim Aufnehmen der Szenerie: " + t.getMessage())
                );
            }
            return false;
        }
    }
}
