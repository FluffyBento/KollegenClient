package dev.kollegen.client.mods.modules;

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
    public static float targetZoomFovDivisor = 1.0f;
    public static boolean noFogEnabled = false;
    public static boolean zoomActive = false;
    public static boolean zoomSmoothCamera = false;
    public static boolean zoomSmoothTransition = true;
    public static boolean zoomScrollEnabled = true;

    public static boolean isZoomActive() {
        return zoomActive;
    }

    public static boolean isZoomSmoothCamera() {
        return zoomActive && zoomSmoothCamera;
    }

    public static boolean isZoomScrollEnabled() {
        return zoomActive && zoomScrollEnabled;
    }

    public static void onZoomScroll(double amount) {
        if (Zoom.INSTANCE != null && zoomActive) {
            double cur = Zoom.INSTANCE.zoom.value;
            cur += amount * 0.5;
            cur = Math.max(1.5, Math.min(24.0, cur));
            Zoom.INSTANCE.zoom.value = cur;
            targetZoomFovDivisor = (float) cur;
        }
    }

    public static float getEffectiveFovDivisor(float delta) {
        if (!zoomSmoothTransition) {
            zoomFovDivisor = targetZoomFovDivisor;
            return zoomFovDivisor;
        }
        float speed = 12.0f;
        zoomFovDivisor += (targetZoomFovDivisor - zoomFovDivisor) * Math.min(1.0f, delta * speed);
        if (Math.abs(targetZoomFovDivisor - zoomFovDivisor) < 0.005f) {
            zoomFovDivisor = targetZoomFovDivisor;
        }
        return zoomFovDivisor;
    }

    private Visual() {
    }

    public static void register() {
        ModuleManager.register(new Fullbright());
        ModuleManager.register(new ReducedParticles());
        ModuleManager.register(new Zoom());
        ModuleManager.register(new Freecam());
        ModuleManager.register(new NoFog());
        ModuleManager.register(new ArmorTransparency());
        ModuleManager.register(new ShieldSettings());
        ModuleManager.register(new FovModifier());
        ModuleManager.register(new KollegenShader());
        ModuleManager.register(new WaveyCapes());
        ModuleManager.register(new LowFire());
        ModuleManager.register(new ItemPhysics());
        ModuleManager.register(new HitColor());
        ModuleManager.register(new HitboxSettings());
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

    private static class NoFog extends Module {
        NoFog() {
            super("nofog", "Kein Nebel", "Entfernt den atmosphärischen Nebel für freie Sicht.", Category.VISUAL);
        }

        @Override
        public void onEnable() {
            noFogEnabled = true;
        }

        @Override
        public void onDisable() {
            noFogEnabled = false;
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
        public static Zoom INSTANCE;
        private final SliderSetting zoom = new SliderSetting("Zoom", "Zoomstärke (1 = kein Zoom, höher = stärker heran).", 4.0, 1.0, 20.0, 0.5);
        private final KeybindSetting key = new KeybindSetting("Taste", "Taste zum Zoomen (Standard: C).", InputConstants.KEY_C);
        private final BooleanSetting holdKey = new BooleanSetting("Taste halten", "Zoom nur aktiv solange die Taste gedrückt gehalten wird.", true);
        private final BooleanSetting smoothTransition = new BooleanSetting("Weicher Übergang", "Sanfte Zoom-Animation statt harter Schnitt.", true);
        private final BooleanSetting scrollZoom = new BooleanSetting("Mausrad-Zoom", "Mit dem Scrollrad stufenlos rein- und rauszoomen während des Zooms.", true);
        private final BooleanSetting smoothCamera = new BooleanSetting("Cinematic Kamera", "Kamerabewegung verlangsamen / glätten.", false);

        Zoom() {
            super("zoom", "Zoom", "Zoomt heran ohne Maus-Verzögerung mit Animation & Scroll-Zoom.", Category.VISUAL);
            INSTANCE = this;
            add(zoom);
            add(holdKey);
            add(smoothTransition);
            add(scrollZoom);
            add(smoothCamera);
            add(key);
        }

        @Override
        public void onKey() {
            if (!this.enabled) return;
            if (holdKey.value) {
                Visual.zoomActive = true;
            } else {
                Visual.zoomActive = !Visual.zoomActive;
            }
            if (Visual.zoomActive) {
                Visual.targetZoomFovDivisor = zoom.value > 1.001 ? (float) zoom.value : 1.0f;
            } else {
                Visual.targetZoomFovDivisor = 1.0f;
            }
        }

        @Override
        public void onKeyRelease() {
            if (!this.enabled) return;
            if (holdKey.value) {
                Visual.zoomActive = false;
                Visual.targetZoomFovDivisor = 1.0f;
            }
        }

        @Override
        public void onEnable() {
            Visual.zoomActive = false;
            Visual.targetZoomFovDivisor = 1.0f;
            Visual.zoomSmoothCamera = smoothCamera.value;
            Visual.zoomSmoothTransition = smoothTransition.value;
            Visual.zoomScrollEnabled = scrollZoom.value;
        }

        @Override
        public void onTick() {
            if (!this.enabled) {
                Visual.zoomActive = false;
                Visual.targetZoomFovDivisor = 1.0f;
                return;
            }
            Visual.zoomSmoothCamera = smoothCamera.value;
            Visual.zoomSmoothTransition = smoothTransition.value;
            Visual.zoomScrollEnabled = scrollZoom.value;
            if (Visual.zoomActive) {
                Visual.targetZoomFovDivisor = zoom.value > 1.001 ? (float) zoom.value : 1.0f;
            } else {
                Visual.targetZoomFovDivisor = 1.0f;
            }
        }

        @Override
        public void onDisable() {
            Visual.zoomActive = false;
            Visual.zoomSmoothCamera = false;
            Visual.targetZoomFovDivisor = 1.0f;
            if (!smoothTransition.value) {
                Visual.zoomFovDivisor = 1.0f;
            }
        }

        @Override
        public void load(com.google.gson.JsonObject o) {
            super.load(o);
            if (key.value == 33 || key.value == 67) {
                key.value = InputConstants.KEY_C;
            }
        }
    }

    public static class LowFire extends Module {
        public static boolean enabled = true;
        public static float heightOffset = -0.38f;

        private final BooleanSetting active = new BooleanSetting("Niedriges Feuer (Low Fire)", "Senkt das Feuer-Overlay im Blickfeld ab für freie Sicht.", true);
        private final SliderSetting height = new SliderSetting("Absenkung", "Wie tief das Feuer im Blickfeld abgesenkt wird.", 0.38, 0.10, 0.70, 0.05,
                v -> Math.round(v * 100.0) + "%", null);

        public LowFire() {
            super("lowfire", "Low Fire", "Senkt das Sichtfeld-Feuer im First-Person-Modus (FOV) ab.", Category.VISUAL);
            this.enabled = true;
            add(active);
            add(height);
            updateOffset();
        }

        private void updateOffset() {
            double h = height.value;
            if (h <= 0.08) h = 0.38;
            heightOffset = -(float) Math.max(0.32, h);
        }

        @Override
        public void onEnable() {
            enabled = active.value;
            updateOffset();
        }

        @Override
        public void onTick() {
            enabled = this.enabled && active.value;
            updateOffset();
        }

        @Override
        public void onDisable() {
            enabled = false;
        }

        public static boolean isLowFireEnabled() {
            return enabled;
        }

        public static float getHeightOffset() {
            return heightOffset;
        }
    }

}
