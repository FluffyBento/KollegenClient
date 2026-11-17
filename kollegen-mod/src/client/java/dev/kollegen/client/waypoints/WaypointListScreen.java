package dev.kollegen.client.waypoints;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kollegen.client.mods.Palette;
import dev.kollegen.client.ui.Glass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class WaypointListScreen extends Screen {
    private final Screen parent;

    private int px;
    private int py;
    private int pw;
    private int ph;

    private EditBox searchBox;
    private float scrollY = 0.0f;
    private float maxScroll = 0.0f;
    private float openAnim = 0.0f;
    private final Map<String, Float> anims = new HashMap<>();

    public WaypointListScreen(Screen parent) {
        super(Component.literal("Wegpunkte"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.pw = Math.min(this.width - 24, 460);
        this.ph = Math.min(this.height - 24, 380);
        this.px = (this.width - this.pw) / 2;
        this.py = (this.height - this.ph) / 2;

        this.searchBox = new EditBox(this.font, this.px + 20, this.py + 44, this.pw - 40 - 130, 20, Component.literal(""));
        this.searchBox.setMaxLength(32);
        this.addRenderableWidget(this.searchBox);
    }

    private List<Waypoint> getFilteredWaypoints() {
        String q = (this.searchBox != null ? this.searchBox.getValue().trim().toLowerCase(Locale.ROOT) : "");
        List<Waypoint> all = WaypointManager.getWaypoints();
        if (q.isEmpty()) {
            return all;
        }
        List<Waypoint> filtered = new ArrayList<>();
        for (Waypoint wp : all) {
            if (wp.name.toLowerCase(Locale.ROOT).contains(q)
                    || wp.dimension.toLowerCase(Locale.ROOT).contains(q)
                    || String.valueOf((int) wp.x).contains(q)
                    || String.valueOf((int) wp.z).contains(q)) {
                filtered.add(wp);
            }
        }
        return filtered;
    }

    private String getDimName(String dim) {
        if (dim.contains("the_nether")) return "Nether";
        if (dim.contains("the_end")) return "End";
        return "Overworld";
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (mx >= this.px && mx <= this.px + this.pw && my >= this.py + 72 && my <= this.py + this.ph - 46) {
            this.scrollY = Math.max(0.0f, Math.min(this.maxScroll, this.scrollY - (float) (vertical * 24.0)));
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        double mx = event.x();
        double my = event.y();

        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            int addBtnX = this.px + this.pw - 20 - 120;
            int addBtnY = this.py + 44;
            if (mx >= addBtnX && mx <= addBtnX + 120 && my >= addBtnY && my <= addBtnY + 20) {
                if (this.minecraft != null) {
                    this.minecraft.setScreenAndShow(new AddEditWaypointScreen(this, null));
                }
                return true;
            }

            int closeBtnY = this.py + this.ph - 36;
            int closeBtnW = 100;
            int closeBtnX = this.px + (this.pw - closeBtnW) / 2;
            if (mx >= closeBtnX && mx <= closeBtnX + closeBtnW && my >= closeBtnY && my <= closeBtnY + 24) {
                if (this.minecraft != null) {
                    this.minecraft.setScreenAndShow(this.parent);
                }
                return true;
            }

            int listY0 = this.py + 72;
            int listH = this.ph - 120;
            if (mx >= this.px + 16 && mx <= this.px + this.pw - 16 && my >= listY0 && my <= listY0 + listH) {
                List<Waypoint> list = getFilteredWaypoints();
                int rowH = 40;
                int startY = listY0 - (int) this.scrollY;
                for (int i = 0; i < list.size(); i++) {
                    int ry = startY + i * (rowH + 6);
                    if (ry + rowH < listY0 || ry > listY0 + listH) continue;

                    Waypoint wp = list.get(i);
                    int actionX = this.px + this.pw - 28;

                    int delX = actionX - 18;
                    if (mx >= delX && mx <= delX + 18 && my >= ry + 11 && my <= ry + 29) {
                        WaypointManager.remove(wp.id);
                        return true;
                    }

                    int tpX = delX - 26;
                    if (mx >= tpX && mx <= tpX + 22 && my >= ry + 11 && my <= ry + 29) {
                        teleportTo(wp);
                        return true;
                    }

                    int editX = tpX - 26;
                    if (mx >= editX && mx <= editX + 22 && my >= ry + 11 && my <= ry + 29) {
                        if (this.minecraft != null) {
                            this.minecraft.setScreenAndShow(new AddEditWaypointScreen(this, wp));
                        }
                        return true;
                    }

                    int visX = editX - 26;
                    if (mx >= visX && mx <= visX + 22 && my >= ry + 11 && my <= ry + 29) {
                        wp.visible = !wp.visible;
                        WaypointManager.save();
                        return true;
                    }
                }
            }
        }

        return super.mouseClicked(event, bl);
    }

    private void teleportTo(Waypoint wp) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        String cmd = String.format(Locale.ROOT, "tp %.1f %.1f %.1f", wp.x, wp.y, wp.z);
        if (mc.getConnection() != null) {
            mc.getConnection().sendCommand(cmd);
        }
        if (this.minecraft != null) {
            this.minecraft.setScreenAndShow(null);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent ki) {
        if (ki.key() == InputConstants.KEY_ESCAPE || ki.isEscape()) {
            if (this.minecraft != null) {
                this.minecraft.setScreenAndShow(this.parent);
            }
            return true;
        }
        return super.keyPressed(ki);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        Glass.frame();
        this.openAnim = Math.min(1.0f, this.openAnim + Glass.dt * 6.0f);
        float open = Glass.ease(this.openAnim);

        g.fill(0, 0, this.width, this.height, Glass.tint(Palette.BG, (int) (140 * open)));
        Glass.alpha = open;
        Glass.glassCard(g, this.px, this.py, this.pw, this.ph, 14, Palette.BG, Math.min(0.92f, Glass.opacity + 0.14f), 1.0f);

        Glass.textScaled(g, this.font, "Wegpunkte", this.px + 20, this.py + 16, 1.25f, Palette.TEXT, true);

        int addBtnX = this.px + this.pw - 20 - 120;
        int addBtnY = this.py + 44;
        boolean hovAdd = (mx >= addBtnX && mx <= addBtnX + 120 && my >= addBtnY && my <= addBtnY + 20);
        Float curAdd = this.anims.get("add");
        float aAdd = Glass.step(curAdd == null ? 0.0f : curAdd, hovAdd ? 1.0f : 0.0f, 14.0f);
        this.anims.put("add", aAdd);
        Glass.pill(g, addBtnX, addBtnY, 120, 20, aAdd, true, false);
        Glass.textC(g, this.font, "+ Neuer Wegpunkt", addBtnX + 60, addBtnY + 6, 0xFFFFFFFF, false);

        int listX = this.px + 20;
        int listY = this.py + 72;
        int listW = this.pw - 40;
        int listH = this.ph - 118;

        List<Waypoint> list = getFilteredWaypoints();
        int rowH = 40;
        int totalContentH = list.size() * (rowH + 6);
        this.maxScroll = Math.max(0.0f, (float) (totalContentH - listH));

        Minecraft mc = Minecraft.getInstance();
        double px = (mc != null && mc.player != null) ? mc.player.getX() : 0.0;
        double py = (mc != null && mc.player != null) ? mc.player.getY() : 0.0;
        double pz = (mc != null && mc.player != null) ? mc.player.getZ() : 0.0;

        if (list.isEmpty()) {
            Glass.textC(g, this.font, "Keine Wegpunkte gefunden.", this.px + this.pw / 2, listY + 30, Palette.MUTED, false);
            Glass.textC(g, this.font, "Drücke im Spiel 'N', um schnell einen Wegpunkt zu setzen.", this.px + this.pw / 2, listY + 48, Palette.MUTED, false);
        } else {
            int startY = listY - (int) this.scrollY;
            for (int i = 0; i < list.size(); i++) {
                int ry = startY + i * (rowH + 6);
                if (ry + rowH < listY || ry > listY + listH) continue;

                Waypoint wp = list.get(i);
                boolean hovRow = (mx >= listX && mx <= listX + listW && my >= ry && my <= ry + rowH && my >= listY && my <= listY + listH);

                Glass.rr(g, listX, ry, listW, rowH, 8, hovRow ? 0x2AFFFFFF : 0x1AFFFFFF);
                Glass.ring(g, listX, ry, listW, rowH, 8, 0.5f, 0x26FFFFFF);

                int color = wp.getEffectiveColor();
                Glass.disc(g, listX + 16, ry + rowH / 2.0f, 6.0f, color);
                if (!wp.visible) {
                    Glass.line(g, listX + 11, ry + rowH / 2.0f - 5, listX + 21, ry + rowH / 2.0f + 5, 1.5f, 0xFFFF3333);
                }

                Glass.text(g, this.font, wp.name, listX + 30, ry + 8, wp.visible ? Palette.TEXT : Palette.MUTED, false);

                String coordsStr = String.format(Locale.ROOT, "X: %.0f  Y: %.0f  Z: %.0f  [%s]", wp.x, wp.y, wp.z, getDimName(wp.dimension));
                Glass.text(g, this.font, coordsStr, listX + 30, ry + 22, Palette.MUTED, false);

                double dist = wp.distanceTo(px, py, pz);
                String distStr = wp.formatDistance(dist);
                int distW = this.font.width(distStr);

                int actionX = listX + listW - 8;

                int delX = actionX - 18;
                boolean hovDel = (hovRow && mx >= delX && mx <= delX + 18 && my >= ry + 11 && my <= ry + 29);
                Glass.rr(g, delX, ry + 11, 18, 18, 4, hovDel ? 0x55FF4444 : 0x22FFFFFF);
                Glass.textC(g, this.font, "✕", delX + 9, ry + 16, hovDel ? 0xFFFFFFFF : 0xFFAAAAAA, false);

                int tpX = delX - 24;
                boolean hovTp = (hovRow && mx >= tpX && mx <= tpX + 20 && my >= ry + 11 && my <= ry + 29);
                Glass.rr(g, tpX, ry + 11, 20, 18, 4, hovTp ? Glass.a(Palette.ACCENT, 0.6f) : 0x22FFFFFF);
                Glass.textC(g, this.font, "TP", tpX + 10, ry + 16, hovTp ? 0xFFFFFFFF : 0xFFAAAAAA, false);

                int editX = tpX - 24;
                boolean hovEdit = (hovRow && mx >= editX && mx <= editX + 20 && my >= ry + 11 && my <= ry + 29);
                Glass.rr(g, editX, ry + 11, 20, 18, 4, hovEdit ? 0x44FFFFFF : 0x22FFFFFF);
                Glass.textC(g, this.font, "✎", editX + 10, ry + 16, hovEdit ? 0xFFFFFFFF : 0xFFAAAAAA, false);

                int visX = editX - 24;
                boolean hovVis = (hovRow && mx >= visX && mx <= visX + 20 && my >= ry + 11 && my <= ry + 29);
                Glass.rr(g, visX, ry + 11, 20, 18, 4, hovVis ? 0x44FFFFFF : 0x22FFFFFF);
                Glass.textC(g, this.font, wp.visible ? "👁" : "Ø", visX + 10, ry + 16, wp.visible ? 0xFF55FF55 : 0xFF888888, false);

                Glass.textR(g, this.font, distStr, visX - 8, ry + 15, Palette.ACCENT, false);
            }
        }

        if (this.maxScroll > 0) {
            float sbH = Math.max(16.0f, (listH / (float) totalContentH) * listH);
            float sbY = listY + (this.scrollY / this.maxScroll) * (listH - sbH);
            Glass.rr(g, listX + listW + 4, sbY, 4, sbH, 2, 0x66FFFFFF);
        }

        int closeBtnY = this.py + this.ph - 36;
        int closeBtnW = 100;
        int closeBtnX = this.px + (this.pw - closeBtnW) / 2;
        boolean hovClose = (mx >= closeBtnX && mx <= closeBtnX + closeBtnW && my >= closeBtnY && my <= closeBtnY + 24);
        Float curClose = this.anims.get("close");
        float aClose = Glass.step(curClose == null ? 0.0f : curClose, hovClose ? 1.0f : 0.0f, 14.0f);
        this.anims.put("close", aClose);
        Glass.pill(g, closeBtnX, closeBtnY, closeBtnW, 24, aClose, false, false);
        Glass.textC(g, this.font, "Schließen", closeBtnX + closeBtnW / 2, closeBtnY + 8, Palette.TEXT, false);

        Glass.alpha = 1.0f;
        super.extractRenderState(g, mx, my, pt);
    }
}
