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

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class AddEditWaypointScreen extends Screen {
    private final Screen parent;
    private final Waypoint target;
    private final boolean isNew;

    private int px;
    private int py;
    private int pw;
    private int ph;

    private EditBox nameBox;
    private EditBox xBox;
    private EditBox yBox;
    private EditBox zBox;

    private String dimension;
    private int selectedColor = 0xFF4488FF;
    private boolean hasCustomColor = false;
    private float openAnim = 0.0f;
    private final Map<String, Float> anims = new HashMap<>();

    private static final int[] PRESET_COLORS = {
            0xFFFF4444, 0xFFFF9900, 0xFFFFDD00, 0xFF44EE44,
            0xFF00DDFF, 0xFF4488FF, 0xFFAA44FF, 0xFFFF55AA,
            0xFFFFFFFF
    };

    private static final String[] DIMENSIONS = {
            "minecraft:overworld",
            "minecraft:the_nether",
            "minecraft:the_end"
    };

    public AddEditWaypointScreen(Screen parent, Waypoint wp) {
        super(Component.literal(wp == null ? "Wegpunkt erstellen" : "Wegpunkt bearbeiten"));
        this.parent = parent;
        this.target = wp;
        this.isNew = (wp == null);

        Minecraft mc = Minecraft.getInstance();
        if (wp != null) {
            this.dimension = wp.dimension;
            this.selectedColor = wp.color;
            this.hasCustomColor = wp.hasColor;
        } else {
            this.dimension = WaypointManager.getCurrentDimension();
            this.hasCustomColor = false;
            this.selectedColor = Palette.ACCENT;
        }
    }

    @Override
    protected void init() {
        this.pw = Math.min(this.width - 24, 340);
        this.ph = Math.min(this.height - 24, 300);
        this.px = (this.width - this.pw) / 2;
        this.py = (this.height - this.ph) / 2;

        Minecraft mc = Minecraft.getInstance();
        double defX = (mc != null && mc.player != null) ? Math.round(mc.player.getX() * 10.0) / 10.0 : 0.0;
        double defY = (mc != null && mc.player != null) ? Math.round(mc.player.getY() * 10.0) / 10.0 : 64.0;
        double defZ = (mc != null && mc.player != null) ? Math.round(mc.player.getZ() * 10.0) / 10.0 : 0.0;

        String initName = isNew ? ("Wegpunkt " + (WaypointManager.getWaypoints().size() + 1)) : target.name;
        String initX = isNew ? String.format(Locale.ROOT, "%.1f", defX) : String.format(Locale.ROOT, "%.1f", target.x);
        String initY = isNew ? String.format(Locale.ROOT, "%.1f", defY) : String.format(Locale.ROOT, "%.1f", target.y);
        String initZ = isNew ? String.format(Locale.ROOT, "%.1f", defZ) : String.format(Locale.ROOT, "%.1f", target.z);

        this.nameBox = new EditBox(this.font, this.px + 20, this.py + 44, this.pw - 40, 20, Component.literal(""));
        this.nameBox.setMaxLength(32);
        this.nameBox.setValue(initName);
        this.addRenderableWidget(this.nameBox);

        int cw = (this.pw - 50) / 3;
        this.xBox = new EditBox(this.font, this.px + 20, this.py + 86, cw, 18, Component.literal(""));
        this.xBox.setMaxLength(16);
        this.xBox.setValue(initX);
        this.addRenderableWidget(this.xBox);

        this.yBox = new EditBox(this.font, this.px + 20 + cw + 5, this.py + 86, cw, 18, Component.literal(""));
        this.yBox.setMaxLength(16);
        this.yBox.setValue(initY);
        this.addRenderableWidget(this.yBox);

        this.zBox = new EditBox(this.font, this.px + 20 + (cw + 5) * 2, this.py + 86, cw, 18, Component.literal(""));
        this.zBox.setMaxLength(16);
        this.zBox.setValue(initZ);
        this.addRenderableWidget(this.zBox);

        this.nameBox.setFocused(true);
    }

    private void saveAndClose() {
        String name = this.nameBox.getValue().trim();
        if (name.isEmpty()) name = "Wegpunkt";

        double x = parseCoord(this.xBox.getValue(), 0.0);
        double y = parseCoord(this.yBox.getValue(), 64.0);
        double z = parseCoord(this.zBox.getValue(), 0.0);

        if (isNew) {
            Waypoint wp = new Waypoint(null, name, x, y, z, this.dimension, this.selectedColor, this.hasCustomColor, true);
            WaypointManager.add(wp);
        } else {
            target.name = name;
            target.x = x;
            target.y = y;
            target.z = z;
            target.dimension = this.dimension;
            target.color = this.selectedColor;
            target.hasColor = this.hasCustomColor;
            WaypointManager.save();
        }
        if (this.minecraft != null) {
            this.minecraft.setScreenAndShow(this.parent);
        }
    }

    private double parseCoord(String s, double def) {
        try {
            return Double.parseDouble(s.replace(',', '.').trim());
        } catch (Throwable t) {
            return def;
        }
    }

    private String getDimensionDisplay(String dim) {
        if (dim.contains("the_nether")) return "Nether";
        if (dim.contains("the_end")) return "The End";
        return "Overworld";
    }

    private void cycleDimension() {
        for (int i = 0; i < DIMENSIONS.length; i++) {
            if (DIMENSIONS[i].equalsIgnoreCase(this.dimension)) {
                this.dimension = DIMENSIONS[(i + 1) % DIMENSIONS.length];
                return;
            }
        }
        this.dimension = DIMENSIONS[0];
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        double mx = event.x();
        double my = event.y();

        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            int dimBtnY = this.py + 124;
            if (mx >= this.px + 20 && mx <= this.px + this.pw - 20 && my >= dimBtnY && my <= dimBtnY + 22) {
                cycleDimension();
                return true;
            }

            int colorY = this.py + 168;
            int noneBtnW = 75;
            if (mx >= this.px + 20 && mx <= this.px + 20 + noneBtnW && my >= colorY && my <= colorY + 18) {
                this.hasCustomColor = false;
                return true;
            }

            int cx0 = this.px + 20 + noneBtnW + 8;
            for (int i = 0; i < PRESET_COLORS.length; i++) {
                int cx = cx0 + i * 22;
                if (cx + 18 > this.px + this.pw - 20) break;
                if (mx >= cx && mx <= cx + 18 && my >= colorY && my <= colorY + 18) {
                    this.hasCustomColor = true;
                    this.selectedColor = PRESET_COLORS[i];
                    return true;
                }
            }

            int btnY = this.py + this.ph - 36;
            int btnW = (this.pw - 50) / 2;
            if (mx >= this.px + 20 && mx <= this.px + 20 + btnW && my >= btnY && my <= btnY + 24) {
                saveAndClose();
                return true;
            }

            int cancelX = this.px + 20 + btnW + 10;
            if (mx >= cancelX && mx <= cancelX + btnW && my >= btnY && my <= btnY + 24) {
                if (this.minecraft != null) {
                    this.minecraft.setScreenAndShow(this.parent);
                }
                return true;
            }
        }

        return super.mouseClicked(event, bl);
    }

    @Override
    public boolean keyPressed(KeyEvent ki) {
        if (ki.key() == InputConstants.KEY_ESCAPE || ki.isEscape()) {
            if (this.minecraft != null) {
                this.minecraft.setScreenAndShow(this.parent);
            }
            return true;
        }
        if (ki.key() == InputConstants.KEY_RETURN || ki.key() == InputConstants.KEY_NUMPADENTER) {
            saveAndClose();
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

        String title = isNew ? "Neuer Wegpunkt" : "Wegpunkt bearbeiten";
        Glass.textScaled(g, this.font, title, this.px + 20, this.py + 16, 1.2f, Palette.TEXT, true);

        Glass.text(g, this.font, "Name", this.px + 20, this.py + 34, Palette.MUTED, false);
        Glass.text(g, this.font, "X", this.px + 20, this.py + 74, Palette.MUTED, false);
        int cw = (this.pw - 50) / 3;
        Glass.text(g, this.font, "Y", this.px + 20 + cw + 5, this.py + 74, Palette.MUTED, false);
        Glass.text(g, this.font, "Z", this.px + 20 + (cw + 5) * 2, this.py + 74, Palette.MUTED, false);

        int dimY = this.py + 112;
        Glass.text(g, this.font, "Dimension", this.px + 20, dimY, Palette.MUTED, false);
        int dimBtnY = this.py + 124;
        boolean hovDim = (mx >= this.px + 20 && mx <= this.px + this.pw - 20 && my >= dimBtnY && my <= dimBtnY + 22);
        Glass.rr(g, this.px + 20, dimBtnY, this.pw - 40, 22, 6, hovDim ? 0x44FFFFFF : 0x22FFFFFF);
        Glass.ring(g, this.px + 20, dimBtnY, this.pw - 40, 22, 6, 0.5f, 0x33FFFFFF);
        Glass.textC(g, this.font, getDimensionDisplay(this.dimension) + " (Klicken zum Wechseln)", this.px + this.pw / 2, dimBtnY + 7, Palette.TEXT, false);

        int colY = this.py + 154;
        Glass.text(g, this.font, "Farbe", this.px + 20, colY, Palette.MUTED, false);
        int colorRowY = this.py + 168;
        int noneBtnW = 75;
        boolean hovNone = (mx >= this.px + 20 && mx <= this.px + 20 + noneBtnW && my >= colorRowY && my <= colorRowY + 18);
        int noneFill = !this.hasCustomColor ? Glass.a(Palette.ACCENT, 0.35f) : (hovNone ? 0x44FFFFFF : 0x22FFFFFF);
        Glass.rr(g, this.px + 20, colorRowY, noneBtnW, 18, 6, noneFill);
        Glass.ring(g, this.px + 20, colorRowY, noneBtnW, 18, 6, 0.5f, !this.hasCustomColor ? Palette.ACCENT : 0x33FFFFFF);
        Glass.textC(g, this.font, "Standard", this.px + 20 + noneBtnW / 2, colorRowY + 5, !this.hasCustomColor ? Palette.ACCENT : Palette.TEXT, false);

        int cx0 = this.px + 20 + noneBtnW + 8;
        for (int i = 0; i < PRESET_COLORS.length; i++) {
            int cx = cx0 + i * 22;
            if (cx + 18 > this.px + this.pw - 20) break;
            int c = PRESET_COLORS[i];
            boolean selected = this.hasCustomColor && (this.selectedColor == c);
            boolean hov = (mx >= cx && mx <= cx + 18 && my >= colorRowY && my <= colorRowY + 18);
            Glass.disc(g, cx + 9, colorRowY + 9, 8.0f, c);
            if (selected) {
                Glass.disc(g, cx + 9, colorRowY + 9, 3.0f, 0xFFFFFFFF);
            }
            if (selected || hov) {
                Glass.ring(g, cx, colorRowY, 18, 18, 9, 1.2f, 0xFFFFFFFF);
            }
        }

        int btnY = this.py + this.ph - 36;
        int btnW = (this.pw - 50) / 2;

        boolean hovSave = (mx >= this.px + 20 && mx <= this.px + 20 + btnW && my >= btnY && my <= btnY + 24);
        Float curSave = this.anims.get("save");
        float aSave = Glass.step(curSave == null ? 0.0f : curSave, hovSave ? 1.0f : 0.0f, 14.0f);
        this.anims.put("save", aSave);
        Glass.pill(g, this.px + 20, btnY, btnW, 24, aSave, true, false);
        Glass.textC(g, this.font, "Speichern", this.px + 20 + btnW / 2, btnY + 8, 0xFFFFFFFF, false);

        int cancelX = this.px + 20 + btnW + 10;
        boolean hovCancel = (mx >= cancelX && mx <= cancelX + btnW && my >= btnY && my <= btnY + 24);
        Float curCancel = this.anims.get("cancel");
        float aCancel = Glass.step(curCancel == null ? 0.0f : curCancel, hovCancel ? 1.0f : 0.0f, 14.0f);
        this.anims.put("cancel", aCancel);
        Glass.pill(g, cancelX, btnY, btnW, 24, aCancel, false, false);
        Glass.textC(g, this.font, "Abbrechen", cancelX + btnW / 2, btnY + 8, Palette.TEXT, false);

        Glass.alpha = 1.0f;
        super.extractRenderState(g, mx, my, pt);
    }
}
