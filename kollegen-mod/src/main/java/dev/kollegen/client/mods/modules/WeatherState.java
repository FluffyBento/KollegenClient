package dev.kollegen.client.mods.modules;

/** Gewählter Wetter-Modus (von Modul + Biome-Mixin genutzt). */
public final class WeatherState {
    /** -1 = aus; 0 Klar, 1 Regen, 2 Gewitter, 3 Schnee, 4 Nebel, 5 End-Blitz, 6 Basalt-Delta. */
    public static int mode = -1;

    private WeatherState() {
    }
}
