package dev.kollegen.client.mods.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/** Geteilter Zustand für FreeCam (von Modul + Tick-Ende-Hook genutzt). */
public final class FreeCamState {
    public static boolean active = false;
    public static double x, y, z;

    private FreeCamState() {
    }

    /** Hart am Tick-Ende anwenden, damit die normale Bewegung nicht überschreibt. */
    public static void apply() {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        LocalPlayer p = mc.player;
        p.setDeltaMovement(0, 0, 0);
        p.setPos(x, y, z);
    }
}
