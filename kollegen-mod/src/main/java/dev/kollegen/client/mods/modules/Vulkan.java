package dev.kollegen.client.mods.modules;

import dev.kollegen.client.KollegenMod;
import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.Module;
import dev.kollegen.client.mods.ModuleManager;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Integriert den Vulkan-Renderer (VulkanMod, LGPL-3.0) in den
 * Kollegen-Client. VulkanMod und Sodium (plus Iris) sind gegenseitig
 * inkompatibel (beide ersetzen den Renderer). Dieser Toggle managt die
 * Exklusivität automatisch:
 * <ul>
 *   <li>Vulkan AN  → Sodium + Iris werden deaktiviert (.disabled), VulkanMod wird deployed.</li>
 *   <li>Vulkan AUS → VulkanMod wird entfernt, Sodium + Iris werden wiederhergestellt.</li>
 * </ul>
 * Der Wechsel wirkt erst nach Neustart (Renderer-Tausch im laufenden Spiel nicht möglich).
 * Eine Marker-Datei (`.kollegen-vulkan-disabled`) merkt sich, welche Mods wir deaktiviert
 * haben, damit nur unsere Änderungen rückgängig gemacht werden.
 */
public final class Vulkan {

    private Vulkan() {
    }

    public static void register() {
        ModuleManager.register(new VulkanModule());
    }

    private static final class VulkanModule extends Module {
        private static final String EMBEDDED = "/dev/kollegen/client/vulkanmod.bin";
        private static final String FILE_NAME = "VulkanMod-0.6.8+1.21.11.jar";
        private static final String VULKAN_PREFIX = "VulkanMod-";
        private static final String[] EXCLUSIVE_PREFIXES = {"sodium-", "iris-"};

        private static final Path MARKER =
                FabricLoader.getInstance().getGameDir().resolve("mods").resolve(".kollegen-vulkan-disabled");

        VulkanModule() {
            super("vulkan", "Vulkan Renderer",
                    "Integrierter Vulkan-Renderer (VulkanMod). Inkompatibel mit Sodium/Iris – diese werden automatisch deaktiviert. Wirkt nach Neustart.",
                    Category.PERFORMANCE);
        }

        @Override
        public void onEnable() {
            apply(true);
        }

        @Override
        public void onDisable() {
            apply(false);
        }

        private void apply(boolean enable) {
            Path mods = FabricLoader.getInstance().getGameDir().resolve("mods");
            if (!Files.isDirectory(mods)) {
                risk = "mods-Ordner nicht gefunden";
                return;
            }
            if (enable) {
                // Alle vorhandenen VulkanMod-Versionen entfernen (sauberer Zustand)
                removeAllVulkanMod(mods);

                // Exklusive Mods (Sodium + Iris) deaktivieren und merken
                List<Path> disabled = disableExclusiveMods(mods);
                if (!disabled.isEmpty()) {
                    KollegenMod.LOGGER.info("Kollegen: Für Vulkan deaktiviert: {}", disabled);
                }

                // VulkanMod deployen
                try (InputStream in = VulkanModule.class.getResourceAsStream(EMBEDDED)) {
                    if (in == null) {
                        risk = "VulkanMod ist nicht in diesem Mod eingebettet (Build-Fehler)";
                        restoreExclusiveMods(mods, disabled);
                        return;
                    }
                    Files.copy(in, mods.resolve(FILE_NAME), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    KollegenMod.LOGGER.warn("Kollegen: VulkanMod konnte nicht deployt werden: {}", e.getMessage());
                    risk = "VulkanMod konnte nicht aktiviert werden";
                    restoreExclusiveMods(mods, disabled);
                    return;
                }

                // Marker schreiben (damit wir beim Deaktivieren wissen, was wir deaktiviert haben)
                writeMarker(disabled);

                risk = "Vulkan ist AKTIV – Minecraft neu starten, damit es lädt. (Sodium/Iris wurden automatisch deaktiviert.)";
                KollegenMod.LOGGER.info("Kollegen: VulkanMod deployt, Exklusiv-Mods deaktiviert (aktiv nach Neustart).");
            } else {
                // VulkanMod entfernen
                boolean removed = removeAllVulkanMod(mods);
                if (!removed) {
                    risk = "VulkanMod war nicht installiert";
                    return;
                }

                // Exklusive Mods wiederherstellen (falls wir sie deaktiviert hatten)
                List<Path> disabled = readMarker();
                if (!disabled.isEmpty()) {
                    restoreExclusiveMods(mods, disabled);
                    KollegenMod.LOGGER.info("Kollegen: Exklusive Mods wiederhergestellt: {}", disabled);
                }
                deleteMarker();

                risk = "Vulkan ist DEAKTIVIERT – Minecraft neu starten (lädt OpenGL). (Sodium/Iris wieder aktiv.)";
                KollegenMod.LOGGER.info("Kollegen: VulkanMod entfernt, Exklusiv-Mods wiederhergestellt (OpenGL nach Neustart).");
            }
        }

        private static List<Path> disableExclusiveMods(Path mods) {
            List<Path> disabled = new ArrayList<>();
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(mods)) {
                for (Path p : ds) {
                    String n = p.getFileName().toString().toLowerCase();
                    for (String prefix : EXCLUSIVE_PREFIXES) {
                        if (n.startsWith(prefix) && (n.endsWith(".jar") || n.endsWith(".jar.disabled"))) {
                            Path target = p.getFileName().toString().endsWith(".jar")
                                    ? p.resolveSibling(p.getFileName() + ".disabled")
                                    : p; // schon .disabled? dann nichts tun
                            if (!p.equals(target)) {
                                try {
                                    Files.move(p, target);
                                    disabled.add(target);
                                } catch (IOException ignored) {
                                }
                            }
                        }
                    }
                }
            } catch (IOException ignored) {
            }
            return disabled;
        }

        private static void restoreExclusiveMods(Path mods, List<Path> disabled) {
            for (Path p : disabled) {
                String n = p.getFileName().toString();
                if (n.endsWith(".jar.disabled")) {
                    Path target = p.resolveSibling(n.substring(0, n.length() - ".disabled".length()));
                    try {
                        Files.move(p, target);
                    } catch (IOException ignored) {
                    }
                }
            }
        }

        private void writeMarker(List<Path> disabled) {
            if (disabled.isEmpty()) return;
            try {
                List<String> lines = new ArrayList<>();
                for (Path p : disabled) {
                    lines.add(p.getFileName().toString());
                }
                Files.write(MARKER, lines);
            } catch (IOException ignored) {
            }
        }

        private List<Path> readMarker() {
            List<Path> out = new ArrayList<>();
            if (!Files.exists(MARKER)) return out;
            try {
                List<String> lines = Files.readAllLines(MARKER);
                Path mods = FabricLoader.getInstance().getGameDir().resolve("mods");
                for (String name : lines) {
                    out.add(mods.resolve(name));
                }
            } catch (IOException ignored) {
            }
            return out;
        }

        private void deleteMarker() {
            try {
                Files.deleteIfExists(MARKER);
            } catch (IOException ignored) {
            }
        }

        private static boolean removeAllVulkanMod(Path mods) {
            boolean removed = false;
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(mods)) {
                for (Path p : ds) {
                    String n = p.getFileName().toString().toLowerCase();
                    if (n.startsWith(VULKAN_PREFIX.toLowerCase())
                            && (n.endsWith(".jar") || n.endsWith(".jar.disabled"))) {
                        try {
                            if (Files.deleteIfExists(p)) removed = true;
                        } catch (IOException ignored) {
                        }
                    }
                }
            } catch (IOException ignored) {
            }
            return removed;
        }
    }
}