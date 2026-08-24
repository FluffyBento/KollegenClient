package dev.kollegen.client.mods.modules;

import dev.kollegen.client.KollegenMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Verwaltet die eingebetteten Renderer-Mods des Kollegen-Clients. Sodium + Iris
 * (OpenGL-Gruppe) und VulkanMod-Fork + Beryl (Vulkan-Gruppe) sind
 * gegenseitig exklusiv – Fabric crasht hart ("Incompatible mods found"), wenn
 * beide Gruppen gleichzeitig aktiv sind. Dieser Manager stellt sicher, dass
 * exakt eine Gruppe aktiv ist:
 *
 * <ul>
 *   <li>aktive Gruppe  → Jars aus den eingebetteten Resources deployen (.jar)</li>
 *   <li>inaktive Gruppe → Jars deaktivieren (.jar → .jar.disabled)</li>
 * </ul>
 *
 * Der gewünschte Zustand liegt in {@code mods/.kollegen-renderer}
 * ("vulkan" / "opengl"); der Launcher schreibt ihn vor jedem Start basierend
 * auf dem Instanz-Flag {@code vulkan_enabled}. So ist der Launcher die
 * alleinige Quelle der Wahrheit und der Mod-Menü-Toggle spiegelt das nur.
 */
public final class RendererManager {

    private RendererManager() {
    }

    /** [embedded resource, deployed file name, mod id]. */
    private static final String[][] MODS = {
            // OpenGL-Gruppe
            {"/dev/kollegen/client/sodium.bin", "sodium.jar", "sodium"},
            {"/dev/kollegen/client/iris.bin", "iris.jar", "iris"},
            // Vulkan-Gruppe
            {"/dev/kollegen/client/vulkanmod.bin", "VulkanMod.jar", "vulkanmod"},
            {"/dev/kollegen/client/beryl.bin", "beryl.jar", "beryl"},
    };

    private static final Path STATE_FILE = FabricLoader.getInstance()
            .getGameDir().resolve("mods").resolve(".kollegen-renderer");

    public enum Group {
        OPENGL,
        VULKAN
    }

    public static Group desiredGroup() {
        try {
            if (Files.exists(STATE_FILE)) {
                String s = Files.readString(STATE_FILE).trim();
                if ("vulkan".equalsIgnoreCase(s)) {
                    return Group.VULKAN;
                }
            }
        } catch (IOException ignored) {
        }
        return Group.OPENGL;
    }

    public static void setDesired(Group g) {
        try {
            Files.writeString(STATE_FILE, g == Group.VULKAN ? "vulkan" : "opengl");
        } catch (IOException e) {
            KollegenMod.LOGGER.warn("Kollegen: Renderer-State konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    /** Stellt den auf dem State-File basierenden Renderer-Zustand her. */
    public static void apply() {
        Group active = desiredGroup();
        Path mods = FabricLoader.getInstance().getGameDir().resolve("mods");
        if (!Files.isDirectory(mods)) {
            return;
        }
        for (String[] mod : MODS) {
            boolean wantActive = isVulkan(mod[2]) == (active == Group.VULKAN);
            ensureState(mods, mod[0], mod[1], wantActive);
        }
        KollegenMod.LOGGER.info("Kollegen: Renderer abgestimmt → {}", active == Group.VULKAN ? "Vulkan (VulkanMod + Beryl)" : "OpenGL (Sodium + Iris)");
    }

    private static boolean isVulkan(String id) {
        return id.equals("vulkanmod") || id.equals("beryl");
    }

    private static void ensureState(Path mods, String resource, String fileName, boolean active) {
        Path jar = mods.resolve(fileName);
        Path disabled = mods.resolve(fileName + ".disabled");
        if (active) {
            // Aktiv: aus der eingebetteten Resource (immer) neu deployen, damit ein
            // gebündeltes Versions-Update (z.B. Sodium 0.8.12 statt 0.8.13) einen evtl.
            // schon im Instance-Mods-Ordner liegenden, veralteten Jar ersetzt. Sonst
            // bliebe die alte, mit Iris inkompatible Version trotz Launcher-Update aktiv.
            try (InputStream in = RendererManager.class.getResourceAsStream(resource)) {
                if (in == null) {
                    KollegenMod.LOGGER.warn("Kollegen: eingebettete Mod '{}' fehlt (Build-Fehler).", resource);
                } else {
                    Files.copy(in, jar, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                KollegenMod.LOGGER.warn("Kollegen: Mod '{}' konnte nicht deployt werden: {}", fileName, e.getMessage());
            }
            // Falls zuvor deaktiviert, die .disabled-Kopie aufräumen.
            try {
                Files.deleteIfExists(disabled);
            } catch (IOException ignored) {
            }
        } else {
            // Inaktiv: vorhandenes Jar deaktivieren.
            if (Files.exists(jar)) {
                try {
                    Files.move(jar, disabled);
                } catch (IOException ignored) {
                }
            }
        }
    }
}
