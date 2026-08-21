package dev.kollegen.client.mods;

import dev.kollegen.client.mods.BooleanSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * Basis für HUD-Module: gemeinsame Position/Anker-Logik und Zeichen-Helfer.
 */
public abstract class HudModule extends Module {
    protected final ModeSetting position;
    protected final ColorSetting color;
    protected final BooleanSetting background;
    protected final ColorSetting backgroundColor;
    public final SliderSetting offsetX;
    public final SliderSetting offsetY;
    protected final BooleanSetting move;

    /** Zuletzt gezeichnete Bounds (für Drag-Hit-Test). */
    public int lastX, lastY, lastW, lastH;
    public static HudModule dragging = null;
    public static int dragOffX = 0, dragOffY = 0;
    /** Aktuelle Maus-Position in GUI-Skalierung (für Drag). */
    public static double cursorX = 0, cursorY = 0;
    /** Globaler Bearbeitungsmodus: alle HUD-Elemente werden verschiebbar. */
    public static boolean editMode = false;

    protected HudModule(String id, String name, String description) {
        super(id, name, description, Category.HUD);
        position = new ModeSetting("Position", "",
                new String[]{"Oben links", "Oben rechts", "Unten links", "Unten rechts"}, 0);
        color = new ColorSetting("Farbe", "", Palette.TEXT);
        background = new BooleanSetting("Hintergrund", "Zeichnet einen Hintergrund hinter dem Text.", true);
        backgroundColor = new ColorSetting("Hintergrundfarbe", "", Palette.tint(Palette.PANEL, 0xCC));
        offsetX = new SliderSetting("X-Versatz", "Verschiebt das HUD horizontal.", 0, -2000, 2000, 1);
        offsetY = new SliderSetting("Y-Versatz", "Verschiebt das HUD vertikal.", 0, -2000, 2000, 1);
        move = new BooleanSetting("Verschieben", "Mod mit der Maus ziehen (linksklick halten).", false);
        add(position);
        add(color);
        add(background);
        add(backgroundColor);
        add(offsetX);
        add(offsetY);
        add(move);
    }

    protected int[] anchor(int screenW, int screenH, int panelW, int panelH) {
        int m = 6;
        int[] a = switch (position.index) {
            case 1 -> new int[]{screenW - panelW - m, m};
            case 2 -> new int[]{m, screenH - panelH - m - 48};
            case 3 -> new int[]{screenW - panelW - m, screenH - panelH - m - 48};
            default -> new int[]{m, m};
        };
        return new int[]{a[0] + (int) offsetX.value, a[1] + (int) offsetY.value};
    }

    protected void markBounds(int x, int y, int w, int h) {
        lastX = x;
        lastY = y;
        lastW = w;
        lastH = h;
    }

    public static HudModule moduleAt(double x, double y) {
        for (Module m : ModuleManager.modules()) {
            if (m instanceof HudModule hm && hm.enabled && (hm.move.value || editMode)) {
                if (x >= hm.lastX && x <= hm.lastX + hm.lastW && y >= hm.lastY && y <= hm.lastY + hm.lastH) {
                    return hm;
                }
            }
        }
        return null;
    }

    /**
     * Ordnet alle aktiven HUD-Elemente so an, dass sie sich innerhalb ihrer
     * Ecke nicht überlappen (stapelt sie mit Abstand).
     */
    public static void autoArrange() {
        for (int corner = 0; corner < 4; corner++) {
            List<HudModule> list = new ArrayList<>();
            for (Module m : ModuleManager.modules()) {
                if (m instanceof HudModule hm && hm.enabled && hm.position.index == corner) {
                    list.add(hm);
                }
            }
            list.sort((a, b) -> Integer.compare(a.lastY, b.lastY));
            int gap = 8;
            int total = 0;
            for (HudModule hm : list) total += Math.max(20, hm.lastH) + gap;
            total = total > 0 ? total - gap : 0;
            int y = (corner == 2 || corner == 3) ? -total : 0;
            for (HudModule hm : list) {
                hm.offsetX.value = 0;
                hm.offsetY.value = y;
                y += Math.max(20, hm.lastH) + gap;
            }
        }
        ModuleManager.save();
    }

    protected void panel(GuiGraphics g, int x, int y, int w, int h) {
        if (background.value) {
            dev.kollegen.client.ui.Glass.fillRound(g, x - 5, y - 5, w + 10, h + 10, 6, backgroundColor.value);
        }
        markBounds(x - 5, y - 5, w + 10, h + 10);
    }

    protected void text(GuiGraphics g, String s, int x, int y) {
        g.drawString(mc.font, s, x, y, color.value, true);
    }

    protected int lineWidth(List<String> lines) {
        int w = 0;
        for (String l : lines) w = Math.max(w, mc.font.width(l));
        return w;
    }

    protected void renderLines(GuiGraphics g, List<String> lines, int x, int y) {
        int w = lineWidth(lines);
        int h = lines.size() * (mc.font.lineHeight + 3);
        int[] a = anchor(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), w, h);
        panel(g, a[0], a[1], w, h);
        int yy = a[1];
        for (String l : lines) {
            text(g, l, a[0], yy);
            yy += mc.font.lineHeight + 3;
        }
    }
}
