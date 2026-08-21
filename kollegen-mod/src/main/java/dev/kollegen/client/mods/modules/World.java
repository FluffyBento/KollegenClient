package dev.kollegen.client.mods.modules;

import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.Module;
import dev.kollegen.client.mods.ModuleManager;
import dev.kollegen.client.mods.SliderSetting;
import net.minecraft.client.Minecraft;

public final class World {

    private World() {
    }

    public static void register() {
        ModuleManager.register(new TimeChanger());
        ModuleManager.register(new NoWeather());
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

    private static class NoWeather extends Module {
        NoWeather() {
            super("noweather", "Kein Wetter", "Schaltet Regen/Gewitter optisch aus.", Category.WORLD);
        }

        @Override
        public void onTick() {
            try {
                if (mc.level instanceof net.minecraft.client.multiplayer.ClientLevel cl) {
                    cl.setRainLevel(0f);
                    cl.setThunderLevel(0f);
                }
            } catch (Throwable ignored) {
            }
        }
    }
}
