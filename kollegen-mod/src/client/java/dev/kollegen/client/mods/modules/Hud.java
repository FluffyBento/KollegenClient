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
import dev.kollegen.client.mods.StringSetting;
import dev.kollegen.client.ui.Glass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.food.FoodProperties;

import java.util.ArrayList;
import java.util.List;

public final class Hud {

    public static double lastAttackReach = -1.0;
    public static long lastAttackTime = 0L;

    public static void recordAttackHit(double dist) {
        lastAttackReach = dist;
        lastAttackTime = System.currentTimeMillis();
    }

    private Hud() {
    }

    public static void register() {
        ModuleManager.register(new Coordinates());
        ModuleManager.register(new Fps());
        ModuleManager.register(new Ping());
        ModuleManager.register(new Tps());
        ModuleManager.register(new Direction());
        ModuleManager.register(new DayCounter());
        ModuleManager.register(new Clock());
        ModuleManager.register(new Keystrokes());
        ModuleManager.register(new Cps());
        ModuleManager.register(new ArmorHud());
        ModuleManager.register(new PotionHud());
        ModuleManager.register(new Speed());
        ModuleManager.register(new Crosshair());
        ModuleManager.register(new Memory());
        ModuleManager.register(new Biome());
        ModuleManager.register(new EntityCount());
        ModuleManager.register(new Reach());
        ModuleManager.register(new Combo());
        ModuleManager.register(new Saturation());
        ModuleManager.register(new ServerInfo());
        ModuleManager.register(new TargetInfo());
        ModuleManager.register(new ShieldStatus());
        ModuleManager.register(new ItemCounter());
        ModuleManager.register(new InventoryHud());
    }

    private static class Coordinates extends HudModule {
        Coordinates() {
            super("coordinates", "Koordinaten", "Zeigt X / Y / Z und Dimension.");
        }

        @Override
        public void onRenderHud(GuiGraphicsExtractor g, float td) {
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
        public void onRenderHud(GuiGraphicsExtractor g, float td) {
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
        public void onRenderHud(GuiGraphicsExtractor g, float td) {
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
        public void onRenderHud(GuiGraphicsExtractor g, float td) {
            List<String> lines = new ArrayList<>();
            lines.add("TPS: " + Math.round(tps));
            renderLines(g, lines, 0, 0);
        }
    }

    private static class Direction extends HudModule {
        private final ModeSetting style = new ModeSetting("Stil", "Anzeigestil der Richtung.", new String[]{"Kompass-Leiste", "Text"}, 0);
        private final SliderSetting compassWidth = new SliderSetting("Breite", "Breite des Kompasses in Pixeln.", 180.0, 100.0, 300.0, 10.0);
        private final BooleanSetting showDegrees = new BooleanSetting("Gradzahl", "Zeigt den genauen Blickwinkel in Grad über der Leiste.", true);

        Direction() {
            super("direction", "Richtung", "Blickrichtung als moderne Kompass-Leiste oder Text.");
            this.position.index = 4;
            this.accentBar.value = false;
            add(style);
            add(compassWidth);
            add(showDegrees);
        }

        @Override
        public void onRenderHud(GuiGraphicsExtractor g, float td) {
            if (mc.player == null) return;
            float yaw = mc.player.getYRot();
            float heading = (yaw + 180f) % 360f;
            if (heading < 0f) heading += 360f;

            if (style.index == 1) {
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
                return;
            }

            int baseW = (int) compassWidth.value;
            int h = 18;
            boolean deg = showDegrees.value;
            int baseTotalH = deg ? h + 16 : h;

            float sc = (float) this.scale.value;
            int sw = Math.round(baseW * sc);
            int sh = Math.round(baseTotalH * sc);

            int[] a = anchor(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), sw, sh);
            int x = a[0];
            int y = a[1];

            markBounds(x, y, sw, sh);

            g.pose().pushMatrix();
            g.pose().translate(x, y);
            g.pose().scale(sc, sc);

            int drawY = deg ? 16 : 0;
            if (background.value) {
                Glass.fillRound(g, 0, drawY, baseW, h, 6, backgroundColor.value);
                Glass.ring(g, 0, drawY, baseW, h, 6, 0.5f, 0x22FFFFFF);
                if (accentBar.value) {
                    Glass.rrGrad(g, 2, drawY + 2, 2.5f, h - 4, 1.2f, Palette.ACCENT, Glass.a(Palette.ACCENT, 0.35f));
                }
            }

            float centerX = baseW / 2.0f;
            float pixelsPerDegree = baseW / 100.0f;

            for (int degMark = 0; degMark < 360; degMark += 15) {
                float diff = (degMark - heading + 540f) % 360f - 180f;
                float mx = centerX + diff * pixelsPerDegree;

                if (mx < 6 || mx > baseW - 6) continue;

                float dist = Math.abs(mx - centerX);
                float maxDist = (baseW / 2.0f) - 6;
                float fade = Math.max(0.18f, 1.0f - (dist / maxDist) * 0.82f);
                int aVal = (int) (fade * 255);
                int markColor = (aVal << 24) | (color.value & 0x00FFFFFF);

                if (degMark % 45 == 0) {
                    String label = switch (degMark) {
                        case 0 -> "N";
                        case 45 -> "NE";
                        case 90 -> "E";
                        case 135 -> "SE";
                        case 180 -> "S";
                        case 225 -> "SW";
                        case 270 -> "W";
                        case 315 -> "NW";
                        default -> "";
                    };
                    int tw = mc.font.width(label);
                    g.text(mc.font, label, Math.round(mx - tw / 2.0f), Math.round(drawY + (h - 8) / 2.0f), markColor, true);
                } else {
                    int tw = mc.font.width("|");
                    g.text(mc.font, "|", Math.round(mx - tw / 2.0f), Math.round(drawY + (h - 8) / 2.0f), markColor, true);
                }
            }

            if (deg) {
                int headingInt = Math.round(heading) % 360;
                String degStr = String.valueOf(headingInt);
                int dw = mc.font.width(degStr);
                g.text(mc.font, degStr, Math.round(centerX - dw / 2.0f), 2, 0xFFFFFFFF, true);

                int rx = Math.round(centerX);
                int ry = drawY - 3;
                g.fill(rx - 3, ry - 2, rx + 4, ry - 1, 0xFFE05252);
                g.fill(rx - 2, ry - 1, rx + 3, ry,     0xFFE05252);
                g.fill(rx - 1, ry,     rx + 2, ry + 1, 0xFFE05252);
                g.fill(rx,     ry + 1, rx + 1, ry + 2, 0xFFE05252);
            }

            g.pose().popMatrix();
        }
    }

    private static class DayCounter extends HudModule {
        private final StringSetting prefix = new StringSetting("Präfix", "Präfix vor der Tagesanzahl.", "Tag ");

        DayCounter() {
            super("daycounter", "Tageszähler", "Zeigt den aktuellen Minecraft-Tag der Welt an.");
            add(prefix);
        }

        @Override
        public void onRenderHud(GuiGraphicsExtractor g, float td) {
            if (mc.level == null) return;
            long day = mc.level.getLevelData() != null ? mc.level.getLevelData().getGameTime() / 24000L : 0L;
            List<String> lines = new ArrayList<>();
            lines.add((prefix.value == null ? "Tag " : prefix.value) + day);
            renderLines(g, lines, 0, 0);
        }
    }

    private static class Clock extends HudModule {
        Clock() {
            super("clock", "Uhrzeit", "Echte Uhrzeit (HH:MM:SS).");
        }

        @Override
        public void onRenderHud(GuiGraphicsExtractor g, float td) {
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
        public void onRenderHud(GuiGraphicsExtractor g, float td) {
            int s = 18, gap = 2;
            int baseW = s * 3 + gap * 2;
            int baseH = s * 3 + gap * 2;

            float sc = (float) this.scale.value;
            int sw = Math.round(baseW * sc);
            int sh = Math.round(baseH * sc);

            int cx = mc.getWindow().getGuiScaledWidth() / 2;
            int cy = mc.getWindow().getGuiScaledHeight() - 70;
            int baseX = cx - sw / 2 + (int) offsetX.value;
            int baseY = cy - sh / 2 + (int) offsetY.value;

            markBounds(baseX, baseY, sw, sh);

            g.pose().pushMatrix();
            g.pose().translate(baseX, baseY);
            g.pose().scale(sc, sc);

            int midX = s + gap;
            int midY = s + gap;
            boolean w = mc.options.keyUp.isDown();
            boolean a = mc.options.keyLeft.isDown();
            boolean sD = mc.options.keyDown.isDown();
            boolean d = mc.options.keyRight.isDown();
            drawKey(g, "W", midX, 0, w);
            drawKey(g, "A", 0, midY, a);
            drawKey(g, "S", midX, midY, sD);
            drawKey(g, "D", midX + s + gap, midY, d);
            boolean ml = ClickTracker.leftDown, mr = ClickTracker.rightDown;
            int mouseW = (baseW - gap) / 2;
            drawKeyCustomW(g, "L", 0, midY + s + gap, mouseW, ml);
            drawKeyCustomW(g, "R", mouseW + gap, midY + s + gap, mouseW, mr);

            g.pose().popMatrix();
        }

        private void drawKey(GuiGraphicsExtractor g, String label, int x, int y, boolean pressed) {
            int s = 18;
            Glass.fillRound(g, x, y, s, s, 4, pressed ? Palette.tint(Palette.ACCENT, 0xD8) : Palette.tint(Palette.PANEL2, 0xCC));
            g.text(mc.font, label, x + (s - mc.font.width(label)) / 2, y + (s - mc.font.lineHeight) / 2, pressed ? 0xffffffff : Palette.TEXT, true);
        }

        private void drawKeyCustomW(GuiGraphicsExtractor g, String label, int x, int y, int kw, boolean pressed) {
            int s = 18;
            Glass.fillRound(g, x, y, kw, s, 4, pressed ? Palette.tint(Palette.ACCENT, 0xD8) : Palette.tint(Palette.PANEL2, 0xCC));
            g.text(mc.font, label, x + (kw - mc.font.width(label)) / 2, y + (s - mc.font.lineHeight) / 2, pressed ? 0xffffffff : Palette.TEXT, true);
        }
    }

    private static class Cps extends HudModule {
        Cps() {
            super("cps", "CPS", "Klicks pro Sekunde (links/rechts).");
        }

        @Override
        public void onRenderHud(GuiGraphicsExtractor g, float td) {
            List<String> lines = new ArrayList<>();
            lines.add("Links: " + ClickTracker.cps(ClickTracker.LEFT) + " CPS");
            lines.add("Rechts: " + ClickTracker.cps(ClickTracker.RIGHT) + " CPS");
            renderLines(g, lines, 0, 0);
        }
    }

    private static class ArmorHud extends HudModule {
        private final SliderSetting layout = new SliderSetting(
                "Ausrichtung",
                "Layout der Rüstungsanzeige: Vertikal oder Horizontal.",
                0.0, 0.0, 1.0, 1.0,
                v -> ((int) Math.round(v) == 1) ? "Horizontal" : "Vertikal", null
        );
        private final BooleanSetting showHands = new BooleanSetting(
                "Hände anzeigen",
                "Zeigt zusätzlich Haupthand- und Nebenhand-Items an.",
                true
        );
        private final SliderSetting durabilityMode = new SliderSetting(
                "Haltbarkeit-Modus",
                "Format der Haltbarkeitsanzeige.",
                0.0, 0.0, 3.0, 1.0,
                v -> switch ((int) Math.round(v)) {
                    case 1 -> "Nur Restwert (420)";
                    case 2 -> "Prozent (85%)";
                    case 3 -> "Ausblenden";
                    default -> "Rest / Max (420/528)";
                }, null
        );
        private final BooleanSetting showDamageBar = new BooleanSetting(
                "Haltbarkeits-Balken",
                "Zeigt einen farbigen Mini-Balken unter jedem Item an.",
                true
        );

        ArmorHud() {
            super("armor", "Rüstung", "Zeigt die getragene Rüstung + Haltbarkeit vertikal oder horizontal.");
            add(layout);
            add(showHands);
            add(durabilityMode);
            add(showDamageBar);
        }

        @Override
        public void onRenderHud(GuiGraphicsExtractor g, float td) {
            if (mc.player == null) return;
            List<ItemStack> items = new ArrayList<>();
            items.add(mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD));
            items.add(mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST));
            items.add(mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS));
            items.add(mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET));
            if (showHands.value) {
                items.add(mc.player.getMainHandItem());
                items.add(mc.player.getOffhandItem());
            }

            boolean isHoriz = (int) Math.round(layout.value) == 1;
            int n = items.size();
            int icon = 16;
            int dMode = (int) Math.round(durabilityMode.value);

            if (isHoriz) {
                int colW = 22;
                int textH = (dMode != 3) ? 10 : 0;
                int baseH = icon + (showDamageBar.value ? 4 : 0) + textH + 4;
                int baseW = n * colW + 4;

                float sc = (float) this.scale.value;
                int sw = Math.round((baseW + 8) * sc);
                int sh = Math.round((baseH + 8) * sc);
                int[] a = anchor(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), sw, sh);
                int x = a[0];
                int y = a[1];

                markBounds(x, y, sw, sh);

                g.pose().pushMatrix();
                g.pose().translate(x, y);
                g.pose().scale(sc, sc);

                if (background.value) {
                    Glass.fillRound(g, 0, 0, baseW + 8, baseH + 8, 6, backgroundColor.value);
                    Glass.ring(g, 0, 0, baseW + 8, baseH + 8, 6, 0.5f, 0x22FFFFFF);
                }

                for (int i = 0; i < n; i++) {
                    ItemStack stack = items.get(i);
                    int rx = 4 + i * colW + 3;
                    if (!stack.isEmpty()) {
                        g.item(stack, rx, 4);
                        if (showDamageBar.value && stack.isDamageableItem()) {
                            renderMiniBar(g, rx, 4 + 17, 16, stack);
                        }
                        if (dMode != 3) {
                            String t = durabilityText(stack, dMode);
                            if (!t.isEmpty()) {
                                int col = durabilityColor(stack);
                                int tw = mc.font.width(t);
                                g.text(mc.font, t, rx + (16 - tw) / 2, 4 + icon + (showDamageBar.value ? 4 : 2), col, true);
                            }
                        }
                    }
                }
                g.pose().popMatrix();
            } else {
                int rowH = (showDamageBar.value ? 22 : 20);
                int textGap = 6;
                int textW = 0;
                if (dMode != 3) {
                    for (ItemStack s : items) {
                        String t = durabilityText(s, dMode);
                        if (!t.isEmpty()) textW = Math.max(textW, mc.font.width(t));
                    }
                }
                int baseW = (textW > 0 ? icon + textGap + textW : icon) + 6;
                int baseH = n * rowH;

                float sc = (float) this.scale.value;
                int sw = Math.round((baseW + 8) * sc);
                int sh = Math.round((baseH + 8) * sc);
                int[] a = anchor(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), sw, sh);
                int x = a[0];
                int y = a[1];

                markBounds(x, y, sw, sh);

                g.pose().pushMatrix();
                g.pose().translate(x, y);
                g.pose().scale(sc, sc);

                if (background.value) {
                    Glass.fillRound(g, 0, 0, baseW + 8, baseH + 8, 6, backgroundColor.value);
                    Glass.ring(g, 0, 0, baseW + 8, baseH + 8, 6, 0.5f, 0x22FFFFFF);
                }

                for (int i = 0; i < n; i++) {
                    ItemStack stack = items.get(i);
                    int ry = 4 + i * rowH;
                    if (!stack.isEmpty()) {
                        g.item(stack, 4, ry + 1);
                        if (showDamageBar.value && stack.isDamageableItem()) {
                            renderMiniBar(g, 4, ry + 18, 16, stack);
                        }
                        if (dMode != 3) {
                            String t = durabilityText(stack, dMode);
                            if (!t.isEmpty()) {
                                int col = durabilityColor(stack);
                                g.text(mc.font, t, 4 + icon + textGap, ry + (rowH - mc.font.lineHeight) / 2, col, true);
                            }
                        }
                    }
                }
                g.pose().popMatrix();
            }
        }

        private static void renderMiniBar(GuiGraphicsExtractor g, int bx, int by, int bw, ItemStack stack) {
            int dmg = stack.getDamageValue();
            int max = Math.max(1, stack.getMaxDamage());
            float f = Math.max(0.0f, Math.min(1.0f, 1.0f - (float) dmg / max));
            int fillW = Math.round(f * bw);
            int col = f > 0.5f ? Palette.GREEN : (f > 0.25f ? Palette.ACCENT : Palette.DANGER);
            g.fill(bx, by, bx + bw, by + 2, 0xAA000000);
            if (fillW > 0) {
                g.fill(bx, by, bx + fillW, by + 2, col);
            }
        }

        private static int durabilityColor(ItemStack stack) {
            if (!stack.isDamageableItem()) return Palette.TEXT;
            int dmg = stack.getDamageValue();
            int max = Math.max(1, stack.getMaxDamage());
            float f = 1f - (float) dmg / max;
            return f > 0.5f ? Palette.GREEN : (f > 0.25f ? Palette.ACCENT : Palette.DANGER);
        }

        private static String durabilityText(ItemStack s, int mode) {
            if (s.isEmpty() || !s.isDamageableItem()) return "";
            int left = s.getMaxDamage() - s.getDamageValue();
            int max = s.getMaxDamage();
            return switch (mode) {
                case 1 -> String.valueOf(left);
                case 2 -> Math.round((left * 100.0f) / max) + "%";
                case 3 -> "";
                default -> left + "/" + max;
            };
        }
    }

    private static class PotionHud extends HudModule {
        PotionHud() {
            super("potions", "Effekte", "Aktive Statuseffekte + Restdauer.");
        }

        @Override
        public void onRenderHud(GuiGraphicsExtractor g, float td) {
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
        public void onRenderHud(GuiGraphicsExtractor g, float td) {
            List<String> lines = new ArrayList<>();
            lines.add("Speed: " + String.format("%.1f", speed) + " m/s");
            renderLines(g, lines, 0, 0);
        }
    }

    private static class Crosshair extends HudModule {
        private final SliderSetting size = new SliderSetting("Größe", "", 4, 1, 20, 1);
        private final SliderSetting gap = new SliderSetting("Lücke", "", 2, 0, 12, 1);
        private final ModeSetting type = new ModeSetting("Typ", "", new String[]{"Kreuz", "Punkt", "Viereck"}, 0);
        private final BooleanSetting canHitEnabled = new BooleanSetting("Farbwechsel bei Treffer",
                "Fadenkreuz färbt sich um, wenn die anvisierte Person schlagbar ist.", false);
        private final ColorSetting canHitColor = new ColorSetting("Treffer-Farbe", "", 0xFFff5b6e);

        Crosshair() {
            super("crosshair", "Fadenkreuz", "Eigenes Fadenkreuz über dem Vanilla-Kreuz.");
            add(size);
            add(gap);
            add(type);
            add(canHitEnabled);
            add(canHitColor);
        }

        private boolean canHit() {
            try {
                var hr = mc.hitResult;
                if (hr != null && hr.getType() == net.minecraft.world.phys.HitResult.Type.ENTITY) {
                    var e = ((net.minecraft.world.phys.EntityHitResult) hr).getEntity();
                    if (e != mc.player && !(e instanceof net.minecraft.world.entity.player.Player p && p.isSpectator())) {
                        return true;
                    }
                }
            } catch (Throwable ignored) {
            }
            return false;
        }

        @Override
        public void onRenderHud(GuiGraphicsExtractor g, float td) {
            int cx = mc.getWindow().getGuiScaledWidth() / 2;
            int cy = mc.getWindow().getGuiScaledHeight() / 2;
            float sc = (float) scale.value;
            int s = (int) size.value;
            int gp = (int) gap.value;
            int c = (canHitEnabled.value && canHit()) ? canHitColor.value : color.value;
            g.pose().pushMatrix();
            g.pose().translate(cx, cy);
            g.pose().scale(sc, sc);
            if (type.index == 1) {
                g.fill(-s / 2, -s / 2, s / 2, s / 2, c);
            } else if (type.index == 2) {
                Glass.fillRound(g, -s, -s, s * 2, s * 2, s, c);
            } else {
                g.fill(-s, -gp - s, s, -gp, c);
                g.fill(-s, gp, s, gp + s, c);
                g.fill(-gp - s, -s, -gp, s, c);
                g.fill(gp, -s, gp + s, s, c);
            }
            g.pose().popMatrix();
        }
    }

    private static class Memory extends HudModule {
        Memory() {
            super("memory", "Arbeitsspeicher", "Zeigt belegten RAM (MB) an.");
        }

        @Override
        public void onRenderHud(GuiGraphicsExtractor g, float td) {
            Runtime r = Runtime.getRuntime();
            long used = (r.totalMemory() - r.freeMemory()) / (1024 * 1024);
            long total = r.maxMemory() / (1024 * 1024);
            List<String> lines = new ArrayList<>();
            lines.add("RAM: " + used + " / " + total + " MB");
            renderLines(g, lines, 0, 0);
        }
    }

    private static class Biome extends HudModule {
        Biome() {
            super("biome", "Biom", "Aktuelles Biom an der Spielerposition.");
        }

        @Override
        public void onRenderHud(GuiGraphicsExtractor g, float td) {
            List<String> lines = new ArrayList<>();
            if (mc.level != null && mc.player != null) {
                try {
                    var holder = mc.level.getBiome(mc.player.blockPosition());
                    String name = holder.getRegisteredName();
                    lines.add("Biom: " + name);
                } catch (Throwable ignored) {
                    lines.add("Biom: ?");
                }
            } else {
                lines.add("Biom: –");
            }
            renderLines(g, lines, 0, 0);
        }
    }

    private static class EntityCount extends HudModule {
        EntityCount() {
            super("entitycount", "Entitäten", "Anzahl geladener Entitäten.");
        }

        @Override
        public void onRenderHud(GuiGraphicsExtractor g, float td) {
            List<String> lines = new ArrayList<>();
            int n = 0;
            if (mc.level != null) {
                net.minecraft.world.level.entity.EntityTypeTest<net.minecraft.world.entity.Entity, net.minecraft.world.entity.Entity> test =
                        new net.minecraft.world.level.entity.EntityTypeTest<net.minecraft.world.entity.Entity, net.minecraft.world.entity.Entity>() {
                            public boolean test(net.minecraft.world.entity.EntityType<net.minecraft.world.entity.Entity> t) {
                                return true;
                            }

                            public Class<net.minecraft.world.entity.Entity> getBaseClass() {
                                return net.minecraft.world.entity.Entity.class;
                            }

                            public net.minecraft.world.entity.Entity tryCast(net.minecraft.world.entity.Entity e) {
                                return e;
                            }
                        };
                var all = mc.level.getEntities(test,
                        new net.minecraft.world.phys.AABB(-3.0E7, -3.0E7, -3.0E7, 3.0E7, 3.0E7, 3.0E7),
                        e -> true);
                n = all.size();
            }
            lines.add("Entitäten: " + n);
            renderLines(g, lines, 0, 0);
        }
    }

    private static class Reach extends HudModule {
        private final ModeSetting mode = new ModeSetting("Modus", "Anzeigemodus der Reichweite.", new String[]{"Letzter Treffer", "Fadenkreuz"}, 0);

        Reach() {
            super("reach", "Reichweite", "Distanz des letzten Angriffs-Treffers oder Ziels.");
            add(mode);
        }

        @Override
        public void onRenderHud(GuiGraphicsExtractor g, float td) {
            List<String> lines = new ArrayList<>();
            double d = -1;
            if (mc.player == null) return;

            if (mode.index == 0) {
                if (System.currentTimeMillis() - lastAttackTime < 3000L && lastAttackReach > 0) {
                    d = lastAttackReach;
                }
            } else {
                try {
                    var hr = mc.hitResult;
                    if (hr != null) {
                        if (hr.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                            var center = net.minecraft.world.phys.Vec3.atCenterOf(((net.minecraft.world.phys.BlockHitResult) hr).getBlockPos());
                            d = mc.player.getEyePosition().distanceTo(center);
                        } else if (hr.getType() == net.minecraft.world.phys.HitResult.Type.ENTITY) {
                            d = mc.player.getEyePosition().distanceTo(hr.getLocation());
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
            lines.add("Reichweite: " + (d < 0 ? "–" : String.format("%.2f", d) + " m"));
            renderLines(g, lines, 0, 0);
        }
    }

    private static class Combo extends HudModule {
        Combo() {
            super("combo", "Combo", "Aufeinanderfolgende Linksklicks (letzte 2 s).");
        }

        @Override
        public void onRenderHud(GuiGraphicsExtractor g, float td) {
            List<String> lines = new ArrayList<>();
            long now = System.currentTimeMillis();
            int c = 0;
            for (Long t : ClickTracker.LEFT) {
                if (now - t <= 2000) c++;
                else break;
            }
            lines.add("Combo: " + c);
            renderLines(g, lines, 0, 0);
        }
    }

    private static class Saturation extends HudModule {
        Saturation() {
            super("saturation", "Sättigung", "Aktuelle Nahrungssättigung und wie viel die gehaltene Speise auffüllt.");
        }

        @Override
        public void onRenderHud(GuiGraphicsExtractor g, float td) {
            List<String> lines = new ArrayList<>();
            if (mc.player != null) {
                var food = mc.player.getFoodData();
                float s = food.getSaturationLevel();
                int h = food.getFoodLevel();
                lines.add("Sättigung: " + String.format("%.1f", s));
                lines.add("Hunger: " + h + "/20");

                var held = mc.player.getMainHandItem();
                FoodProperties fp = held.get(DataComponents.FOOD);
                if (fp != null) {
                    int addH = fp.nutrition();
                    float addS = fp.saturation();
                    int newH = Math.min(20, h + addH);
                    float newS = Math.min(newH, s + addS);
                    lines.add("Speise: +" + addH + " Hunger, +" + String.format("%.1f", addS) + " Sat");
                    lines.add("Danach: " + newH + "/20, " + String.format("%.1f", newS) + " Sat");
                }
            } else {
                lines.add("Sättigung: –");
            }
            renderLines(g, lines, 0, 0);
        }
    }

    private static class ServerInfo extends HudModule {
        ServerInfo() {
            super("serverinfo", "Server-Info", "Adresse und Spielerzahl des Servers.");
        }

        @Override
        public void onRenderHud(GuiGraphicsExtractor g, float td) {
            List<String> lines = new ArrayList<>();
            ServerData srv = mc.getCurrentServer();
            if (srv != null) {
                lines.add("Server: " + srv.ip);
                try {
                    int n = mc.getConnection().getOnlinePlayers().size();
                    lines.add("Spieler: " + n);
                } catch (Throwable ignored) {
                }
            } else {
                lines.add("Modus: Einzelspieler");
            }
            renderLines(g, lines, 0, 0);
        }
    }

    private static class TargetInfo extends HudModule {
        TargetInfo() {
            super("targetinfo", "Ziel-Info", "Name & Leben des anvisierten Lebewesens.");
        }

        @Override
        public void onRenderHud(GuiGraphicsExtractor g, float td) {
            List<String> lines = new ArrayList<>();
            try {
                var hr = mc.hitResult;
                if (hr != null && hr.getType() == net.minecraft.world.phys.HitResult.Type.ENTITY) {
                    var e = ((net.minecraft.world.phys.EntityHitResult) hr).getEntity();
                    if (e instanceof LivingEntity le) {
                        lines.add(le.getName().getString());
                        lines.add("Leben: " + String.format("%.1f", le.getHealth()) + " / "
                                + String.format("%.1f", le.getMaxHealth()));
                    } else {
                        lines.add(e.getName().getString());
                    }
                } else {
                    lines.add("Kein Ziel");
                }
            } catch (Throwable ignored) {
                lines.add("Kein Ziel");
            }
            renderLines(g, lines, 0, 0);
        }
    }

    private static class ShieldStatus extends HudModule {
        ShieldStatus() {
            super("shield", "Schild-Status", "Zeigt an, ob das Schild einsatzbereit (grün) oder auf Cooldown (rot) ist.");
        }

        @Override
        public void onRenderHud(GuiGraphicsExtractor g, float td) {
            if (mc.player == null) return;
            ItemStack shield = mc.player.getOffhandItem();
            if (!shield.is(net.minecraft.world.item.Items.SHIELD)) {
                shield = mc.player.getMainHandItem();
                if (!shield.is(net.minecraft.world.item.Items.SHIELD)) return;
            }

            int icon = 16;
            boolean onCooldown = mc.player.getCooldowns().isOnCooldown(shield);

            String statusStr = onCooldown ? "§cDeaktiviert (Cooldown)" : "§aEinsatzbereit";
            int textW = mc.font.width(statusStr);

            int baseW = icon + 12 + textW + 6;
            int baseH = 20;
            float sc = (float) scale.value;
            int w = Math.round(baseW * sc);
            int h = Math.round(baseH * sc);
            int[] a = anchor(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), w, h);
            int x = a[0];
            int y = a[1];

            markBounds(x - Math.round(3 * sc), y - Math.round(2 * sc), Math.round((baseW + 6) * sc), Math.round((baseH + 4) * sc));

            g.pose().pushMatrix();
            g.pose().translate(x, y);
            g.pose().scale(sc, sc);

            if (background.value) {
                Glass.fillRound(g, -3, -2, baseW + 6, baseH + 4, 6, backgroundColor.value);
                int ringCol = onCooldown ? 0x66FF3333 : 0x6633FF33;
                Glass.ring(g, -3, -2, baseW + 6, baseH + 4, 6, 0.8f, ringCol);
            }

            g.item(shield, 0, (baseH - icon) / 2);
            int dotCol = onCooldown ? 0xFFFF3333 : 0xFF33FF33;
            g.fill(icon + 3, (baseH - 6) / 2, icon + 9, (baseH + 6) / 2, dotCol);
            g.text(mc.font, statusStr, icon + 12, (baseH - 8) / 2, Palette.TEXT, true);

            g.pose().popMatrix();
        }
    }

    private static class ItemCounter extends HudModule {
        public final BooleanSetting showTotem = new BooleanSetting("Totem", "Zählt Totems der Unsterblichkeit.", true);
        public final BooleanSetting showGapple = new BooleanSetting("Goldäpfel", "Zählt normale Goldäpfel.", true);
        public final BooleanSetting showNotch = new BooleanSetting("Verzauberte Äpfel", "Zählt verzauberte Goldäpfel.", true);
        public final BooleanSetting showPearl = new BooleanSetting("Enderperlen", "Zählt Enderperlen.", true);
        public final BooleanSetting showArrow = new BooleanSetting("Pfeile", "Zählt Pfeile im Inventar.", true);

        ItemCounter() {
            super("itemcounter", "Item-Zähler", "Zählt wichtige PvP-Items (Totems, Goldäpfel, Perlen, Pfeile) im Inventar.");
            add(showTotem);
            add(showGapple);
            add(showNotch);
            add(showPearl);
            add(showArrow);
        }

        @Override
        public void onRenderHud(GuiGraphicsExtractor g, float td) {
            if (mc.player == null) return;

            List<ItemStack> items = new ArrayList<>();
            if (showTotem.value) checkAdd(items, net.minecraft.world.item.Items.TOTEM_OF_UNDYING);
            if (showGapple.value) checkAdd(items, net.minecraft.world.item.Items.GOLDEN_APPLE);
            if (showNotch.value) checkAdd(items, net.minecraft.world.item.Items.ENCHANTED_GOLDEN_APPLE);
            if (showPearl.value) checkAdd(items, net.minecraft.world.item.Items.ENDER_PEARL);
            if (showArrow.value) checkAdd(items, net.minecraft.world.item.Items.ARROW);

            if (items.isEmpty()) return;

            int count = items.size();
            int cellW = 32;
            int baseH = 24;
            int baseW = count * cellW + 4;
            float sc = (float) scale.value;
            int w = Math.round(baseW * sc);
            int h = Math.round(baseH * sc);
            int[] a = anchor(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), w, h);
            int x = a[0];
            int y = a[1];

            markBounds(x, y, w, h);

            g.pose().pushMatrix();
            g.pose().translate(x, y);
            g.pose().scale(sc, sc);

            if (background.value) {
                Glass.fillRound(g, 0, 0, baseW, baseH, 6, backgroundColor.value);
            }

            for (int i = 0; i < count; i++) {
                ItemStack stack = items.get(i);
                int ix = 4 + i * cellW;
                g.item(stack, ix, 4);
                String cnt = String.valueOf(stack.getCount());
                g.text(mc.font, cnt, ix + 18, 8, Palette.TEXT, true);
            }

            g.pose().popMatrix();
        }

        private void checkAdd(List<ItemStack> list, net.minecraft.world.item.Item item) {
            int total = 0;
            int size = mc.player.getInventory().getContainerSize();
            for (int i = 0; i < size; i++) {
                ItemStack s = mc.player.getInventory().getItem(i);
                if (s.is(item)) total += s.getCount();
            }
            if (mc.player.getOffhandItem().is(item)) {
                total += mc.player.getOffhandItem().getCount();
            }
            if (total > 0) {
                list.add(new ItemStack(item, total));
            }
        }
    }

    private static class InventoryHud extends HudModule {
        InventoryHud() {
            super("invhud", "Inventar-Vorschau", "Zeigt die 27 Slots des Inventars dauerhaft auf dem HUD an.");
        }

        @Override
        public void onRenderHud(GuiGraphicsExtractor g, float td) {
            if (mc.player == null) return;

            int cols = 9;
            int rows = 3;
            int slotSize = 18;
            int baseW = cols * slotSize + 6;
            int baseH = rows * slotSize + 6;
            float sc = (float) scale.value;
            int w = Math.round(baseW * sc);
            int h = Math.round(baseH * sc);

            int[] a = anchor(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), w, h);
            int x = a[0];
            int y = a[1];

            markBounds(x, y, w, h);

            g.pose().pushMatrix();
            g.pose().translate(x, y);
            g.pose().scale(sc, sc);

            if (background.value) {
                Glass.fillRound(g, 0, 0, baseW, baseH, 6, backgroundColor.value);
                Glass.ring(g, 0, 0, baseW, baseH, 6, 0.5f, 0x22FFFFFF);
            }

            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    int slotIndex = 9 + r * cols + c;
                    int sx = 3 + c * slotSize;
                    int sy = 3 + r * slotSize;

                    Glass.fillRound(g, sx + 1, sy + 1, slotSize - 2, slotSize - 2, 3, 0x18FFFFFF);

                    if (slotIndex < mc.player.getInventory().getContainerSize()) {
                        ItemStack stack = mc.player.getInventory().getItem(slotIndex);
                        if (!stack.isEmpty()) {
                            g.item(stack, sx + 1, sy + 1);
                            if (stack.getCount() > 1) {
                                String cnt = String.valueOf(stack.getCount());
                                g.text(mc.font, cnt, sx + slotSize - mc.font.width(cnt) - 1, sy + slotSize - 7, 0xFFFFFFFF, true);
                            }
                        }
                    }
                }
            }

            g.pose().popMatrix();
        }
    }
}
