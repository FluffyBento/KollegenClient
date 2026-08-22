package dev.kollegen.client.mods.modules;

import dev.kollegen.client.mods.BooleanSetting;
import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.Module;
import dev.kollegen.client.mods.ModeSetting;
import dev.kollegen.client.mods.ModuleManager;
import dev.kollegen.client.mods.SliderSetting;
import net.minecraft.client.Minecraft;

public final class World {

    /** Vom SkyBodiesMixin gelesen: Sonne/Mond ausblenden. */
    public static boolean hideSun = false;
    public static boolean hideMoon = false;

    private World() {
    }

    public static void register() {
        ModuleManager.register(new TimeChanger());
        ModuleManager.register(new WeatherChanger());
        ModuleManager.register(new CelestialBodies());
    }

    private static class TimeChanger extends Module {
        private final SliderSetting time = new SliderSetting("Zeit", "Tageszeit (0–24000)", 6000, 0, 24000, 100);

        TimeChanger() {
            super("timechanger", "Time Changer", "Friert die Tageszeit auf einen Wert ein.", Category.WORLD);
            add(time);
        }

        @Override
        public void onTick() {
            if (mc.level != null) mc.level.getLevelData().setDayTime((long) time.value);
        }
    }

    private static class WeatherChanger extends Module {
        private final ModeSetting mode = new ModeSetting("Wetter", "",
                new String[]{"Klar", "Regen", "Gewitter", "Schnee", "Nebel", "End-Blitz", "Basalt-Delta"}, 0);

        WeatherChanger() {
            super("weather", "Wetter", "Stellt das Wetter manuell ein.", Category.WORLD);
            add(mode);
        }

        @Override
        public void onEnable() {
            apply();
        }

        @Override
        public void onTick() {
            apply();
        }

        private void apply() {
            WeatherState.mode = mode.index;
            try {
                if (mc.level instanceof net.minecraft.client.multiplayer.ClientLevel cl) {
                    switch (mode.index) {
                        case 1: cl.setRainLevel(1f); cl.setThunderLevel(0f); break;   // Regen
                        case 2: cl.setRainLevel(1f); cl.setThunderLevel(1f); break;   // Gewitter
                        case 3: cl.setRainLevel(1f); cl.setThunderLevel(0f); break;   // Schnee (Precipitation via Mixin)
                        case 4: cl.setRainLevel(0f); cl.setThunderLevel(0f); break;   // Nebel (Atmosphäre via Mixin)
                        case 5: cl.setRainLevel(0f); cl.setThunderLevel(0f); break;   // End-Blitz (Atmosphäre via Mixin)
                        case 6: cl.setRainLevel(0f); cl.setThunderLevel(0f); break;   // Basalt-Delta (Atmosphäre via Mixin)
                        default: cl.setRainLevel(0f); cl.setThunderLevel(0f);          // Klar
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        @Override
        public void onDisable() {
            WeatherState.mode = -1;
            try {
                if (mc.level instanceof net.minecraft.client.multiplayer.ClientLevel cl) {
                    cl.setRainLevel(0f);
                    cl.setThunderLevel(0f);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    /** Blendet Sonne und/oder Mond im Überworld-Himmel aus. */
    private static class CelestialBodies extends Module {
        private final BooleanSetting sun = new BooleanSetting("Sonne ausblenden", "Blendet die Sonne aus.", false);
        private final BooleanSetting moon = new BooleanSetting("Mond ausblenden", "Blendet den Mond aus.", false);

        CelestialBodies() {
            super("celestial", "Himmelskörper", "Blendet Sonne und/oder Mond aus.", Category.WORLD);
            add(sun);
            add(moon);
        }

        @Override
        public void onEnable() {
            World.hideSun = sun.value;
            World.hideMoon = moon.value;
        }

        @Override
        public void onTick() {
            World.hideSun = sun.value;
            World.hideMoon = moon.value;
        }

        @Override
        public void onDisable() {
            World.hideSun = false;
            World.hideMoon = false;
        }
    }
}
