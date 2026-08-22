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
 * Integriert den Vulkan-Renderer (VulkanMod, LGPL-3.0) vollständig in den
 * Kollegen-Client: die VulkanMod-Datei ist als Resource im Mod-Jar eingebettet,
 * sodass KEIN externer VulkanMod-Download nötig ist. Der Toggle entpackt
 * VulkanMod in den {@code mods/}-Ordner (aktiv) bzw. entfernt es wieder
 * (inaktiv).
 *
 * <p>VulkanMod hat selbst keinen Abschalt-Schalter in seiner Config – die
 * einzige Möglichkeit, es zu deaktivieren, ist das Entfernen der Jar. Deshalb
 * wird beim Umschalten jeweils ein Neustart nötig (ein Renderer-Tausch im
 * laufenden Spiel ist nicht möglich). Damit der Launcher diese von uns
 * verwaltete Jar nicht als "Konflikt" repariert (und so eine Neustart-Schleife
 * erzeugt), überspringt {@code auto_resolve_conflict} in {@code main.rs}
 * VulkanMod.</p>
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
        private static final String PREFIX = "VulkanMod-";

        VulkanModule() {
            super("vulkan", "Vulkan Renderer",
                    "Integrierter Vulkan-Renderer (VulkanMod). Ein-Klick aktiv/deaktiv – wirkt nach Neustart.",
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
                // Alle vorhandenen VulkanMod-Versionen entfernen, damit genau
                // EINE (unsere eingebettete) übrig bleibt. Zwei VulkanMod-Jars
                // würde Fabric sonst als "incompatible mods" melden und crashen.
                removeAllVulkanMod(mods);
                try (InputStream in = VulkanModule.class.getResourceAsStream(EMBEDDED)) {
                    if (in == null) {
                        risk = "VulkanMod ist nicht in diesem Mod eingebettet (Build-Fehler)";
                        return;
                    }
                    Files.copy(in, mods.resolve(FILE_NAME), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    KollegenMod.LOGGER.warn("Kollegen: VulkanMod konnte nicht deployt werden: {}", e.getMessage());
                    risk = "VulkanMod konnte nicht aktiviert werden";
                    return;
                }
                risk = "Vulkan ist AKTIV – Minecraft neu starten, damit es lädt.";
                KollegenMod.LOGGER.info("Kollegen: VulkanMod deployt (aktiv nach Neustart).");
            } else {
                boolean removed = removeAllVulkanMod(mods);
                if (!removed) {
                    risk = "VulkanMod war nicht installiert";
                    return;
                }
                risk = "Vulkan ist DEAKTIVIERT – Minecraft neu starten (lädt OpenGL).";
                KollegenMod.LOGGER.info("Kollegen: VulkanMod entfernt (OpenGL nach Neustart).");
            }
        }

        private static boolean removeAllVulkanMod(Path mods) {
            boolean removed = false;
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(mods)) {
                List<Path> toRemove = new ArrayList<>();
                for (Path p : ds) {
                    String n = p.getFileName().toString().toLowerCase();
                    if (n.startsWith(PREFIX.toLowerCase())
                            && (n.endsWith(".jar") || n.endsWith(".jar.disabled"))) {
                        toRemove.add(p);
                    }
                }
                for (Path p : toRemove) {
                    try {
                        if (Files.deleteIfExists(p)) removed = true;
                    } catch (IOException ignored) {
                    }
                }
            } catch (IOException ignored) {
            }
            return removed;
        }
    }
}
