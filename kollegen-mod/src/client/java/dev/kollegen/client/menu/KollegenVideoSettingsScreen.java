package dev.kollegen.client.menu;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kollegen.client.mods.modules.KollegenShader;
import dev.kollegen.client.mods.modules.Performance;
import dev.kollegen.client.mods.modules.RendererManager;
import dev.kollegen.client.ui.Glass;
import dev.kollegen.client.ui.LogoDraw;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ParticleStatus;

public class KollegenVideoSettingsScreen extends Screen {

    private final Screen parent;
    private int currentTab = 0;
    private static final String[] TABS = {
            "Grafik & Anzeige",
            "Performance & Chunks",
            "Kollegen Shader"
    };

    private boolean restartRequired = false;
    private int activeSlider = -1;

    public KollegenVideoSettingsScreen(Screen parent) {
        super(Component.literal("Kollegen Client Grafik-Einstellungen"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
    }

    private static void drawRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + h, color);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        Glass.frame();
        drawRect(g, 0, 0, this.width, this.height, 0xEE0B0F15);

        int logoW = 110;
        LogoDraw.draw(g, this.width / 2 - logoW / 2, 8, logoW);

        Glass.textC(g, this.font, "Kollegen Client Grafik-Einstellungen", this.width / 2, 38, 0xFFFFFFFF, true);

        String backendText = "Vulkan Native (SPIR-V)";
        int badgeCol = 0xFF8B5CF6;
        int badgeW = this.font.width(backendText) + 16;
        Glass.pill(g, this.width - badgeW - 14, 12, badgeW, 20, 0.0f, false, false);
        drawRect(g, this.width - badgeW - 14, 12, badgeW, 20, 0x33000000);
        Glass.textC(g, this.font, backendText, this.width - badgeW - 14 + badgeW / 2, 17, badgeCol, true);

        int tabY = 52;
        int tabW = 140;
        int totalTabsW = TABS.length * tabW + (TABS.length - 1) * 8;
        int startTabX = (this.width - totalTabsW) / 2;

        for (int i = 0; i < TABS.length; i++) {
            int tx = startTabX + i * (tabW + 8);
            boolean active = currentTab == i;
            boolean hov = mx >= tx && mx <= tx + tabW && my >= tabY && my <= tabY + 22;
            int bg = active ? 0xFF3B82F6 : (hov ? 0x44FFFFFF : 0x22FFFFFF);
            Glass.pill(g, tx, tabY, tabW, 22, hov ? 1.0f : 0.0f, active, false);
            drawRect(g, tx, tabY, tabW, 22, bg);
            Glass.textC(g, this.font, TABS[i], tx + tabW / 2, tabY + 7, active ? 0xFFFFFFFF : (hov ? 0xFFFFFFFF : 0xFFAAAAAA), true);
        }

        int panelW = Math.min(this.width - 32, 520);
        int panelH = this.height - 130;
        int panelX = (this.width - panelW) / 2;
        int panelY = 82;

        Glass.fillRound(g, panelX, panelY, panelW, panelH, 8, 0xDD141A23);

        if (this.activeSlider != -1) {
            handleSliderDrag(mx);
        }

        switch (currentTab) {
            case 0 -> renderTabDisplay(g, panelX, panelY, panelW, panelH, mx, my);
            case 1 -> renderTabPerformance(g, panelX, panelY, panelW, panelH, mx, my);
            case 2 -> renderTabKollegenShader(g, panelX, panelY, panelW, panelH, mx, my);
        }

        renderFooter(g, mx, my);

        super.extractRenderState(g, mx, my, pt);
    }

    private void renderFooter(GuiGraphicsExtractor g, int mx, int my) {
        int footY = this.height - 34;

        if (this.restartRequired) {
            String note = "Neustart empfohlen für geänderte Grafik-Optionen.";
            Glass.text(g, this.font, note, 20, footY + 6, 0xFFF59E0B, true);
        }

        int doneW = 100;
        int doneX = this.width - doneW - 16;
        boolean doneHov = mx >= doneX && mx <= doneX + doneW && my >= footY && my <= footY + 24;
        Glass.pill(g, doneX, footY, doneW, 24, doneHov ? 1.0f : 0.0f, true, false);
        Glass.textC(g, this.font, "Fertig", doneX + doneW / 2, footY + 8, 0xFFFFFFFF, true);
    }

    private void renderTabDisplay(GuiGraphicsExtractor g, int px, int py, int pw, int ph, int mx, int my) {
        Minecraft mc = Minecraft.getInstance();
        int rowY = py + 16;
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

        boolean fs = mc.options.fullscreen().get();
        renderButton(g, col1X, rowY, colW, 24, "Vollbild: " + (fs ? "Ein" : "Aus"), fs ? 0xFF10B981 : 0xFFEF4444, mx, my);

        boolean vs = mc.options.enableVsync().get();
        renderButton(g, col2X, rowY, colW, 24, "VSync: " + (vs ? "Ein" : "Aus"), vs ? 0xFF10B981 : 0xFFEF4444, mx, my);

        rowY += rowH;

        int sc = mc.options.guiScale().get();
        String scStr = sc == 0 ? "Auto" : String.valueOf(sc);
        renderButton(g, col1X, rowY, colW, 24, "GUI-Skalierung: " + scStr, 0xFF38BDF8, mx, my);

        CloudStatus cs = mc.options.cloudStatus().get();
        String csStr = cs == CloudStatus.OFF ? "Aus" : (cs == CloudStatus.FAST ? "Schnell" : "Schön");
        renderButton(g, col2X, rowY, colW, 24, "Wolken: " + csStr, 0xFFFFFFFF, mx, my);

        rowY += rowH;

        ParticleStatus ps = mc.options.particles().get();
        String psStr = ps == ParticleStatus.ALL ? "Alle" : (ps == ParticleStatus.DECREASED ? "Verringert" : "Minimal");
        renderButton(g, col1X, rowY, colW, 24, "Partikel: " + psStr, 0xFFFFFFFF, mx, my);

        double gam = mc.options.gamma().get();
        int gamPct = (int) Math.round(gam * 100.0);
        renderSlider(g, col2X, rowY, colW, 24, "Helligkeit: " + gamPct + "%", (float) gam, mx, my);
    }

    private void renderTabPerformance(GuiGraphicsExtractor g, int px, int py, int pw, int ph, int mx, int my) {
        Minecraft mc = Minecraft.getInstance();
        int rowY = py + 20;
        int rowH = 46;
        int colW = pw - 32;
        int colX = px + 16;

        int rd = mc.options.renderDistance().get();
        renderSlider(g, colX, rowY, colW, 24, "Render Distance (Echte Chunks): " + rd + " Chunks", (rd - 2) / 30.0f, mx, my);
        Glass.text(g, this.font, "Standard Minecraft Sichtweite, lädt und rendert aktive Spielwelt-Chunks.", colX + 4, rowY + 26, 0xFF888888, false);

        rowY += rowH;

        boolean bobbyOn = Performance.KollegenDistance.isDistanceEnabled();
        int limit = Performance.KollegenDistance.getDistanceLimit();
        String limitStr = !bobbyOn ? "Aus" : (limit >= 250 ? "Unbegrenzt" : limit + " Chunks");
        float limProg = !bobbyOn ? 0.0f : (limit >= 250 ? 1.0f : Math.max(0.0f, (limit - 16) / 240.0f));
        renderSlider(g, colX, rowY, colW, 24, "Inaktive Chunks: " + limitStr, limProg, mx, my);
        Glass.text(g, this.font, "Speichert und rendert gesehene Chunks dauerhaft im Hintergrund.", colX + 4, rowY + 26, 0xFF888888, false);

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

        int y1 = py + 14;
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

        int y2 = py + 14;
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
    }

    private void renderButton(GuiGraphicsExtractor g, int x, int y, int w, int h, String text, int color, int mx, int my) {
        boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + h;
        Glass.pill(g, x, y, w, h, hov ? 1.0f : 0.0f, false, false);
        drawRect(g, x, y, w, h, hov ? 0x44FFFFFF : 0x22FFFFFF);
        Glass.textC(g, this.font, text, x + w / 2, y + (h - 8) / 2, color, true);
    }

    private void renderSlider(GuiGraphicsExtractor g, int x, int y, int w, int h, String text, float progress, int mx, int my) {
        progress = Math.max(0.0f, Math.min(1.0f, progress));
        boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + h;
        Glass.pill(g, x, y, w, h, hov ? 1.0f : 0.0f, false, false);
        drawRect(g, x, y, w, h, 0x22000000);
        int fillW = Math.round(w * progress);
        if (fillW > 0) {
            drawRect(g, x, y, fillW, h, 0x443B82F6);
        }
        Glass.textC(g, this.font, text, x + w / 2, y + (h - 8) / 2, 0xFFFFFFFF, true);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        double mx = event.x();
        double my = event.y();

        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, bl);
        }

        int tabY = 52;
        int tabW = 140;
        int totalTabsW = TABS.length * tabW + (TABS.length - 1) * 8;
        int startTabX = (this.width - totalTabsW) / 2;

        for (int i = 0; i < TABS.length; i++) {
            int tx = startTabX + i * (tabW + 8);
            if (mx >= tx && mx <= tx + tabW && my >= tabY && my <= tabY + 22) {
                this.currentTab = i;
                return true;
            }
        }

        int footY = this.height - 34;
        int doneW = 100;
        int doneX = this.width - doneW - 16;
        if (mx >= doneX && mx <= doneX + doneW && my >= footY && my <= footY + 24) {
            this.onClose();
            return true;
        }

        int panelW = Math.min(this.width - 32, 520);
        int panelH = this.height - 130;
        int panelX = (this.width - panelW) / 2;
        int panelY = 82;

        if (currentTab == 0) {
            handleTabDisplayClick(panelX, panelY, panelW, mx, my);
            return true;
        } else if (currentTab == 1) {
            handleTabPerformanceClick(panelX, panelY, panelW, mx, my);
            return true;
        } else if (currentTab == 2) {
            handleTabKollegenShaderClick(panelX, panelY, panelW, mx, my);
            return true;
        }

        return super.mouseClicked(event, bl);
    }

    private void handleTabDisplayClick(int px, int py, int pw, double mx, double my) {
        Minecraft mc = Minecraft.getInstance();
        int rowY = py + 16;
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
            return;
        }

        rowY += rowH;

        if (mx >= col1X && mx <= col1X + colW && my >= rowY && my <= rowY + 24) {
            boolean fs = !mc.options.fullscreen().get();
            mc.options.fullscreen().set(fs);
            mc.getWindow().setFullscreen(fs);
            return;
        }

        if (mx >= col2X && mx <= col2X + colW && my >= rowY && my <= rowY + 24) {
            boolean vs = !mc.options.enableVsync().get();
            mc.options.enableVsync().set(vs);
            return;
        }

        rowY += rowH;

        if (mx >= col1X && mx <= col1X + colW && my >= rowY && my <= rowY + 24) {
            int sc = mc.options.guiScale().get();
            mc.options.guiScale().set((sc + 1) % 5);
            mc.resizeGui();
            return;
        }

        if (mx >= col2X && mx <= col2X + colW && my >= rowY && my <= rowY + 24) {
            CloudStatus cs = mc.options.cloudStatus().get();
            CloudStatus next = cs == CloudStatus.OFF ? CloudStatus.FAST : (cs == CloudStatus.FAST ? CloudStatus.FANCY : CloudStatus.OFF);
            mc.options.cloudStatus().set(next);
            return;
        }

        rowY += rowH;

        if (mx >= col1X && mx <= col1X + colW && my >= rowY && my <= rowY + 24) {
            ParticleStatus ps = mc.options.particles().get();
            ParticleStatus next = ps == ParticleStatus.ALL ? ParticleStatus.DECREASED : (ps == ParticleStatus.DECREASED ? ParticleStatus.MINIMAL : ParticleStatus.ALL);
            mc.options.particles().set(next);
            return;
        }

        if (mx >= col2X && mx <= col2X + colW && my >= rowY && my <= rowY + 24) {
            this.activeSlider = 0;
            float ratio = (float) ((mx - col2X) / colW);
            mc.options.gamma().set(Math.max(0.0, Math.min(1.0, (double) ratio)));
        }
    }

    private void handleTabPerformanceClick(int px, int py, int pw, double mx, double my) {
        int rowY = py + 20;
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

        int y1 = py + 14;
        if (mx >= col1X && mx <= col1X + colW && my >= y1 && my <= y1 + 22) {
            ks.enabled = !ks.enabled;
            if (!ks.enabled) {
                ks.onDisable();
            } else {
                ks.onEnable();
            }
            return;
        }

        y1 += rowH;
        if (mx >= col1X && mx <= col1X + colW && my >= y1 && my <= y1 + 22) {
            int next = (ks.preset.index + 1) % ks.preset.options.length;
            ks.preset.index = next;
            ks.applyPreset(next);
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

        int y2 = py + 14;
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

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.activeSlider = -1;
        return super.mouseReleased(event);
    }

    private void handleSliderDrag(double mx) {
        if (this.activeSlider != -1) {
            setSliderValue(this.activeSlider, mx);
        }
    }

    private void setSliderValue(int id, double mx) {
        Minecraft mc = Minecraft.getInstance();
        int panelW = Math.min(this.width - 32, 520);
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
                int rd = 2 + Math.round(ratio * 30.0f);
                mc.options.renderDistance().set(rd);
                Performance.KollegenDistance.actualDistance.value = (double) rd;
                Performance.KollegenDistance.syncDistance();
            }
            case 2 -> {
                if (ratio <= 0.05f) {
                    Performance.KollegenDistance.distanceLimit.value = 0.0;
                    Performance.KollegenDistance.syncDistance();
                } else if (ratio >= 0.95f) {
                    Performance.KollegenDistance.distanceLimit.value = 256.0;
                    Performance.KollegenDistance.syncDistance();
                } else {
                    int val = 16 + Math.round(((ratio - 0.05f) / 0.90f) * 240.0f);
                    Performance.KollegenDistance.distanceLimit.value = (double) val;
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
                    ks.warmth.value = Math.round((0.7 + half2Ratio * 0.7) * 50.0) / 50.0;
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

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null) {
            mc.options.save();
        }
        mc.setScreenAndShow(this.parent);
    }
}
