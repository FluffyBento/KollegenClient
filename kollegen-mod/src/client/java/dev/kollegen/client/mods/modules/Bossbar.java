package dev.kollegen.client.mods.modules;

import dev.kollegen.client.mods.BooleanSetting;
import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.Module;
import dev.kollegen.client.mods.SliderSetting;

public final class Bossbar extends Module {
    public static Bossbar INSTANCE;

    public final BooleanSetting hide = new BooleanSetting("Verstecken", "Blendet alle Bossbars komplett aus", false);
    public final SliderSetting yOffset = new SliderSetting("Y-Verschiebung", "Verschiebt die Bossbar vertikal", 0.0, -50.0, 200.0, 1.0);

    public Bossbar() {
        super("bossbar", "Bossbar", "Ermöglicht das Ausblenden oder Verschieben der Bossbar.", Category.HUD);
        INSTANCE = this;
        add(hide);
        add(yOffset);
        this.enabled = true;
    }
}
