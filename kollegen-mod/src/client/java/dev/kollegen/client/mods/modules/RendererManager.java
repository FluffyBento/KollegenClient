package dev.kollegen.client.mods.modules;

import dev.kollegen.client.KollegenMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipFile;

public final class RendererManager {

    private RendererManager() {
    }

    private static final String[][] MODS = {
            {"/dev/kollegen/client/sodium.bin", "sodium.jar", "sodium"},
            {"/dev/kollegen/client/iris.bin", "iris.jar", "iris"},
    };

    private static final Path STATE_FILE = FabricLoader.getInstance()
            .getGameDir().resolve("mods").resolve(".kollegen-renderer");

    public enum Group {
        OPENGL,
        VULKAN
    }

    public static boolean isSodiumPresent() {
        if (FabricLoader.getInstance().isModLoaded("sodium")) {
            return true;
        }
        try {
            Path mods = FabricLoader.getInstance().getGameDir().resolve("mods");
            if (Files.isDirectory(mods)) {
                try (java.nio.file.DirectoryStream<Path> ds = Files.newDirectoryStream(mods, "*.jar")) {
                    for (Path p : ds) {
                        String name = p.getFileName().toString().toLowerCase();
                        if (name.startsWith("sodium") && !name.endsWith(".disabled")) {
                            return true;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    public static Group desiredGroup() {
        try {
            if (Files.exists(STATE_FILE)) {
                String s = Files.readString(STATE_FILE).trim();
                if ("opengl".equalsIgnoreCase(s)) {
                    return Group.OPENGL;
                }
                if ("vulkan".equalsIgnoreCase(s)) {
                    return Group.VULKAN;
                }
            }
        } catch (IOException ignored) {
        }
        if (isSodiumPresent()) {
            return Group.OPENGL;
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
        for (String[] mod : MODS) {
            boolean wantActive = active == Group.OPENGL;
            ensureState(mods, mod[0], mod[1], wantActive);
        }
        try {
            Files.deleteIfExists(mods.resolve("beryl.jar"));
            Files.deleteIfExists(mods.resolve("beryl.jar.disabled"));
            Files.deleteIfExists(mods.resolve("VulkanMod.jar"));
            Files.deleteIfExists(mods.resolve("VulkanMod.jar.disabled"));
        } catch (IOException ignored) {
        }
        handleRendererConflicts(mods, active);
        syncOptionsBackend(active);
        KollegenMod.LOGGER.info("Kollegen: Renderer abgestimmt -> {}", active == Group.VULKAN ? "Vulkan Native MDI" : "OpenGL (Sodium + Iris)");
    }

    public static void syncOptionsBackend(Group active) {
        try {
            Path opts = FabricLoader.getInstance().getGameDir().resolve("options.txt");
            if (Files.isRegularFile(opts)) {
                String content = Files.readString(opts);
                String target = active == Group.VULKAN ? "preferredGraphicsBackend:\"vulkan\"" : "preferredGraphicsBackend:\"opengl\"";
                String other = active == Group.VULKAN ? "preferredGraphicsBackend:\"opengl\"" : "preferredGraphicsBackend:\"vulkan\"";
                if (content.contains(other)) {
                    Files.writeString(opts, content.replace(other, target));
                } else if (!content.contains(target)) {
                    Files.writeString(opts, content + "\n" + target + "\n");
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc != null && mc.options != null && mc.options.preferredGraphicsBackend() != null) {
                net.minecraft.client.PreferredGraphicsApi api = active == Group.VULKAN
                        ? net.minecraft.client.PreferredGraphicsApi.VULKAN
                        : net.minecraft.client.PreferredGraphicsApi.OPENGL;
                if (mc.options.preferredGraphicsBackend().get() != api) {
                    mc.options.preferredGraphicsBackend().set(api);
                }
            }
        } catch (Throwable ignored) {
        }
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
        if (!active) {
            if (Files.exists(jar)) {
                try {
                    Files.move(jar, disabled, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ignored) {
                }
            }
            return;
        }
        if (Files.exists(disabled)) {
            try {
                Files.move(disabled, jar, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
            }
        }
        if (Files.exists(jar) && isIntactJar(jar)) {
            return;
        }
        try (InputStream in = RendererManager.class.getResourceAsStream(resource)) {
            if (in == null) {
                KollegenMod.LOGGER.warn("Kollegen: Mod-Ressource fehlt: " + resource);
                removeIfBroken(jar, fileName);
            } else {
                byte[] payload = in.readAllBytes();
                Path tmp = mods.resolve(fileName + ".tmp");
                Files.write(tmp, payload);
                try {
                    Files.move(tmp, jar, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    Files.move(tmp, jar, StandardCopyOption.REPLACE_EXISTING);
                }
                removeIfBroken(jar, fileName);
            }
        } catch (IOException e) {
            KollegenMod.LOGGER.warn("Kollegen: Mod konnte nicht deployt werden: " + fileName);
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

    private static void removeIfBroken(Path jar, String fileName) {
        if (!Files.isRegularFile(jar) || isIntactJar(jar)) {
            return;
        }
        try {
            Files.deleteIfExists(jar);
        } catch (IOException ignored) {
        }
    }

    private static boolean isIntactJar(Path jar) {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}