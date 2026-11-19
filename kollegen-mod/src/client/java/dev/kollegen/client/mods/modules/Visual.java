package dev.kollegen.client.mods.modules;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
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
    public static float afkRotationSpeed = 0.6f;
    public static float afkCameraDistance = 4.0f;
    public static float afkCameraPitch = 12.0f;
    public static float afkCameraYaw = 0.0f;
    private static long lastAfkFrameNano = 0L;
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

    public static float getAfkCameraYaw() {
        return afkCameraYaw;
    }

    public static float getAfkCameraPitch() {
        return afkCameraPitch;
    }

    public static float getAfkCameraDistance() {
        return afkCameraDistance;
    }

    private static Zoom zoomModuleInstance = null;

    public static boolean onMouseScroll(double yoffset) {
        if (zoomModuleInstance != null && zoomModuleInstance.mouseScroll.value && zoomActive) {
            zoomModuleInstance.scrollOffset = (float) Math.max(-zoomModuleInstance.zoom.value + 1.1, Math.min(20.0, zoomModuleInstance.scrollOffset + yoffset * 0.5));
            return true;
        }
        return false;
    }

    public static void onFrame() {
        long now = System.nanoTime();
        if (lastAfkFrameNano == 0L) {
            lastAfkFrameNano = now;
            return;
        }
        float dt = (now - lastAfkFrameNano) / 1_000_000_000.0f;
        lastAfkFrameNano = now;
        if (dt > 0.1f) dt = 0.016f;

        if (afkCinematicActive) {
            afkCameraYaw += afkRotationSpeed * 25.0f * dt;
            if (afkCameraYaw >= 360.0f) afkCameraYaw -= 360.0f;
        }

        if (zoomModuleInstance != null) {
            zoomModuleInstance.updateFrame(dt);
        }
    }

    public static void resetAfkTimer() {
        lastInputTime = System.currentTimeMillis();
        afkCinematicActive = false;
    }

    private Visual() {
    }

    public static void register() {
        ModuleManager.register(new Fullbright());
        ModuleManager.register(new ReducedParticles());
        Zoom zoomMod = new Zoom();
        zoomModuleInstance = zoomMod;
        ModuleManager.register(zoomMod);
        ModuleManager.register(new Freecam());
        ModuleManager.register(new NoFog());
        ModuleManager.register(new ArmorTransparency());
        ModuleManager.register(new FovModifier());
        ModuleManager.register(new KollegenShader());
        ModuleManager.register(new WaveyCapes());
        ModuleManager.register(new ItemPhysics());
        ModuleManager.register(new SpectatorHighlighting());
        ModuleManager.register(new SmoothSwapping());
        ModuleManager.register(new FightCamera());
        ModuleManager.register(new AfkCinematic());
        ModuleManager.register(new LowFire());
        ModuleManager.register(new LowShield());
    }

    public static class WaveyCapes extends Module {
        public static WaveyCapes INSTANCE;
        public static boolean enabled = false;
        public static float waveStrength = 1.0f;

        private final BooleanSetting physics = new BooleanSetting("Wellen-Physik", "Aktiviert fließende Stoff-Simulation für Umhänge.", true);
        private final SliderSetting strength = new SliderSetting("Wellenstärke", "Intensität der Umhang-Wellenbewegung.", 1.0, 0.2, 3.0, 0.1);

        public WaveyCapes() {
            super("waveycapes", "Wavey Capes", "Physikalische Wellen- und Stoffbewegung für Umhänge.", Category.VISUAL);
            INSTANCE = this;
            enabled = false;
            add(physics);
            add(strength);
        }

        public static boolean isWavey() {
            return INSTANCE != null && INSTANCE.enabled && INSTANCE.physics.value;
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

    
    public static class Zoom extends Module {
        private final SliderSetting zoom = new SliderSetting("Zoom", "Zoomstärke (1 = kein Zoom, höher = stärker heran).", 4.0, 1.0, 16.0, 0.5);
        private final BooleanSetting hold = new BooleanSetting("Taste halten", "Zoom nur aktiv solange die Taste gedrückt gehalten wird.", true);
        private final BooleanSetting smoothTransition = new BooleanSetting("Weicher Übergang", "Sanftes Herein- und Herauszoomen.", true);
        private final BooleanSetting mouseScroll = new BooleanSetting("Mausrad-Zoom", "Zoomstufe mit dem Mausrad feinjustieren.", true);
        private final BooleanSetting smoothCamera = new BooleanSetting("Cinematic Kamera", "Kamerabewegung verlangsamen / glätten (Cinematic).", false);
        private final KeybindSetting key = new KeybindSetting("Taste", "Zoom-Taste.");

        private float currentDivisor = 1.0f;
        private float scrollOffset = 0.0f;
        private boolean wasKeyHeld = false;
        private boolean toggleState = false;

        Zoom() {
            super("zoom", "Zoom", "Zoomt heran ohne Maus-Verzögerung.", Category.VISUAL);
            key.value = InputConstants.KEY_C;
            add(zoom);
            add(hold);
            add(smoothTransition);
            add(mouseScroll);
            add(smoothCamera);
            add(key);
        }

        @Override
        public void onKey() {
            if (!hold.value) {
                toggleState = !toggleState;
            }
        }

        @Override
        public void onEnable() {
            toggleState = true;
        }

        @Override
        public void onDisable() {
            toggleState = false;
            scrollOffset = 0.0f;
        }

        @Override
        public void load(JsonObject o) {
            super.load(o);
            if (o.has("settings")) {
                JsonObject s = o.getAsJsonObject("settings");
                if (s.has("Weiche Kamera") && !s.has("Cinematic Kamera")) {
                    JsonObject wc = s.getAsJsonObject("Weiche Kamera");
                    if (wc.has("value")) {
                        smoothCamera.value = wc.get("value").getAsBoolean();
                    }
                }
            }
        }

        public void updateFrame(float dt) {
            Minecraft mc = Minecraft.getInstance();
            boolean screenOpen = mc != null && mc.gui != null && mc.gui.screen() != null;
            boolean keyHeld = false;
            if (!screenOpen && key.value > 0) {
                keyHeld = dev.kollegen.client.KollegenMod.isKeyDown(key.value);
                if (key.ctrl && !dev.kollegen.client.KollegenMod.isCtrlDown()) keyHeld = false;
                if (key.shift && !dev.kollegen.client.KollegenMod.isShiftDown()) keyHeld = false;
                if (key.alt && !dev.kollegen.client.KollegenMod.isAltDown()) keyHeld = false;
            }

            boolean shouldZoom;
            if (hold.value) {
                shouldZoom = keyHeld || (this.enabled && key.value <= 0);
            } else {
                if (keyHeld && !this.wasKeyHeld) {
                    this.toggleState = !this.toggleState;
                }
                shouldZoom = this.toggleState || this.enabled;
            }
            this.wasKeyHeld = keyHeld;

            float targetDivisor = 1.0f;
            if (shouldZoom) {
                targetDivisor = (float) (zoom.value + scrollOffset);
                if (targetDivisor < 1.0f) targetDivisor = 1.0f;
            } else {
                scrollOffset = 0.0f;
            }

            if (smoothTransition.value) {
                float speed = 24.0f;
                currentDivisor += (targetDivisor - currentDivisor) * Math.min(1.0f, dt * speed);
                if (Math.abs(currentDivisor - targetDivisor) < 0.01f) {
                    currentDivisor = targetDivisor;
                }
            } else {
                currentDivisor = targetDivisor;
            }

            Visual.zoomFovDivisor = currentDivisor;
            Visual.zoomActive = currentDivisor > 1.01f || targetDivisor > 1.01f;
            Visual.zoomSmoothCamera = shouldZoom && smoothCamera.value;
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
        private final SliderSetting delay = new SliderSetting("AFK-Zeit (s)", "Wartezeit bis zum Start.", 20.0, 5.0, 120.0, 5.0);
        private final SliderSetting speed = new SliderSetting("Rotations-Tempo", "Drehgeschwindigkeit der Kamera.", 0.6, 0.1, 2.0, 0.1);
        private final SliderSetting distance = new SliderSetting("Kamera-Abstand", "Distanz zum Spieler in Blöcken.", 4.0, 2.0, 8.0, 0.5);
        private final SliderSetting pitch = new SliderSetting("Kamera-Neigung", "Blickwinkel von oben herab.", 12.0, -20.0, 45.0, 1.0);

        AfkCinematic() {
            super("afkcinematic", "AFK-Kamera", "Weiche rotierende Drittpersonen-Kamera nach Inaktivität.", Category.VISUAL);
            this.enabled = true;
            add(delay);
            add(speed);
            add(distance);
            add(pitch);
        }

        @Override
        public void onTick() {
            if (!enabled || mc.player == null || (mc.gui != null && mc.gui.screen() != null)) {
                if (afkCinematicActive) resetAfkTimer();
                return;
            }

            afkRotationSpeed = (float) speed.value;
            afkCameraDistance = (float) distance.value;
            afkCameraPitch = (float) pitch.value;

            long idle = System.currentTimeMillis() - lastInputTime;
            long threshold = (long) (delay.value * 1000L);
            if (idle > threshold && !afkCinematicActive) {
                afkCinematicActive = true;
                if (mc.player != null) {
                    afkCameraYaw = mc.player.getYRot();
                }
            }
        }

        @Override
        public void onDisable() {
            resetAfkTimer();
        }
    }

    public static class LowFire extends Module {
        public static volatile LowFire instance;

        public static final SliderSetting height = new SliderSetting(
                "Feuer-Höhe",
                "Vertikale Position des Feuers (negativ = tiefer).",
                -0.35, -0.65, 0.0, 0.05,
                v -> String.format(java.util.Locale.ROOT, "%.2f", v), null
        );

        public LowFire() {
            super("lowfire", "Low Fire", "Senkt das Feuer auf dem Bildschirm ab für ein freies Sichtfeld im Kampf.", Category.VISUAL);
            this.enabled = true;
            instance = this;
            add(height);
        }

        public static boolean isLowFireEnabled() {
            return instance != null && instance.enabled;
        }

        public static float getHeight() {
            return instance != null ? (float) height.value : -0.35f;
        }
    }

    public static class LowShield extends Module {
        public static volatile LowShield instance;

        public static final SliderSetting height = new SliderSetting(
                "Schild-Höhe",
                "Senkt das Schild im First-Person-Sichtfeld nach unten ab.",
                -0.25, -0.60, 0.0, 0.05,
                v -> String.format(java.util.Locale.ROOT, "%.2f", v), null
        );

        public static final SliderSetting scale = new SliderSetting(
                "Schild-Größe",
                "Größe des Schilds in der Hand.",
                0.80, 0.40, 1.20, 0.05,
                v -> Math.round(v * 100.0) + "%", null
        );

        public LowShield() {
            super("lowshield", "Low Shield", "Senkt das gehaltene Schild im First-Person-Sichtfeld nach unten ab für freies Sichtfeld.", Category.VISUAL);
            this.enabled = true;
            instance = this;
            add(height);
            add(scale);
        }

        public static boolean isLowShieldEnabled() {
            return instance != null && instance.enabled;
        }

        public static float getHeight() {
            return instance != null ? (float) height.value : -0.25f;
        }

        public static float getScale() {
            return instance != null ? (float) scale.value : 0.8f;
        }
    }

}
