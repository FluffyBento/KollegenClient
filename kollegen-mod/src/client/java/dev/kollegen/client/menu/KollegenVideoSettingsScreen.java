package dev.kollegen.client.menu;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kollegen.client.mods.Module;
import dev.kollegen.client.mods.ModuleManager;
import dev.kollegen.client.mods.Palette;
import dev.kollegen.client.mods.modules.KollegenShader;
import dev.kollegen.client.mods.modules.Performance;
import dev.kollegen.client.mods.modules.RendererManager;
import dev.kollegen.client.server.ServerListSettings;
import dev.kollegen.client.ui.Glass;
import dev.kollegen.client.ui.GuiCustomizationRenderer;
import dev.kollegen.client.ui.GuiCustomizationSettings;
import dev.kollegen.client.ui.LogoDraw;
import dev.kollegen.client.ui.UiStyleManager;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ParticleStatus;

public class KollegenVideoSettingsScreen extends Screen {

    public enum ModalTarget {
        NONE,
        BUTTON_OPACITY,
        CONTAINER_OPACITY,
        GLOW_STRENGTH,
        COLUMNS,
        CARD_HEIGHT,
        ICON_SIZE,
        MENU_WIDTH,
        SHADER_SATURATION,
        SHADER_VIBRANCE,
        SHADER_CONTRAST,
        SHADER_BRIGHTNESS,
        SHADER_EXPOSURE,
        SHADER_BLOOM,
        SHADER_WARMTH,
        SHADER_VIGNETTE,
        SHADER_MOTION_BLUR
    }

    private final Screen parent;
    private int currentTab = 0;
    private static final String[] TABS = {
            "Grafik & Anzeige",
            "Performance & Chunks",
            "Kollegen Shader",
            "GUI Customization"
    };

    private boolean restartRequired = false;
    private int activeSlider = -1;
    private float scrollY = 0.0f;
    private float maxScroll = 0.0f;
    private String statusMessage = null;
    private long statusMessageTime = 0L;

    private ModalTarget modalTarget = ModalTarget.NONE;
    private EditBox modalInput = null;
    private String modalTitle = "";
    private String modalSubtitle = "";
    private String modalUnit = "";
    private float modalMin = 0.0f;
    private float modalMax = 100.0f;

    public KollegenVideoSettingsScreen(Screen parent) {
        super(Component.literal("Kollegen Client Grafik-Einstellungen"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        if (this.modalTarget != ModalTarget.NONE && this.modalInput != null) {
            String val = this.modalInput.getValue();
            this.removeWidget(this.modalInput);
            int mw = 340;
            int mh = 164;
            int mx0 = (this.width - mw) / 2;
            int my0 = (this.height - mh) / 2;
            this.modalInput = new EditBox(this.font, mx0 + 20, my0 + 52, mw - 40, 22, Component.literal(this.modalTitle));
            this.modalInput.setMaxLength(8);
            this.modalInput.setValue(val);
            this.modalInput.setFocused(true);
            this.addRenderableWidget(this.modalInput);
            this.setFocused(this.modalInput);
        }
    }

    private static void drawRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + h, color);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        Glass.frame();
        int screenBgTop = Glass.tint(Palette.BG, 238);
        int screenBgBot = Glass.tint(Glass.mix(Palette.BG, Palette.PANEL, 0.4f), 246);
        g.fillGradient(0, 0, this.width, this.height, screenBgTop, screenBgBot);

        int logoW = 110;
        LogoDraw.draw(g, this.width / 2 - logoW / 2, 8, logoW);

        Glass.textC(g, this.font, "Kollegen Client Grafik-Einstellungen", this.width / 2, 38, Palette.TEXT, true);

        String backendText = "Vulkan Native (SPIR-V)";
        int badgeCol = Palette.ACCENT;
        int badgeW = this.font.width(backendText) + 16;
        GuiCustomizationRenderer.drawCustomButton(g, this.width - badgeW - 14, 12, badgeW, 20, false, false, GuiCustomizationSettings.getButtonStyle());
        Glass.textC(g, this.font, backendText, this.width - badgeW - 14 + badgeW / 2, 17, badgeCol, true);

        int tabY = 52;
        int tabW = Math.min(125, (this.width - 32 - (TABS.length - 1) * 6) / TABS.length);
        int totalTabsW = TABS.length * tabW + (TABS.length - 1) * 6;
        int startTabX = (this.width - totalTabsW) / 2;

        for (int i = 0; i < TABS.length; i++) {
            int tx = startTabX + i * (tabW + 6);
            boolean active = currentTab == i;
            boolean hov = mx >= tx && mx <= tx + tabW && my >= tabY && my <= tabY + 22;
            float r = GuiCustomizationSettings.getCornerRadius(22);
            if (active) {
                int top = Glass.mix(Palette.ACCENT, 0xFFFFFFFF, 0.18f);
                Glass.rrGrad(g, tx, tabY, tabW, 22, r, Glass.a(top, 0.95f), Glass.a(Palette.ACCENT, 0.95f));
                Glass.shadowSoft(g, tx, tabY, tabW, 22, r, 8.0f, 1.0f, Glass.a(Palette.ACCENT, 0.45f));
                Glass.ring(g, tx, tabY, tabW, 22, r, 0.5f, 0x88FFFFFF);
                Glass.textC(g, this.font, TABS[i], tx + tabW / 2, tabY + 7, 0xFFFFFFFF, true);
            } else {
                int base = hov ? Glass.mix(Palette.PANEL, 0xFFFFFFFF, 0.10f) : Palette.PANEL;
                Glass.rr(g, tx, tabY, tabW, 22, r, Glass.a(base, 0.75f));
                int ring = hov ? Glass.a(Palette.ACCENT, 0.65f) : Glass.a(Palette.BORDER, 0.50f);
                Glass.ring(g, tx, tabY, tabW, 22, r, 0.5f, ring);
                Glass.textC(g, this.font, TABS[i], tx + tabW / 2, tabY + 7, hov ? 0xFFFFFFFF : Palette.MUTED, true);
            }
        }

        int panelW = Math.min(this.width - 32, 540);
        int panelH = this.height - 130;
        int panelX = (this.width - panelW) / 2;
        int panelY = 82;

        float panelR = 12.0f;
        Glass.shadowSoft(g, panelX, panelY, panelW, panelH, panelR, 16.0f, 4.0f, 0x66000000);
        Glass.glassCard(g, panelX, panelY, panelW, panelH, panelR, Palette.PANEL, Glass.opacity * 0.95f, 0.0f, Glass.a(Palette.BORDER, 0.60f));

        if (this.activeSlider != -1 && this.modalTarget == ModalTarget.NONE) {
            handleSliderDrag(mx);
        }

        g.enableScissor(panelX, panelY + 4, panelX + panelW, panelY + panelH - 4);
        switch (currentTab) {
            case 0 -> renderTabDisplay(g, panelX, panelY, panelW, panelH, mx, my);
            case 1 -> renderTabPerformance(g, panelX, panelY, panelW, panelH, mx, my);
            case 2 -> renderTabKollegenShader(g, panelX, panelY, panelW, panelH, mx, my);
            case 3 -> renderTabGuiCustomization(g, panelX, panelY, panelW, panelH, mx, my);
        }
        g.disableScissor();

        if (this.maxScroll > 0) {
            int scrollBarX = panelX + panelW - 6;
            int scrollBarY = panelY + 10;
            int scrollBarH = panelH - 20;
            float thumbRatio = (float) panelH / (panelH + this.maxScroll);
            int thumbH = Math.max(20, Math.round(scrollBarH * thumbRatio));
            int thumbY = scrollBarY + Math.round((scrollBarH - thumbH) * (this.scrollY / this.maxScroll));
            Glass.rr(g, scrollBarX, thumbY, 3, thumbH, 1.5f, Glass.a(Palette.ACCENT, 0.75f));
        }

        renderFooter(g, mx, my);

        if (this.modalTarget != ModalTarget.NONE) {
            renderModalCard(g, mx, my);
        }

        super.extractRenderState(g, mx, my, pt);

        if (this.modalTarget != ModalTarget.NONE) {
            renderModalForeground(g, mx, my);
        }
    }

    private void renderFooter(GuiGraphicsExtractor g, int mx, int my) {
        int footY = this.height - 34;

        if (this.currentTab == 3) {
            int resetW = 140;
            int resetX = 16;
            boolean resetHov = mx >= resetX && mx <= resetX + resetW && my >= footY && my <= footY + 24;
            GuiCustomizationRenderer.drawCustomButton(g, resetX, footY, resetW, 24, true, resetHov, GuiCustomizationSettings.getButtonStyle());
            Glass.textC(g, this.font, "↺ Zurücksetzen", resetX + resetW / 2, footY + 8, resetHov ? 0xFFFFFFFF : 0xFFEF4444, true);
        }

        if (this.statusMessage != null && System.currentTimeMillis() - this.statusMessageTime < 3500L) {
            Glass.text(g, this.font, this.statusMessage, this.currentTab == 3 ? 168 : 20, footY + 8, 0xFF10B981, true);
        } else if (this.restartRequired) {
            String note = "Neustart empfohlen für geänderte Grafik-Optionen.";
            Glass.text(g, this.font, note, this.currentTab == 3 ? 168 : 20, footY + 8, Palette.ACCENT2, true);
        }

        int doneW = 110;
        int doneX = this.width - doneW - 16;
        boolean doneHov = mx >= doneX && mx <= doneX + doneW && my >= footY && my <= footY + 24;
        GuiCustomizationRenderer.drawCustomButton(g, doneX, footY, doneW, 24, true, doneHov, GuiCustomizationSettings.getButtonStyle());
        Glass.textC(g, this.font, "Fertig", doneX + doneW / 2, footY + 8, doneHov ? 0xFFFFFFFF : Palette.TEXT, true);
    }

    private void renderTabDisplay(GuiGraphicsExtractor g, int px, int py, int pw, int ph, int mx, int my) {
        Minecraft mc = Minecraft.getInstance();
        int rowY = py + 16 - (int) this.scrollY;
        int rowH = 28;
        int colW = (pw - 40) / 2;
        int col1X = px + 16;
        int col2X = px + 24 + colW;

        String renLabel = "Renderer: Vulkan Native (SPIR-V)";
        renderButton(g, col1X, rowY, colW, 24, renLabel, 0xFF8B5CF6, mx, my);

        GraphicsPreset gp = mc.options.graphicsPreset().get();
        String gpStr = gp == GraphicsPreset.FAST ? "Schnell" : (gp == GraphicsPreset.FANCY ? "Schön" : (gp == GraphicsPreset.FABULOUS ? "Fabelhaft" : "Eigene"));
        renderButton(g, col2X, rowY, colW, 24, "Grafik: " + gpStr, 0xFFFFFFFF, mx, my);

        rowY += rowH;

        renderButton(g, col1X, rowY, colW, 24, DisplaySettings.getWindowModeLabel(), DisplaySettings.getWindowModeColor(), mx, my);
        renderButton(g, col2X, rowY, colW, 24, DisplaySettings.getResolutionLabel(), 0xFF38BDF8, mx, my);

        rowY += rowH;

        boolean vs = mc.options.enableVsync().get();
        renderButton(g, col1X, rowY, colW, 24, "VSync: " + (vs ? "Ein" : "Aus"), vs ? 0xFF10B981 : 0xFFEF4444, mx, my);

        int sc = mc.options.guiScale().get();
        String scStr = sc == 0 ? "Auto" : String.valueOf(sc);
        renderButton(g, col2X, rowY, colW, 24, "GUI-Skalierung: " + scStr, 0xFF38BDF8, mx, my);

        rowY += rowH;

        int fov = mc.options.fov().get();
        String fovStr = fov == 110 ? "Quake Pro" : fov + "°";
        float fovProg = (fov - 30) / 80.0f;
        renderSlider(g, col1X, rowY, colW, 24, "Sichtfeld (FOV): " + fovStr, fovProg, mx, my);

        double gam = mc.options.gamma().get();
        int gamPct = (int) Math.round(gam * 100.0);
        renderSlider(g, col2X, rowY, colW, 24, "Helligkeit: " + gamPct + "%", (float) gam, mx, my);

        rowY += rowH;

        CloudStatus cs = mc.options.cloudStatus().get();
        String csStr = cs == CloudStatus.OFF ? "Aus" : (cs == CloudStatus.FAST ? "Schnell" : "Schön");
        renderButton(g, col1X, rowY, colW, 24, "Wolken: " + csStr, 0xFFFFFFFF, mx, my);

        ParticleStatus ps = mc.options.particles().get();
        String psStr = ps == ParticleStatus.ALL ? "Alle" : (ps == ParticleStatus.DECREASED ? "Verringert" : "Minimal");
        renderButton(g, col2X, rowY, colW, 24, "Partikel: " + psStr, 0xFFFFFFFF, mx, my);

        rowY += rowH;

        boolean bob = mc.options.bobView().get();
        renderButton(g, col1X, rowY, colW, 24, "Kamerawackeln: " + (bob ? "Ein" : "Aus"), bob ? 0xFF10B981 : 0xFFEF4444, mx, my);

        boolean shadows = mc.options.entityShadows().get();
        renderButton(g, col2X, rowY, colW, 24, "Schatten: " + (shadows ? "Ein" : "Aus"), shadows ? 0xFF10B981 : 0xFFEF4444, mx, my);

        rowY += rowH;

        double fovEff = mc.options.fovEffectScale().get();
        renderSlider(g, col1X, rowY, colW, 24, "FOV-Effekte: " + Math.round(fovEff * 100.0) + "%", (float) fovEff, mx, my);

        double scrEff = mc.options.screenEffectScale().get();
        renderSlider(g, col2X, rowY, colW, 24, "Bildschirm-Effekte: " + Math.round(scrEff * 100.0) + "%", (float) scrEff, mx, my);

        rowY += rowH;

        int mm = mc.options.mipmapLevels().get();
        renderButton(g, col1X, rowY, colW, 24, "Mipmap-Stufen: " + mm, 0xFFFFFFFF, mx, my);

        AttackIndicatorStatus ais = mc.options.attackIndicator().get();
        String aisStr = ais == AttackIndicatorStatus.CROSSHAIR ? "Fadenkreuz" : (ais == AttackIndicatorStatus.HOTBAR ? "Hotbar" : "Aus");
        renderButton(g, col2X, rowY, colW, 24, "Angriffsanzeige: " + aisStr, 0xFFFFFFFF, mx, my);

        int totalH = (rowY + 36 + (int) this.scrollY) - py;
        this.maxScroll = Math.max(0.0f, totalH - (ph - 16));
    }

    private void renderTabPerformance(GuiGraphicsExtractor g, int px, int py, int pw, int ph, int mx, int my) {
        Minecraft mc = Minecraft.getInstance();
        int rowY = py + 20 - (int) this.scrollY;
        int rowH = 46;
        int colW = pw - 32;
        int colX = px + 16;

        int serverDist = Performance.KollegenDistance.getActualDistance();
        renderSlider(g, colX, rowY, colW, 24, "Server-Sichtweite (Aktive Chunks): " + serverDist + " Chunks", (serverDist - 4) / 28.0f, mx, my);
        Glass.text(g, this.font, "Sichtweite für aktive Server-Chunks (Simulation & Mobs).", colX + 4, rowY + 26, 0xFF888888, false);

        rowY += rowH;

        boolean bobbyOn = Performance.KollegenDistance.isDistanceEnabled();
        int fakeDist = Performance.KollegenDistance.getDistanceLimit();
        String limitStr = !bobbyOn ? "Aus" : fakeDist + " Chunks";
        float limProg = !bobbyOn ? 0.0f : Math.max(0.0f, Math.min(1.0f, (fakeDist - 16) / 112.0f));
        renderSlider(g, colX, rowY, colW, 24, "Gesamte Sichtweite (Fake Chunks): " + limitStr, limProg, mx, my);
        Glass.text(g, this.font, "Erweiterte visuelle Sichtweite ohne Server-Belastung (16 bis 128 Chunks).", colX + 4, rowY + 26, 0xFF888888, false);

        rowY += rowH;

        int sim = mc.options.simulationDistance().get();
        renderSlider(g, colX, rowY, colW, 24, "Simulations-Distanz: " + sim + " Chunks", (sim - 5) / 27.0f, mx, my);
        Glass.text(g, this.font, "Radius, in dem Mobs, Ticks und Block-Updates berechnet werden.", colX + 4, rowY + 26, 0xFF888888, false);

        rowY += rowH;

        int fps = mc.options.framerateLimit().get();
        String fpsStr = fps >= 260 ? "Unbegrenzt" : fps + " FPS";
        float fpsProg = fps >= 260 ? 1.0f : Math.max(0.0f, (fps - 10) / 250.0f);
        renderSlider(g, colX, rowY, colW, 24, "FPS-Limit: " + fpsStr, fpsProg, mx, my);
        Glass.text(g, this.font, "Maximale Bildwiederholrate zur Begrenzung der GPU-Last.", colX + 4, rowY + 26, 0xFF888888, false);

        int totalH = (rowY + 48 + (int) this.scrollY) - py;
        this.maxScroll = Math.max(0.0f, totalH - (ph - 16));
    }

    private void renderTabKollegenShader(GuiGraphicsExtractor g, int px, int py, int pw, int ph, int mx, int my) {
        KollegenShader ks = KollegenShader.INSTANCE;
        if (ks == null) {
            Glass.textC(g, this.font, "Kollegen Shader Modul nicht initialisiert.", px + pw / 2, py + ph / 2, 0xFFEF4444, true);
            return;
        }

        int colW = (pw - 40) / 2;
        int col1X = px + 16;
        int col2X = px + 24 + colW;
        int rowH = 26;

        int y1 = py + 14 - (int) this.scrollY;
        boolean en = ks.enabled;
        renderButton(g, col1X, y1, colW, 22, "Kollegen Shader: " + (en ? "Aktiviert" : "Aus"), en ? 0xFF10B981 : 0xFFEF4444, mx, my);

        y1 += rowH;
        String[] profs = ks.preset.options;
        int profIdx = ks.preset.index;
        String curProf = (profIdx >= 0 && profIdx < profs.length) ? profs[profIdx] : "Eigenes";
        renderButton(g, col1X, y1, colW, 22, "Profil: " + curProf, 0xFFA78BFA, mx, my);

        y1 += rowH;
        boolean satEn = ks.saturationEnabled.value;
        double sat = ks.saturation.value;
        renderSlider(g, col1X, y1, colW, 22, "Sättigung: " + (satEn ? Math.round(sat * 100.0) + "%" : "Aus"), (float) (sat / 3.0), mx, my);

        y1 += rowH;
        boolean vibEn = ks.vibranceEnabled.value;
        double vib = ks.vibrance.value;
        renderSlider(g, col1X, y1, colW, 22, "Vibrance: " + (vibEn ? String.format("%.2f", vib) : "Aus"), (float) ((vib - 0.5) / 2.0), mx, my);

        y1 += rowH;
        boolean conEn = ks.contrastEnabled.value;
        double con = ks.contrast.value;
        renderSlider(g, col1X, y1, colW, 22, "Kontrast: " + (conEn ? String.format("%.2f", con) : "Aus"), (float) ((con - 0.5) / 1.5), mx, my);

        int y2 = py + 14 - (int) this.scrollY;
        boolean briEn = ks.brightnessEnabled.value;
        double bri = ks.brightness.value;
        renderSlider(g, col2X, y2, colW, 22, "Helligkeit: " + (briEn ? String.format("%.2f", bri) : "Aus"), (float) ((bri - 0.5) / 1.5), mx, my);

        y2 += rowH;
        boolean expEn = ks.exposureEnabled.value;
        double exp = ks.exposure.value;
        renderSlider(g, col2X, y2, colW, 22, "Belichtung: " + (expEn ? String.format("%.2f", exp) : "Aus"), (float) ((exp - 0.5) / 1.5), mx, my);

        y2 += rowH;
        boolean blEn = ks.bloomEnabled.value;
        double bl = ks.bloom.value;
        renderSlider(g, col2X, y2, colW, 22, "Bloom: " + (blEn ? Math.round(bl * 100.0) + "%" : "Aus"), (float) bl, mx, my);

        y2 += rowH;
        boolean warEn = ks.warmthEnabled.value;
        double war = ks.warmth.value;
        renderSlider(g, col2X, y2, colW, 22, "Farbtemperatur: " + (warEn ? String.format("%.2f", war) : "Aus"), (float) ((war - 0.7) / 0.7), mx, my);

        y2 += rowH;
        boolean vigEn = ks.vignetteEnabled.value;
        double vig = ks.vignetteStrength.value;
        renderSlider(g, col2X, y2, colW, 22, "Kino-Vignette: " + (vigEn ? Math.round(vig * 100.0) + "%" : "Aus"), (float) vig, mx, my);

        y2 += rowH;
        boolean motEn = ks.motionBlurEnabled.value;
        double mot = ks.motionBlurStrength.value;
        renderSlider(g, col2X, y2, colW, 22, "Motion Blur: " + (motEn ? Math.round(mot * 100.0) + "%" : "Aus"), (float) mot, mx, my);

        int totalH = (Math.max(y1, y2) + 32 + (int) this.scrollY) - py;
        this.maxScroll = Math.max(0.0f, totalH - (ph - 16));
    }

    private void renderButton(GuiGraphicsExtractor g, int x, int y, int w, int h, String text, int color, int mx, int my) {
        boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + h;
        GuiCustomizationRenderer.drawCustomButton(g, x, y, w, h, true, hov, GuiCustomizationSettings.getButtonStyle());
        Glass.textC(g, this.font, text, x + w / 2, y + (h - 8) / 2, color, true);
    }

    private void renderSlider(GuiGraphicsExtractor g, int x, int y, int w, int h, String text, float progress, int mx, int my) {
        progress = Math.max(0.0f, Math.min(1.0f, progress));
        boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + h;
        GuiCustomizationRenderer.drawCustomSlider(g, x, y, w, h, (double) progress, text, true, hov, GuiCustomizationSettings.getButtonStyle());
    }

    private void renderSettingRow(GuiGraphicsExtractor g, int x, int y, int w, int h, String title, String value, int valueColor, int mx, int my) {
        renderSettingRow(g, x, y, w, h, title, value, valueColor, mx, my, false);
    }

    private void renderSettingRow(GuiGraphicsExtractor g, int x, int y, int w, int h, String title, String value, int valueColor, int mx, int my, boolean editable) {
        boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + h;
        GuiCustomizationRenderer.drawCustomButton(g, x, y, w, h, true, hov, GuiCustomizationSettings.getButtonStyle());
        int fontY = y + (h - 8) / 2;
        Glass.text(g, this.font, title, x + 10, fontY, hov ? Palette.TEXT : Palette.MUTED, true);

        if (editable) {
            int penW = 16;
            int penX = x + w - 8 - penW;
            boolean penHov = mx >= penX && mx <= x + w - 4 && my >= y && my <= y + h;
            Glass.textR(g, this.font, value, penX - 4, fontY, valueColor, true);
            Glass.textC(g, this.font, "✎", penX + penW / 2, fontY, penHov ? Palette.ACCENT : Glass.a(Palette.TEXT, 0.45f), true);
        } else {
            Glass.textR(g, this.font, value, x + w - 10, fontY, valueColor, true);
        }
    }

    private void openNumericModal(ModalTarget target) {
        this.modalTarget = target;
        String initialValue = "";
        switch (target) {
            case BUTTON_OPACITY -> {
                this.modalTitle = "Button-Transparenz";
                this.modalSubtitle = "Wert von 10% bis 100% eingeben:";
                this.modalUnit = "%";
                this.modalMin = 10.0f;
                this.modalMax = 100.0f;
                initialValue = String.valueOf(Math.round(GuiCustomizationSettings.getButtonOpacity() * 100.0f));
            }
            case CONTAINER_OPACITY -> {
                this.modalTitle = "Container-Transparenz";
                this.modalSubtitle = "Deckkraft von 10% bis 100% eingeben:";
                this.modalUnit = "%";
                this.modalMin = 10.0f;
                this.modalMax = 100.0f;
                initialValue = String.valueOf(Math.round(GuiCustomizationSettings.getContainerOpacity() * 100.0f));
            }
            case GLOW_STRENGTH -> {
                this.modalTitle = "Hover-Glanz Stärke";
                this.modalSubtitle = "Stärke von 0% (Aus) bis 300% (Extrem):";
                this.modalUnit = "%";
                this.modalMin = 0.0f;
                this.modalMax = 300.0f;
                initialValue = String.valueOf(Math.round(GuiCustomizationSettings.getGlowStrength() * 100.0f));
            }
            case COLUMNS -> {
                this.modalTitle = "Multiplayer Spalten";
                this.modalSubtitle = "Anzahl an Spalten (1 bis 4):";
                this.modalUnit = "";
                this.modalMin = 1.0f;
                this.modalMax = 4.0f;
                initialValue = String.valueOf(ServerListSettings.columns);
            }
            case CARD_HEIGHT -> {
                this.modalTitle = "Kartenhöhe";
                this.modalSubtitle = "Höhe der Serverkarten in Pixeln (36 - 140px):";
                this.modalUnit = "px";
                this.modalMin = 36.0f;
                this.modalMax = 140.0f;
                initialValue = String.valueOf(ServerListSettings.cardHeight);
            }
            case ICON_SIZE -> {
                this.modalTitle = "Server-Icon Größe";
                this.modalSubtitle = "Pixel (16 - 96px, oder 0 für Automatisch):";
                this.modalUnit = "px";
                this.modalMin = 0.0f;
                this.modalMax = 96.0f;
                initialValue = ServerListSettings.iconSize == -1 ? "0" : String.valueOf(ServerListSettings.iconSize);
            }
            case MENU_WIDTH -> {
                this.modalTitle = "Menübreite";
                this.modalSubtitle = "Breite der Serverliste in Pixeln (400 - 1600px):";
                this.modalUnit = "px";
                this.modalMin = 400.0f;
                this.modalMax = 1600.0f;
                initialValue = String.valueOf(ServerListSettings.menuWidth);
            }
            case SHADER_SATURATION -> {
                this.modalTitle = "Shader Sättigung";
                this.modalSubtitle = "Sättigung von 0% bis 300% eingeben:";
                this.modalUnit = "%";
                this.modalMin = 0.0f;
                this.modalMax = 300.0f;
                KollegenShader ks = KollegenShader.INSTANCE;
                initialValue = ks != null ? String.valueOf(Math.round(ks.saturation.value * 100.0)) : "100";
            }
            case SHADER_VIBRANCE -> {
                this.modalTitle = "Shader Vibrance";
                this.modalSubtitle = "Vibrance von 0.50 bis 2.50 eingeben:";
                this.modalUnit = "float";
                this.modalMin = 0.50f;
                this.modalMax = 2.50f;
                KollegenShader ks = KollegenShader.INSTANCE;
                initialValue = ks != null ? String.format(java.util.Locale.US, "%.2f", ks.vibrance.value) : "1.00";
            }
            case SHADER_CONTRAST -> {
                this.modalTitle = "Shader Kontrast";
                this.modalSubtitle = "Kontrast von 0.50 bis 2.00 eingeben:";
                this.modalUnit = "float";
                this.modalMin = 0.50f;
                this.modalMax = 2.00f;
                KollegenShader ks = KollegenShader.INSTANCE;
                initialValue = ks != null ? String.format(java.util.Locale.US, "%.2f", ks.contrast.value) : "1.00";
            }
            case SHADER_BRIGHTNESS -> {
                this.modalTitle = "Shader Helligkeit";
                this.modalSubtitle = "Helligkeit von 0.50 bis 2.00 eingeben:";
                this.modalUnit = "float";
                this.modalMin = 0.50f;
                this.modalMax = 2.00f;
                KollegenShader ks = KollegenShader.INSTANCE;
                initialValue = ks != null ? String.format(java.util.Locale.US, "%.2f", ks.brightness.value) : "1.00";
            }
            case SHADER_EXPOSURE -> {
                this.modalTitle = "Shader Belichtung";
                this.modalSubtitle = "Belichtung von 0.50 bis 2.00 eingeben:";
                this.modalUnit = "float";
                this.modalMin = 0.50f;
                this.modalMax = 2.00f;
                KollegenShader ks = KollegenShader.INSTANCE;
                initialValue = ks != null ? String.format(java.util.Locale.US, "%.2f", ks.exposure.value) : "1.00";
            }
            case SHADER_BLOOM -> {
                this.modalTitle = "Shader Bloom";
                this.modalSubtitle = "Bloom von 0% bis 100% eingeben:";
                this.modalUnit = "%";
                this.modalMin = 0.0f;
                this.modalMax = 100.0f;
                KollegenShader ks = KollegenShader.INSTANCE;
                initialValue = ks != null ? String.valueOf(Math.round(ks.bloom.value * 100.0)) : "25";
            }
            case SHADER_WARMTH -> {
                this.modalTitle = "Shader Farbtemperatur";
                this.modalSubtitle = "Farbtemperatur von 0.70 bis 1.40 eingeben:";
                this.modalUnit = "float";
                this.modalMin = 0.70f;
                this.modalMax = 1.40f;
                KollegenShader ks = KollegenShader.INSTANCE;
                initialValue = ks != null ? String.format(java.util.Locale.US, "%.2f", ks.warmth.value) : "1.00";
            }
            case SHADER_VIGNETTE -> {
                this.modalTitle = "Shader Kino-Vignette";
                this.modalSubtitle = "Vignette von 0% bis 100% eingeben:";
                this.modalUnit = "%";
                this.modalMin = 0.0f;
                this.modalMax = 100.0f;
                KollegenShader ks = KollegenShader.INSTANCE;
                initialValue = ks != null ? String.valueOf(Math.round(ks.vignetteStrength.value * 100.0)) : "0";
            }
            case SHADER_MOTION_BLUR -> {
                this.modalTitle = "Shader Motion Blur";
                this.modalSubtitle = "Motion Blur von 0% bis 100% eingeben:";
                this.modalUnit = "%";
                this.modalMin = 0.0f;
                this.modalMax = 100.0f;
                KollegenShader ks = KollegenShader.INSTANCE;
                initialValue = ks != null ? String.valueOf(Math.round(ks.motionBlurStrength.value * 100.0)) : "0";
            }
            case NONE -> {
                return;
            }
        }

        int mw = 340;
        int mh = 164;
        int mx0 = (this.width - mw) / 2;
        int my0 = (this.height - mh) / 2;

        if (this.modalInput != null) {
            this.removeWidget(this.modalInput);
        }
        this.modalInput = new EditBox(this.font, mx0 + 20, my0 + 52, mw - 40, 22, Component.literal(this.modalTitle));
        this.modalInput.setMaxLength(8);
        this.modalInput.setValue(initialValue);
        this.modalInput.setFocused(true);
        this.addRenderableWidget(this.modalInput);
        this.setFocused(this.modalInput);
    }

    private void closeNumericModal(boolean apply) {
        if (apply && this.modalInput != null && this.modalTarget != ModalTarget.NONE) {
            String raw = this.modalInput.getValue().trim().replace("%", "").replace("px", "").replace("PX", "").replace(",", ".").trim();
            try {
                float val = Float.parseFloat(raw);
                val = Math.max(this.modalMin, Math.min(this.modalMax, val));
                switch (this.modalTarget) {
                    case BUTTON_OPACITY -> GuiCustomizationSettings.setButtonOpacity(val / 100.0f);
                    case CONTAINER_OPACITY -> GuiCustomizationSettings.setContainerOpacity(val / 100.0f);
                    case GLOW_STRENGTH -> GuiCustomizationSettings.setGlowStrength(val / 100.0f);
                    case COLUMNS -> ServerListSettings.setColumns(Math.round(val));
                    case CARD_HEIGHT -> ServerListSettings.setCardHeight(Math.round(val));
                    case ICON_SIZE -> ServerListSettings.setIconSize(Math.round(val) <= 0 ? -1 : Math.round(val));
                    case MENU_WIDTH -> ServerListSettings.setMenuWidth(Math.round(val));
                    case SHADER_SATURATION -> {
                        KollegenShader ks = KollegenShader.INSTANCE;
                        if (ks != null) {
                            ks.saturationEnabled.value = val > 5.0f;
                            ks.saturation.value = (double) (val / 100.0f);
                        }
                    }
                    case SHADER_VIBRANCE -> {
                        KollegenShader ks = KollegenShader.INSTANCE;
                        if (ks != null) {
                            ks.vibranceEnabled.value = true;
                            ks.vibrance.value = (double) val;
                        }
                    }
                    case SHADER_CONTRAST -> {
                        KollegenShader ks = KollegenShader.INSTANCE;
                        if (ks != null) {
                            ks.contrastEnabled.value = true;
                            ks.contrast.value = (double) val;
                        }
                    }
                    case SHADER_BRIGHTNESS -> {
                        KollegenShader ks = KollegenShader.INSTANCE;
                        if (ks != null) {
                            ks.brightnessEnabled.value = true;
                            ks.brightness.value = (double) val;
                        }
                    }
                    case SHADER_EXPOSURE -> {
                        KollegenShader ks = KollegenShader.INSTANCE;
                        if (ks != null) {
                            ks.exposureEnabled.value = true;
                            ks.exposure.value = (double) val;
                        }
                    }
                    case SHADER_BLOOM -> {
                        KollegenShader ks = KollegenShader.INSTANCE;
                        if (ks != null) {
                            ks.bloomEnabled.value = val > 2.0f;
                            ks.bloom.value = (double) (val / 100.0f);
                        }
                    }
                    case SHADER_WARMTH -> {
                        KollegenShader ks = KollegenShader.INSTANCE;
                        if (ks != null) {
                            ks.warmthEnabled.value = true;
                            ks.warmth.value = (double) val;
                        }
                    }
                    case SHADER_VIGNETTE -> {
                        KollegenShader ks = KollegenShader.INSTANCE;
                        if (ks != null) {
                            ks.vignetteEnabled.value = val > 2.0f;
                            ks.vignetteStrength.value = (double) (val / 100.0f);
                        }
                    }
                    case SHADER_MOTION_BLUR -> {
                        KollegenShader ks = KollegenShader.INSTANCE;
                        if (ks != null) {
                            ks.motionBlurEnabled.value = val > 2.0f;
                            ks.motionBlurStrength.value = (double) (val / 100.0f);
                        }
                    }
                    case NONE -> {}
                }
                Minecraft mc = Minecraft.getInstance();
                if (mc.options != null) {
                    mc.options.save();
                }
                ModuleManager.save();
            } catch (Throwable ignored) {
            }
        }
        if (this.modalInput != null) {
            this.removeWidget(this.modalInput);
            this.modalInput = null;
        }
        this.modalTarget = ModalTarget.NONE;
    }

    private void renderModalCard(GuiGraphicsExtractor g, int mx, int my) {
        g.fill(0, 0, this.width, this.height, 0x88000000);

        int mw = 340;
        int mh = 164;
        int mx0 = (this.width - mw) / 2;
        int my0 = (this.height - mh) / 2;
        float r = 12.0f;

        Glass.shadowSoft(g, mx0, my0, mw, mh, r, 24.0f, 4.0f, 0xAA000000);
        Glass.glassCard(g, mx0, my0, mw, mh, r, Palette.PANEL, 0.98f, 0.0f, Glass.a(Palette.BORDER, 0.85f));
        Glass.rr(g, mx0 + 16, my0 + 14, 3, 16, 1.5f, Palette.ACCENT);
        Glass.textBold(g, this.font, this.modalTitle, mx0 + 26, my0 + 14, Palette.TEXT, true);
        Glass.text(g, this.font, this.modalSubtitle, mx0 + 26, my0 + 32, Palette.MUTED, false);
    }

    private void renderModalForeground(GuiGraphicsExtractor g, int mx, int my) {
        int mw = 340;
        int mh = 164;
        int mx0 = (this.width - mw) / 2;
        int my0 = (this.height - mh) / 2;

        int pillW = (mw - 40 - 18) / 4;
        int p1X = mx0 + 20;
        int p2X = p1X + pillW + 6;
        int p3X = p2X + pillW + 6;
        int p4X = p3X + pillW + 6;
        int pillY = my0 + 80;
        int pillH = 18;

        String[] pillLabels = getPillLabels();
        int[] pillXs = { p1X, p2X, p3X, p4X };
        for (int i = 0; i < 4; i++) {
            int px = pillXs[i];
            boolean pHov = mx >= px && mx <= px + pillW && my >= pillY && my <= pillY + pillH;
            GuiCustomizationRenderer.drawCustomButton(g, px, pillY, pillW, pillH, true, pHov, GuiCustomizationSettings.getButtonStyle());
            Glass.textC(g, this.font, pillLabels[i], px + pillW / 2, pillY + 5, pHov ? 0xFFFFFFFF : Palette.MUTED, true);
        }

        int btnW = (mw - 48) / 2;
        int btnH = 24;
        int cancelX = mx0 + 20;
        int saveX = mx0 + mw - 20 - btnW;
        int btnY = my0 + 106;

        boolean cancelHov = mx >= cancelX && mx <= cancelX + btnW && my >= btnY && my <= btnY + btnH;
        GuiCustomizationRenderer.drawCustomButton(g, cancelX, btnY, btnW, btnH, true, cancelHov, GuiCustomizationSettings.getButtonStyle());
        Glass.textC(g, this.font, "Abbrechen", cancelX + btnW / 2, btnY + 8, cancelHov ? 0xFFFFFFFF : Palette.MUTED, true);

        boolean saveHov = mx >= saveX && mx <= saveX + btnW && my >= btnY && my <= btnY + btnH;
        GuiCustomizationRenderer.drawCustomButton(g, saveX, btnY, btnW, btnH, true, saveHov, GuiCustomizationSettings.getButtonStyle());
        Glass.textC(g, this.font, "Speichern", saveX + btnW / 2, btnY + 8, saveHov ? 0xFFFFFFFF : Palette.ACCENT, true);

        Glass.textC(g, this.font, "Enter = Übernehmen · Esc = Schließen", mx0 + mw / 2, my0 + 138, Glass.a(Palette.MUTED, 0.65f), false);
    }

    private String[] getPillLabels() {
        if (this.modalTarget == ModalTarget.COLUMNS) {
            return new String[]{ "1 Spalte", "2 Spalten", "3 Spalten", "4 Spalten" };
        }
        if ("%".equals(this.modalUnit)) {
            return new String[]{ "-25%", "-10%", "+10%", "+25%" };
        }
        if ("px".equals(this.modalUnit)) {
            return new String[]{ "-50px", "-10px", "+10px", "+50px" };
        }
        return new String[]{ "-0.10", "-0.05", "+0.05", "+0.10" };
    }

    private void applyModalPillOffset(int index) {
        if (this.modalInput == null) return;
        if (this.modalTarget == ModalTarget.COLUMNS) {
            this.modalInput.setValue(String.valueOf(index + 1));
            return;
        }

        float cur = 0;
        try {
            cur = Float.parseFloat(this.modalInput.getValue().trim().replace("%", "").replace("px", "").replace(",", "."));
        } catch (Throwable ignored) {
        }

        if ("%".equals(this.modalUnit)) {
            float delta = switch (index) {
                case 0 -> -25.0f;
                case 1 -> -10.0f;
                case 2 -> 10.0f;
                default -> 25.0f;
            };
            float nxt = Math.max(this.modalMin, Math.min(this.modalMax, cur + delta));
            this.modalInput.setValue(String.valueOf(Math.round(nxt)));
            return;
        }

        if ("px".equals(this.modalUnit)) {
            float delta = switch (index) {
                case 0 -> -50.0f;
                case 1 -> -10.0f;
                case 2 -> 10.0f;
                default -> 50.0f;
            };
            float nxt = Math.max(this.modalMin, Math.min(this.modalMax, cur + delta));
            this.modalInput.setValue(String.valueOf(Math.round(nxt)));
            return;
        }

        if ("float".equals(this.modalUnit)) {
            float delta = switch (index) {
                case 0 -> -0.10f;
                case 1 -> -0.05f;
                case 2 -> 0.05f;
                default -> 0.10f;
            };
            float nxt = Math.max(this.modalMin, Math.min(this.modalMax, cur + delta));
            this.modalInput.setValue(String.format(java.util.Locale.US, "%.2f", nxt));
        }
    }

    private void handleModalClick(double mx, double my, int button, boolean bl) {
        int mw = 340;
        int mh = 164;
        int mx0 = (this.width - mw) / 2;
        int my0 = (this.height - mh) / 2;

        int pillW = (mw - 40 - 18) / 4;
        int p1X = mx0 + 20;
        int p2X = p1X + pillW + 6;
        int p3X = p2X + pillW + 6;
        int p4X = p3X + pillW + 6;
        int pillY = my0 + 80;
        int pillH = 18;

        if (my >= pillY && my <= pillY + pillH) {
            if (mx >= p1X && mx <= p1X + pillW) {
                applyModalPillOffset(0);
                return;
            }
            if (mx >= p2X && mx <= p2X + pillW) {
                applyModalPillOffset(1);
                return;
            }
            if (mx >= p3X && mx <= p3X + pillW) {
                applyModalPillOffset(2);
                return;
            }
            if (mx >= p4X && mx <= p4X + pillW) {
                applyModalPillOffset(3);
                return;
            }
        }

        int btnW = (mw - 48) / 2;
        int btnH = 24;
        int cancelX = mx0 + 20;
        int saveX = mx0 + mw - 20 - btnW;
        int btnY = my0 + 106;

        if (mx >= cancelX && mx <= cancelX + btnW && my >= btnY && my <= btnY + btnH) {
            closeNumericModal(false);
            return;
        }
        if (mx >= saveX && mx <= saveX + btnW && my >= btnY && my <= btnY + btnH) {
            closeNumericModal(true);
            return;
        }

        if (mx >= mx0 && mx <= mx0 + mw && my >= my0 && my <= my0 + mh) {
            if (this.modalInput != null) {
                this.modalInput.setFocused(true);
            }
            return;
        }

        closeNumericModal(false);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.modalTarget != ModalTarget.NONE) {
            return true;
        }
        if (this.maxScroll > 0) {
            this.scrollY = Math.max(0.0f, Math.min(this.maxScroll, this.scrollY - (float) verticalAmount * 24.0f));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (this.modalTarget != ModalTarget.NONE && this.modalInput != null) {
            return this.modalInput.charTyped(event);
        }
        return super.charTyped(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        double mx = event.x();
        double my = event.y();

        if (this.modalTarget != ModalTarget.NONE) {
            handleModalClick(mx, my, event.button(), bl);
            return true;
        }

        int panelW = Math.min(this.width - 32, 540);
        int panelH = this.height - 130;
        int panelX = (this.width - panelW) / 2;
        int panelY = 82;

        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            if (currentTab == 3) {
                handleTabGuiCustomizationRightClick(panelX, panelY, panelW, mx, my);
                return true;
            } else if (currentTab == 2) {
                handleTabKollegenShaderRightClick(panelX, panelY, panelW, mx, my);
                return true;
            }
            return super.mouseClicked(event, bl);
        }

        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, bl);
        }

        int tabY = 52;
        int tabW = Math.min(125, (this.width - 32 - (TABS.length - 1) * 6) / TABS.length);
        int totalTabsW = TABS.length * tabW + (TABS.length - 1) * 6;
        int startTabX = (this.width - totalTabsW) / 2;

        for (int i = 0; i < TABS.length; i++) {
            int tx = startTabX + i * (tabW + 6);
            if (mx >= tx && mx <= tx + tabW && my >= tabY && my <= tabY + 22) {
                this.currentTab = i;
                this.scrollY = 0.0f;
                this.maxScroll = 0.0f;
                this.activeSlider = -1;
                return true;
            }
        }

        int footY = this.height - 34;
        if (currentTab == 3) {
            int resetW = 140;
            int resetX = 16;
            if (mx >= resetX && mx <= resetX + resetW && my >= footY && my <= footY + 24) {
                GuiCustomizationSettings.resetToVanillaDefaults();
                this.statusMessage = "✓ Auf Standard Minecraft zurückgesetzt!";
                this.statusMessageTime = System.currentTimeMillis();
                return true;
            }
        }

        int doneW = 110;
        int doneX = this.width - doneW - 16;
        if (mx >= doneX && mx <= doneX + doneW && my >= footY && my <= footY + 24) {
            this.onClose();
            return true;
        }

        if (currentTab == 0) {
            handleTabDisplayClick(panelX, panelY, panelW, mx, my);
            return true;
        } else if (currentTab == 1) {
            handleTabPerformanceClick(panelX, panelY, panelW, mx, my);
            return true;
        } else if (currentTab == 2) {
            handleTabKollegenShaderClick(panelX, panelY, panelW, mx, my);
            return true;
        } else if (currentTab == 3) {
            handleTabGuiCustomizationClick(panelX, panelY, panelW, mx, my);
            return true;
        }

        return super.mouseClicked(event, bl);
    }

    private void handleTabDisplayClick(int px, int py, int pw, double mx, double my) {
        Minecraft mc = Minecraft.getInstance();
        int rowY = py + 16 - (int) this.scrollY;
        int rowH = 28;
        int colW = (pw - 40) / 2;
        int col1X = px + 16;
        int col2X = px + 24 + colW;

        if (mx >= col1X && mx <= col1X + colW && my >= rowY && my <= rowY + 24) {
            RendererManager.setDesired(RendererManager.Group.VULKAN);
            RendererManager.apply();
            return;
        }

        if (mx >= col2X && mx <= col2X + colW && my >= rowY && my <= rowY + 24) {
            GraphicsPreset gp = mc.options.graphicsPreset().get();
            GraphicsPreset next = gp == GraphicsPreset.FAST ? GraphicsPreset.FANCY
                    : (gp == GraphicsPreset.FANCY ? GraphicsPreset.FABULOUS : GraphicsPreset.FAST);
            mc.options.applyGraphicsPreset(next);
            Module fg = ModuleManager.byId("fastgraphics");
            if (fg != null) fg.enabled = false;
            Module mf = ModuleManager.byId("maxfps");
            if (mf != null) mf.enabled = false;
            mc.options.save();
            ModuleManager.save();
            return;
        }

        rowY += rowH;

        if (mx >= col1X && mx <= col1X + colW && my >= rowY && my <= rowY + 24) {
            DisplaySettings.cycleWindowMode();
            return;
        }

        if (mx >= col2X && mx <= col2X + colW && my >= rowY && my <= rowY + 24) {
            DisplaySettings.cycleResolution();
            return;
        }

        rowY += rowH;

        if (mx >= col1X && mx <= col1X + colW && my >= rowY && my <= rowY + 24) {
            boolean vs = !mc.options.enableVsync().get();
            mc.options.enableVsync().set(vs);
            Module co = ModuleManager.byId("cpuoffload");
            if (co != null) co.enabled = false;
            Module mf = ModuleManager.byId("maxfps");
            if (mf != null) mf.enabled = false;
            mc.options.save();
            ModuleManager.save();
            return;
        }

        if (mx >= col2X && mx <= col2X + colW && my >= rowY && my <= rowY + 24) {
            int sc = mc.options.guiScale().get();
            mc.options.guiScale().set((sc + 1) % 5);
            mc.resizeGui();
            mc.options.save();
            return;
        }

        rowY += rowH;

        if (mx >= col1X && mx <= col1X + colW && my >= rowY && my <= rowY + 24) {
            this.activeSlider = 5;
            float ratio = (float) ((mx - col1X) / colW);
            int fov = 30 + Math.round(Math.max(0.0f, Math.min(1.0f, ratio)) * 80.0f);
            mc.options.fov().set(fov);
            mc.options.save();
            return;
        }

        if (mx >= col2X && mx <= col2X + colW && my >= rowY && my <= rowY + 24) {
            this.activeSlider = 0;
            float ratio = (float) ((mx - col2X) / colW);
            mc.options.gamma().set(Math.max(0.0, Math.min(1.0, (double) ratio)));
            mc.options.save();
            return;
        }

        rowY += rowH;

        if (mx >= col1X && mx <= col1X + colW && my >= rowY && my <= rowY + 24) {
            CloudStatus cs = mc.options.cloudStatus().get();
            CloudStatus next = cs == CloudStatus.OFF ? CloudStatus.FAST : (cs == CloudStatus.FAST ? CloudStatus.FANCY : CloudStatus.OFF);
            mc.options.cloudStatus().set(next);
            Module nc = ModuleManager.byId("noclouds");
            if (nc != null) nc.enabled = false;
            Module mf = ModuleManager.byId("maxfps");
            if (mf != null) mf.enabled = false;
            mc.options.save();
            ModuleManager.save();
            return;
        }

        if (mx >= col2X && mx <= col2X + colW && my >= rowY && my <= rowY + 24) {
            ParticleStatus ps = mc.options.particles().get();
            ParticleStatus next = ps == ParticleStatus.ALL ? ParticleStatus.DECREASED : (ps == ParticleStatus.DECREASED ? ParticleStatus.MINIMAL : ParticleStatus.ALL);
            mc.options.particles().set(next);
            mc.options.save();
            return;
        }

        rowY += rowH;

        if (mx >= col1X && mx <= col1X + colW && my >= rowY && my <= rowY + 24) {
            boolean bob = !mc.options.bobView().get();
            mc.options.bobView().set(bob);
            mc.options.save();
            return;
        }

        if (mx >= col2X && mx <= col2X + colW && my >= rowY && my <= rowY + 24) {
            boolean shadows = !mc.options.entityShadows().get();
            mc.options.entityShadows().set(shadows);
            mc.options.save();
            return;
        }

        rowY += rowH;

        if (mx >= col1X && mx <= col1X + colW && my >= rowY && my <= rowY + 24) {
            this.activeSlider = 6;
            float ratio = (float) ((mx - col1X) / colW);
            mc.options.fovEffectScale().set(Math.max(0.0, Math.min(1.0, (double) ratio)));
            mc.options.save();
            return;
        }

        if (mx >= col2X && mx <= col2X + colW && my >= rowY && my <= rowY + 24) {
            this.activeSlider = 7;
            float ratio = (float) ((mx - col2X) / colW);
            mc.options.screenEffectScale().set(Math.max(0.0, Math.min(1.0, (double) ratio)));
            mc.options.save();
            return;
        }

        rowY += rowH;

        if (mx >= col1X && mx <= col1X + colW && my >= rowY && my <= rowY + 24) {
            int mm = (mc.options.mipmapLevels().get() + 1) % 5;
            mc.options.mipmapLevels().set(mm);
            mc.updateMaxMipLevel(mm);
            mc.delayTextureReload();
            Module co = ModuleManager.byId("cpuoffload");
            if (co != null) co.enabled = false;
            mc.options.save();
            ModuleManager.save();
            return;
        }

        if (mx >= col2X && mx <= col2X + colW && my >= rowY && my <= rowY + 24) {
            AttackIndicatorStatus cur = mc.options.attackIndicator().get();
            AttackIndicatorStatus next = cur == AttackIndicatorStatus.CROSSHAIR ? AttackIndicatorStatus.HOTBAR
                    : (cur == AttackIndicatorStatus.HOTBAR ? AttackIndicatorStatus.OFF : AttackIndicatorStatus.CROSSHAIR);
            mc.options.attackIndicator().set(next);
            mc.options.save();
        }
    }

    private void handleTabPerformanceClick(int px, int py, int pw, double mx, double my) {
        int rowY = py + 20 - (int) this.scrollY;
        int rowH = 46;
        int colW = pw - 32;
        int colX = px + 16;

        if (mx >= colX && mx <= colX + colW && my >= rowY && my <= rowY + 24) {
            this.activeSlider = 1;
            setSliderValue(1, mx);
            return;
        }

        rowY += rowH;

        if (mx >= colX && mx <= colX + colW && my >= rowY && my <= rowY + 24) {
            this.activeSlider = 2;
            setSliderValue(2, mx);
            return;
        }

        rowY += rowH;

        if (mx >= colX && mx <= colX + colW && my >= rowY && my <= rowY + 24) {
            this.activeSlider = 3;
            setSliderValue(3, mx);
            return;
        }

        rowY += rowH;

        if (mx >= colX && mx <= colX + colW && my >= rowY && my <= rowY + 24) {
            this.activeSlider = 4;
            setSliderValue(4, mx);
        }
    }

    private void handleTabKollegenShaderClick(int px, int py, int pw, double mx, double my) {
        KollegenShader ks = KollegenShader.INSTANCE;
        if (ks == null) return;

        int colW = (pw - 40) / 2;
        int col1X = px + 16;
        int col2X = px + 24 + colW;
        int rowH = 26;

        int y1 = py + 14 - (int) this.scrollY;
        if (mx >= col1X && mx <= col1X + colW && my >= y1 && my <= y1 + 22) {
            ks.enabled = !ks.enabled;
            if (!ks.enabled) {
                ks.onDisable();
            } else {
                ks.onEnable();
            }
            ModuleManager.save();
            return;
        }

        y1 += rowH;
        if (mx >= col1X && mx <= col1X + colW && my >= y1 && my <= y1 + 22) {
            int next = (ks.preset.index + 1) % ks.preset.options.length;
            ks.preset.index = next;
            ks.applyPreset(next);
            ModuleManager.save();
            return;
        }

        y1 += rowH;
        if (mx >= col1X && mx <= col1X + colW && my >= y1 && my <= y1 + 22) {
            this.activeSlider = 10;
            setSliderValue(10, mx);
            return;
        }

        y1 += rowH;
        if (mx >= col1X && mx <= col1X + colW && my >= y1 && my <= y1 + 22) {
            this.activeSlider = 11;
            setSliderValue(11, mx);
            return;
        }

        y1 += rowH;
        if (mx >= col1X && mx <= col1X + colW && my >= y1 && my <= y1 + 22) {
            this.activeSlider = 12;
            setSliderValue(12, mx);
            return;
        }

        int y2 = py + 14 - (int) this.scrollY;
        if (mx >= col2X && mx <= col2X + colW && my >= y2 && my <= y2 + 22) {
            this.activeSlider = 13;
            setSliderValue(13, mx);
            return;
        }

        y2 += rowH;
        if (mx >= col2X && mx <= col2X + colW && my >= y2 && my <= y2 + 22) {
            this.activeSlider = 14;
            setSliderValue(14, mx);
            return;
        }

        y2 += rowH;
        if (mx >= col2X && mx <= col2X + colW && my >= y2 && my <= y2 + 22) {
            this.activeSlider = 15;
            setSliderValue(15, mx);
            return;
        }

        y2 += rowH;
        if (mx >= col2X && mx <= col2X + colW && my >= y2 && my <= y2 + 22) {
            this.activeSlider = 16;
            setSliderValue(16, mx);
            return;
        }

        y2 += rowH;
        if (mx >= col2X && mx <= col2X + colW && my >= y2 && my <= y2 + 22) {
            this.activeSlider = 17;
            setSliderValue(17, mx);
            return;
        }

        y2 += rowH;
        if (mx >= col2X && mx <= col2X + colW && my >= y2 && my <= y2 + 22) {
            this.activeSlider = 18;
            setSliderValue(18, mx);
        }
    }

    private void handleTabKollegenShaderRightClick(int px, int py, int pw, double mx, double my) {
        int colW = (pw - 40) / 2;
        int col1X = px + 16;
        int col2X = px + 24 + colW;
        int rowH = 26;

        int y1 = py + 14 - (int) this.scrollY;
        y1 += rowH;
        y1 += rowH;
        if (mx >= col1X && mx <= col1X + colW && my >= y1 && my <= y1 + 22) {
            openNumericModal(ModalTarget.SHADER_SATURATION);
            return;
        }
        y1 += rowH;
        if (mx >= col1X && mx <= col1X + colW && my >= y1 && my <= y1 + 22) {
            openNumericModal(ModalTarget.SHADER_VIBRANCE);
            return;
        }
        y1 += rowH;
        if (mx >= col1X && mx <= col1X + colW && my >= y1 && my <= y1 + 22) {
            openNumericModal(ModalTarget.SHADER_CONTRAST);
            return;
        }

        int y2 = py + 14 - (int) this.scrollY;
        if (mx >= col2X && mx <= col2X + colW && my >= y2 && my <= y2 + 22) {
            openNumericModal(ModalTarget.SHADER_BRIGHTNESS);
            return;
        }
        y2 += rowH;
        if (mx >= col2X && mx <= col2X + colW && my >= y2 && my <= y2 + 22) {
            openNumericModal(ModalTarget.SHADER_EXPOSURE);
            return;
        }
        y2 += rowH;
        if (mx >= col2X && mx <= col2X + colW && my >= y2 && my <= y2 + 22) {
            openNumericModal(ModalTarget.SHADER_BLOOM);
            return;
        }
        y2 += rowH;
        if (mx >= col2X && mx <= col2X + colW && my >= y2 && my <= y2 + 22) {
            openNumericModal(ModalTarget.SHADER_WARMTH);
            return;
        }
        y2 += rowH;
        if (mx >= col2X && mx <= col2X + colW && my >= y2 && my <= y2 + 22) {
            openNumericModal(ModalTarget.SHADER_VIGNETTE);
            return;
        }
        y2 += rowH;
        if (mx >= col2X && mx <= col2X + colW && my >= y2 && my <= y2 + 22) {
            openNumericModal(ModalTarget.SHADER_MOTION_BLUR);
        }
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.activeSlider = -1;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null) {
            mc.options.save();
        }
        ModuleManager.save();
        return super.mouseReleased(event);
    }

    private void handleSliderDrag(double mx) {
        if (this.activeSlider != -1) {
            setSliderValue(this.activeSlider, mx);
        }
    }

    private void setSliderValue(int id, double mx) {
        Minecraft mc = Minecraft.getInstance();
        int panelW = Math.min(this.width - 32, 540);
        int px = (this.width - panelW) / 2;
        int colW = panelW - 32;
        int colX = px + 16;
        float ratio = Math.max(0.0f, Math.min(1.0f, (float) ((mx - colX) / colW)));

        int halfW = (panelW - 40) / 2;
        int halfCol1X = px + 16;
        int halfCol2X = px + 24 + halfW;
        float half1Ratio = Math.max(0.0f, Math.min(1.0f, (float) ((mx - halfCol1X) / halfW)));
        float half2Ratio = Math.max(0.0f, Math.min(1.0f, (float) ((mx - halfCol2X) / halfW)));

        switch (id) {
            case 0 -> {
                int c2W = (panelW - 40) / 2;
                int c2X = px + 24 + c2W;
                float r = Math.max(0.0f, Math.min(1.0f, (float) ((mx - c2X) / c2W)));
                mc.options.gamma().set((double) r);
            }
            case 1 -> {
                int sd = 4 + Math.round(ratio * 28.0f);
                Performance.KollegenDistance.actualDistance.value = (double) sd;
                Performance.KollegenDistance.syncDistance();
            }
            case 2 -> {
                if (ratio <= 0.02f) {
                    if (Performance.KollegenDistance.instance != null) {
                        Performance.KollegenDistance.instance.enabled = false;
                    }
                    Performance.KollegenDistance.syncDistance();
                } else {
                    int val = 16 + Math.round(((ratio - 0.02f) / 0.98f) * 112.0f);
                    Performance.KollegenDistance.distanceLimit.value = (double) val;
                    if (Performance.KollegenDistance.instance != null) {
                        Performance.KollegenDistance.instance.enabled = true;
                    }
                    Performance.KollegenDistance.syncDistance();
                }
            }
            case 3 -> {
                int sim = 5 + Math.round(ratio * 27.0f);
                mc.options.simulationDistance().set(sim);
            }
            case 4 -> {
                int fps = ratio >= 0.98f ? 260 : 10 + Math.round(ratio * 250.0f);
                mc.options.framerateLimit().set(fps);
            }
            case 5 -> {
                int fov = 30 + Math.round(half1Ratio * 80.0f);
                mc.options.fov().set(fov);
            }
            case 6 -> {
                mc.options.fovEffectScale().set((double) half1Ratio);
            }
            case 7 -> {
                mc.options.screenEffectScale().set((double) half2Ratio);
            }
            case 10 -> {
                KollegenShader ks = KollegenShader.INSTANCE;
                if (ks != null) {
                    ks.saturationEnabled.value = half1Ratio > 0.02f;
                    ks.saturation.value = Math.round(half1Ratio * 3.0 * 20.0) / 20.0;
                }
            }
            case 11 -> {
                KollegenShader ks = KollegenShader.INSTANCE;
                if (ks != null) {
                    ks.vibranceEnabled.value = half1Ratio > 0.02f;
                    ks.vibrance.value = Math.round((0.5 + half1Ratio * 2.0) * 20.0) / 20.0;
                }
            }
            case 12 -> {
                KollegenShader ks = KollegenShader.INSTANCE;
                if (ks != null) {
                    ks.contrastEnabled.value = true;
                    ks.contrast.value = Math.round((0.5 + half1Ratio * 1.5) * 50.0) / 50.0;
                }
            }
            case 13 -> {
                KollegenShader ks = KollegenShader.INSTANCE;
                if (ks != null) {
                    ks.brightnessEnabled.value = half2Ratio > 0.02f;
                    ks.brightness.value = Math.round((0.5 + half2Ratio * 1.5) * 20.0) / 20.0;
                }
            }
            case 14 -> {
                KollegenShader ks = KollegenShader.INSTANCE;
                if (ks != null) {
                    ks.exposureEnabled.value = half2Ratio > 0.02f;
                    ks.exposure.value = Math.round((0.5 + half2Ratio * 1.5) * 20.0) / 20.0;
                }
            }
            case 15 -> {
                KollegenShader ks = KollegenShader.INSTANCE;
                if (ks != null) {
                    ks.bloomEnabled.value = half2Ratio > 0.02f;
                    ks.bloom.value = (double) half2Ratio;
                }
            }
            case 16 -> {
                KollegenShader ks = KollegenShader.INSTANCE;
                if (ks != null) {
                    ks.warmthEnabled.value = half2Ratio > 0.02f;
                    ks.warmth.value = Math.round((0.7 + half2Ratio * 0.7) * 20.0) / 20.0;
                }
            }
            case 17 -> {
                KollegenShader ks = KollegenShader.INSTANCE;
                if (ks != null) {
                    ks.vignetteEnabled.value = half2Ratio > 0.02f;
                    ks.vignetteStrength.value = (double) half2Ratio;
                }
            }
            case 18 -> {
                KollegenShader ks = KollegenShader.INSTANCE;
                if (ks != null) {
                    ks.motionBlurEnabled.value = half2Ratio > 0.02f;
                    ks.motionBlurStrength.value = (double) half2Ratio;
                }
            }
        }
    }

    private void renderTabGuiCustomization(GuiGraphicsExtractor g, int px, int py, int pw, int ph, int mx, int my) {
        int cardW = pw - 32;
        int cardX = px + 16;
        int colW = (cardW - 32) / 2;
        int col1X = cardX + 12;
        int col2X = cardX + 20 + colW;
        int rowH = 26;

        int curY = py + 14 - (int) this.scrollY;

        int card1H = 132;
        Glass.glassCard(g, cardX, curY, cardW, card1H, 8, Palette.PANEL, Glass.opacity * 0.85f, 0.2f, Glass.a(Palette.BORDER, 0.45f));
        Glass.rr(g, cardX + 12, curY + 11, 3, 14, 1.5f, Palette.ACCENT);
        Glass.textBold(g, this.font, "Minecraft Buttons", cardX + 20, curY + 11, Palette.TEXT, true);
        Glass.text(g, this.font, "Optik, Rundung und Effekte aller Buttons im Spiel.", cardX + 20, curY + 25, Palette.MUTED, false);

        int r1Y = curY + 40;
        renderSettingRow(g, col1X, r1Y, colW, 24, "Button-Stil", GuiCustomizationSettings.getButtonStyle().label, 0xFF38BDF8, mx, my, false);
        renderSettingRow(g, col2X, r1Y, colW, 24, "Button-Form", GuiCustomizationSettings.getButtonShape().label, 0xFF10B981, mx, my, false);

        int r2Y = r1Y + rowH + 2;
        int opPct = Math.round(GuiCustomizationSettings.getButtonOpacity() * 100);
        renderSettingRow(g, col1X, r2Y, colW, 24, "Transparenz", opPct + "%", 0xFFF59E0B, mx, my, true);
        float glowVal = GuiCustomizationSettings.getGlowStrength();
        int glowCol = glowVal > 0.01f ? 0xFF10B981 : 0xFFEF4444;
        renderSettingRow(g, col2X, r2Y, colW, 24, "Hover-Glanz", GuiCustomizationSettings.getGlowLabel(), glowCol, mx, my, true);

        int r3Y = r2Y + rowH + 2;
        renderSettingRow(g, col1X, r3Y, colW, 24, "Button-Rand", GuiCustomizationSettings.getButtonBorder().label, 0xFFA78BFA, mx, my, false);
        boolean prevHov = mx >= col2X && mx <= col2X + colW && my >= r3Y && my <= r3Y + 24;
        GuiCustomizationRenderer.drawCustomButton(g, col2X, r3Y, colW, 24, true, prevHov, GuiCustomizationSettings.getButtonStyle());
        Glass.textC(g, this.font, "▶ Klick-Vorschau", col2X + colW / 2, r3Y + 8, Palette.TEXT, true);

        curY += card1H + 12;

        int card2H = 104;
        Glass.glassCard(g, cardX, curY, cardW, card2H, 8, Palette.PANEL, Glass.opacity * 0.85f, 0.2f, Glass.a(Palette.BORDER, 0.45f));
        Glass.rr(g, cardX + 12, curY + 11, 3, 14, 1.5f, Palette.ACCENT);
        Glass.textBold(g, this.font, "Inventar & Kisten Menüs", cardX + 20, curY + 11, Palette.TEXT, true);
        Glass.text(g, this.font, "Design von Inventar, Kisten, Werkbank und Item-Slots.", cardX + 20, curY + 25, Palette.MUTED, false);

        int r4Y = curY + 40;
        renderSettingRow(g, col1X, r4Y, colW, 24, "Container-Stil", GuiCustomizationSettings.getContainerStyle().label, 0xFF38BDF8, mx, my, false);
        renderSettingRow(g, col2X, r4Y, colW, 24, "Container-Form", GuiCustomizationSettings.getContainerShape().label, 0xFF10B981, mx, my, false);

        int r5Y = r4Y + rowH + 2;
        int cOpPct = Math.round(GuiCustomizationSettings.getContainerOpacity() * 100);
        renderSettingRow(g, col1X, r5Y, colW, 24, "Transparenz", cOpPct + "%", 0xFFF59E0B, mx, my, true);
        renderSettingRow(g, col2X, r5Y, colW, 24, "Slot-Design", GuiCustomizationSettings.getSlotStyle().label, 0xFFA78BFA, mx, my, false);

        curY += card2H + 12;

        int card3H = 104;
        Glass.glassCard(g, cardX, curY, cardW, card3H, 8, Palette.PANEL, Glass.opacity * 0.85f, 0.2f, Glass.a(Palette.BORDER, 0.45f));
        Glass.rr(g, cardX + 12, curY + 11, 3, 14, 1.5f, Palette.ACCENT);
        Glass.textBold(g, this.font, "Globales Theme & Interface", cardX + 20, curY + 11, Palette.TEXT, true);
        Glass.text(g, this.font, "Farbschema, Kontrast und Menü-Akzente.", cardX + 20, curY + 25, Palette.MUTED, false);

        int r6Y = curY + 40;
        renderSettingRow(g, col1X, r6Y, colW, 24, "UI-Design", UiStyleManager.getGlobalStyle().title, 0xFF38BDF8, mx, my, false);
        renderSettingRow(g, col2X, r6Y, colW, 24, "Theme", GuiCustomizationSettings.getThemeDisplayName(), 0xFFA78BFA, mx, my, false);

        int r7Y = r6Y + rowH + 2;
        renderSettingRow(g, col1X, r7Y, colW, 24, "Akzentfarbe", GuiCustomizationSettings.getAccentName(), GuiCustomizationSettings.getAccentColor(), mx, my, false);
        boolean sHov = mx >= col2X && mx <= col2X + colW && my >= r7Y && my <= r7Y + 24;
        GuiCustomizationRenderer.drawCustomSlider(g, col2X, r7Y, colW, 24, 0.65, "Vorschau Regler (65%)", true, sHov, GuiCustomizationSettings.getButtonStyle());

        curY += card3H + 12;

        int cardSceneryH = 104;
        Glass.glassCard(g, cardX, curY, cardW, cardSceneryH, 8, Palette.PANEL, Glass.opacity * 0.85f, 0.2f, Glass.a(Palette.BORDER, 0.45f));
        Glass.rr(g, cardX + 12, curY + 11, 3, 14, 1.5f, 0xFF10B981);
        Glass.textBold(g, this.font, "Startbildschirm & Szenerie", cardX + 20, curY + 11, Palette.TEXT, true);
        Glass.text(g, this.font, "360° Panorama als rotierender Startscreen-Hintergrund (F2 im Spiel halten).", cardX + 20, curY + 25, Palette.MUTED, false);

        int rSceneryY1 = curY + 40;
        renderSettingRow(g, col1X, rSceneryY1, colW, 24, "Hintergrund", GuiCustomizationSettings.getSceneryDisplayName(), 0xFF10B981, mx, my, false);
        renderSettingRow(g, col2X, rSceneryY1, colW, 24, "Szenerien-Ordner", "Ordner öffnen ↗", 0xFF38BDF8, mx, my, false);

        int rSceneryY2 = rSceneryY1 + rowH + 2;
        boolean inGame = Minecraft.getInstance().player != null;
        renderSettingRow(g, col1X, rSceneryY2, colW, 24, "Szenerie erstellen", inGame ? "Jetzt aufnehmen 📷" : "Im Spiel F2 halten", inGame ? 0xFFF59E0B : 0x88FFFFFF, mx, my, false);
        renderSettingRow(g, col2X, rSceneryY2, colW, 24, "F2-Schnellauswahl", "Clippen & Szenerie", 0xFFA78BFA, mx, my, false);

        curY += cardSceneryH + 12;

        int card4H = 104;
        Glass.glassCard(g, cardX, curY, cardW, card4H, 8, Palette.PANEL, Glass.opacity * 0.85f, 0.2f, Glass.a(Palette.BORDER, 0.45f));
        Glass.rr(g, cardX + 12, curY + 11, 3, 14, 1.5f, Palette.ACCENT);
        Glass.textBold(g, this.font, "Multiplayer-Menü Einstellungen", cardX + 20, curY + 11, Palette.TEXT, true);
        Glass.text(g, this.font, "Karten-Grid, Spaltenanzahl und Server-Banner Skalierung.", cardX + 20, curY + 25, Palette.MUTED, false);

        int r8Y = curY + 40;
        renderSettingRow(g, col1X, r8Y, colW, 24, "Spalten", ServerListSettings.columns + " Spalten", 0xFF38BDF8, mx, my, true);
        renderSettingRow(g, col2X, r8Y, colW, 24, "Kartenhöhe", ServerListSettings.cardHeight + "px", 0xFF10B981, mx, my, true);

        int r9Y = r8Y + rowH + 2;
        renderSettingRow(g, col1X, r9Y, colW, 24, "Server-Icons", ServerListSettings.iconSize == -1 ? "Automatisch" : ServerListSettings.iconSize + "px", 0xFFE879F9, mx, my, true);
        renderSettingRow(g, col2X, r9Y, colW, 24, "Menübreite", ServerListSettings.menuWidth + "px", 0xFFF59E0B, mx, my, true);

        curY += card4H + 12;

        int card5H = 74;
        Glass.glassCard(g, cardX, curY, cardW, card5H, 8, Palette.PANEL, Glass.opacity * 0.85f, 0.2f, Glass.a(Palette.BORDER, 0.45f));
        Glass.rr(g, cardX + 12, curY + 11, 3, 14, 1.5f, 0xFFEF4444);
        Glass.textBold(g, this.font, "Auf Standard zurücksetzen", cardX + 20, curY + 11, Palette.TEXT, true);
        Glass.text(g, this.font, "Setzt alle Buttons, Inventare, Farben & Menüs auf Minecraft-Standard zurück.", cardX + 20, curY + 25, Palette.MUTED, false);

        int resetBtnY = curY + 40;
        int resetBtnW = cardW - 24;
        boolean rCardHov = mx >= col1X && mx <= col1X + resetBtnW && my >= resetBtnY && my <= resetBtnY + 24;
        GuiCustomizationRenderer.drawCustomButton(g, col1X, resetBtnY, resetBtnW, 24, true, rCardHov, GuiCustomizationSettings.getButtonStyle());
        Glass.textC(g, this.font, "↺ Alles auf Minecraft-Standard zurücksetzen", col1X + resetBtnW / 2, resetBtnY + 8, rCardHov ? 0xFFFFFFFF : 0xFFEF4444, true);

        curY += card5H + 12;

        int totalH = (curY + (int) this.scrollY) - py;
        this.maxScroll = Math.max(0.0f, totalH - (ph - 16));
    }

    private void handleTabGuiCustomizationClick(int px, int py, int pw, double mx, double my) {
        int cardW = pw - 32;
        int cardX = px + 16;
        int colW = (cardW - 32) / 2;
        int col1X = cardX + 12;
        int col2X = cardX + 20 + colW;
        int rowH = 26;

        int curY = py + 14 - (int) this.scrollY;

        int r1Y = curY + 40;
        if (mx >= col1X && mx <= col1X + colW && my >= r1Y && my <= r1Y + 24) {
            GuiCustomizationSettings.cycleButtonStyle();
            return;
        }
        if (mx >= col2X && mx <= col2X + colW && my >= r1Y && my <= r1Y + 24) {
            GuiCustomizationSettings.cycleButtonShape();
            return;
        }

        int r2Y = r1Y + rowH + 2;
        if (mx >= col1X && mx <= col1X + colW && my >= r2Y && my <= r2Y + 24) {
            if (mx >= col1X + colW - 24) {
                openNumericModal(ModalTarget.BUTTON_OPACITY);
            } else {
                GuiCustomizationSettings.cycleButtonOpacity();
            }
            return;
        }
        if (mx >= col2X && mx <= col2X + colW && my >= r2Y && my <= r2Y + 24) {
            if (mx >= col2X + colW - 24) {
                openNumericModal(ModalTarget.GLOW_STRENGTH);
            } else {
                GuiCustomizationSettings.cycleGlowStrength();
            }
            return;
        }

        int r3Y = r2Y + rowH + 2;
        if (mx >= col1X && mx <= col1X + colW && my >= r3Y && my <= r3Y + 24) {
            GuiCustomizationSettings.cycleButtonBorder();
            return;
        }
        if (mx >= col2X && mx <= col2X + colW && my >= r3Y && my <= r3Y + 24) {
            GuiCustomizationSettings.cycleButtonStyle();
            return;
        }

        curY += 132 + 12;

        int r4Y = curY + 40;
        if (mx >= col1X && mx <= col1X + colW && my >= r4Y && my <= r4Y + 24) {
            GuiCustomizationSettings.cycleContainerStyle();
            return;
        }
        if (mx >= col2X && mx <= col2X + colW && my >= r4Y && my <= r4Y + 24) {
            GuiCustomizationSettings.cycleContainerShape();
            return;
        }

        int r5Y = r4Y + rowH + 2;
        if (mx >= col1X && mx <= col1X + colW && my >= r5Y && my <= r5Y + 24) {
            if (mx >= col1X + colW - 24) {
                openNumericModal(ModalTarget.CONTAINER_OPACITY);
            } else {
                GuiCustomizationSettings.cycleContainerOpacity();
            }
            return;
        }
        if (mx >= col2X && mx <= col2X + colW && my >= r5Y && my <= r5Y + 24) {
            GuiCustomizationSettings.cycleSlotStyle();
            return;
        }

        curY += 104 + 12;

        int r6Y = curY + 40;
        if (mx >= col1X && mx <= col1X + colW && my >= r6Y && my <= r6Y + 24) {
            GuiCustomizationSettings.cycleUiStyle();
            return;
        }
        if (mx >= col2X && mx <= col2X + colW && my >= r6Y && my <= r6Y + 24) {
            GuiCustomizationSettings.cycleTheme();
            return;
        }

        int r7Y = r6Y + rowH + 2;
        if (mx >= col1X && mx <= col1X + colW && my >= r7Y && my <= r7Y + 24) {
            GuiCustomizationSettings.cycleAccentColor();
            return;
        }

        curY += 104 + 12;

        int rSceneryY1 = curY + 40;
        if (mx >= col1X && mx <= col1X + colW && my >= rSceneryY1 && my <= rSceneryY1 + 24) {
            GuiCustomizationSettings.cycleScenery();
            return;
        }
        if (mx >= col2X && mx <= col2X + colW && my >= rSceneryY1 && my <= rSceneryY1 + 24) {
            try {
                com.mojang.blaze3d.Blaze3D.openPath(dev.kollegen.client.scenery.SceneryManager.getSceneriesDir());
            } catch (Throwable ignored) {
            }
            return;
        }

        int rSceneryY2 = rSceneryY1 + rowH + 2;
        if (mx >= col1X && mx <= col1X + colW && my >= rSceneryY2 && my <= rSceneryY2 + 24) {
            if (Minecraft.getInstance().player != null) {
                this.onClose();
                dev.kollegen.client.scenery.SceneryManager.captureCurrentScenery();
            }
            return;
        }

        curY += 104 + 12;

        int r8Y = curY + 40;
        if (mx >= col1X && mx <= col1X + colW && my >= r8Y && my <= r8Y + 24) {
            if (mx >= col1X + colW - 24) {
                openNumericModal(ModalTarget.COLUMNS);
            } else {
                ServerListSettings.cycleColumns();
            }
            return;
        }
        if (mx >= col2X && mx <= col2X + colW && my >= r8Y && my <= r8Y + 24) {
            if (mx >= col2X + colW - 24) {
                openNumericModal(ModalTarget.CARD_HEIGHT);
            } else {
                ServerListSettings.cycleHeight();
            }
            return;
        }

        int r9Y = r8Y + rowH + 2;
        if (mx >= col1X && mx <= col1X + colW && my >= r9Y && my <= r9Y + 24) {
            if (mx >= col1X + colW - 24) {
                openNumericModal(ModalTarget.ICON_SIZE);
            } else {
                ServerListSettings.cycleIconSize();
            }
            return;
        }
        if (mx >= col2X && mx <= col2X + colW && my >= r9Y && my <= r9Y + 24) {
            if (mx >= col2X + colW - 24) {
                openNumericModal(ModalTarget.MENU_WIDTH);
            } else {
                ServerListSettings.cycleWidth();
            }
            return;
        }

        curY += 104 + 12;

        int resetBtnY = curY + 40;
        int resetBtnW = cardW - 24;
        if (mx >= col1X && mx <= col1X + resetBtnW && my >= resetBtnY && my <= resetBtnY + 24) {
            GuiCustomizationSettings.resetToVanillaDefaults();
            this.statusMessage = "✓ Auf Standard Minecraft zurückgesetzt!";
            this.statusMessageTime = System.currentTimeMillis();
        }
    }

    private void handleTabGuiCustomizationRightClick(int px, int py, int pw, double mx, double my) {
        int cardW = pw - 32;
        int cardX = px + 16;
        int colW = (cardW - 32) / 2;
        int col1X = cardX + 12;
        int col2X = cardX + 20 + colW;
        int rowH = 26;

        int curY = py + 14 - (int) this.scrollY;

        int r1Y = curY + 40;
        int r2Y = r1Y + rowH + 2;

        if (mx >= col1X && mx <= col1X + colW && my >= r2Y && my <= r2Y + 24) {
            openNumericModal(ModalTarget.BUTTON_OPACITY);
            return;
        }
        if (mx >= col2X && mx <= col2X + colW && my >= r2Y && my <= r2Y + 24) {
            openNumericModal(ModalTarget.GLOW_STRENGTH);
            return;
        }

        curY += 132 + 12;

        int r4Y = curY + 40;
        int r5Y = r4Y + rowH + 2;
        if (mx >= col1X && mx <= col1X + colW && my >= r5Y && my <= r5Y + 24) {
            openNumericModal(ModalTarget.CONTAINER_OPACITY);
            return;
        }

        curY += 104 + 12;
        curY += 104 + 12;
        curY += 104 + 12;

        int r8Y = curY + 40;
        if (mx >= col1X && mx <= col1X + colW && my >= r8Y && my <= r8Y + 24) {
            openNumericModal(ModalTarget.COLUMNS);
            return;
        }
        if (mx >= col2X && mx <= col2X + colW && my >= r8Y && my <= r8Y + 24) {
            openNumericModal(ModalTarget.CARD_HEIGHT);
            return;
        }

        int r9Y = r8Y + rowH + 2;
        if (mx >= col1X && mx <= col1X + colW && my >= r9Y && my <= r9Y + 24) {
            openNumericModal(ModalTarget.ICON_SIZE);
            return;
        }
        if (mx >= col2X && mx <= col2X + colW && my >= r9Y && my <= r9Y + 24) {
            openNumericModal(ModalTarget.MENU_WIDTH);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.modalTarget != ModalTarget.NONE) {
            if (event.key() == InputConstants.KEY_ESCAPE) {
                closeNumericModal(false);
                return true;
            }
            if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
                closeNumericModal(true);
                return true;
            }
            return super.keyPressed(event);
        }
        if (event.key() == InputConstants.KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        closeNumericModal(false);
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null) {
            mc.options.save();
        }
        ModuleManager.save();
        mc.setScreenAndShow(this.parent);
    }
}
