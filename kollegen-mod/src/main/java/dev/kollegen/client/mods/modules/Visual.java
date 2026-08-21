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
import net.minecraft.world.phys.Vec3;

public final class Visual {

    private Visual() {
    }

    public static void register() {
        ModuleManager.register(new Fullbright());
        ModuleManager.register(new NightVision());
        ModuleManager.register(new ReducedParticles());
        ModuleManager.register(new Zoom());
        ModuleManager.register(new FreeCam());
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

    /**
     * Freie Kamerasteuerung (Client-seitiges Fliegen ohne Physik/Gravitation).
     * Die Ziel-Position wird in {@link #onTick()} (Tick-Anfang) aus den
     * Tasten berechnet und am Tick-Ende via {@link #apply()} hart gesetzt,
     * damit die normale Spieler-Bewegung sie nicht überschreibt.
     */
    private static class FreeCam extends Module {
        private final KeybindSetting key = new KeybindSetting("Taste", "Aktiviert/deaktiviert FreeCam.");
        private final SliderSetting speed = new SliderSetting("Speed", "Bewegungsgeschwindigkeit (Blöcke/Tick).", 0.5, 0.1, 3.0, 0.1);

        FreeCam() {
            super("freecam", "FreeCam", "Freie Kamerasteuerung zum Fliegen – unabhängig von Physik.", Category.VISUAL);
            this.risk = "Kann auf Servern als Cheat/Unfair gelten – Vorsicht!";
            add(key);
            add(speed);
        }

        @Override
        public void onKey() {
            enabled = !enabled;
            FreeCamState.active = enabled;
            if (enabled) onEnable();
            else onDisable();
        }

        @Override
        public void onEnable() {
            if (mc.player != null) {
                FreeCamState.x = mc.player.getX();
                FreeCamState.y = mc.player.getY();
                FreeCamState.z = mc.player.getZ();
            }
        }

        @Override
        public void onTick() {
            if (!FreeCamState.active || mc.player == null) return;
            float f = (mc.options.keyUp.isDown() ? 1 : 0) - (mc.options.keyDown.isDown() ? 1 : 0);
            float s = (mc.options.keyRight.isDown() ? 1 : 0) - (mc.options.keyLeft.isDown() ? 1 : 0);
            Vec3 look = mc.player.getLookAngle();
            Vec3 right = new Vec3(look.z, 0, -look.x).normalize();
            double sp = speed.value;
            double vUp = (mc.options.keyJump.isDown() ? 1 : 0) - (mc.options.keyShift.isDown() ? 1 : 0);
            FreeCamState.x += (look.x * f + right.x * s) * sp;
            FreeCamState.y += vUp * sp;
            FreeCamState.z += (look.z * f + right.z * s) * sp;
        }

        @Override
        public void onDisable() {
            FreeCamState.active = false;
            if (mc.player != null) mc.player.setDeltaMovement(0, 0, 0);
        }
    }
}
