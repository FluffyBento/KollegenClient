package dev.kollegen.client.mods.modules;

import dev.kollegen.client.mods.BooleanSetting;
import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.KeybindSetting;
import dev.kollegen.client.mods.ModeSetting;
import dev.kollegen.client.mods.Module;
import dev.kollegen.client.mods.ModuleManager;
import dev.kollegen.client.mods.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

public final class Visual {

    
    public static boolean particleCancelAll = false;

    
    public static float zoomFovDivisor = 1.0f;
    public static boolean noFogEnabled = false;
    public static boolean noFogTerrain = true;
    public static boolean noFogNether = true;
    public static boolean noFogWater = true;
    public static boolean noFogLava = true;
    public static boolean spectatorHighlightEnabled = true;
    public static boolean smoothSwappingEnabled = true;
    public static float fightCameraIntensity = 1.0f;
    public static boolean afkCinematicActive = false;
    public static long lastInputTime = System.currentTimeMillis();
    public static boolean zoomActive = false;
    public static boolean zoomSmoothCamera = false;

    public static boolean isZoomActive() {
        return zoomActive;
    }

    public static boolean isZoomSmoothCamera() {
        return zoomActive && zoomSmoothCamera;
    }

    public static boolean isSpectatorHighlightEnabled() {
        return spectatorHighlightEnabled;
    }

    public static boolean isSmoothSwappingEnabled() {
        return smoothSwappingEnabled;
    }

    public static float getFightCameraIntensity() {
        return fightCameraIntensity;
    }

    public static boolean isAfkCinematicActive() {
        return afkCinematicActive;
    }

    public static void resetAfkTimer() {
        lastInputTime = System.currentTimeMillis();
        if (afkCinematicActive) {
            afkCinematicActive = false;
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.options != null && AfkCinematic.savedCamera != null) {
                mc.options.setCameraType(AfkCinematic.savedCamera);
                AfkCinematic.savedCamera = null;
            }
        }
    }

    private Visual() {
    }

    public static void register() {
        ModuleManager.register(new Fullbright());
        ModuleManager.register(new ReducedParticles());
        ModuleManager.register(new Zoom());
        ModuleManager.register(new Freecam());
        ModuleManager.register(new NoFog());
        ModuleManager.register(new ArmorTransparency.Own());
        ModuleManager.register(new ArmorTransparency.Others());
        ModuleManager.register(new FovModifier());
        ModuleManager.register(new KollegenShader());
        ModuleManager.register(new WaveyCapes());
        ModuleManager.register(new SpectatorHighlighting());
        ModuleManager.register(new SmoothSwapping());
        ModuleManager.register(new FightCamera());
        ModuleManager.register(new AfkCinematic());
    }

    public static class WaveyCapes extends Module {
        public static boolean enabled = true;
        public static float waveStrength = 1.0f;

        private final BooleanSetting physics = new BooleanSetting("Wellen-Physik", "Aktiviert fließende Stoff-Simulation für Umhänge.", true);
        private final SliderSetting strength = new SliderSetting("Wellenstärke", "Intensität der Umhang-Wellenbewegung.", 1.0, 0.2, 3.0, 0.1);

        public WaveyCapes() {
            super("waveycapes", "Wavey Capes", "Physikalische Wellen- und Stoffbewegung für Umhänge.", Category.VISUAL);
            add(physics);
            add(strength);
        }

        @Override
        public void onEnable() {
            enabled = physics.value;
            waveStrength = (float) strength.value;
        }

        @Override
        public void onTick() {
            enabled = this.enabled && physics.value;
            waveStrength = (float) strength.value;
        }

        @Override
        public void onDisable() {
            enabled = false;
        }
    }

    public static class NoFog extends Module {
        public static NoFog INSTANCE;
        private final BooleanSetting terrain = new BooleanSetting("Oberwelt-Nebel", "Entfernt normalen Atmosphären-Nebel.", true);
        private final BooleanSetting nether = new BooleanSetting("Nether-Nebel", "Entfernt dichten roten Nether-Nebel.", true);
        private final BooleanSetting water = new BooleanSetting("Unterwasser-Nebel", "Entfernt trüben Unterwasser-Nebel für freie Sicht.", true);
        private final BooleanSetting lava = new BooleanSetting("Lava-Nebel", "Entfernt Lava-Sichtbehinderung.", true);

        NoFog() {
            super("nofog", "Kein Nebel", "Entfernt Nebel in Oberwelt, Nether, Wasser und Lava.", Category.VISUAL);
            INSTANCE = this;
            add(terrain);
            add(nether);
            add(water);
            add(lava);
        }

        @Override
        public void onEnable() {
            noFogEnabled = true;
            updateFlags();
        }

        @Override
        public void onTick() {
            noFogEnabled = enabled;
            updateFlags();
        }

        @Override
        public void onDisable() {
            noFogEnabled = false;
            updateFlags();
        }

        private void updateFlags() {
            noFogTerrain = enabled && terrain.value;
            noFogNether = enabled && nether.value;
            noFogWater = enabled && water.value;
            noFogLava = enabled && lava.value;
        }
    }

    
    private static class Fullbright extends Module {
        Fullbright() {
            super("fullbright", "Fullbright", "Permanente Helligkeit (Vanilla-Nachtsicht-Effekt).", Category.VISUAL);
        }

        @Override
        public void onTick() {
            if (mc.player != null && !mc.player.hasEffect(MobEffects.NIGHT_VISION)) {
                mc.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 999999, 0, false, false, false));
            }
        }

        @Override
        public void onDisable() {
            if (mc.player != null) mc.player.removeEffect(MobEffects.NIGHT_VISION);
        }
    }

    
    private static class ReducedParticles extends Module {
        private ParticleStatus saved = ParticleStatus.ALL;
        private final ModeSetting mode = new ModeSetting("Modus",
                "An = alle Partikel, Reduziert = Minimum, Aus = keine Partikel.",
                new String[]{"An", "Reduziert", "Aus"}, 1, v -> { if (enabled) apply(); });

        ReducedParticles() {
            super("reducedparticles", "Partikel", "Steuert die Sichtbarkeit von Partikeln.", Category.VISUAL);
            add(mode);
        }

        @Override
        public void onEnable() {
            if (mc.options != null) saved = mc.options.particles().get();
            apply();
        }

        @Override
        public void onDisable() {
            Visual.particleCancelAll = false;
            if (mc.options != null) mc.options.particles().set(saved);
        }

        private void apply() {
            if (mc.options == null) return;
            switch (mode.index) {
                case 0 -> { mc.options.particles().set(ParticleStatus.ALL); Visual.particleCancelAll = false; }
                case 1 -> { mc.options.particles().set(ParticleStatus.MINIMAL); Visual.particleCancelAll = false; }
                default -> { mc.options.particles().set(ParticleStatus.MINIMAL); Visual.particleCancelAll = true; }
            }
        }
    }

    
    private static class Zoom extends Module {
        private final SliderSetting zoom = new SliderSetting("Zoom", "Zoomstärke (1 = kein Zoom, höher = stärker heran).", 4.0, 1.0, 16.0, 0.5);
        private final KeybindSetting key = new KeybindSetting("Taste", "Schaltet den Zoom ein/aus.");
        private final BooleanSetting smoothCamera = new BooleanSetting("Weiche Kamera", "Kamerabewegung verlangsamen / glätten (Cinematic).", false);

        Zoom() {
            super("zoom", "Zoom", "Zoomt heran ohne Maus-Verzögerung.", Category.VISUAL);
            add(zoom);
            add(smoothCamera);
            add(key);
        }

        @Override
        public void onKey() {
            enabled = !enabled;
            if (enabled) onEnable();
            else onDisable();
        }

        @Override
        public void onEnable() {
            Visual.zoomActive = true;
            Visual.zoomSmoothCamera = smoothCamera.value;
            applyZoom();
        }

        @Override
        public void onTick() {
            Visual.zoomActive = enabled;
            Visual.zoomSmoothCamera = smoothCamera.value;
            applyZoom();
        }

        private void applyZoom() {
            Visual.zoomFovDivisor = zoom.value > 1.001 ? (float) zoom.value : 1.0f;
        }

        @Override
        public void onDisable() {
            Visual.zoomActive = false;
            Visual.zoomSmoothCamera = false;
            Visual.zoomFovDivisor = 1.0f;
        }
    }

    public static class SpectatorHighlighting extends Module {
        SpectatorHighlighting() {
            super("spectatorhighlight", "Zuschauer-Leuchten", "Hebt alle Spieler im Zuschauermodus mit leuchtenden Konturen hervor.", Category.VISUAL);
            this.enabled = true;
        }

        @Override
        public void onEnable() {
            spectatorHighlightEnabled = true;
        }

        @Override
        public void onTick() {
            spectatorHighlightEnabled = enabled;
        }

        @Override
        public void onDisable() {
            spectatorHighlightEnabled = false;
        }
    }

    public static class SmoothSwapping extends Module {
        SmoothSwapping() {
            super("smoothswapping", "Flüssiger Item-Wechsel", "Weiche Animation beim Wechseln von Items in den Händen.", Category.VISUAL);
            this.enabled = true;
        }

        @Override
        public void onEnable() {
            smoothSwappingEnabled = true;
        }

        @Override
        public void onTick() {
            smoothSwappingEnabled = enabled;
        }

        @Override
        public void onDisable() {
            smoothSwappingEnabled = false;
        }
    }

    public static class FightCamera extends Module {
        private final SliderSetting intensity = new SliderSetting("Schadens-Wackeln", "Stärke des Kamerawackelns bei Treffern (0% = Aus).", 1.0, 0.0, 2.0, 0.1);

        FightCamera() {
            super("fightcamera", "Kampf-Kamera", "Passt die Intensität des Kamerawackelns bei erlittenem Schaden an.", Category.VISUAL);
            this.enabled = true;
            add(intensity);
        }

        @Override
        public void onEnable() {
            fightCameraIntensity = (float) intensity.value;
        }

        @Override
        public void onTick() {
            fightCameraIntensity = enabled ? (float) intensity.value : 1.0f;
        }

        @Override
        public void onDisable() {
            fightCameraIntensity = 1.0f;
        }
    }

    public static class AfkCinematic extends Module {
        public static net.minecraft.client.CameraType savedCamera = null;
        private final SliderSetting delay = new SliderSetting("AFK-Zeit (s)", "Wartezeit bis zum Start.", 20.0, 5.0, 120.0, 5.0);
        private final SliderSetting speed = new SliderSetting("Rotations-Tempo", "Drehgeschwindigkeit der Kamera.", 0.5, 0.1, 2.0, 0.1);

        AfkCinematic() {
            super("afkcinematic", "AFK-Kamera", "Weiche rotierende Drittpersonen-Kamera nach Inaktivität.", Category.VISUAL);
            this.enabled = true;
            add(delay);
            add(speed);
        }

        @Override
        public void onTick() {
            if (!enabled || mc.player == null) {
                if (afkCinematicActive) resetAfkTimer();
                return;
            }

            long idle = System.currentTimeMillis() - lastInputTime;
            long threshold = (long) (delay.value * 1000L);
            if (idle > threshold) {
                if (!afkCinematicActive) {
                    savedCamera = mc.options.getCameraType();
                    mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
                    afkCinematicActive = true;
                }
                mc.player.setYRot(mc.player.getYRot() + (float) speed.value);
            }
        }

        @Override
        public void onDisable() {
            resetAfkTimer();
        }
    }

}
