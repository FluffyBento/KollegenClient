package dev.kollegen.client.menu;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Method;

public final class IrisBridge {

    private IrisBridge() {
    }

    public static boolean isIrisLoaded() {
        return FabricLoader.getInstance().isModLoaded("iris");
    }

    public static boolean areShadersEnabled() {
        if (!isIrisLoaded()) return false;
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
        return false;
    }

    public static void setShadersEnabled(boolean enabled) {
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
        if (!isIrisLoaded()) return "";
        try {
            Class<?> irisClass = Class.forName("net.irisshaders.iris.Iris");
            Object pack = irisClass.getMethod("getCurrentPackName").invoke(null);
            if (pack instanceof String s) {
                return s;
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    public static void setShaderpack(String packName) {
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
        new Thread(() -> {
            try {
                Class<?> irisClass = Class.forName("net.irisshaders.iris.Iris");
                irisClass.getMethod("reload").invoke(null);
            } catch (Throwable ignored) {
            }
        }, "Kollegen-Iris-Reloader").start();
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