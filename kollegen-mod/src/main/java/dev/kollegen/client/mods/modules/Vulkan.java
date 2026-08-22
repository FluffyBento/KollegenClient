package dev.kollegen.client.mods.modules;

import dev.kollegen.client.KollegenMod;
import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.Module;
import dev.kollegen.client.mods.ModuleManager;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Integriert den Vulkan-Renderer (VulkanMod) direkt ins Kollegen-Menü. Statt
 * wie im Original-Mod eine Config-Datei von Hand zu editieren, wird
 * VulkanMod hier über das Modul-Ein/Aus sanft ein- und ausgeschaltet, indem
 * seine Jar-Datei in der Instance umbenannt wird:
 *   - aktiv   → {@code VulkanMod-*.jar}
 *   - inaktiv → {@code VulkanMod-*.jar.disabled} (Fabric lädt diese nicht)
 * Das Umbenennen greift natürlich erst beim nächsten Start – das Modul zeigt
 * daher über {@code risk} einen Hinweis, falls VulkanMod fehlt.
 */
public final class Vulkan {

    private Vulkan() {
    }

    public static void register() {
        ModuleManager.register(new VulkanModule());
    }

    private static final class VulkanModule extends Module {

        VulkanModule() {
            super("vulkan", "Vulkan Renderer",
                    "Aktiviert/deaktiviert den Vulkan-Renderer (VulkanMod). Wirkt nach Neustart.",
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
            Path current = findVulkanMod(mods);
            if (current == null) {
                risk = "VulkanMod nicht installiert – in mods/ ablegen";
                return;
            }
            Path target = targetPath(current, enable);
            if (!current.equals(target)) {
                try {
                    Files.move(current, target);
                } catch (IOException e) {
                    KollegenMod.LOGGER.warn("Kollegen: VulkanMod konnte nicht {} werden: {}",
                            enable ? "aktiviert" : "deaktiviert", e.getMessage());
                    risk = "VulkanMod konnte nicht " + (enable ? "aktiviert" : "deaktiviert") + " werden";
                    return;
                }
            }
            String ver = versionFromName(target);
            risk = null;
            KollegenMod.LOGGER.info("Kollegen: Vulkan-Renderer {} (VulkanMod {})",
                    enable ? "aktiviert" : "deaktiviert", ver);
        }

        private static Path findVulkanMod(Path mods) {
            List<Path> files;
            try (Stream<Path> s = Files.list(mods)) {
                files = s.collect(Collectors.toList());
            } catch (IOException e) {
                return null;
            }
            Path enabled = null;
            Path disabled = null;
            for (Path p : files) {
                String n = p.getFileName().toString().toLowerCase();
                if (!n.startsWith("vulkanmod-")) continue;
                if (n.endsWith(".jar")) enabled = p;
                else if (n.endsWith(".jar.disabled")) disabled = p;
            }
            return enabled != null ? enabled : disabled;
        }

        private static Path targetPath(Path p, boolean enable) {
            String name = p.getFileName().toString();
            if (enable) {
                if (name.endsWith(".jar.disabled"))
                    return p.resolveSibling(name.substring(0, name.length() - ".disabled".length()));
                return p;
            }
            if (name.endsWith(".jar.disabled")) return p;
            return p.resolveSibling(name + ".disabled");
        }

        private static String versionFromName(Path p) {
            String n = p.getFileName().toString();
            String v = n.substring("vulkanmod-".length());
            if (v.endsWith(".jar")) v = v.substring(0, v.length() - ".jar".length());
            if (v.endsWith(".disabled")) v = v.substring(0, v.length() - ".disabled".length());
            return v;
        }
    }
}
