package dev.kollegen.client.mods.modules;

import dev.kollegen.client.mods.BooleanSetting;
import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.ClickTracker;
import dev.kollegen.client.mods.ColorSetting;
import dev.kollegen.client.mods.HudModule;
import dev.kollegen.client.mods.ModeSetting;
import dev.kollegen.client.mods.Module;
import dev.kollegen.client.mods.ModuleManager;
import dev.kollegen.client.mods.Palette;
import dev.kollegen.client.mods.SliderSetting;
import dev.kollegen.client.ui.Glass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class Hud {

    private Hud() {
    }

    public static void register() {
        ModuleManager.register(new Coordinates());
        ModuleManager.register(new Fps());
        ModuleManager.register(new Ping());
        ModuleManager.register(new Tps());
        ModuleManager.register(new Direction());
        ModuleManager.register(new Clock());
        ModuleManager.register(new Keystrokes());
        ModuleManager.register(new Cps());
        ModuleManager.register(new ArmorHud());
        ModuleManager.register(new PotionHud());
        ModuleManager.register(new Speed());
        ModuleManager.register(new Crosshair());
    }

    private static class Coordinates extends HudModule {
        Coordinates() {
            super("coordinates", "Koordinaten", "Zeigt X / Y / Z und Dimension.");
        }

        @Override
        public void onRenderHud(GuiGraphics g, float td) {
            if (mc.player == null || mc.level == null) return;
            List<String> lines = new ArrayList<>();
            lines.add(String.format("X: %.1f", mc.player.getX()));
            lines.add(String.format("Y: %.1f", mc.player.getY()));
            lines.add(String.format("Z: %.1f", mc.player.getZ()));
            Identifier dim = mc.level.dimension().identifier();
            lines.add(dim.getNamespace().equals("minecraft") ? dim.getPath() : dim.toString());
            renderLines(g, lines, 0, 0);
        }
    }

    private static class Fps extends HudModule {
        Fps() {
            super("fps", "FPS", "Bildwiederholrate anzeigen.");
        }

        @Override
        public void onRenderHud(GuiGraphics g, float td) {
            List<String> lines = new ArrayList<>();
            lines.add("FPS: " + mc.getFps());
            renderLines(g, lines, 0, 0);
        }
    }

    private static class Ping extends HudModule {
        Ping() {
            super("ping", "Ping", "Latenz zum Server in ms.");
        }

        @Override
        public void onRenderHud(GuiGraphics g, float td) {
            List<String> lines = new ArrayList<>();
            int p = -1;
            try {
                if (mc.getConnection() != null && mc.player != null) {
                    var info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
                    if (info != null) p = info.getLatency();
                }
            } catch (Throwable ignored) {
            }
            lines.add("Ping: " + (p < 0 ? "–" : p + " ms"));
            renderLines(g, lines, 0, 0);
        }
    }

    private static class Tps extends HudModule {
        private long lastTickReal = -1;
        private double tps = 20;

        Tps() {
            super("tps", "TPS", "Server-Ticks pro Sekunde.");
        }

        @Override
        public void onTick() {
            long now = System.currentTimeMillis();
            if (lastTickReal != -1) {
                long dt = now - lastTickReal;
                if (dt > 0) tps = tps * 0.9 + (1000.0 / dt) * 0.1;
            }
            lastTickReal = now;
        }

        @Override
        public void onRenderHud(GuiGraphics g, float td) {
            List<String> lines = new ArrayList<>();
            lines.add("TPS: " + Math.round(tps));
            renderLines(g, lines, 0, 0);
        }
    }

    private static class Direction extends HudModule {
        Direction() {
            super("direction", "Richtung", "Blickrichtung (Himmelsrichtung + Yaw).");
        }

        @Override
        public void onRenderHud(GuiGraphics g, float td) {
            if (mc.player == null) return;
            float yaw = mc.player.getYRot();
            int y = (int) Math.floor(yaw);
            if (y < 0) y += 360;
            String cardinal;
            if (y >= 315 || y < 45) cardinal = "S";
            else if (y < 135) cardinal = "W";
            else if (y < 225) cardinal = "N";
            else cardinal = "E";
            List<String> lines = new ArrayList<>();
            lines.add("Blick: " + cardinal + " (" + ((int) yaw) + "°)");
            renderLines(g, lines, 0, 0);
        }
    }

    private static class Clock extends HudModule {
        Clock() {
            super("clock", "Uhrzeit", "Echte Uhrzeit (HH:MM:SS).");
        }

        @Override
        public void onRenderHud(GuiGraphics g, float td) {
            java.time.LocalTime t = java.time.LocalTime.now();
            List<String> lines = new ArrayList<>();
            lines.add(String.format("%02d:%02d:%02d", t.getHour(), t.getMinute(), t.getSecond()));
            renderLines(g, lines, 0, 0);
        }
    }

    private static class Keystrokes extends HudModule {
        Keystrokes() {
            super("keystrokes", "Keystrokes", "Zeigt gedrückte Tasten (W/A/S/D, Maus).");
        }

        @Override
        public void onRenderHud(GuiGraphics g, float td) {
            int cx = mc.getWindow().getGuiScaledWidth() / 2;
            int by = mc.getWindow().getGuiScaledHeight() - 70;
            int s = 18, gap = 2;
            int baseX = cx - s - gap / 2;
            boolean w = mc.options.keyUp.isDown();
            boolean a = mc.options.keyLeft.isDown();
            boolean sD = mc.options.keyDown.isDown();
            boolean d = mc.options.keyRight.isDown();
            drawKey(g, "W", baseX, by - s - gap, w);
            drawKey(g, "A", baseX - s - gap, by, a);
            drawKey(g, "S", baseX, by, sD);
            drawKey(g, "D", baseX + s + gap, by, d);
            boolean ml = ClickTracker.leftDown, mr = ClickTracker.rightDown;
            drawKey(g, "L", baseX - s - gap, by + s + gap, ml);
            drawKey(g, "R", baseX, by + s + gap, mr);
        }

        private void drawKey(GuiGraphics g, String label, int x, int y, boolean pressed) {
            int s = 18;
            Glass.fillRound(g, x, y, s, s, 4, pressed ? Palette.tint(Palette.ACCENT, 0xD8) : Palette.tint(Palette.PANEL2, 0xCC));
            g.drawString(mc.font, label, x + (s - mc.font.width(label)) / 2, y + (s - mc.font.lineHeight) / 2, pressed ? 0xffffffff : Palette.TEXT, true);
        }
    }

    private static class Cps extends HudModule {
        Cps() {
            super("cps", "CPS", "Klicks pro Sekunde (links/rechts).");
        }

        @Override
        public void onRenderHud(GuiGraphics g, float td) {
            List<String> lines = new ArrayList<>();
            lines.add("Links: " + ClickTracker.cps(ClickTracker.LEFT) + " CPS");
            lines.add("Rechts: " + ClickTracker.cps(ClickTracker.RIGHT) + " CPS");
            renderLines(g, lines, 0, 0);
        }
    }

    private static class ArmorHud extends HudModule {
        ArmorHud() {
            super("armor", "Rüstung", "Zeigt getragene Rüstung + Haltbarkeit.");
        }

        @Override
        public void onRenderHud(GuiGraphics g, float td) {
            if (mc.player == null) return;
            var armor = java.util.List.of(
                    mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD),
                    mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST),
                    mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS),
                    mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET));
            int n = armor.size();
            int w = 4 * 20 + 10, h = 20;
            int m = 6;
            int x = switch (position.index) {
                case 1, 3 -> mc.getWindow().getGuiScaledWidth() - w - m;
                default -> m;
            };
            int y = switch (position.index) {
                case 2, 3 -> mc.getWindow().getGuiScaledHeight() - h - m;
                default -> m;
            };
            if (background.value) {
                Glass.fillRound(g, x - 4, y - 4, w + 8, h + 8, 6, backgroundColor.value);
            }
            for (int i = 0; i < n; i++) {
                ItemStack stack = armor.get(i);
                int ix = x + i * 20;
                g.renderItem(stack, ix, y);
                if (!stack.isEmpty() && stack.isDamageableItem()) {
                    int dmg = stack.getDamageValue();
                    int max = Math.max(1, stack.getMaxDamage());
                    float f = 1f - (float) dmg / max;
                    int col = f > 0.5 ? Palette.GREEN : (f > 0.25 ? Palette.ACCENT : Palette.DANGER);
                    g.fill(ix, y + 17, (int) (16 * f), 2, col);
                }
            }
        }
    }

    private static class PotionHud extends HudModule {
        PotionHud() {
            super("potions", "Effekte", "Aktive Statuseffekte + Restdauer.");
        }

        @Override
        public void onRenderHud(GuiGraphics g, float td) {
            if (mc.player == null) return;
            List<String> lines = new ArrayList<>();
            for (MobEffectInstance e : mc.player.getActiveEffects()) {
                String name = e.getEffect().value().getDisplayName().getString();
                int sec = e.getDuration() / 20;
                lines.add(name + " " + (sec / 60) + ":" + String.format("%02d", sec % 60));
            }
            if (lines.isEmpty()) lines.add("Keine Effekte");
            renderLines(g, lines, 0, 0);
        }
    }

    private static class Speed extends HudModule {
        private double lastX = Double.NaN, lastZ = Double.NaN;
        private double speed = 0;

        Speed() {
            super("speed", "Speed", "Bewegungsgeschwindigkeit in m/s.");
        }

        @Override
        public void onTick() {
            if (mc.player == null) return;
            double x = mc.player.getX(), z = mc.player.getZ();
            if (!Double.isNaN(lastX)) {
                double d = Math.hypot(x - lastX, z - lastZ) * 20;
                speed = d;
            }
            lastX = x;
            lastZ = z;
        }

        @Override
        public void onRenderHud(GuiGraphics g, float td) {
            List<String> lines = new ArrayList<>();
            lines.add("Speed: " + String.format("%.1f", speed) + " m/s");
            renderLines(g, lines, 0, 0);
        }
    }

    private static class Crosshair extends HudModule {
        private final SliderSetting size = new SliderSetting("Größe", "", 4, 1, 20, 1);
        private final SliderSetting gap = new SliderSetting("Lücke", "", 2, 0, 12, 1);
        private final ModeSetting type = new ModeSetting("Typ", "", new String[]{"Kreuz", "Punkt", "Viereck"}, 0);

        Crosshair() {
            super("crosshair", "Fadenkreuz", "Eigenes Fadenkreuz über dem Vanilla-Kreuz.");
            add(size);
            add(gap);
            add(type);
        }

        @Override
        public void onRenderHud(GuiGraphics g, float td) {
            int cx = mc.getWindow().getGuiScaledWidth() / 2;
            int cy = mc.getWindow().getGuiScaledHeight() / 2;
            int s = (int) size.value;
            int gp = (int) gap.value;
            int c = color.value;
            if (type.index == 1) {
                g.fill(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2, c);
            } else if (type.index == 2) {
                Glass.fillRound(g, cx - s, cy - s, s * 2, s * 2, s, c);
            } else {
                g.fill(cx - s, cy - gp - s, cx + s, cy - gp, c);
                g.fill(cx - s, cy + gp, cx + s, cy + gp + s, c);
                g.fill(cx - gp - s, cy - s, cx - gp, cy + s, c);
                g.fill(cx + gp, cy - s, cx + gp + s, cy + s, c);
            }
        }
    }
}
