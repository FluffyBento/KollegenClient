package dev.kollegen.client.ui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kollegen.client.mods.Palette;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.nio.file.Files;
import java.nio.file.Path;

public final class UiStyleManager {

    private static UiStyle globalStyle = UiStyle.LIQUID_GLASS;
    private static boolean loaded = false;

    private UiStyleManager() {
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("kollegen-uistyle.json");
    }

    public static UiStyle getGlobalStyle() {
        ensureLoaded();
        return globalStyle;
    }

    public static void setGlobalStyle(UiStyle style) {
        if (style == null) return;
        globalStyle = style;
        save();
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try {
            Path p = configPath();
            if (!Files.exists(p)) return;
            JsonObject o = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
            if (o.has("style")) {
                globalStyle = UiStyle.valueOf(o.get("style").getAsString());
            }
        } catch (Throwable ignored) {
        }
    }

    private static void save() {
        try {
            Path p = configPath();
            JsonObject o = new JsonObject();
            o.addProperty("style", globalStyle.name());
            if (p.getParent() != null) Files.createDirectories(p.getParent());
            Files.writeString(p, o.toString());
        } catch (Throwable ignored) {
        }
    }

    public static void drawPanel(GuiGraphicsExtractor g, float x, float y, float w, float h, int bgTint) {
        drawPanel(g, x, y, w, h, bgTint, getGlobalStyle());
    }

    public static void drawPanel(GuiGraphicsExtractor g, float x, float y, float w, float h, int bgTint, UiStyle style) {
        if (style == null) style = getGlobalStyle();
        switch (style) {
            case MINECRAFT_VANILLA -> {
                int ix = (int) x;
                int iy = (int) y;
                int iw = (int) w;
                int ih = (int) h;
                g.fill(ix, iy, ix + iw, iy + ih, 0xF0101010);
                g.fill(ix, iy, ix + iw, iy + 2, 0xFF373737);
                g.fill(ix, iy, ix + 2, iy + ih, 0xFF373737);
                g.fill(ix, iy + ih - 2, ix + iw, iy + ih, 0xFF000000);
                g.fill(ix + iw - 2, iy, ix + iw, iy + ih, 0xFF000000);
            }
            case MODERN_FLAT -> {
                Glass.fillRound(g, (int) x, (int) y, (int) w, (int) h, 3, 0xF218181B);
                Glass.ring(g, x, y, w, h, 3, 1.0f, 0x33FFFFFF);
            }
            case NEON_CYBER -> {
                Glass.fillRound(g, (int) x, (int) y, (int) w, (int) h, 2, 0xF60A0A0C);
                Glass.ring(g, x, y, w, h, 2, 1.5f, Glass.a(Palette.ACCENT, 0.9f));
                Glass.shadowSoft(g, x, y, w, h, 2, 12.0f, 2.0f, Glass.a(Palette.ACCENT, 0.25f));
            }
            default -> {
                Glass.dropShadow(g, (int) x, (int) y, (int) w, (int) h, 16, 6, 12);
                Glass.panelDark(g, (int) x, (int) y, (int) w, (int) h, 16);
            }
        }
    }

    public static void drawCard(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, int tint, float hover, UiStyle style) {
        if (style == null) style = getGlobalStyle();
        switch (style) {
            case MINECRAFT_VANILLA -> {
                int ix = (int) x;
                int iy = (int) y;
                int iw = (int) w;
                int ih = (int) h;
                int base = hover > 0.05f ? 0xFF222222 : 0xFF181818;
                g.fill(ix, iy, ix + iw, iy + ih, base);
                int border = hover > 0.05f ? Palette.ACCENT : 0xFF3A3A3A;
                g.fill(ix, iy, ix + iw, iy + 1, border);
                g.fill(ix, iy, ix + 1, iy + ih, border);
                g.fill(ix, iy + ih - 1, ix + iw, iy + ih, 0xFF000000);
                g.fill(ix + iw - 1, iy, ix + iw, iy + ih, 0xFF000000);
            }
            case MODERN_FLAT -> {
                int base = hover > 0.05f ? Glass.mix(0xFF222226, Palette.ACCENT, 0.08f) : 0xFF1C1C20;
                Glass.fillRound(g, (int) x, (int) y, (int) w, (int) h, 3, base);
                int border = hover > 0.05f ? Palette.ACCENT : 0x2AFFFFFF;
                Glass.ring(g, x, y, w, h, 3, 1.0f, border);
            }
            case NEON_CYBER -> {
                int base = hover > 0.05f ? 0xFF121216 : 0xFF0B0B0E;
                Glass.fillRound(g, (int) x, (int) y, (int) w, (int) h, 2, base);
                int border = hover > 0.05f ? Palette.ACCENT : Glass.a(Palette.ACCENT, 0.35f);
                Glass.ring(g, x, y, w, h, 2, hover > 0.05f ? 1.5f : 1.0f, border);
                if (hover > 0.05f) {
                    Glass.shadowSoft(g, x, y, w, h, 2, 8.0f, 1.0f, Glass.a(Palette.ACCENT, 0.3f));
                }
            }
            default -> {
                int border = hover > 0.01f ? Glass.a(0xFFFFFFFF, 0.35f * hover) : 0x38FFFFFF;
                Glass.glassCard(g, x, y, w, h, r, Glass.mix(tint, Palette.ACCENT, 0.05f * hover),
                        Glass.opacity * (0.85f + 0.25f * hover), 0.0f, border);
            }
        }
    }

    public static void drawHudCard(GuiGraphicsExtractor g, float x, float y, float w, float h, int tint, float op, boolean accentBar, UiStyle style) {
        if (style == null) style = getGlobalStyle();
        switch (style) {
            case MINECRAFT_VANILLA -> {
                int ix = (int) x;
                int iy = (int) y;
                int iw = (int) w;
                int ih = (int) h;
                int fill = Glass.tint(0xFF101010, (int) (255 * Math.min(1.0f, op)));
                g.fill(ix, iy, ix + iw, iy + ih, fill);
                g.fill(ix, iy, ix + iw, iy + 1, 0xFF4A4A4A);
                g.fill(ix, iy, ix + 1, iy + ih, 0xFF4A4A4A);
                g.fill(ix, iy + ih - 1, ix + iw, iy + ih, 0xFF000000);
                g.fill(ix + iw - 1, iy, ix + iw, iy + ih, 0xFF000000);
            }
            case MODERN_FLAT -> {
                int fill = Glass.tint(0xFF141416, (int) (255 * Math.min(1.0f, op)));
                Glass.fillRound(g, (int) x, (int) y, (int) w, (int) h, 2, fill);
                Glass.ring(g, x, y, w, h, 2, 1.0f, 0x30FFFFFF);
                if (accentBar) {
                    g.fill((int) x + 2, (int) y + 2, (int) x + 4, (int) y + (int) h - 2, Palette.ACCENT);
                }
            }
            case NEON_CYBER -> {
                int fill = Glass.tint(0xFF070709, (int) (255 * Math.min(1.0f, op)));
                Glass.fillRound(g, (int) x, (int) y, (int) w, (int) h, 1, fill);
                Glass.ring(g, x, y, w, h, 1, 1.2f, Glass.a(Palette.ACCENT, 0.85f));
                if (accentBar) {
                    g.fill((int) x + 2, (int) y + 2, (int) x + 4, (int) y + (int) h - 2, Palette.ACCENT);
                }
            }
            default -> {
                float r = Math.min(7.0f, h / 2.0f);
                Glass.rr(g, x, y, w, h, r, Glass.tint(tint, (int) (255 * Math.min(1.0f, op))));
                if (accentBar) {
                    float bh = Math.max(4.0f, h - 10.0f);
                    Glass.rrGrad(g, x + 3.0f, y + (h - bh) / 2.0f, 2.0f, bh, 1.0f, Palette.ACCENT, Palette.ACCENT2);
                }
            }
        }
    }
}
