package dev.kollegen.client.menu;

import dev.kollegen.client.KollegenMod;
import dev.kollegen.client.theme.ThemeSync;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * In-game mod menu, opened with Right-Shift. Colors are synced live with the
 * Kollegen Client launcher (see {@link ThemeSync}) so the menu always matches
 * the launcher's palette, even when the launcher's accent color is changed at
 * runtime. The only setting for now is the Minecraft-logo replacement toggle.
 */
public class KollegenMenuScreen extends Screen {
    private final Screen parent;

    private int toggleX, toggleY, toggleW = 52, toggleH = 28;

    public KollegenMenuScreen(Screen parent) {
        super(Component.literal("Kollegen Client"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ThemeSync.refresh();
        int bw = 200;
        this.addRenderableWidget(Button.builder(Component.literal("Schließen"), b -> close())
                .bounds(this.width / 2 - bw / 2, this.height - 64, bw, 30)
                .build());
    }

    private void close() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(GuiGraphics gg, int mx, int my, float delta) {
        ThemeSync.refresh(); // live sync with launcher colors

        int bg = ThemeSync.argb(ThemeSync.get("bg", "#0d0d12"), 0xff0d0d12);
        int panel = ThemeSync.argb(ThemeSync.get("panel", "#1a1a24"), 0xff1a1a24);
        int border = ThemeSync.argb(ThemeSync.get("border", "#34303a"), 0xff34303a);
        int accent = ThemeSync.argb(ThemeSync.get("accent", "#f5a623"), 0xfff5a623);
        int text = ThemeSync.argb(ThemeSync.get("text", "#f3e9d8"), 0xfff3e9d8);
        int muted = ThemeSync.argb(ThemeSync.get("muted", "#b9a98c"), 0xffb9a98c);

        gg.fill(0, 0, this.width, this.height, bg);

        int pw = Math.min(420, this.width - 60);
        int ph = Math.min(360, this.height - 60);
        int px = (this.width - pw) / 2;
        int py = (this.height - ph) / 2;
        gg.fill(px, py, px + pw, py + ph, panel);
        gg.fill(px, py, px + pw, py + 2, accent);          // accent top bar
        gg.fill(px, py, px + 2, py + ph, border);
        gg.fill(px + pw - 2, py, px + pw, py + ph, border);
        gg.fill(px, py + ph - 2, px + pw, py + ph, border);

        int cx = px + pw / 2;
        gg.drawCenteredString(this.font, "Kollegen Client", cx, py + 24, accent);
        gg.drawCenteredString(this.font, "Einstellungen", cx, py + 44, muted);

        int rowY = py + 96;
        gg.drawString(this.font, "Minecraft-Logo durch Logo.png ersetzen", px + 28, rowY + 9, text);

        toggleW = 52;
        toggleH = 28;
        toggleX = px + pw - 28 - toggleW;
        toggleY = rowY;

        boolean on = KollegenMod.CONFIG.replaceLogo;
        gg.fill(toggleX, toggleY, toggleX + toggleW, toggleY + toggleH, on ? accent : muted);
        int kx = on ? (toggleX + toggleW - toggleH + 3) : (toggleX + 3);
        gg.fill(kx, toggleY + 3, kx + toggleH - 6, toggleY + toggleH - 3, 0xffffffff);

        super.render(gg, mx, my, delta);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (mx >= toggleX && mx <= toggleX + toggleW && my >= toggleY && my <= toggleY + toggleH) {
            KollegenMod.CONFIG.replaceLogo = !KollegenMod.CONFIG.replaceLogo;
            KollegenMod.CONFIG.save();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
