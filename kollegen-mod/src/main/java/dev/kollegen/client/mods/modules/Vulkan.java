package dev.kollegen.client.mods.modules;

import dev.kollegen.client.KollegenMod;
import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.Module;
import dev.kollegen.client.mods.ModuleManager;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Integriert den Vulkan-Renderer (VulkanMod, LGPL-3.0) vollständig in den
 * Kollegen-Client: die VulkanMod-Datei ist als Resource in unserem Mod-Jar
 * eingebettet, sodass KEIN externer VulkanMod-Download/-Mod mehr nötig ist.
 * Der Toggle entpackt VulkanMod bei Bedarf in den {@code mods/}-Ordner der
 * Instanz (aktiv) bzw. entfernt es wieder (inaktiv). Das ist komfortabler als
 * im Original-Mod: ein Klick statt manueller Jar-Verwaltung, versionsfest und
 * mit klarer Statusanzeige. Die Änderung greift natürlich erst nach Neustart.
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
            Path target = mods.resolve(FILE_NAME);
            Path disabled = mods.resolve(FILE_NAME + ".disabled");
            if (enable) {
                if (Files.exists(target)) {
                    risk = null;
                    return; // bereits deployt
                }
                if (Files.exists(disabled)) {
                    // zuvor deaktivierte Variante wieder aktivieren
                    try {
                        Files.move(disabled, target);
                        risk = null;
                        KollegenMod.LOGGER.info("Kollegen: VulkanMod reaktiviert (aktiv nach Neustart).");
                    } catch (IOException e) {
                        risk = "VulkanMod konnte nicht aktiviert werden";
                    }
                    return;
                }
                try (InputStream in = VulkanModule.class.getResourceAsStream(EMBEDDED)) {
                    if (in == null) {
                        risk = "VulkanMod ist nicht in diesem Mod eingebettet (Build-Fehler)";
                        return;
                    }
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    KollegenMod.LOGGER.warn("Kollegen: VulkanMod konnte nicht deployt werden: {}", e.getMessage());
                    risk = "VulkanMod konnte nicht aktiviert werden";
                    return;
                }
                risk = null;
                KollegenMod.LOGGER.info("Kollegen: VulkanMod deployt (aktiv nach Neustart).");
            } else {
                try {
                    boolean removed = Files.deleteIfExists(target) | Files.deleteIfExists(disabled);
                    if (!removed) {
                        risk = "VulkanMod war nicht installiert";
                        return;
                    }
                } catch (IOException e) {
                    KollegenMod.LOGGER.warn("Kollegen: VulkanMod konnte nicht entfernt werden: {}", e.getMessage());
                    risk = "VulkanMod konnte nicht deaktiviert werden";
                    return;
                }
                risk = null;
                KollegenMod.LOGGER.info("Kollegen: VulkanMod entfernt (OpenGL nach Neustart).");
            }
        }
    }
}
