package dev.kollegen.client.mods.modules;

import dev.kollegen.client.mods.BooleanSetting;
import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.KeybindSetting;
import dev.kollegen.client.mods.Module;
import dev.kollegen.client.mods.ModuleManager;
import dev.kollegen.client.mods.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public final class Visual {

    private Visual() {
    }

    public static void register() {
        ModuleManager.register(new Fullbright());
        ModuleManager.register(new NightVision());
        ModuleManager.register(new ReducedParticles());
        ModuleManager.register(new Zoom());
    }

    /** Gamma auf Maximum setzen, damit alles ausgeleuchtet ist. */
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

    /** Respektiert die Sicht auch nachts, ohne den Gamme-Wert zu verändern. */
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

    /** Reduziert Partikel auf ein Minimum (mehr FPS, weniger Ablenkung). */
    private static class ReducedParticles extends Module {
        private ParticleStatus saved = ParticleStatus.ALL;

        ReducedParticles() {
            super("reducedparticles", "Reduzierte Partikel", "Schaltet Partikel auf Minimum.", Category.VISUAL);
        }

        @Override
        public void onEnable() {
            if (mc.options != null) {
                saved = mc.options.particles().get();
                mc.options.particles().set(ParticleStatus.MINIMAL);
            }
        }

        @Override
        public void onDisable() {
            if (mc.options != null) mc.options.particles().set(saved);
        }
    }

    /** Optifine-artiger Zoom über den FOV-Wert. */
    private static class Zoom extends Module {
        private final SliderSetting fov = new SliderSetting("FOV", "Sichtfeld beim Zoomen", 30, 10, 70, 1);
        private final KeybindSetting key = new KeybindSetting("Taste", "Schaltet den Zoom ein/aus.");
        private double saved = -1;

        Zoom() {
            super("zoom", "Zoom", "Verringert den FOV zum Heranzoomen.", Category.VISUAL);
            add(fov);
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
                saved = mc.options.fov().get();
                mc.options.fov().set((int) fov.value);
            }
        }

        @Override
        public void onDisable() {
            if (mc.options != null) mc.options.fov().set((int) (saved > 0 ? saved : 70));
        }
    }
}
