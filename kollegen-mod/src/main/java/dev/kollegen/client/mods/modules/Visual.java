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

    private Visual() {
    }

    public static void register() {
        ModuleManager.register(new Fullbright());
        ModuleManager.register(new NightVision());
        ModuleManager.register(new ReducedParticles());
        ModuleManager.register(new Zoom());
    }

    
    private static class Fullbright extends Module {
        private double saved = -1;

        Fullbright() {
            super("fullbright", "Fullbright", "Leuchtet die Welt vollständig aus (Gamma-Maximum).", Category.VISUAL);
        }

        @Override
        public void onEnable() {
            if (mc.options != null) {
                saved = mc.options.gamma().get();
                mc.options.gamma().set(100.0);
            }
        }

        @Override
        public void onDisable() {
            if (mc.options != null) mc.options.gamma().set(saved > 0 ? saved : 0.5);
        }
    }

    
    private static class NightVision extends Module {
        NightVision() {
            super("nightvision", "Night Vision", "Permanente Nachtsicht (Vanilla-Effekt).", Category.VISUAL);
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
        private final SliderSetting zoom = new SliderSetting("Zoom", "Zoomstärke (1 = kein Zoom, höher = stärker heran). Wirkt sofort.", 4.0, 1.0, 16.0, 0.5);
        private final KeybindSetting key = new KeybindSetting("Taste", "Schaltet den Zoom ein/aus.");
        private boolean savedSmooth = false;

        Zoom() {
            super("zoom", "Zoom", "Zoomt heran (mit weicher Kamera), ohne die eingestellte FOV zu verändern.", Category.VISUAL);
            add(zoom);
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
            if (mc.options != null) {
                savedSmooth = mc.options.smoothCamera;
                mc.options.smoothCamera = true;
            }
            applyZoom();
        }

        @Override
        public void onTick() {
            applyZoom();
        }

        private void applyZoom() {
            Visual.zoomFovDivisor = zoom.value > 1.001 ? (float) (1.0 / zoom.value) : 1.0f;
        }

        @Override
        public void onDisable() {
            Visual.zoomFovDivisor = 1.0f;
            if (mc.options != null) mc.options.smoothCamera = savedSmooth;
        }
    }

}
