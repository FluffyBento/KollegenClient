package dev.kollegen.client.mods;

import dev.kollegen.client.ui.Glass;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2fStack;

public abstract class HudModule extends Module {
    protected final ModeSetting position = new ModeSetting("Position", "", new String[]{"Oben links", "Oben rechts", "Unten links", "Unten rechts"}, 0);
    protected final ColorSetting color = new ColorSetting("Farbe", "", Palette.TEXT);
    protected final BooleanSetting background = new BooleanSetting("Hintergrund", "Zeichnet einen Glas-Hintergrund hinter dem Text.", true);
    protected final ColorSetting backgroundColor = new ColorSetting("Hintergrundfarbe", "", Palette.tint(Palette.PANEL, 204));
    protected final SliderSetting glassOpacity = new SliderSetting("Deckkraft", "Wie durchsichtig das Glas ist.", 0.55, 0.0, 1.0, 0.05, v -> Math.round(v * 100.0) + "%", null);
    protected final SliderSetting scale = new SliderSetting("Skalierung", "Größe des Elements.", 1.0, 0.5, 2.5, 0.05, v -> Math.round(v * 100.0) + "%", null);
    protected final BooleanSetting accentBar = new BooleanSetting("Akzentleiste", "Farbige Leiste am linken Rand.", true);
    public final SliderSetting offsetX = new SliderSetting("X-Versatz", "Verschiebt das HUD horizontal.", 0.0, -2000.0, 2000.0, 1.0);
    public final SliderSetting offsetY = new SliderSetting("Y-Versatz", "Verschiebt das HUD vertikal.", 0.0, -2000.0, 2000.0, 1.0);
    public final BooleanSetting move = new BooleanSetting("Verschieben", "Mod mit der Maus ziehen (linksklick halten).", false);
    public int lastX;
    public int lastY;
    public int lastW;
    public int lastH;
    public static HudModule dragging = null;
    public static int dragOffX = 0;
    public static int dragOffY = 0;
    public static double cursorX = 0.0;
    public static double cursorY = 0.0;
    public static boolean editMode = false;

    protected HudModule(String id, String name, String description) {
        super(id, name, description, Category.HUD);
        this.add(this.position);
        this.add(this.color);
        this.add(this.background);
        this.add(this.backgroundColor);
        this.add(this.glassOpacity);
        this.add(this.scale);
        this.add(this.accentBar);
        this.add(this.offsetX);
        this.add(this.offsetY);
        this.add(this.move);
    }

    protected int[] anchor(int screenW, int screenH, int panelW, int panelH) {
        int m = 6;
        int ax;
        int ay;
        switch (this.position.index) {
            case 1:
                ax = screenW - panelW - m;
                ay = m;
                break;
            case 2:
                ax = m;
                ay = screenH - panelH - m - 48;
                break;
            case 3:
                ax = screenW - panelW - m;
                ay = screenH - panelH - m - 48;
                break;
            default:
                ax = m;
                ay = m;
        }
        return new int[]{ax + (int) this.offsetX.value, ay + (int) this.offsetY.value};
    }

    protected void markBounds(int x, int y, int w, int h) {
        this.lastX = x;
        this.lastY = y;
        this.lastW = w;
        this.lastH = h;
    }

    public static HudModule moduleAt(double x, double y) {
        for (Module m : ModuleManager.modules()) {
            if (!(m instanceof HudModule)) {
                continue;
            }
            HudModule hm = (HudModule) m;
            if (!hm.enabled || (!hm.move.value && !editMode) || x < hm.lastX || x > hm.lastX + hm.lastW || y < hm.lastY || y > hm.lastY + hm.lastH) {
                continue;
            }
            return hm;
        }
        return null;
    }

    public static void autoArrange() {
        for (int corner = 0; corner < 4; ++corner) {
            ArrayList<HudModule> list = new ArrayList<HudModule>();
            for (Module m : ModuleManager.modules()) {
                if (!(m instanceof HudModule)) {
                    continue;
                }
                HudModule hm = (HudModule) m;
                if (!hm.enabled || hm.position.index != corner) {
                    continue;
                }
                list.add(hm);
            }
            list.sort((a, b) -> Integer.compare(a.lastY, b.lastY));
            int gap = 8;
            int total = 0;
            for (HudModule hudModule : list) {
                total += Math.max(20, hudModule.lastH) + gap;
            }
            total = total > 0 ? total - gap : 0;
            int y = corner == 2 || corner == 3 ? -total : 0;
            for (HudModule hudModule : list) {
                hudModule.offsetX.value = 0.0;
                hudModule.offsetY.value = y;
                y += Math.max(20, hudModule.lastH) + gap;
            }
        }
        ModuleManager.save();
    }

    protected void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        if (this.background.value) {
            this.card(g, x - 3, y - 3, w + 6, h + 6);
        }
        this.markBounds(x - 3, y - 3, w + 6, h + 6);
    }

    protected void card(GuiGraphicsExtractor g, float x, float y, float w, float h) {
        int tint = 0xFF000000 | this.backgroundColor.value;
        float op = (float) this.glassOpacity.value;
        float r = Math.min(7.0f, h / 2.0f);
        Glass.glassCard(g, x, y, w, h, r, tint, op, 0.35f);
        if (this.accentBar.value) {
            float bh = Math.max(4.0f, h - 10.0f);
            Glass.rrGrad(g, x + 3.0f, y + (h - bh) / 2.0f, 2.0f, bh, 1.0f, Palette.ACCENT, Palette.ACCENT2);
        }
    }

    protected void text(GuiGraphicsExtractor g, String s, int x, int y) {
        Glass.text(g, this.mc.font, s, x, y, this.color.value, true);
    }

    protected int lineWidth(List<String> lines) {
        int w = 0;
        for (String l : lines) {
            w = Math.max(w, this.mc.font.width(l));
        }
        return w;
    }

    protected void renderLines(GuiGraphicsExtractor g, List<String> lines, int x, int y) {
        int n = lines.size();
        if (n == 0) {
            return;
        }
        String[] lab = new String[n];
        String[] val = new String[n];
        int lw = 0;
        int vw = 0;
        boolean anyLabel = false;
        for (int i = 0; i < n; i++) {
            String l = lines.get(i);
            int c = l.indexOf(": ");
            if (c > 0 && n > 0) {
                lab[i] = l.substring(0, c);
                val[i] = l.substring(c + 2);
                anyLabel = true;
                lw = Math.max(lw, this.mc.font.width(lab[i]));
            } else {
                lab[i] = null;
                val[i] = l;
            }
        }
        for (int i = 0; i < n; i++) {
            int w = this.mc.font.width(val[i]);
            if (lab[i] == null) {
                vw = Math.max(vw, Math.max(0, w - lw - (anyLabel ? 8 : 0)));
            } else {
                vw = Math.max(vw, w);
            }
        }
        int gap = anyLabel ? 8 : 0;
        int padL = this.accentBar.value ? 10 : 7;
        int padR = 7;
        int padV = 5;
        int lineH = 12;
        int w = padL + padR + lw + gap + vw;
        int h = padV * 2 + n * lineH - 3;
        float s = (float) this.scale.value;
        int sw = Math.round(w * s);
        int sh = Math.round(h * s);
        int[] a = this.anchor(this.mc.getWindow().getGuiScaledWidth(), this.mc.getWindow().getGuiScaledHeight(), sw, sh);
        this.markBounds(a[0], a[1], sw, sh);
        Matrix3x2fStack p = g.pose();
        p.pushMatrix();
        p.translate((float) a[0], (float) a[1]);
        p.scale(s, s);
        if (this.background.value) {
            this.card(g, 0.0f, 0.0f, w, h);
        }
        int yy = padV;
        for (int i = 0; i < n; i++) {
            if (lab[i] != null) {
                Glass.text(g, this.mc.font, lab[i], padL, yy, Palette.MUTED, true);
                Glass.text(g, this.mc.font, val[i], padL + lw + gap, yy, this.color.value, true);
            } else {
                Glass.text(g, this.mc.font, val[i], padL, yy, this.color.value, true);
            }
            yy += lineH;
        }
        p.popMatrix();
    }
}
