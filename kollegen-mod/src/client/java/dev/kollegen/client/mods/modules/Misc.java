package dev.kollegen.client.mods.modules;

import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.Module;
import dev.kollegen.client.mods.ModuleManager;
import dev.kollegen.client.mods.SliderSetting;

public final class Misc {

    public static SoundController SOUND_CONTROLLER;
    public static SmoothScroll SMOOTH_SCROLL;

    public static boolean isSoundControllerActive() {
        return SOUND_CONTROLLER != null && SOUND_CONTROLLER.enabled;
    }

    public static boolean shouldMuteSound(net.minecraft.client.resources.sounds.SoundInstance sound) {
        if (SOUND_CONTROLLER == null || !SOUND_CONTROLLER.enabled) return false;
        if (sound == null || sound.getIdentifier() == null) return false;
        String path = sound.getIdentifier().getPath().toLowerCase();
        if (path.contains("portal") && SOUND_CONTROLLER.portalVolume.value <= 0.01) return true;
        if ((path.contains("weather.rain") || path.contains("ambient.weather")) && SOUND_CONTROLLER.rainVolume.value <= 0.01) return true;
        if (path.contains("piston") && SOUND_CONTROLLER.pistonVolume.value <= 0.01) return true;
        if (path.contains("thunder") && SOUND_CONTROLLER.thunderVolume.value <= 0.01) return true;
        if (path.contains("step.") && SOUND_CONTROLLER.footstepsVolume.value <= 0.01) return true;
        if (path.contains("villager") && SOUND_CONTROLLER.villagerVolume.value <= 0.01) return true;
        return false;
    }

    private Misc() {
    }

    public static void register() {
        InventoryColor.register();
        ChatHeads.register();
        Appleskin.register();
        AutoRemoveMods.register();
        SOUND_CONTROLLER = new SoundController();
        SMOOTH_SCROLL = new SmoothScroll();
        ModuleManager.register(new ControllerSupport());
        ModuleManager.register(new Clips());
        ModuleManager.register(new Emotes());
        ModuleManager.register(SOUND_CONTROLLER);
        ModuleManager.register(SMOOTH_SCROLL);
    }

    public static class SoundController extends Module {
        public final SliderSetting rainVolume = new SliderSetting("Regen-Lautstärke", "", 1.0, 0.0, 1.5, 0.05);
        public final SliderSetting portalVolume = new SliderSetting("Portal-Lautstärke", "", 0.5, 0.0, 1.5, 0.05);
        public final SliderSetting pistonVolume = new SliderSetting("Piston-Lautstärke", "", 0.6, 0.0, 1.5, 0.05);
        public final SliderSetting thunderVolume = new SliderSetting("Donner-Lautstärke", "", 0.8, 0.0, 1.5, 0.05);
        public final SliderSetting footstepsVolume = new SliderSetting("Schritt-Lautstärke", "", 1.0, 0.0, 1.5, 0.05);
        public final SliderSetting villagerVolume = new SliderSetting("Dorfbewohner-Lautstärke", "", 0.4, 0.0, 1.5, 0.05);

        SoundController() {
            super("soundcontroller", "Sound-Controller", "Individuelle Lautstärkeregler für störende Spielgeräusche.", Category.MISC);
            add(rainVolume);
            add(portalVolume);
            add(pistonVolume);
            add(thunderVolume);
            add(footstepsVolume);
            add(villagerVolume);
        }
    }

    public static class SmoothScroll extends Module {
        public final SliderSetting speed = new SliderSetting("Scroll-Tempo", "", 1.2, 0.5, 3.0, 0.1);

        SmoothScroll() {
            super("smoothscroll", "Flüssiges Scrollen", "Weiches Scrollen im Chat, Menüs und Server-Listen.", Category.MISC);
            this.enabled = true;
            add(speed);
        }
    }

    private static class ControllerSupport extends Module {
        ControllerSupport() {
            super("controllersupport", "Controller-Support", "Aktiviert Gamepad-Steuerung und Menue-Navigation.", Category.MISC);
            this.enabled = dev.kollegen.client.input.ControllerMode.isActive();
        }

        @Override
        public void onEnable() {
            dev.kollegen.client.input.ControllerMode.setActive(true);
        }

        @Override
        public void onDisable() {
            dev.kollegen.client.input.ControllerMode.setActive(false);
        }
    }
}

