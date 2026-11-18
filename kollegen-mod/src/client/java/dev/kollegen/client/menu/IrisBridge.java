package dev.kollegen.client.menu;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

public final class IrisBridge {

    private IrisBridge() {
    }

    public static boolean isIrisLoaded() {
        return FabricLoader.getInstance().isModLoaded("iris");
    }

    private static Path getIrisPropertiesPath() {
        return FabricLoader.getInstance().getGameDir().resolve("config").resolve("iris.properties");
    }

    public static String getSavedShaderpack() {
        try {
            Path path = getIrisPropertiesPath();
            if (Files.exists(path)) {
                Properties props = new Properties();
                try (InputStream in = Files.newInputStream(path)) {
                    props.load(in);
                }
                String en = props.getProperty("enableShaders", "true");
                if ("false".equalsIgnoreCase(en)) {
                    return "(Deaktiviert)";
                }
                String pack = props.getProperty("shaderPack", "");
                if (pack != null && !pack.trim().isEmpty()) {
                    return pack.trim();
                }
            }
        } catch (Throwable ignored) {
        }
        return "(Deaktiviert)";
    }

    public static void saveShaderpackToProperties(String packName) {
        try {
            Path path = getIrisPropertiesPath();
            if (path.getParent() != null && !Files.exists(path.getParent())) {
                Files.createDirectories(path.getParent());
            }
            Properties props = new Properties();
            if (Files.exists(path)) {
                try (InputStream in = Files.newInputStream(path)) {
                    props.load(in);
                } catch (Throwable ignored) {
                }
            }
            boolean disable = packName == null || packName.isEmpty() || packName.equalsIgnoreCase("(aus)")
                    || packName.equalsIgnoreCase("off") || packName.contains("Deaktiviert");
            props.setProperty("enableShaders", disable ? "false" : "true");
            props.setProperty("shaderPack", disable ? "" : packName);
            try (OutputStream out = Files.newOutputStream(path)) {
                props.store(out, "This file stores configuration options for Iris, such as the currently active shaderpack");
            }
        } catch (Throwable ignored) {
        }
    }

    public static boolean areShadersEnabled() {
        if (isIrisLoaded()) {
            try {
                Class<?> irisClass = Class.forName("net.irisshaders.iris.Iris");
                Object config = irisClass.getMethod("getIrisConfig").invoke(null);
                if (config != null) {
                    Method m = config.getClass().getMethod("areShadersEnabled");
                    Object res = m.invoke(config);
                    if (res instanceof Boolean b) {
                        return b;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        String pack = getSavedShaderpack();
        return !pack.contains("Deaktiviert");
    }

    public static void setShadersEnabled(boolean enabled) {
        if (!enabled) {
            saveShaderpackToProperties("(Deaktiviert)");
        }
        if (!isIrisLoaded()) return;
        try {
            Class<?> irisClass = Class.forName("net.irisshaders.iris.Iris");
            Object config = irisClass.getMethod("getIrisConfig").invoke(null);
            if (config != null) {
                config.getClass().getMethod("setShadersEnabled", boolean.class).invoke(config, enabled);
                config.getClass().getMethod("save").invoke(config);
            }
            reloadAsync();
        } catch (Throwable ignored) {
        }
    }

    public static String getCurrentShaderpack() {
        if (isIrisLoaded()) {
            try {
                Class<?> irisClass = Class.forName("net.irisshaders.iris.Iris");
                Object pack = irisClass.getMethod("getCurrentPackName").invoke(null);
                if (pack instanceof String s && !s.isEmpty()) {
                    return s;
                }
                Object config = irisClass.getMethod("getIrisConfig").invoke(null);
                if (config != null) {
                    Method m = config.getClass().getMethod("getShaderPackName");
                    Object res = m.invoke(config);
                    if (res instanceof Optional<?> opt && opt.isPresent()) {
                        return opt.get().toString();
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return getSavedShaderpack();
    }

    public static void setShaderpack(String packName) {
        saveShaderpackToProperties(packName);
        if (!isIrisLoaded()) return;
        boolean disable = packName == null || packName.isEmpty() || packName.equalsIgnoreCase("(aus)")
                || packName.equalsIgnoreCase("off") || packName.contains("Deaktiviert");
        try {
            Class<?> irisClass = Class.forName("net.irisshaders.iris.Iris");
            Object config = irisClass.getMethod("getIrisConfig").invoke(null);
            if (config != null) {
                String target = disable ? "" : packName;
                config.getClass().getMethod("setShaderPackName", String.class).invoke(config, target);
                config.getClass().getMethod("setShadersEnabled", boolean.class).invoke(config, !disable);
                config.getClass().getMethod("save").invoke(config);
            }
            reloadAsync();
        } catch (Throwable ignored) {
        }
    }

    public static void reloadAsync() {
        if (!isIrisLoaded()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.execute(() -> {
                try {
                    Class<?> irisClass = Class.forName("net.irisshaders.iris.Iris");
                    irisClass.getMethod("reload").invoke(null);
                } catch (Throwable ignored) {
                }
            });
        }
    }

    public static Screen openIrisConfigScreen(Screen parent) {
        if (!isIrisLoaded()) return null;
        try {
            Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            if (api != null) {
                Object screen = apiClass.getMethod("openMainIrisScreenObj", Object.class).invoke(api, parent);
                if (screen instanceof Screen s) {
                    return s;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
