package dev.kollegen.client.ui;

import dev.kollegen.client.mixin.AbstractSliderButtonAccessor;
import dev.kollegen.client.mods.Palette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;

import java.util.List;

public final class GuiCustomizationRenderer {

    private GuiCustomizationRenderer() {
    }

    private static int getBorderColor(boolean hovered, boolean active) {
        if (!active) return 0x18FFFFFF;
        return switch (GuiCustomizationSettings.getButtonBorder()) {
            case ACCENT -> hovered ? Palette.ACCENT : Glass.a(Palette.ACCENT, 0.55f);
            case SUBTLE -> hovered ? 0x66FFFFFF : 0x30FFFFFF;
            case BRIGHT -> hovered ? 0xCCFFFFFF : 0x77FFFFFF;
            case NONE -> 0;
        };
    }

    public static boolean renderButtonBackground(GuiGraphicsExtractor g, AbstractButton button) {
        GuiCustomizationSettings.ButtonStyle style = GuiCustomizationSettings.getButtonStyle();
        if (style == GuiCustomizationSettings.ButtonStyle.VANILLA) {
            return false;
        }

        int x = button.getX();
        int y = button.getY();
        int w = button.getWidth();
        int h = button.getHeight();
        boolean active = button.isActive();
        boolean hovered = button.isHoveredOrFocused() && active;

        drawCustomButton(g, x, y, w, h, active, hovered, style);
        return true;
    }

    public static void drawCustomButton(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean active, boolean hovered, GuiCustomizationSettings.ButtonStyle style) {
        float r = GuiCustomizationSettings.getCornerRadius(h);
        float op = GuiCustomizationSettings.getButtonOpacity();
        float glow = GuiCustomizationSettings.getGlowStrength();

        switch (style) {
            case LIQUID_GLASS -> {
                if (glow > 0.01f) {
                    if (hovered) {
                        float sRadius = Math.max(2.0f, 8.0f * Math.min(2.5f, glow));
                        float sAlpha = Math.min(1.0f, 0.45f * glow);
                        Glass.shadowSoft(g, x, y, w, h, r, sRadius, 1.0f, Glass.a(Palette.ACCENT, sAlpha));
                    } else {
                        float sRadius = Math.max(1.0f, 5.0f * Math.min(2.0f, glow));
                        float sAlpha = Math.min(0.5f, 0.20f * glow);
                        Glass.shadowSoft(g, x, y, w, h, r, sRadius, 1.0f, Glass.a(0x000000, sAlpha));
                    }
                }

                int c1 = hovered ? Glass.mix(Palette.PANEL, Palette.ACCENT, 0.16f) : Palette.PANEL;
                int top = Glass.mix(c1, 0xFFFFFFFF, hovered ? 0.22f : 0.09f);
                int bot = Glass.mix(c1, 0xFF000000, 0.18f);

                float alphaMul = active ? op : (op * 0.40f);
                Glass.rrGrad(g, x, y, w, h, r, Glass.a(top, alphaMul), Glass.a(bot, alphaMul));

                if (r > 0 && w > r * 2) {
                    Glass.rr(g, x + r / 2.0f, y + 1.0f, w - r, 1.0f, 0.5f, Glass.a(0xFFFFFFFF, active ? (hovered ? 0.45f : 0.25f) : 0.08f));
                } else if (r == 0) {
                    Glass.line(g, x + 1, y + 1, x + w - 1, y + 1, 1.0f, Glass.a(0xFFFFFFFF, active ? (hovered ? 0.45f : 0.25f) : 0.08f));
                }

                int ringCol = getBorderColor(hovered, active);
                if ((ringCol >>> 24) != 0) {
                    Glass.ring(g, x, y, w, h, r, 0.5f, ringCol);
                }
            }
            case MODERN_DARK -> {
                int base = hovered ? Glass.mix(Palette.PANEL, Palette.ACCENT, 0.12f) : Palette.PANEL;
                float alphaMul = active ? op : (op * 0.45f);
                if (hovered && glow > 0.01f) {
                    float sRadius = Math.max(2.0f, 6.0f * Math.min(2.0f, glow));
                    float sAlpha = Math.min(0.8f, 0.35f * glow);
                    Glass.shadowSoft(g, x, y, w, h, r, sRadius, 1.0f, Glass.a(Palette.ACCENT, sAlpha));
                }
                Glass.rr(g, x, y, w, h, r, Glass.a(base, alphaMul));
                int ringCol = hovered ? Palette.ACCENT : Glass.a(Palette.BORDER, 0.6f);
                Glass.ring(g, x, y, w, h, r, 0.5f, active ? ringCol : 0x1AFFFFFF);
            }
            case NEON_CYBER -> {
                int base = hovered ? Glass.mix(Palette.BG, Palette.ACCENT, 0.08f) : Palette.BG;
                float alphaMul = active ? 0.95f : 0.45f;
                Glass.rr(g, x, y, w, h, r, Glass.a(base, alphaMul));
                if (hovered && glow > 0.01f) {
                    float sRadius = Math.max(3.0f, 10.0f * Math.min(2.5f, glow));
                    float sAlpha = Math.min(1.0f, 0.55f * glow);
                    Glass.shadowSoft(g, x, y, w, h, r, sRadius, 1.0f, Glass.a(Palette.ACCENT, sAlpha));
                }
                int ringCol = hovered ? Palette.ACCENT : Glass.a(Palette.ACCENT, 0.40f);
                Glass.ring(g, x, y, w, h, r, hovered ? 1.0f : 0.5f, active ? ringCol : 0x22888888);
            }
            case VANILLA -> {
                Glass.drawMinecraftButton(g, x, y, w, h, hovered ? 1.0f : 0.0f, false, !active);
            }
        }
    }

    public static boolean renderSlider(GuiGraphicsExtractor g, AbstractSliderButton slider, int mouseX, int mouseY) {
        GuiCustomizationSettings.ButtonStyle style = GuiCustomizationSettings.getButtonStyle();
        if (style == GuiCustomizationSettings.ButtonStyle.VANILLA) {
            return false;
        }

        int x = slider.getX();
        int y = slider.getY();
        int w = slider.getWidth();
        int h = slider.getHeight();
        boolean active = slider.isActive();
        boolean hovered = slider.isHoveredOrFocused() && active;

        double val = 0.0;
        if (slider instanceof AbstractSliderButtonAccessor acc) {
            val = acc.kollegen$getValue();
        }
        val = Math.max(0.0, Math.min(1.0, val));

        drawCustomSlider(g, x, y, w, h, val, slider.getMessage().getString(), active, hovered, style);
        return true;
    }

    public static void drawCustomSlider(GuiGraphicsExtractor g, int x, int y, int w, int h, double value, String label, boolean active, boolean hovered, GuiCustomizationSettings.ButtonStyle style) {
        float r = GuiCustomizationSettings.getCornerRadius(h);
        float op = GuiCustomizationSettings.getButtonOpacity();

        int trackBg = Glass.a(Glass.mix(Palette.BG, Palette.PANEL, 0.4f), 0.85f);
        Glass.rr(g, x, y, w, h, r, trackBg);

        int fillW = (int) (w * value);
        if (fillW > 0) {
            int fillCol = Glass.a(Palette.ACCENT, hovered ? 0.85f : 0.70f);
            Glass.rr(g, x, y, fillW, h, r, fillCol);
        }

        int ringCol = getBorderColor(hovered, active);
        if ((ringCol >>> 24) != 0) {
            Glass.ring(g, x, y, w, h, r, 0.5f, ringCol);
        }

        int thumbW = 8;
        int thumbX = (int) (x + (w - thumbW) * value);
        float tr = Math.min(thumbW / 2.0f, r);
        int thumbCol = hovered ? 0xFFFFFFFF : 0xFFE0E0E0;
        Glass.rr(g, thumbX, y, thumbW, h, tr, thumbCol);
        Glass.ring(g, thumbX, y, thumbW, h, tr, 0.5f, 0x88000000);

        Font font = Minecraft.getInstance().font;
        int textCol = active ? 0xFFFFFFFF : 0xFFAAAAAA;
        Glass.textC(g, font, label, x + w / 2, y + (h - 8) / 2, textCol, true);
    }

    public static boolean shouldCancelContainerBlit(Identifier texture) {
        if (GuiCustomizationSettings.getContainerStyle() == GuiCustomizationSettings.ContainerStyle.VANILLA) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gui == null) {
            return false;
        }
        Screen cur = mc.gui.screen();
        if (!(cur instanceof AbstractContainerScreen)) {
            return false;
        }
        if (cur instanceof CreativeModeInventoryScreen) {
            return false;
        }
        if (texture == null) {
            return false;
        }
        String path = texture.getPath();
        if (path.contains("creative")) {
            return false;
        }
        return path.startsWith("textures/gui/container/");
    }

    public static void drawCustomContainer(GuiGraphicsExtractor g, int x, int y, int w, int h, List<Slot> slots, Slot hoveredSlot, Screen screen) {
        GuiCustomizationSettings.ContainerStyle style = GuiCustomizationSettings.getContainerStyle();
        if (style == GuiCustomizationSettings.ContainerStyle.VANILLA) {
            return;
        }

        float r = GuiCustomizationSettings.getContainerCornerRadius();
        float op = GuiCustomizationSettings.getContainerOpacity();
        float glow = GuiCustomizationSettings.getGlowStrength();

        Glass.shadowSoft(g, x, y, w, h, r, 8.0f, 1.5f, 0x60000000);

        if (glow > 0.01f) {
            float sRadius = Math.max(1.0f, 3.5f * glow);
            float sAlpha = Math.min(0.18f, 0.06f * glow);
            Glass.shadowSoft(g, x, y, w, h, r, sRadius, 1.0f, Glass.a(Palette.ACCENT, sAlpha));
        }

        switch (style) {
            case LIQUID_GLASS -> {
                int top = Glass.mix(Palette.PANEL, 0xFFFFFFFF, 0.12f);
                int bot = Glass.mix(Palette.BG, 0xFF000000, 0.25f);
                Glass.rrGrad(g, x, y, w, h, r, Glass.a(top, op * 0.85f), Glass.a(bot, op * 0.95f));

                if (r > 0 && w > r * 2) {
                    Glass.rr(g, x + r / 2.0f, y + 1.0f, w - r, 1.0f, 0.5f, Glass.a(0xFFFFFFFF, 0.25f));
                }

                int ring = Glass.mix(0x35FFFFFF, Palette.ACCENT, Math.min(0.35f, 0.15f * glow));
                Glass.ring(g, x, y, w, h, r, 0.5f, ring);
            }
            case MODERN_DARK -> {
                Glass.rrGrad(g, x, y, w, h, r, Glass.a(Palette.BG, op * 0.92f), Glass.a(0x0A0C14, op * 0.98f));
                Glass.ring(g, x, y, w, h, r, 0.5f, 0x22FFFFFF);
            }
            case NEON -> {
                Glass.rr(g, x, y, w, h, r, Glass.a(0x060812, op * 0.95f));
                Glass.ring(g, x, y, w, h, r, 0.5f, Palette.ACCENT);
            }
            case VANILLA -> {
                return;
            }
        }

        if (screen instanceof InventoryScreen) {
            Glass.rr(g, x + 25, y + 7, 51, 72, 4.0f, 0x30000000);
            Glass.ring(g, x + 25, y + 7, 51, 72, 4.0f, 0.5f, 0x18FFFFFF);
            Glass.text(g, Minecraft.getInstance().font, "→", x + 135, y + 29, 0x99FFFFFF, false);
        }

        if (slots != null) {
            for (Slot s : slots) {
                if (s == null) continue;
                int sx = x + s.x;
                int sy = y + s.y;
                Glass.rr(g, sx - 1, sy - 1, 18, 18, 3.0f, 0x35000000);
                Glass.ring(g, sx - 1, sy - 1, 18, 18, 3.0f, 0.5f, 0x18FFFFFF);
            }
        }
    }
}
