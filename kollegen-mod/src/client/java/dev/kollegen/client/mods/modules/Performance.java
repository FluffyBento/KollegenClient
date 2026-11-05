package dev.kollegen.client.mods.modules;

import dev.kollegen.client.mods.BooleanSetting;
import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.Module;
import dev.kollegen.client.mods.ModuleManager;
import dev.kollegen.client.mods.SliderSetting;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.client.PrioritizeChunkUpdates;

public final class Performance {

    private Performance() {
    }

    public static void register() {
        ModuleManager.register(new KollegenTricks());
        ModuleManager.register(new KollegenDistance());
        ModuleManager.register(new FastGraphics());
        ModuleManager.register(new NoClouds());
        ModuleManager.register(new SmoothLighting());
        ModuleManager.register(new CpuOffload());
        ModuleManager.register(new MaxFps());
        ModuleManager.register(new AdaptivePerformance());
    }

    public static class KollegenTricks extends Module {
        public static volatile KollegenTricks instance;

        public static final BooleanSetting cullBlockEntities = new BooleanSetting(
                "Sichtfeld- & Wand-Culling (Blöcke)",
                "Berechnet und rendert Kisten, Schilder, Banner etc. nur im Kamerasichtfeld und blendet sie hinter Mauern aus.",
                true
        );

        public static final BooleanSetting cullOccludedEntities = new BooleanSetting(
                "Wand-Culling (Entities)",
                "Blendet Mobs, Tiere und Spieler aus, die vollständig hinter Wänden, Decken oder Höhlen verborgen sind.",
                true
        );

        public static final BooleanSetting cullParticles = new BooleanSetting(
                "Smarte Partikel",
                "Blockiert Partikelberechnungen & Kollisionen für Effekte außerhalb des Blickfelds.",
                true
        );

        public static final BooleanSetting cullShadows = new BooleanSetting(
                "Smarte Schatten",
                "Überspringt Voxel-Kollisionsprüfungen für Entity-Schatten außerhalb des Sichtfelds.",
                true
        );

        public static final BooleanSetting cacheFont = new BooleanSetting(
                "Schrift-Beschleuniger",
                "Extrem schneller O(1)-Cache für Textbreiten in HUD, Nametags, Tabliste und Chat.",
                true
        );

        public static final BooleanSetting cullPets = new BooleanSetting(
                "Begleiter-Optimierung",
                "Rendert Begleiter & Animationen nur, wenn sie im Sichtfeld sichtbar sind.",
                true
        );

        public static final BooleanSetting bedrockMath = new BooleanSetting(
                "Bedrock Fast-Math",
                "Beschleunigt Winkelberechnungen (wrapDegrees) und Rundungen (floor/ceil) auf 1 CPU-Zyklus.",
                true
        );

        public static final BooleanSetting bedrockCache = new BooleanSetting(
                "Bedrock L1-Tint-Cache",
                "Allokationsfreier L1-Cache für Biom- und Blockfarben (Gras, Wasser, Blätter) ohne Lock-Barrieren.",
                true
        );

        public static final BooleanSetting optimizeItems = new BooleanSetting(
                "Smarte Items (Fast Items)",
                "Rendert gedroppte Item-Stacks als 1 sauberes Modell statt bis zu 5 übereinanderliegender Duplikate.",
                true
        );

        public static final BooleanSetting boostThread = new BooleanSetting(
                "Render-Thread Priorität",
                "Erhöht die CPU-Priorität des Haupt-Render-Threads für stabile Frametimes ohne Mikroruckler.",
                true
        );

        public static final BooleanSetting cullDistantMobs = new BooleanSetting(
                "Distanz-Culling (Mobs)",
                "Rendert passive Tiere und Mobs nur bis 28 Blöcke Entfernung. Spieler, Projektile & Bosse bleiben auf voller Sichtweite!",
                true
        );

        public static final BooleanSetting purgeMarkerArmorStands = new BooleanSetting(
                "Unsichtbare Marker entfernen",
                "Filtert unsichtbare Server-Marker & Hologramm-Puffer ohne Namen sofort heraus.",
                true
        );

        public static final BooleanSetting cullCaveSky = new BooleanSetting(
                "Höhlen-Himmel Optimierung",
                "Schaltet das Rendern von Himmelskuppel, Sonne, Mond und Sternen in Höhlen und unter Tage komplett ab.",
                true
        );

        public static final BooleanSetting cullHanging = new BooleanSetting(
                "Rückseiten-Culling (Rahmen & Bilder)",
                "Überspringt Item-Frames und Gemälde, die mit dem Rücken zur Kamera an Wänden hängen.",
                true
        );

        public static final BooleanSetting cullRain = new BooleanSetting(
                "Sichtfeld-Regen (Rain Culling)",
                "Generiert und berechnet Regen- und Schneesäulen nur innerhalb des Kamerasichtfelds.",
                true
        );

        public static final BooleanSetting dynamicFps = new BooleanSetting(
                "Dynamische FPS (Hintergrund-Sparmodus)",
                "Drosselt die Bildrate auf 15 FPS, wenn Minecraft minimiert ist oder das Fenster nicht fokussiert ist.",
                true
        );

        public static final BooleanSetting cullSounds = new BooleanSetting(
                "Smarter Sound-Filter (Sound Culling)",
                "Überspringt Audio-Pufferung und Mixer-Berechnungen für Geräusche außerhalb der Hörweite.",
                true
        );

        public KollegenTricks() {
            super("kollegentricks", "Kollegen Tricks", "Smarte Optimierungen ohne Qualitätsverlust: Wand- & Sichtfeld-Culling, Bedrock L1-Cache, Fast-Math & Thread-Boost.", Category.PERFORMANCE);
            this.enabled = true;
            instance = this;
            add(cullOccludedEntities);
            add(cullBlockEntities);
            add(cullHanging);
            add(cullRain);
            add(dynamicFps);
            add(cullSounds);
            add(cullDistantMobs);
            add(purgeMarkerArmorStands);
            add(cullCaveSky);
            add(cullParticles);
            add(cullShadows);
            add(optimizeItems);
            add(boostThread);
            add(cacheFont);
            add(cullPets);
            add(bedrockMath);
            add(bedrockCache);
        }

        public static boolean isEnabled() {
            return instance != null && instance.enabled;
        }

        public static boolean shouldCullBlockEntities() {
            return isEnabled() && cullBlockEntities.value;
        }

        public static boolean shouldCullOccludedEntities() {
            return isEnabled() && cullOccludedEntities.value;
        }

        public static boolean shouldCullDistantMobs() {
            return isEnabled() && cullDistantMobs.value;
        }

        public static boolean shouldPurgeMarkerArmorStands() {
            return isEnabled() && purgeMarkerArmorStands.value;
        }

        public static boolean shouldCullCaveSky() {
            return isEnabled() && cullCaveSky.value;
        }

        public static boolean shouldCullParticles() {
            return isEnabled() && cullParticles.value;
        }

        public static boolean shouldCullShadows() {
            return isEnabled() && cullShadows.value;
        }

        public static boolean shouldOptimizeItems() {
            return isEnabled() && optimizeItems.value;
        }

        public static boolean shouldBoostThread() {
            return isEnabled() && boostThread.value;
        }

        public static boolean shouldCacheFont() {
            return isEnabled() && cacheFont.value;
        }

        public static boolean shouldCullPets() {
            return isEnabled() && cullPets.value;
        }

        public static boolean shouldUseBedrockMath() {
            return isEnabled() && bedrockMath.value;
        }

        public static boolean shouldUseBedrockCache() {
            return isEnabled() && bedrockCache.value;
        }

        public static boolean shouldCullHanging() {
            return isEnabled() && cullHanging.value;
        }

        public static boolean shouldCullRain() {
            return isEnabled() && cullRain.value;
        }

        public static boolean shouldUseDynamicFps() {
            return isEnabled() && dynamicFps.value;
        }

        public static boolean shouldCullSounds() {
            return isEnabled() && cullSounds.value;
        }

        public static boolean shouldUseBobby() {
            return KollegenDistance.isDistanceEnabled();
        }

        public static boolean shouldUseDistance() {
            return KollegenDistance.isDistanceEnabled();
        }
    }

    public static class KollegenDistance extends Module {
        public static volatile KollegenDistance instance;

        public static final SliderSetting maxChunks = new SliderSetting(
                "Max. Sichtweite",
                "Maximale Chunk-Distanz, die im Client-Speicher gehalten und gerendert wird.",
                32.0, 12.0, 64.0, 2.0,
                v -> (int) Math.round(v) + " Chunks",
                null
        );

        public KollegenDistance() {
            super("kollegendistance", "Kollegen Distance", "Erweiterte Sichtweite: Hält bereits besuchte Chunks im Client-Speicher über das Server-Limit hinaus sichtbar (bis zu 64 Chunks Sichtweite auf jedem Server).", Category.PERFORMANCE);
            this.enabled = true;
            instance = this;
            add(maxChunks);
        }

        public static boolean isDistanceEnabled() {
            return instance != null && instance.enabled;
        }

        public static int getMaxChunkDistance() {
            return instance != null ? (int) Math.round(maxChunks.value) : 32;
        }
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

    
    private static class CpuOffload extends Module {
        private Boolean savedVsync = Boolean.TRUE;
        private Integer savedMipmap = 4;
        private Integer savedBiomeBlend = 4;
        private PrioritizeChunkUpdates savedChunk = PrioritizeChunkUpdates.PLAYER_AFFECTED;

        CpuOffload() {
            super("cpuoffload", "CPU-Last", "Last auf die CPU verschieben, GPU entlasten (VSync aus, Mipmaps/Biom-Mischung aus, Chunk-Updates nahe).", Category.PERFORMANCE);
        }

        @Override
        public void onEnable() {
            var o = mc.options;
            if (o == null) return;
            savedVsync = o.enableVsync().get();
            savedMipmap = o.mipmapLevels().get();
            savedBiomeBlend = o.biomeBlendRadius().get();
            savedChunk = o.prioritizeChunkUpdates().get();
            o.enableVsync().set(false);
            o.mipmapLevels().set(0);
            o.biomeBlendRadius().set(0);
            o.prioritizeChunkUpdates().set(PrioritizeChunkUpdates.NEARBY);
        }

        @Override
        public void onDisable() {
            var o = mc.options;
            if (o == null) return;
            o.enableVsync().set(savedVsync);
            o.mipmapLevels().set(savedMipmap);
            o.biomeBlendRadius().set(savedBiomeBlend);
            o.prioritizeChunkUpdates().set(savedChunk);
        }
    }

    
    private static class MaxFps extends Module {
        private GraphicsPreset savedGfx = GraphicsPreset.FANCY;
        private CloudStatus savedCloud = CloudStatus.FANCY;
        private Boolean savedAO = Boolean.TRUE;
        private Boolean savedEntityShadows = Boolean.TRUE;
        private Boolean savedBob = Boolean.TRUE;
        private Boolean savedVignette = Boolean.TRUE;
        private Double savedScreenEffect = 1.0;
        private Double savedEntityDist = 1.0;
        private PrioritizeChunkUpdates savedChunk = PrioritizeChunkUpdates.PLAYER_AFFECTED;

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
            o.prioritizeChunkUpdates().set(PrioritizeChunkUpdates.NEARBY);
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

    
    private static class AdaptivePerformance extends Module {
        private int baseRenderDistance = 12;
        private GraphicsPreset baseGfx = GraphicsPreset.FANCY;
        private int timer = 0;

        AdaptivePerformance() {
            super("adaptiveperf", "Adaptive Performance", "Regelt Render-Distanz/Grafik automatisch anhand der FPS.", Category.PERFORMANCE);
            this.enabled = false;
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
            if (++timer < 100) return; // Nur alle 5 Sekunden prüfen, niemals jede Sekunde Worker Threads neu starten
            timer = 0;
            int fps = mc.getFps();
            int cur = o.renderDistance().get();
            if (fps < 25 && cur > 4) {
                o.renderDistance().set(cur - 2);
            }
        }
    }
}
