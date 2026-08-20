package dev.kollegen.client.render;

import dev.kollegen.client.KollegenMod;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Farb-PostFX: Sättigung + gezielte Farb-Hervorhebung als Vollbild-Shader.
 *
 * Lädt die {@code PostChain} "kollegen:saturize" (siehe
 * `assets/kollegen/shaders/post/saturize.json` + Program-Dateien) über
 * Reflection, damit der Mod auch dann kompiliert/läuft, wenn sich die
 * PostChain-API zwischen MC-Versionen ändert. Jeder Fehler wird abgefangen und
 * nur geloggt – das Spiel läuft in dem Fall schlicht ohne Farb-FX weiter.
 */
public final class KollegenPostFX {
    private static Object chain = null;
    private static int lastW = -1;
    private static int lastH = -1;
    private static boolean applied = false;

    private KollegenPostFX() {
    }

    /** Wird nach Config-Änderungen aufgerufen → Chain wird neu geladen. */
    public static void applyConfig() {
        applied = false;
    }

    /** Einmal pro Client-Tick aufrufen (aus {@code KollegenMod.onTick}). */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        try {
            boolean active = KollegenMod.CONFIG.colorFxActive();
            if (!active) {
                close();
                return;
            }
            if (chain == null || !applied) {
                rebuild(mc);
            }
            if (chain == null) return;

            int w = mc.getWindow().getWidth();
            int h = mc.getWindow().getHeight();
            if (w != lastW || h != lastH) {
                resize(w, h);
            }

            setUniform("Saturation", KollegenMod.CONFIG.colorSaturation);
            float[] c = hexToRgbFloat(KollegenMod.CONFIG.highlightColor);
            setUniform3("HighlightColor", c[0], c[1], c[2]);
            setUniform("HighlightAmount",
                    KollegenMod.CONFIG.colorHighlight ? KollegenMod.CONFIG.highlightAmount : 0.0f);

            process(mc);
        } catch (Throwable t) {
            close();
        }
    }

    private static void rebuild(Minecraft mc) {
        close();
        try {
            Class<?> pc = Class.forName("net.minecraft.client.renderer.PostChain");
            Identifier id = Identifier.fromNamespaceAndPath("kollegen", "saturize");
            Object inst = pc.getConstructor(Identifier.class).newInstance(id);
            chain = inst;
            applied = true;
            lastW = lastH = -1;
        } catch (Throwable t) {
            KollegenMod.LOGGER.warn("Farb-PostFX konnte nicht geladen werden: {}", safeToString(t));
            chain = null;
        }
    }

    private static void resize(int w, int h) {
        try {
            chain.getClass().getMethod("resize", int.class, int.class).invoke(chain, w, h);
            lastW = w;
            lastH = h;
        } catch (Throwable t) {
            close();
        }
    }

    private static void setUniform(String name, float value) {
        try {
            chain.getClass().getMethod("setUniform", String.class, float.class).invoke(chain, name, value);
        } catch (Throwable ignored) {
        }
    }

    private static void setUniform3(String name, float a, float b, float c) {
        try {
            chain.getClass()
                    .getMethod("setUniform", String.class, float.class, float.class, float.class)
                    .invoke(chain, name, a, b, c);
        } catch (Throwable ignored) {
        }
    }

    private static void process(Minecraft mc) {
        try {
            chain.getClass().getMethod("process", float.class).invoke(chain, 0.0f);
        } catch (Throwable t) {
            close();
        }
    }

    private static void close() {
        if (chain != null) {
            try {
                chain.getClass().getMethod("close").invoke(chain);
            } catch (Throwable ignored) {
            }
            chain = null;
        }
        applied = false;
        lastW = lastH = -1;
    }

    private static float[] hexToRgbFloat(String hex) {
        int[] r = intToRgb(hex);
        return new float[]{r[0] / 255f, r[1] / 255f, r[2] / 255f};
    }

    private static int[] intToRgb(String hex) {
        int[] out = new int[]{255, 87, 34};
        try {
            String h = hex.startsWith("#") ? hex.substring(1) : hex;
            if (h.length() == 6) {
                out[0] = Integer.parseInt(h.substring(0, 2), 16);
                out[1] = Integer.parseInt(h.substring(2, 4), 16);
                out[2] = Integer.parseInt(h.substring(4, 6), 16);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static String safeToString(Throwable t) {
        String s = t.toString();
        return s.length() > 140 ? s.substring(0, 140) : s;
    }
}