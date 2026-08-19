package dev.kollegen.client.feature;

import dev.kollegen.client.KollegenMod;
import net.minecraft.client.Minecraft;

/**
 * Einfacher Fullbright-Toggle: setzt die Gamma-Option auf einen hohen Wert,
 * sodass auch dunkle Bereiche (Höhlen) voll ausgeleuchtet sind. Beim
 * Deaktivieren wird der zuvor gespeicherte Gamma-Wert wiederhergestellt.
 */
public class Fullbright {
    private static boolean applied = false;
    private static double savedGamma = -1.0;

    private static double getGamma() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return 0.5;
        return mc.options.getGamma().getValue();
    }

    private static void setGamma(double v) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return;
        mc.options.getGamma().setValue(v);
    }

    /** Gleicht den angewandten Zustand mit der Config-Einstellung ab. */
    public static void reconcile() {
        boolean want = KollegenMod.CONFIG.fullbright;
        if (want && !applied) {
            savedGamma = getGamma();
            setGamma(100.0);
            applied = true;
        } else if (!want && applied) {
            setGamma(savedGamma > 0.0 ? savedGamma : 0.5);
            savedGamma = -1.0;
            applied = false;
        }
    }
}
