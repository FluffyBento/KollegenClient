package dev.kollegen.client.mods.modules;

import dev.kollegen.client.KollegenMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;


public final class RendererManager {

    private RendererManager() {
    }

    
    private static final String[][] MODS = {
            
            {"/dev/kollegen/client/sodium.bin", "sodium.jar", "sodium"},
            {"/dev/kollegen/client/iris.bin", "iris.jar", "iris"},
            
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

    
    public static void apply() {
        Path mods = FabricLoader.getInstance().getGameDir().resolve("mods");
        if (!Files.isDirectory(mods)) {
            return;
        }
        Group active = desiredGroup();
        
        
        
        
        
        
        if (active == Group.VULKAN && essentialPresent(mods)) {
            KollegenMod.LOGGER.warn(
                    "Kollegen: Essential-Mod erkannt – Vulkan (VulkanMod/Beryl) ist mit dem "
                            + "Essential-Renderer inkompatibel (kopfstehende UI / Crash). Erzwinge OpenGL (Sodium+Iris).");
            active = Group.OPENGL;
            setDesired(Group.OPENGL);
        }
        for (String[] mod : MODS) {
            boolean wantActive = isVulkan(mod[2]) == (active == Group.VULKAN);
            ensureState(mods, mod[0], mod[1], wantActive);
        }
        handleRendererConflicts(mods, active);
        KollegenMod.LOGGER.info("Kollegen: Renderer abgestimmt → {}", active == Group.VULKAN ? "Vulkan (VulkanMod + Beryl)" : "OpenGL (Sodium + Iris)");
    }

    private static boolean isVulkan(String id) {
        return id.equals("vulkanmod") || id.equals("beryl");
    }

    
    public static boolean essentialPresent(Path mods) {
        try (java.nio.file.DirectoryStream<Path> ds = Files.newDirectoryStream(mods)) {
            for (Path p : ds) {
                String name = p.getFileName().toString().toLowerCase();
                if (name.startsWith("essential") && name.endsWith(".jar")) {
                    return true;
                }
            }
        } catch (IOException ignored) {
        }
        return false;
    }

    private static void ensureState(Path mods, String resource, String fileName, boolean active) {
        Path jar = mods.resolve(fileName);
        Path disabled = mods.resolve(fileName + ".disabled");
        if (active) {
            
            
            
            
            try (InputStream in = RendererManager.class.getResourceAsStream(resource)) {
                if (in == null) {
                    KollegenMod.LOGGER.warn("Kollegen: eingebettete Mod '{}' fehlt (Build-Fehler).", resource);
                } else {
                    Files.copy(in, jar, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                KollegenMod.LOGGER.warn("Kollegen: Mod '{}' konnte nicht deployt werden: {}", fileName, e.getMessage());
            }
            
            try {
                Files.deleteIfExists(disabled);
            } catch (IOException ignored) {
            }
        } else {
            
            if (Files.exists(jar)) {
                try {
                    Files.move(jar, disabled);
                } catch (IOException ignored) {
                }
            }
        }
    }

    
    private static void handleRendererConflicts(Path mods, Group active) {
        boolean vulkan = active == Group.VULKAN;
        if (vulkan) {
            
            try (java.nio.file.DirectoryStream<Path> ds = Files.newDirectoryStream(mods, "animatium*.jar")) {
                for (Path p : ds) {
                    try {
                        Path disabled = mods.resolve(p.getFileName().toString() + ".kollegen-disabled");
                        Files.move(p, disabled);
                        KollegenMod.LOGGER.warn(
                                "Kollegen: '{}' ist mit dem Vulkan(Beryl)-Renderer inkompatibel "
                                        + "(Sky-Renderer-Mixin-Konflikt) und wurde deaktiviert. Beim Wechsel auf den "
                                        + "OpenGL-Renderer wird es automatisch reaktiviert – ein Neustart der Instanz ist nötig.",
                                p.getFileName());
                    } catch (IOException ignored) {
                    }
                }
            } catch (IOException ignored) {
            }
        } else {
            
            try (java.nio.file.DirectoryStream<Path> ds = Files.newDirectoryStream(mods, "animatium*.jar.kollegen-disabled")) {
                for (Path p : ds) {
                    String name = p.getFileName().toString();
                    Path enabled = mods.resolve(name.substring(0, name.length() - ".kollegen-disabled".length()));
                    try {
                        Files.move(p, enabled);
                    } catch (IOException ignored) {
                    }
                }
            } catch (IOException ignored) {
            }
        }
    }
}
