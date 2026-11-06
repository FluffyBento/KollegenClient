package dev.kollegen.client.mods.modules;

import dev.kollegen.client.emotes.EmoteManager;
import dev.kollegen.client.menu.EmoteWheelScreen;
import dev.kollegen.client.mods.BooleanSetting;
import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.KeybindSetting;
import dev.kollegen.client.mods.Module;
import net.minecraft.client.Minecraft;

public class Emotes extends Module {

    public static Emotes INSTANCE;

    public final KeybindSetting hotkey = new KeybindSetting("Rad-Hotkey", "Taste zum Öffnen des Emote-Rads.");
    public final BooleanSetting autoThirdPerson = new BooleanSetting("Auto 3rd-Person", "Wechselt beim Emoten automatisch in die 3rd-Person Perspektive.", true);

    public Emotes() {
        super("emotes", "Emotes", "Erlaubt das Abspielen und Verwalten von Emotes mit Emote-Rad.", Category.EMOTES);
        INSTANCE = this;
        this.hotkey.value = 66;
        this.add(this.hotkey);
        this.add(this.autoThirdPerson);
        this.enabled = true;
    }

    public void onKey() {
        if (!this.enabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.gui == null) return;
        if (mc.gui.screen() instanceof EmoteWheelScreen) {
            mc.setScreenAndShow(null);
            return;
        }
        if (mc.gui.screen() == null) {
            EmoteManager.autoThirdPerson = this.autoThirdPerson.value;
            mc.setScreenAndShow(new EmoteWheelScreen());
        }
    }

    public static void openWheel() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        if (INSTANCE != null) {
            EmoteManager.autoThirdPerson = INSTANCE.autoThirdPerson.value;
        }
        mc.setScreenAndShow(new EmoteWheelScreen());
    }
}
