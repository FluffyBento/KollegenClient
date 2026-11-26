package dev.kollegen.client.scenery;

import com.mojang.blaze3d.Blaze3D;
import com.mojang.blaze3d.platform.InputConstants;
import dev.kollegen.client.mods.Palette;
import dev.kollegen.client.ui.Glass;
import dev.kollegen.client.ui.GuiCustomizationSettings;
import dev.kollegen.client.ui.Icons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScenerySelectionScreen extends Screen {

    private final Screen parent;
    private final Map<String, Float> anims = new HashMap<>();
    private final SimpleDateFormat dateFmt = new SimpleDateFormat("dd.MM.yyyy HH:mm");

    private List<SceneryManager.SceneryEntry> entries = new ArrayList<>();
    private int px, py, pw, ph;
    private int listX, listY, listW, listH;
    private float scrollY = 0.0f;
    private float scrollTarget = 0.0f;
    private float maxScroll = 0.0f;

    private String renameTargetId = null;
    private EditBox renameBox = null;
    private String confirmDeleteId = null;
    private long confirmDeleteTime = 0L;
    private String statusText = null;
    private long statusUntil = 0L;

    private static final int CARD_H = 54;
    private static final int CARD_GAP = 6;

    public ScenerySelectionScreen(Screen parent) {
        super(Component.literal("Szenerien"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.entries = SceneryManager.listSceneries();
        this.pw = Math.min(this.width - 28, 560);
        this.ph = Math.min(this.height - 28, 420);
        this.px = (this.width - this.pw) / 2;
        this.py = (this.height - this.ph) / 2;

        this.listX = this.px + 14;
        this.listY = this.py + 82;
        this.listW = this.pw - 28;
        this.listH = this.ph - 82 - 14;

        int totalCards = 1 + this.entries.size();
        float totalH = totalCards * (CARD_H + CARD_GAP) - CARD_GAP;
        this.maxScroll = Math.max(0.0f, totalH - this.listH + 4);
        this.scrollTarget = Math.min(this.scrollTarget, this.maxScroll);

        int closeX = this.px + this.pw - 32;
        int closeY = this.py + 12;
        this.addRenderableWidget(new PanelButton(closeX, closeY, 20, 20, "✕", btn -> this.onClose(), (g, mx, my, x, y, w, h, hAnim) -> {
            Glass.rr(g, x, y, w, h, 5.0f, hAnim > 0.01f ? Glass.mixA(0x22FFFFFF, 0x66EF4444, hAnim) : 0x22FFFFFF);
            Glass.textC(g, this.font, "✕", x + w / 2, y + 6, hAnim > 0.3f ? 0xFFF87171 : Palette.TEXT, false);
        }));

        int folderBtnW = 72;
        int folderBtnX = closeX - folderBtnW - 6;
        int capBtnW = 92;
        int capBtnX = folderBtnX - capBtnW - 6;
        int topBtnY = this.py + 13;

        this.addRenderableWidget(new PanelButton(capBtnX, topBtnY, capBtnW, 20, "Aufnehmen", btn -> {
            boolean inGame = this.minecraft != null && this.minecraft.player != null && this.minecraft.level != null;
            if (inGame) {
                this.minecraft.setScreenAndShow(null);
                SceneryManager.captureCurrentScenery();
            } else {
                showStatus("Nur im Spiel: Welt betreten & F2 halten!");
            }
        }, (g, mx, my, x, y, w, h, hAnim) -> {
            boolean inGame = this.minecraft != null && this.minecraft.player != null && this.minecraft.level != null;
            int bg = inGame ? Glass.mixA(0x2810B981, 0x5510B981, hAnim) : Glass.mixA(0x18FFFFFF, 0x2EFFFFFF, hAnim);
            int ring = inGame ? 0x8810B981 : Glass.mixA(0x33FFFFFF, 0x66FFFFFF, hAnim);
            int tc = inGame ? 0xFF34D399 : (hAnim > 0.3f ? 0xCCFFFFFF : 0x77FFFFFF);
            Glass.rr(g, x, y, w, h, 6.0f, bg);
            Glass.ring(g, x, y, w, h, 6.0f, 0.7f, ring);
            Glass.textC(g, this.font, inGame ? "📷 Aufnehmen" : "Nur im Spiel", x + w / 2, y + 6, tc, inGame);
        }));

        this.addRenderableWidget(new PanelButton(folderBtnX, topBtnY, folderBtnW, 20, "Ordner", btn -> openSceneriesFolder(), (g, mx, my, x, y, w, h, hAnim) -> {
            Glass.rr(g, x, y, w, h, 6.0f, Glass.mixA(0x2038BDF8, 0x4438BDF8, hAnim));
            Glass.ring(g, x, y, w, h, 6.0f, 0.7f, 0x7738BDF8);
            Glass.textC(g, this.font, "Ordner ↗", x + w / 2, y + 6, 0xFF7DD3FC, false);
        }));

        int ctrlY = this.py + 48;
        int ctrlW = (this.listW - 8) / 2;

        this.addRenderableWidget(new PanelButton(this.listX, ctrlY, ctrlW, 22, "Panorama-Tempo", btn -> cycleSpeed(), (g, mx, my, x, y, w, h, hAnim) -> {
            drawQuickToggleWidget(g, x, y, w, h, "Panorama-Tempo", formatSpeedLabel(), 0xFF38BDF8, hAnim);
        }));

        this.addRenderableWidget(new PanelButton(this.listX + ctrlW + 8, ctrlY, ctrlW, 22, "Menü-Blur", btn -> cycleBlur(), (g, mx, my, x, y, w, h, hAnim) -> {
            drawQuickToggleWidget(g, x, y, w, h, "Menü-Blur", formatBlurLabel(), 0xFFA78BFA, hAnim);
        }));

        this.renameBox = new EditBox(this.font, this.px + 30, this.py + this.ph / 2 - 12, this.pw - 60, 24, Component.literal("Name"));
        this.renameBox.setMaxLength(48);
        this.renameBox.setVisible(this.renameTargetId != null);
        this.addRenderableWidget(this.renameBox);
    }

    private float anim(String key, boolean hovered) {
        float cur = anims.getOrDefault(key, 0.0f);
        float nxt = Glass.step(cur, hovered ? 1.0f : 0.0f, 14.0f);
        anims.put(key, nxt);
        return nxt;
    }

    private boolean hit(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        if (this.minecraft != null && this.minecraft.level == null) {
            super.extractPanorama(g, partialTick);
            if (this.minecraft.options != null && this.minecraft.options.getMenuBackgroundBlurriness() > 0) {
                super.extractBlurredBackground(g);
            }
            Glass.rr(g, 0, 0, this.width, this.height, 0, 0x33000000);
        } else {
            super.extractBlurredBackground(g);
            Glass.rr(g, 0, 0, this.width, this.height, 0, 0x80000000);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        Glass.frame();
        this.scrollY += (this.scrollTarget - this.scrollY) * 0.25f;
        if (this.confirmDeleteId != null && System.currentTimeMillis() - this.confirmDeleteTime > 3500L) {
            this.confirmDeleteId = null;
        }
        if (this.statusText != null && System.currentTimeMillis() > this.statusUntil) {
            this.statusText = null;
        }

        Glass.shadowSoft(g, px, py, pw, ph, 14.0f, 16.0f, 2.0f, 0x88000000);
        Glass.glassCard(g, px, py, pw, ph, 14.0f, Palette.PANEL, 0.88f, 0.16f, Glass.a(Palette.BORDER, 0.65f));

        Glass.rr(g, px + 14, py + 14, 3, 24, 1.5f, 0xFF10B981);
        Glass.textBold(g, this.font, "360° Szenerien & Hintergrund", px + 24, py + 13, Palette.TEXT, true);
        String subtitle = this.statusText != null ? this.statusText : ("Aktiv: " + GuiCustomizationSettings.getSceneryDisplayName());
        int subtitleCol = this.statusText != null ? 0xFF34D399 : Palette.MUTED;
        Glass.text(g, this.font, subtitle, px + 24, py + 28, subtitleCol, false);

        Glass.rr(g, listX, listY - 4, listW, 1, 0, Glass.a(Palette.BORDER, 0.35f));

        g.enableScissor(listX, listY, listX + listW, listY + listH);
        String selectedId = GuiCustomizationSettings.getSelectedScenery();
        if (selectedId == null || selectedId.isEmpty()) selectedId = "STANDARD";

        int curY = listY - Math.round(this.scrollY);
        renderSceneryCard(g, null, "Standard (Minecraft)", "Standard-Panorama", true, "STANDARD".equalsIgnoreCase(selectedId), listX, curY, listW, CARD_H, mouseX, mouseY);
        curY += CARD_H + CARD_GAP;

        for (SceneryManager.SceneryEntry entry : this.entries) {
            if (curY + CARD_H >= listY && curY <= listY + listH) {
                String sub = formatMetaSubtitle(entry);
                boolean isSel = entry.id().equals(selectedId);
                renderSceneryCard(g, entry, entry.displayName(), sub, false, isSel, listX, curY, listW, CARD_H, mouseX, mouseY);
            }
            curY += CARD_H + CARD_GAP;
        }
        g.disableScissor();

        if (this.renameTargetId == null) {
            super.extractRenderState(g, mouseX, mouseY, partialTick);
        } else {
            super.extractRenderState(g, -9999, -9999, partialTick);
            renderRenameOverlay(g, mouseX, mouseY);
        }
    }

    private void drawQuickToggleWidget(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, String val, int accent, float ha) {
        int bg = Glass.mixA(0x1EFFFFFF, Glass.a(accent, 0.25f), ha);
        int border = Glass.mixA(0x35FFFFFF, Glass.a(accent, 0.75f), ha);
        Glass.rr(g, x, y, w, h, 6.0f, bg);
        Glass.ring(g, x, y, w, h, 6.0f, 0.7f, border);
        Glass.text(g, this.font, label, x + 8, y + 7, Palette.TEXT, false);
        int vw = this.font.width(val);
        Glass.text(g, this.font, val, x + w - vw - 8, y + 7, accent, true);
    }

    private void renderSceneryCard(GuiGraphicsExtractor g, SceneryManager.SceneryEntry entry, String title, String sub, boolean isStandard, boolean selected, int x, int y, int w, int h, int mx, int my) {
        String id = isStandard ? "STANDARD" : entry.id();
        boolean hovered = renameTargetId == null && hit(mx, my, x, y, w, h) && my >= listY && my <= listY + listH;
        float ha = anim("card_" + id, hovered);

        int baseBg = selected ? 0x2810B981 : 0x1BFFFFFF;
        int hoverBg = selected ? 0x3810B981 : Glass.a(Palette.ACCENT, 0.22f);
        int bg = Glass.mixA(baseBg, hoverBg, ha);
        Glass.rr(g, x, y, w, h, 8.0f, bg);
        int border = selected ? 0xCC10B981 : Glass.mixA(0x28FFFFFF, Glass.a(Palette.ACCENT, 0.75f), ha);
        Glass.ring(g, x, y, w, h, 8.0f, selected ? 1.2f : 0.7f, border);

        int thumbX = x + 6;
        int thumbY = y + 5;
        int thumbS = 44;
        Glass.rr(g, thumbX, thumbY, thumbS, thumbS, 6.0f, 0x33000000);
        if (isStandard) {
            Icons.draw(g, Icons.GLOBE, thumbX + 11, thumbY + 11, 22, 0xFF38BDF8);
        } else {
            Identifier thumb = SceneryManager.getOrLoadThumbnail(entry);
            if (thumb != null) {
                try {
                    g.blit(RenderPipelines.GUI_TEXTURED, thumb, thumbX, thumbY, 0.0f, 0.0f, thumbS, thumbS, thumbS, thumbS, thumbS, thumbS);
                } catch (Throwable ignored) {
                    Icons.draw(g, Icons.GLOBE, thumbX + 11, thumbY + 11, 22, 0xFF10B981);
                }
            } else {
                Icons.draw(g, Icons.GLOBE, thumbX + 11, thumbY + 11, 22, 0xFF10B981);
            }
        }
        Glass.ring(g, thumbX, thumbY, thumbS, thumbS, 6.0f, 0.6f, 0x33FFFFFF);

        int textX = thumbX + thumbS + 10;
        Glass.textBold(g, this.font, title, textX, y + 12, selected ? 0xFF6EE7B7 : Palette.TEXT, true);
        Glass.text(g, this.font, sub, textX, y + 28, Palette.MUTED, false);

        if (selected) {
            int badgeW = 46;
            int badgeX = isStandard ? (x + w - badgeW - 10) : (x + w - 120 - badgeW);
            Glass.rr(g, badgeX, y + 18, badgeW, 18, 9.0f, 0x3310B981);
            Glass.ring(g, badgeX, y + 18, badgeW, 18, 9.0f, 0.7f, 0xAA10B981);
            Glass.textC(g, this.font, "Aktiv", badgeX + badgeW / 2, y + 23, 0xFF34D399, true);
        }

        if (!isStandard) {
            int delW = 54;
            int delX = x + w - delW - 8;
            int renW = 46;
            int renX = delX - renW - 6;
            int btnY = y + 16;

            boolean renHov = renameTargetId == null && hit(mx, my, renX, btnY, renW, 22) && my >= listY && my <= listY + listH;
            Glass.rr(g, renX, btnY, renW, 22, 5.0f, renHov ? 0x4438BDF8 : 0x22FFFFFF);
            Glass.textC(g, this.font, "Name", renX + renW / 2, btnY + 7, renHov ? 0xFF7DD3FC : Palette.TEXT, false);

            boolean isConfirm = id.equals(this.confirmDeleteId);
            boolean delHov = renameTargetId == null && hit(mx, my, delX, btnY, delW, 22) && my >= listY && my <= listY + listH;
            int delBg = isConfirm ? 0xAAEF4444 : (delHov ? 0x44EF4444 : 0x22FFFFFF);
            Glass.rr(g, delX, btnY, delW, 22, 5.0f, delBg);
            Glass.textC(g, this.font, isConfirm ? "Sicher?" : "Löschen", delX + delW / 2, btnY + 7, isConfirm || delHov ? 0xFFFFFFFF : 0xFFFCA5A5, isConfirm);
        }
    }

    private void renderRenameOverlay(GuiGraphicsExtractor g, int mx, int my) {
        Glass.rr(g, 0, 0, this.width, this.height, 0, 0x88000000);
        int mw = 320;
        int mh = 110;
        int rx = (this.width - mw) / 2;
        int ry = (this.height - mh) / 2;
        Glass.shadowSoft(g, rx, ry, mw, mh, 10.0f, 12.0f, 2.0f, 0xAA000000);
        Glass.glassCard(g, rx, ry, mw, mh, 10.0f, Palette.PANEL, 0.96f, 0.1f, Glass.a(Palette.ACCENT, 0.7f));
        Glass.textBold(g, this.font, "Szenerie umbenennen", rx + 14, ry + 12, Palette.TEXT, true);

        if (this.renameBox != null) {
            this.renameBox.setX(rx + 14);
            this.renameBox.setY(ry + 36);
            this.renameBox.setWidth(mw - 28);
            this.renameBox.extractRenderState(g, mx, my, 0);
        }

        int bw = 138;
        int by = ry + mh - 32;
        boolean cancelHov = hit(mx, my, rx + 14, by, bw, 22);
        boolean saveHov = hit(mx, my, rx + mw - 14 - bw, by, bw, 22);
        Glass.rr(g, rx + 14, by, bw, 22, 5.0f, cancelHov ? 0x44FFFFFF : 0x22FFFFFF);
        Glass.textC(g, this.font, "Abbrechen", rx + 14 + bw / 2, by + 7, Palette.TEXT, false);
        Glass.rr(g, rx + mw - 14 - bw, by, bw, 22, 5.0f, saveHov ? 0xAA10B981 : 0x6610B981);
        Glass.textC(g, this.font, "Speichern", rx + mw - 14 - bw / 2, by + 7, 0xFFFFFFFF, true);
    }

    private String formatMetaSubtitle(SceneryManager.SceneryEntry entry) {
        String dimLabel = switch (entry.dimension() == null ? "" : entry.dimension()) {
            case "minecraft:overworld" -> "Overworld";
            case "minecraft:the_nether" -> "Nether";
            case "minecraft:the_end" -> "End";
            default -> entry.dimension() != null && !entry.dimension().isEmpty() ? entry.dimension() : "Welt";
        };
        return dimLabel + "  •  " + dateFmt.format(new Date(entry.timestamp()));
    }

    private String formatSpeedLabel() {
        if (this.minecraft == null || this.minecraft.options == null) return "100%";
        double v = this.minecraft.options.panoramaSpeed().get();
        if (v <= 0.01) return "Aus (0%)";
        return Math.round(v * 100.0) + "%";
    }

    private String formatBlurLabel() {
        if (this.minecraft == null || this.minecraft.options == null) return "5";
        int b = this.minecraft.options.menuBackgroundBlurriness().get();
        return b == 0 ? "Aus (Scharf)" : String.valueOf(b);
    }

    private void cycleSpeed() {
        if (this.minecraft == null || this.minecraft.options == null) return;
        double cur = this.minecraft.options.panoramaSpeed().get();
        double next;
        if (cur < 0.12) next = 0.25;
        else if (cur < 0.37) next = 0.5;
        else if (cur < 0.62) next = 0.75;
        else if (cur < 0.87) next = 1.0;
        else next = 0.0;
        this.minecraft.options.panoramaSpeed().set(next);
        this.minecraft.options.save();
        showStatus("Panorama-Tempo: " + formatSpeedLabel());
    }

    private void cycleBlur() {
        if (this.minecraft == null || this.minecraft.options == null) return;
        int cur = this.minecraft.options.menuBackgroundBlurriness().get();
        int next = cur == 0 ? 2 : (cur < 5 ? 5 : (cur < 8 ? 8 : 0));
        this.minecraft.options.menuBackgroundBlurriness().set(next);
        this.minecraft.options.save();
        showStatus("Menü-Blur: " + formatBlurLabel());
    }

    private void playClick() {
        if (this.minecraft != null && this.minecraft.getSoundManager() != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        }
    }

    private void showStatus(String msg) {
        this.statusText = msg;
        this.statusUntil = System.currentTimeMillis() + 3000L;
    }

    private void openSceneriesFolder() {
        java.nio.file.Path dir = SceneryManager.getSceneriesDir();
        boolean launched = false;
        try {
            if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.OPEN)) {
                java.awt.Desktop.getDesktop().open(dir.toFile());
                launched = true;
            }
        } catch (Throwable ignored) {
        }
        if (!launched) {
            try {
                new ProcessBuilder("xdg-open", dir.toAbsolutePath().toString()).start();
                launched = true;
            } catch (Throwable ignored) {
            }
        }
        if (!launched) {
            try {
                Blaze3D.openPath(dir);
            } catch (Throwable ignored) {
            }
        }
        showStatus("Ordner: " + dir.getFileName().toString());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();

        if (this.renameTargetId != null) {
            if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
                int mw = 320;
                int mh = 110;
                int rx = (this.width - mw) / 2;
                int ry = (this.height - mh) / 2;
                int bw = 138;
                int by = ry + mh - 32;
                if (hit(mx, my, rx + 14, by, bw, 22)) {
                    playClick();
                    closeRenameModal();
                    return true;
                }
                if (hit(mx, my, rx + mw - 14 - bw, by, bw, 22)) {
                    playClick();
                    commitRename();
                    return true;
                }
                if (this.renameBox != null && this.renameBox.mouseClicked(event, doubleClick)) {
                    return true;
                }
            }
            return true;
        }

        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }

        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }

        if (my >= listY && my <= listY + listH && mx >= listX && mx <= listX + listW) {
            int curY = listY - Math.round(this.scrollY);
            if (hit(mx, my, listX, curY, listW, CARD_H)) {
                selectScenery("STANDARD");
                showStatus("Standard-Panorama aktiv");
                return true;
            }
            curY += CARD_H + CARD_GAP;

            for (SceneryManager.SceneryEntry entry : this.entries) {
                if (hit(mx, my, listX, curY, listW, CARD_H)) {
                    int delW = 54;
                    int delX = listX + listW - delW - 8;
                    int renW = 46;
                    int renX = delX - renW - 6;
                    int btnY = curY + 16;

                    if (hit(mx, my, renX, btnY, renW, 22)) {
                        playClick();
                        openRenameModal(entry);
                        return true;
                    }
                    if (hit(mx, my, delX, btnY, delW, 22)) {
                        playClick();
                        if (entry.id().equals(this.confirmDeleteId)) {
                            SceneryManager.deleteScenery(entry.id());
                            this.confirmDeleteId = null;
                            this.entries = SceneryManager.listSceneries();
                            int totalCards = 1 + this.entries.size();
                            this.maxScroll = Math.max(0.0f, totalCards * (CARD_H + CARD_GAP) - CARD_GAP - this.listH + 4);
                            this.scrollTarget = Math.min(this.scrollTarget, this.maxScroll);
                            showStatus("Szenerie gelöscht");
                        } else {
                            this.confirmDeleteId = entry.id();
                            this.confirmDeleteTime = System.currentTimeMillis();
                        }
                        return true;
                    }
                    selectScenery(entry.id());
                    showStatus("Aktiv: " + entry.displayName());
                    return true;
                }
                curY += CARD_H + CARD_GAP;
            }
        }

        if (mx < px || mx > px + pw || my < py || my > py + ph) {
            playClick();
            this.onClose();
            return true;
        }

        return false;
    }

    private void selectScenery(String id) {
        GuiCustomizationSettings.setSelectedScenery(id);
        SceneryManager.reloadPanorama();
        playClick();
    }

    private void openRenameModal(SceneryManager.SceneryEntry entry) {
        this.renameTargetId = entry.id();
        if (this.renameBox != null) {
            this.renameBox.setValue(entry.displayName());
            this.renameBox.setVisible(true);
            this.renameBox.setFocused(true);
            this.setFocused(this.renameBox);
        }
    }

    private void closeRenameModal() {
        this.renameTargetId = null;
        if (this.renameBox != null) {
            this.renameBox.setVisible(false);
            this.renameBox.setFocused(false);
        }
    }

    private void commitRename() {
        if (this.renameTargetId != null && this.renameBox != null) {
            SceneryManager.renameScenery(this.renameTargetId, this.renameBox.getValue());
            this.entries = SceneryManager.listSceneries();
        }
        closeRenameModal();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (this.renameTargetId == null && this.maxScroll > 0) {
            this.scrollTarget = Math.max(0.0f, Math.min(this.maxScroll, this.scrollTarget - (float) (vertical * 26.0)));
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.renameTargetId != null) {
            if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
                commitRename();
                return true;
            }
            if (event.key() == InputConstants.KEY_ESCAPE || event.isEscape()) {
                closeRenameModal();
                return true;
            }
            return super.keyPressed(event);
        }
        if (event.key() == InputConstants.KEY_ESCAPE || event.isEscape()) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreenAndShow(this.parent);
        }
    }

    private interface ButtonRenderer {
        void render(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, int h, float hAnim);
    }

    private static final class PanelButton extends Button {

        private final ButtonRenderer renderer;
        private float hAnim = 0.0f;

        private PanelButton(int x, int y, int w, int h, String label, Button.OnPress onPress, ButtonRenderer renderer) {
            super(x, y, w, h, Component.literal(label), onPress, DEFAULT_NARRATION);
            this.renderer = renderer;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float pt) {
            boolean hovered = this.isMouseOver(mx, my) && this.active;
            this.hAnim = Glass.step(this.hAnim, hovered ? 1.0f : 0.0f, 14.0f);
            this.renderer.render(g, mx, my, this.getX(), this.getY(), this.width, this.height, this.hAnim);
        }
    }
}
