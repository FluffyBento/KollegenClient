package dev.kollegen.client.mods.modules;

import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.Module;
import dev.kollegen.client.mods.ModuleManager;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;

public final class Performance {

    private Performance() {
    }

    public static void register() {
        ModuleManager.register(new FastGraphics());
        ModuleManager.register(new NoClouds());
        ModuleManager.register(new SmoothLighting());
    }

    private static class FastGraphics extends Module {
        private GraphicsPreset saved = GraphicsPreset.FANCY;

        FastGraphics() {
            super("fastgraphics", "Fast Graphics", "Setzt Grafik auf Schnell.", Category.PERFORMANCE);
        }

        @Override
        public void onEnable() {
            if (mc.options != null) {
                saved = mc.options.graphicsPreset().get();
                mc.options.graphicsPreset().set(GraphicsPreset.FAST);
            }
        }

        @Override
        public void onDisable() {
            if (mc.options != null) mc.options.graphicsPreset().set(saved);
        }
    }

    private static class NoClouds extends Module {
        private CloudStatus saved = CloudStatus.FANCY;

        NoClouds() {
            super("noclouds", "Keine Wolken", "Blendet Wolken aus.", Category.PERFORMANCE);
        }

        @Override
        public void onEnable() {
            if (mc.options != null) {
                saved = mc.options.cloudStatus().get();
                mc.options.cloudStatus().set(CloudStatus.OFF);
            }
        }

        @Override
        public void onDisable() {
            if (mc.options != null) mc.options.cloudStatus().set(saved);
        }
    }

    private static class SmoothLighting extends Module {
        private Boolean saved = Boolean.TRUE;

        SmoothLighting() {
            super("smoothlighting", "Keine weiche Beleuchtung", "Schaltet Ambient Occlusion aus.", Category.PERFORMANCE);
        }

        @Override
        public void onEnable() {
            if (mc.options != null) {
                saved = mc.options.ambientOcclusion().get();
                mc.options.ambientOcclusion().set(false);
            }
        }

        @Override
        public void onDisable() {
            if (mc.options != null) mc.options.ambientOcclusion().set(saved);
        }
    }
}
