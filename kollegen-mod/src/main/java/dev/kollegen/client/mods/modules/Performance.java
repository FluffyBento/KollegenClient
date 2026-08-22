package dev.kollegen.client.mods.modules;

import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.Module;
import dev.kollegen.client.mods.ModuleManager;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.client.PrioritizeChunkUpdates;

public final class Performance {

    private Performance() {
    }

    public static void register() {
        ModuleManager.register(new FastGraphics());
        ModuleManager.register(new NoClouds());
        ModuleManager.register(new SmoothLighting());
        ModuleManager.register(new MaxFps());
        ModuleManager.register(new AdaptivePerformance());
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

    /**
     * Ein-Klick-Preset fuer maximale Leistung: b&uuml;ndelt die aggressivsten
     * client-seitigen Einstellungen (Schnelle Grafik, keine Wolken, keine weiche
     * Beleuchtung, keine Entity-Schatten, keine Sicht-Effekte
     * wie Verletzungs-/K&uuml;rbis-Overlay, reduzierte Entity-Sichtweite und
     * Chunk-Update-Priorisierung auf Performance). Stellt die Originalwerte beim
     * Deaktivieren wieder her.
     */
    private static class MaxFps extends Module {
        private GraphicsPreset savedGfx = GraphicsPreset.FANCY;
        private CloudStatus savedCloud = CloudStatus.FANCY;
        private Boolean savedAO = Boolean.TRUE;
        private Boolean savedEntityShadows = Boolean.TRUE;
        private Boolean savedBob = Boolean.TRUE;
        private Boolean savedVignette = Boolean.TRUE;
        private Double savedScreenEffect = 1.0;
        private Double savedEntityDist = 1.0;
        private PrioritizeChunkUpdates savedChunk = PrioritizeChunkUpdates.BALANCED;

        MaxFps() {
            super("maxfps", "Max FPS", "Wendet die aggressivsten Performance-Einstellungen auf einmal an.", Category.PERFORMANCE);
        }

        @Override
        public void onEnable() {
            var o = mc.options;
            if (o == null) return;
            savedGfx = o.graphicsPreset().get();
            savedCloud = o.cloudStatus().get();
            savedAO = o.ambientOcclusion().get();
            savedEntityShadows = o.entityShadows().get();
            savedBob = o.bobView().get();
            savedVignette = o.vignette().get();
            savedScreenEffect = o.screenEffectScale().get();
            savedEntityDist = o.entityDistanceScaling().get();
            savedChunk = o.prioritizeChunkUpdates().get();

            o.graphicsPreset().set(GraphicsPreset.FAST);
            o.cloudStatus().set(CloudStatus.OFF);
            o.ambientOcclusion().set(false);
            o.entityShadows().set(false);
            o.bobView().set(false);
            o.vignette().set(false);
            o.screenEffectScale().set(0.0);
            o.entityDistanceScaling().set(0.5);
            o.prioritizeChunkUpdates().set(PrioritizeChunkUpdates.PERFORMANCE);
        }

        @Override
        public void onDisable() {
            var o = mc.options;
            if (o == null) return;
            o.graphicsPreset().set(savedGfx);
            o.cloudStatus().set(savedCloud);
            o.ambientOcclusion().set(savedAO);
            o.entityShadows().set(savedEntityShadows);
            o.bobView().set(savedBob);
            o.vignette().set(savedVignette);
            o.screenEffectScale().set(savedScreenEffect);
            o.entityDistanceScaling().set(savedEntityDist);
            o.prioritizeChunkUpdates().set(savedChunk);
        }
    }

    /**
     * Adaptive Performance: &uuml;berwacht die aktuelle FPS und regelt die
     * Render-Distanz (sowie bei Bedarf die Grafikstufe) automatisch herunter,
     * sobald die FPS einbrechen, und f&auml;hrt sie bei Spielraum wieder hoch
     * (bis zur vom Spieler gew&auml;hlten Ausgangs-Distanz). So bleibt die FPS
     * stabil, ohne die Sicht dauerhaft unn&ouml;tig zu beschneiden.
     */
    private static class AdaptivePerformance extends Module {
        private int baseRenderDistance = 12;
        private GraphicsPreset baseGfx = GraphicsPreset.FANCY;
        private int timer = 0;

        AdaptivePerformance() {
            super("adaptiveperf", "Adaptive Performance", "Regelt Render-Distanz/Grafik automatisch anhand der FPS.", Category.PERFORMANCE);
        }

        @Override
        public void onEnable() {
            var o = mc.options;
            if (o == null) return;
            baseRenderDistance = o.renderDistance().get();
            baseGfx = o.graphicsPreset().get();
        }

        @Override
        public void onDisable() {
            var o = mc.options;
            if (o == null) return;
            o.renderDistance().set(baseRenderDistance);
            o.graphicsPreset().set(baseGfx);
        }

        @Override
        public void onTick() {
            var o = mc.options;
            if (o == null || mc.level == null) return;
            if (++timer < 20) return; // ~1x pro Sekunde auswerten
            timer = 0;
            int fps = mc.getFps();
            int cur = o.renderDistance().get();
            if (fps < 30) {
                if (cur > 4) {
                    o.renderDistance().set(cur - 1);
                } else if (o.graphicsPreset().get() != GraphicsPreset.FAST) {
                    o.graphicsPreset().set(GraphicsPreset.FAST);
                }
            } else if (fps > 55 && cur < baseRenderDistance) {
                o.renderDistance().set(cur + 1);
            }
        }
    }
}
