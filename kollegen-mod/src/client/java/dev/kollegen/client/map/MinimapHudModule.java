package dev.kollegen.client.map;

import dev.kollegen.client.mods.BooleanSetting;
import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.HudModule;
import dev.kollegen.client.mods.ModeSetting;
import dev.kollegen.client.mods.Palette;
import dev.kollegen.client.mods.SliderSetting;
import dev.kollegen.client.ui.Glass;
import dev.kollegen.client.waypoints.Waypoint;
import dev.kollegen.client.waypoints.WaypointManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix3x2fStack;

import java.util.List;
import java.util.Locale;

public class MinimapHudModule extends HudModule {
    public static MinimapHudModule INSTANCE;

    public final ModeSetting mapShape = new ModeSetting("Form", "Erscheinungsform der Karte.", new String[]{"Kreis", "Quadrat"}, 0);
    public final SliderSetting mapSize = new SliderSetting("Größe", "Größe der Karte in Pixeln.", 110.0, 80.0, 220.0, 5.0, v -> Math.round(v) + " px", null);
    public final SliderSetting zoom = new SliderSetting("Zoom", "Karten-Zoomstufe.", 1.0, 0.5, 3.0, 0.1, v -> String.format(Locale.ROOT, "%.1fx", v), null);
    public final BooleanSetting rotateWithPlayer = new BooleanSetting("Mitdrehen", "Rotiert die Karte passend zur Blickrichtung.", true);
    public final BooleanSetting showCoordinates = new BooleanSetting("Koordinaten", "Zeigt aktuelle Koordinaten unter der Karte an.", true);
    public final BooleanSetting showWaypoints = new BooleanSetting("Wegpunkte", "Zeigt Wegpunkte auf der Karte an.", true);
    public final BooleanSetting showEntities = new BooleanSetting("Radar", "Zeigt Spieler und Monster auf dem Radar an.", true);
    public final BooleanSetting showCardinals = new BooleanSetting("Kompass", "Zeigt N, O, S, W am Kartenrand an.", true);

    private MinimapTexture minimapTexture;

    public MinimapHudModule() {
        super("minimap", "Map", "Rendert eine moderne Mini-Karte im Liquid-Glass-Design mit Radar und Wegpunkten.", Category.HUD);
        INSTANCE = this;
        this.position.index = 1;
        this.accentBar.value = false;
        this.add(this.mapShape);
        this.add(this.mapSize);
        this.add(this.zoom);
        this.add(this.rotateWithPlayer);
        this.add(this.showCoordinates);
        this.add(this.showWaypoints);
        this.add(this.showEntities);
        this.add(this.showCardinals);
    }

    private MinimapTexture getTexture() {
        if (this.minimapTexture == null) {
            this.minimapTexture = new MinimapTexture();
        }
        return this.minimapTexture;
    }

    @Override
    public void onRenderHud(GuiGraphicsExtractor g, float td) {
        if (!this.enabled || mc.player == null || mc.level == null) return;

        int size = (int) this.mapSize.value;
        int extraH = this.showCoordinates.value ? 16 : 0;
        int totalH = size + extraH;

        int[] a = anchor(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), size, totalH);
        int x = a[0];
        int y = a[1];

        markBounds(x, y, size, totalH);

        float cx = x + size / 2.0f;
        float cy = y + size / 2.0f;
        float rad = (size / 2.0f) - 3.0f;
        boolean circle = this.mapShape.index == 0;

        if (circle) {
            Glass.disc(g, cx, cy, rad + 2.0f, Palette.tint(Palette.PANEL, 230));
            Glass.disc(g, cx, cy, rad, 0xEE11141A);
        } else {
            Glass.fillRound(g, x, y, size, size, 10, Palette.tint(Palette.PANEL, 230));
            Glass.fillRound(g, x + 2, y + 2, size - 4, size - 4, 8, 0xEE11141A);
        }

        MinimapTexture tex = getTexture();
        float z = (float) this.zoom.value;
        tex.update(mc.level, mc.player.getX(), mc.player.getZ(), (int) Math.floor(mc.player.getY()), z, circle);

        float playerYaw = mc.player.getYRot();
        boolean rotate = this.rotateWithPlayer.value;

        if (!circle) {
            g.enableScissor(x + 2, y + 2, x + size - 2, y + size - 2);
        }

        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(cx, cy);
        if (rotate) {
            pose.rotate((float) Math.toRadians(-playerYaw - 180.0));
        }
        pose.translate(-rad, -rad);
        int drawW = (int) (rad * 2.0f);
        g.blit(RenderPipelines.GUI_TEXTURED, tex.textureId, 0, 0, 0.0f, 0.0f, drawW, drawW, MinimapTexture.RES, MinimapTexture.RES);
        pose.popMatrix();

        if (this.showEntities.value) {
            renderEntities(g, cx, cy, rad, z, rotate, circle, playerYaw);
        }

        if (this.showWaypoints.value) {
            renderWaypoints(g, cx, cy, rad, z, rotate, circle, playerYaw);
        }

        if (!circle) {
            g.disableScissor();
        }

        renderPlayerArrow(g, cx, cy, rotate, playerYaw);

        if (circle) {
            Glass.ring(g, cx - rad, cy - rad, rad * 2.0f, rad * 2.0f, rad, 1.5f, 0x55FFFFFF);
        } else {
            Glass.ring(g, x + 2, y + 2, size - 4, size - 4, 8, 1.5f, 0x55FFFFFF);
        }

        if (this.showCardinals.value) {
            renderCardinals(g, cx, cy, rad, rotate, playerYaw);
        }

        if (this.showCoordinates.value) {
            String posStr = String.format(Locale.ROOT, "%.0f, %.0f, %.0f", mc.player.getX(), mc.player.getY(), mc.player.getZ());
            int tw = mc.font.width(posStr);
            int boxW = tw + 8;
            int boxX = (int) (cx - boxW / 2.0f);
            int boxY = y + size + 2;
            Glass.fillRound(g, boxX, boxY, boxW, 12, 4, 0xCC11141A);
            Glass.ring(g, boxX, boxY, boxW, 12, 4, 0.5f, 0x22FFFFFF);
            Glass.textC(g, mc.font, posStr, (int) cx, boxY + 2, Palette.TEXT, true);
        }
    }

    private void renderEntities(GuiGraphicsExtractor g, float cx, float cy, float rad, float z, boolean rotate, boolean circle, float playerYaw) {
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return;

        double px = mc.player.getX();
        double pz = mc.player.getZ();
        double rotRad = rotate ? Math.toRadians(-playerYaw - 180.0) : 0.0;
        double cos = Math.cos(rotRad);
        double sin = Math.sin(rotRad);

        for (Entity e : level.entitiesForRendering()) {
            if (e == mc.player) continue;
            double dx = (e.getX() - px) * z;
            double dz = (e.getZ() - pz) * z;

            double rx = rotate ? (dx * cos - dz * sin) : dx;
            double ry = rotate ? (dx * sin + dz * cos) : dz;

            float distSq = (float) (rx * rx + ry * ry);
            if (circle && distSq > (rad - 3.0f) * (rad - 3.0f)) continue;
            if (!circle && (Math.abs(rx) > rad - 3.0f || Math.abs(ry) > rad - 3.0f)) continue;

            float ex = (float) (cx + rx);
            float ey = (float) (cy + ry);

            if (e instanceof Player) {
                Glass.disc(g, ex, ey, 3.5f, 0xFF3B82F6);
                Glass.disc(g, ex, ey, 1.5f, 0xFFFFFFFF);
            } else if (e instanceof Monster) {
                Glass.disc(g, ex, ey, 2.5f, 0xFFEF4444);
            } else {
                Glass.disc(g, ex, ey, 2.0f, 0xFF10B981);
            }
        }
    }

    private void renderWaypoints(GuiGraphicsExtractor g, float cx, float cy, float rad, float z, boolean rotate, boolean circle, float playerYaw) {
        if (mc.player == null) return;
        List<Waypoint> list = WaypointManager.getVisibleForCurrentDimension();
        if (list.isEmpty()) return;

        double px = mc.player.getX();
        double pz = mc.player.getZ();
        double rotRad = rotate ? Math.toRadians(-playerYaw - 180.0) : 0.0;
        double cos = Math.cos(rotRad);
        double sin = Math.sin(rotRad);

        for (Waypoint wp : list) {
            double dx = (wp.x - px) * z;
            double dz = (wp.z - pz) * z;

            double rx = rotate ? (dx * cos - dz * sin) : dx;
            double ry = rotate ? (dx * sin + dz * cos) : dz;

            double dist = Math.sqrt(rx * rx + ry * ry);
            float maxDist = rad - 5.0f;
            boolean clamped = false;

            if (dist > maxDist) {
                double scale = maxDist / dist;
                rx *= scale;
                ry *= scale;
                clamped = true;
            }

            float wx = (float) (cx + rx);
            float wy = (float) (cy + ry);
            int color = wp.getEffectiveColor();

            if (wp.isDeath) {
                if (clamped) {
                    Glass.disc(g, wx, wy, 4.0f, 0xFFE53935);
                    Glass.ring(g, wx - 4.5f, wy - 4.5f, 9.0f, 9.0f, 4.5f, 1.2f, 0xFFFFFFFF);
                } else {
                    Glass.disc(g, wx, wy, 5.0f, 0xFFE53935);
                    Glass.disc(g, wx, wy, 2.2f, 0xFFFFFFFF);
                }
            } else if (clamped) {
                Glass.disc(g, wx, wy, 3.0f, color);
                Glass.ring(g, wx - 3.5f, wy - 3.5f, 7.0f, 7.0f, 3.5f, 1.0f, 0xFFFFFFFF);
            } else {
                Glass.disc(g, wx, wy, 4.0f, color);
                Glass.disc(g, wx, wy, 1.8f, 0xFFFFFFFF);
            }
        }
    }

    private void renderPlayerArrow(GuiGraphicsExtractor g, float cx, float cy, boolean rotate, float playerYaw) {
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(cx, cy);
        if (!rotate) {
            pose.rotate((float) Math.toRadians(playerYaw + 180.0));
        }

        Glass.disc(g, 0, 0, 4.0f, 0xAA000000);
        g.fill(-1, -6, 2, 4, 0xFFFFFFFF);
        g.fill(-3, 1, 4, 3, 0xFFFFFFFF);
        g.fill(-2, -2, 3, 1, 0xFFFFFFFF);
        g.fill(0, -7, 1, -6, 0xFF3B82F6);
        pose.popMatrix();
    }

    private void renderCardinals(GuiGraphicsExtractor g, float cx, float cy, float rad, boolean rotate, float playerYaw) {
        String[] dirs = {"N", "O", "S", "W"};
        double baseYaw = rotate ? (-playerYaw - 180.0) : 0.0;

        for (int i = 0; i < 4; i++) {
            double angleDeg = baseYaw + i * 90.0;
            double radAngle = Math.toRadians(angleDeg - 90.0);
            float dist = rad - 7.0f;
            float tx = (float) (cx + Math.cos(radAngle) * dist);
            float ty = (float) (cy + Math.sin(radAngle) * dist);

            int col = (i == 0) ? 0xFFEF4444 : 0xFFE2E8F0;
            int tw = mc.font.width(dirs[i]);
            g.text(mc.font, dirs[i], (int) (tx - tw / 2.0f), (int) (ty - 4.0f), col, true);
        }
    }
}
