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
            this.position.index = 4; // Standard: Oben mittig
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

            // Kompass-Leiste (inspiriert vom Norisk Client)
            int w = (int) compassWidth.value;
            int h = 18;
            boolean deg = showDegrees.value;
            int totalH = deg ? h + 16 : h;

            int[] a = anchor(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), w, totalH);
            int x = a[0];
            int y = a[1] + (deg ? 16 : 0);

            if (background.value) {
                Glass.fillRound(g, x, y, w, h, 6, backgroundColor.value);
                Glass.ring(g, x, y, w, h, 6, 0.5f, 0x22FFFFFF);
            }
            if (accentBar.value) {
                Glass.rrGrad(g, x + 2, y + 2, 2.5f, h - 4, 1.2f, Palette.ACCENT, Glass.a(Palette.ACCENT, 0.35f));
            }

            markBounds(x, deg ? y - 16 : y, w, totalH);

            float centerX = x + w / 2.0f;
            float pixelsPerDegree = w / 100.0f;

            for (int degMark = 0; degMark < 360; degMark += 15) {
                float diff = (degMark - heading + 540f) % 360f - 180f;
                float mx = centerX + diff * pixelsPerDegree;

                if (mx < x + 6 || mx > x + w - 6) continue;

                float dist = Math.abs(mx - centerX);
                float maxDist = (w / 2.0f) - 6;
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
                    g.text(mc.font, label, Math.round(mx - tw / 2.0f), Math.round(y + (h - 8) / 2.0f), markColor, true);
                } else {
                    int tw = mc.font.width("|");
                    g.text(mc.font, "|", Math.round(mx - tw / 2.0f), Math.round(y + (h - 8) / 2.0f), markColor, true);
                }
            }

            if (deg) {
                int headingInt = Math.round(heading) % 360;
                String degStr = String.valueOf(headingInt);
                int dw = mc.font.width(degStr);
                g.text(mc.font, degStr, Math.round(centerX - dw / 2.0f), Math.round(y - 14), 0xFFFFFFFF, true);

                int rx = Math.round(centerX);
                int ry = y - 3;
                g.fill(rx - 3, ry - 2, rx + 4, ry - 1, 0xFFE05252);
                g.fill(rx - 2, ry - 1, rx + 3, ry,     0xFFE05252);
                g.fill(rx - 1, ry,     rx + 2, ry + 1, 0xFFE05252);
                g.fill(rx,     ry + 1, rx + 1, ry + 2, 0xFFE05252);
            }
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
            int cx = mc.getWindow().getGuiScaledWidth() / 2 + (int) offsetX.value;
            int by = mc.getWindow().getGuiScaledHeight() - 70 + (int) offsetY.value;
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
            markBounds(baseX - s - gap, by - s - gap, s * 3 + gap * 2, s * 3 + gap * 2);
        }

        private void drawKey(GuiGraphicsExtractor g, String label, int x, int y, boolean pressed) {
            int s = 18;
            Glass.fillRound(g, x, y, s, s, 4, pressed ? Palette.tint(Palette.ACCENT, 0xD8) : Palette.tint(Palette.PANEL2, 0xCC));
            g.text(mc.font, label, x + (s - mc.font.width(label)) / 2, y + (s - mc.font.lineHeight) / 2, pressed ? 0xffffffff : Palette.TEXT, true);
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
        ArmorHud() {
            super("armor", "Rüstung", "Zeigt die getragene Rüstung + Haltbarkeit untereinander.");
        }

        @Override
        public void onRenderHud(GuiGraphicsExtractor g, float td) {
            if (mc.player == null) return;
            var armor = java.util.List.of(
                    mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD),
                    mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST),
                    mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS),
                    mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET));
            int n = armor.size();
            int rowH = 20;
            int icon = 16;
            int textGap = 6;

            int textW = 0;
            for (ItemStack s : armor) {
                String t = durabilityText(s);
                if (!t.isEmpty()) textW = Math.max(textW, mc.font.width(t));
            }
            int w = (textW > 0 ? icon + textGap + textW : icon) + 6;
            int h = n * rowH;
            int[] a = anchor(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), w, h);
            int x = a[0];
            int y = a[1];
            if (background.value) {
                Glass.fillRound(g, x - 4, y - 4, w + 8, h + 8, 6, backgroundColor.value);
            }
            markBounds(x - 4, y - 4, w + 8, h + 8);
            for (int i = 0; i < n; i++) {
                ItemStack stack = armor.get(i);
                int ry = y + i * rowH;
                g.item(stack, x, ry + (rowH - icon) / 2);
                String t = durabilityText(stack);
                if (!t.isEmpty()) {
                    int dmg = stack.getDamageValue();
                    int max = Math.max(1, stack.getMaxDamage());
                    float f = 1f - (float) dmg / max;
                    int col = f > 0.5 ? Palette.GREEN : (f > 0.25 ? Palette.ACCENT : Palette.DANGER);
                    g.text(mc.font, t, x + icon + textGap, ry + (rowH - mc.font.lineHeight) / 2, col, true);
                }
            }
        }

        private static String durabilityText(ItemStack s) {
            if (s.isEmpty() || !s.isDamageableItem()) return "";
            int left = s.getMaxDamage() - s.getDamageValue();
            return left + "/" + s.getMaxDamage();
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
            int s = (int) size.value;
            int gp = (int) gap.value;
            int c = (canHitEnabled.value && canHit()) ? canHitColor.value : color.value;
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

            int w = icon + 12 + textW + 6;
            int h = 20;
            int[] a = anchor(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), w, h);
            int x = a[0];
            int y = a[1];

            if (background.value) {
                Glass.fillRound(g, x - 3, y - 2, w + 6, h + 4, 6, backgroundColor.value);
                int ringCol = onCooldown ? 0x66FF3333 : 0x6633FF33;
                Glass.ring(g, x - 3, y - 2, w + 6, h + 4, 6, 0.8f, ringCol);
            }
            markBounds(x - 3, y - 2, w + 6, h + 4);

            g.item(shield, x, y + (h - icon) / 2);
            int dotCol = onCooldown ? 0xFFFF3333 : 0xFF33FF33;
            g.fill(x + icon + 3, y + (h - 6) / 2, x + icon + 9, y + (h + 6) / 2, dotCol);
            g.text(mc.font, statusStr, x + icon + 12, y + (h - 8) / 2, Palette.TEXT, true);
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
            int h = 24;
            int w = count * cellW + 4;
            int[] a = anchor(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), w, h);
            int x = a[0];
            int y = a[1];

            if (background.value) {
                Glass.fillRound(g, x, y, w, h, 6, backgroundColor.value);
            }
            markBounds(x, y, w, h);

            for (int i = 0; i < count; i++) {
                ItemStack stack = items.get(i);
                int ix = x + 4 + i * cellW;
                g.item(stack, ix, y + 4);
                String cnt = String.valueOf(stack.getCount());
                g.text(mc.font, cnt, ix + 18, y + 8, Palette.TEXT, true);
            }
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
        public final SliderSetting scale = new SliderSetting("Skalierung", "Größe der Inventar-Vorschau anpassen.", 1.0, 0.4, 2.0, 0.05, v -> Math.round(v * 100.0) + "%", null);

        InventoryHud() {
            super("invhud", "Inventar-Vorschau", "Zeigt die 27 Slots des Inventars dauerhaft auf dem HUD an.");
            add(scale);
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
