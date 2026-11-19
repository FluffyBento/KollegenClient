package dev.kollegen.client.mods.modules;

import dev.kollegen.client.KollegenMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class RendererManager {

    private RendererManager() {
    }

    private static final Path STATE_FILE = FabricLoader.getInstance()
            .getGameDir().resolve("mods").resolve(".kollegen-renderer");

    public enum Group {
        VULKAN
    }

    public static boolean isSodiumPresent() {
        return false;
    }

    public static Group desiredGroup() {
        return Group.VULKAN;
    }

    public static void setDesired(Group g) {
        try {
            Files.writeString(STATE_FILE, "vulkan");
        } catch (IOException e) {
            KollegenMod.LOGGER.warn("Kollegen: Renderer-State konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    public static void apply() {
        Path mods = FabricLoader.getInstance().getGameDir().resolve("mods");
        if (!Files.isDirectory(mods)) {
            return;
        }
        purgeIncompatibleJars(mods);
        handleRendererConflicts(mods);
        syncOptionsBackend();
        KollegenMod.LOGGER.info("Kollegen: Renderer abgestimmt -> Vulkan Native (SPIR-V)");
    }

    public static void syncOptionsBackend() {
        try {
            Path opts = FabricLoader.getInstance().getGameDir().resolve("options.txt");
            if (Files.isRegularFile(opts)) {
                String content = Files.readString(opts);
                String target = "preferredGraphicsBackend:\"vulkan\"";
                String other = "preferredGraphicsBackend:\"opengl\"";
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
                net.minecraft.client.PreferredGraphicsApi api = net.minecraft.client.PreferredGraphicsApi.VULKAN;
                if (mc.options.preferredGraphicsBackend().get() != api) {
                    mc.options.preferredGraphicsBackend().set(api);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void purgeIncompatibleJars(Path mods) {
        String[] patterns = {
                "sodium*.jar", "sodium*.jar.disabled",
                "iris*.jar", "iris*.jar.disabled",
                "beryl*.jar", "beryl*.jar.disabled",
                "VulkanMod*.jar", "VulkanMod*.jar.disabled"
        };
        for (String pattern : patterns) {
            try (java.nio.file.DirectoryStream<Path> ds = Files.newDirectoryStream(mods, pattern)) {
                for (Path p : ds) {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                    }
                }
            } catch (IOException ignored) {
            }
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

    private static void handleRendererConflicts(Path mods) {
        try (java.nio.file.DirectoryStream<Path> ds = Files.newDirectoryStream(mods, "animatium*.jar")) {
            for (Path p : ds) {
                try {
                    Path disabled = mods.resolve(p.getFileName().toString() + ".kollegen-disabled");
                    Files.move(p, disabled, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
        try {
            Path ess = mods.resolve("essentialmod.jar");
            if (Files.exists(ess)) {
                Files.move(ess, mods.resolve("essentialmod.jar.disabled"), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
        }
        for (String pattern : new String[]{"hover_items*.jar", "silicon*.jar"}) {
            try (java.nio.file.DirectoryStream<Path> ds = Files.newDirectoryStream(mods, pattern)) {
                for (Path p : ds) {
                    try {
                        Path disabled = mods.resolve(p.getFileName().toString() + ".disabled");
                        Files.move(p, disabled, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException ignored) {
                    }
                }
            } catch (IOException ignored) {
            }
        }
    }
}
