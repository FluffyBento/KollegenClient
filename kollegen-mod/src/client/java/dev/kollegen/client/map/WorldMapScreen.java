package dev.kollegen.client.map;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import dev.kollegen.client.mods.modules.WaypointsModule;
import dev.kollegen.client.ui.Glass;
import dev.kollegen.client.waypoints.AddEditWaypointScreen;
import dev.kollegen.client.waypoints.Waypoint;
import dev.kollegen.client.waypoints.WaypointManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class WorldMapScreen extends Screen {
    private final Screen parent;
    private double viewX = Double.MIN_VALUE;
    private double viewZ = Double.MIN_VALUE;
    private float zoom = 1.0f;
    private boolean sidebarOpen = true;
    private String searchQuery = "";
    private int sidebarScroll = 0;

    private NativeImage mapImage;
    private DynamicTexture mapTexture;
    private Identifier mapTextureId;
    private boolean textureDirty = true;
    private double lastSampledX = Double.MIN_VALUE;
    private double lastSampledZ = Double.MIN_VALUE;
    private float lastSampledZoom = -1.0f;

    private Waypoint hoveredWaypoint = null;

    public WorldMapScreen(Screen parent) {
        super(Component.literal("Weltkarte"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (this.minecraft != null && this.minecraft.player != null && this.viewX == Double.MIN_VALUE) {
            this.viewX = this.minecraft.player.getX();
            this.viewZ = this.minecraft.player.getZ();
        }
        int w = Math.max(16, this.width);
        int h = Math.max(16, this.height);
        if (this.mapImage == null || this.mapImage.getWidth() != w || this.mapImage.getHeight() != h) {
            if (this.mapImage != null) {
                this.mapImage.close();
                this.mapTexture.close();
            }
            this.mapImage = new NativeImage(w, h, false);
            this.mapTexture = new DynamicTexture(() -> "kollegen_worldmap", this.mapImage);
            this.mapTextureId = Identifier.tryParse("kollegen:worldmap");
            if (this.minecraft != null) {
                this.minecraft.getTextureManager().register(this.mapTextureId, this.mapTexture);
            }
        }
        this.textureDirty = true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (this.minecraft == null || this.minecraft.level == null) {
            super.extractRenderState(g, mouseX, mouseY, delta);
            return;
        }

        g.fill(0, 0, this.width, this.height, 0xFF11141A);

        renderMapTexture(g);
        renderChunkGrid(g);
        renderWaypoints(g, mouseX, mouseY);
        renderPlayer(g);
        renderTopBar(g, mouseX, mouseY);

        if (this.sidebarOpen) {
            renderSidebar(g, mouseX, mouseY);
        }

        if (this.hoveredWaypoint != null && (mouseX < (this.sidebarOpen ? this.width - 250 : this.width))) {
            renderTooltip(g, this.hoveredWaypoint, mouseX, mouseY);
        }
    }

    private void renderMapTexture(GuiGraphicsExtractor g) {
        if (this.mapImage == null) return;
        int w = this.mapImage.getWidth();
        int h = this.mapImage.getHeight();
        boolean moved = Math.abs(this.viewX - this.lastSampledX) > 0.35 || Math.abs(this.viewZ - this.lastSampledZ) > 0.35;
        boolean zoomChanged = Math.abs(this.zoom - this.lastSampledZoom) > 0.04f;

        if (this.textureDirty || moved || zoomChanged) {
            int playerY = this.minecraft.player != null ? (int) Math.floor(this.minecraft.player.getY()) : 64;
            double halfW = w / 2.0;
            double halfH = h / 2.0;

            for (int py = 0; py < h; py++) {
                double worldZ = this.viewZ + (py - halfH) / this.zoom;
                int wz = (int) Math.floor(worldZ);
                for (int px = 0; px < w; px++) {
                    double worldX = this.viewX + (px - halfW) / this.zoom;
                    int wx = (int) Math.floor(worldX);
                    int col = MapEngine.INSTANCE.getBlockColor(this.minecraft.level, wx, wz, playerY);
                    this.mapImage.setPixel(px, py, col != 0 ? col : 0xFF181B20);
                }
            }

            this.mapTexture.upload();
            this.lastSampledX = this.viewX;
            this.lastSampledZ = this.viewZ;
            this.lastSampledZoom = this.zoom;
            this.textureDirty = false;
        }

        g.blit(RenderPipelines.GUI_TEXTURED, this.mapTextureId, 0, 0, 0.0f, 0.0f, this.width, this.height, w, h);
    }

    private void renderChunkGrid(GuiGraphicsExtractor g) {
        if (this.zoom < 0.85f) return;
        int minChunkX = (int) Math.floor((this.viewX - (this.width / 2.0) / this.zoom) / 16.0);
        int maxChunkX = (int) Math.ceil((this.viewX + (this.width / 2.0) / this.zoom) / 16.0);
        int minChunkZ = (int) Math.floor((this.viewZ - (this.height / 2.0) / this.zoom) / 16.0);
        int maxChunkZ = (int) Math.ceil((this.viewZ + (this.height / 2.0) / this.zoom) / 16.0);

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            float sx = (float) (this.width / 2.0 + ((cx * 16) - this.viewX) * this.zoom);
            if (sx >= 0 && sx <= this.width) {
                g.fill((int) sx, 0, (int) sx + 1, this.height, 0x12FFFFFF);
            }
        }
        for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
            float sy = (float) (this.height / 2.0 + ((cz * 16) - this.viewZ) * this.zoom);
            if (sy >= 0 && sy <= this.height) {
                g.fill(0, (int) sy, this.width, (int) sy + 1, 0x12FFFFFF);
            }
        }
    }

    private void renderWaypoints(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        this.hoveredWaypoint = null;
        List<Waypoint> list = WaypointManager.getVisibleForCurrentDimension();

        for (Waypoint wp : list) {
            float sx = (float) (this.width / 2.0 + (wp.x - this.viewX) * this.zoom);
            float sy = (float) (this.height / 2.0 + (wp.z - this.viewZ) * this.zoom);

            if (sx < -20 || sx > this.width + 20 || sy < -20 || sy > this.height + 20) {
                continue;
            }

            int color = wp.getEffectiveColor();
            Glass.disc(g, sx, sy, 7.0f, Glass.a(color, 0.4f));
            Glass.disc(g, sx, sy, 4.5f, color);
            Glass.disc(g, sx, sy, 2.0f, 0xFFFFFFFF);
            Glass.ring(g, sx - 4.5f, sy - 4.5f, 9.0f, 9.0f, 4.5f, 1.0f, 0xFFFFFFFF);

            String label = wp.name;
            int tw = this.font.width(label);
            int bx = (int) (sx - tw / 2.0f - 4);
            int by = (int) (sy + 7);
            Glass.fillRound(g, bx, by, tw + 8, 12, 3, 0xCC11141A);
            Glass.textC(g, this.font, label, (int) sx, by + 2, color, true);

            float dHover = (float) Math.hypot(mouseX - sx, mouseY - sy);
            if (dHover < 11.0f) {
                this.hoveredWaypoint = wp;
            }
        }
    }

    private void renderPlayer(GuiGraphicsExtractor g) {
        if (this.minecraft.player == null) return;
        float px = (float) (this.width / 2.0 + (this.minecraft.player.getX() - this.viewX) * this.zoom);
        float py = (float) (this.height / 2.0 + (this.minecraft.player.getZ() - this.viewZ) * this.zoom);

        if (px < -15 || px > this.width + 15 || py < -15 || py > this.height + 15) {
            return;
        }

        float yaw = this.minecraft.player.getYRot();
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(px, py);
        pose.rotate((float) Math.toRadians(yaw + 180.0));

        Glass.disc(g, 0, 0, 7.0f, 0x55000000);
        g.fill(-2, -9, 3, 4, 0xFFFFFFFF);
        g.fill(-4, 0, 5, 3, 0xFFFFFFFF);
        g.fill(-1, -11, 2, -9, 0xFF3B82F6);
        pose.popMatrix();

        Glass.disc(g, px, py, 4.0f, 0xFF3B82F6);
        Glass.disc(g, px, py, 1.8f, 0xFFFFFFFF);
    }

    private void renderTopBar(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int barW = this.width - 20;
        int barH = 34;
        Glass.fillRound(g, 10, 10, barW, barH, 8, 0xEE11141A);
        Glass.ring(g, 10, 10, barW, barH, 8, 1.0f, 0x33FFFFFF);

        Glass.text(g, this.font, "🗺 Weltkarte", 20, 22, 0xFFFFFFFF, true);

        String dim = "Überwelt";
        if (this.minecraft.level != null) {
            String path = this.minecraft.level.dimension().identifier().getPath();
            if (path.contains("nether")) dim = "Nether";
            else if (path.contains("end")) dim = "Das Ende";
        }
        Glass.text(g, this.font, "§8[" + dim + "§8]", 88, 22, 0xFF94A3B8, true);

        double curX = this.viewX + (mouseX - this.width / 2.0) / this.zoom;
        double curZ = this.viewZ + (mouseY - this.height / 2.0) / this.zoom;
        String coords = String.format(Locale.ROOT, "Cursor: %.0f, %.0f", curX, curZ);
        if (this.minecraft.player != null) {
            coords = String.format(Locale.ROOT, "Spieler: %.0f, %.0f, %.0f  |  %s", this.minecraft.player.getX(), this.minecraft.player.getY(), this.minecraft.player.getZ(), coords);
        }
        Glass.textC(g, this.font, coords, this.width / 2, 22, 0xFFCBD5E1, true);

        int bx = this.width - 240;
        drawButton(g, bx, 15, 24, 24, "−", mouseX, mouseY);
        bx += 28;
        String zoomStr = String.format(Locale.ROOT, "%.1fx", this.zoom);
        Glass.textC(g, this.font, zoomStr, bx + 16, 22, 0xFFFFFFFF, true);
        bx += 36;
        drawButton(g, bx, 15, 24, 24, "+", mouseX, mouseY);
        bx += 32;
        drawButton(g, bx, 15, 74, 24, "Zentrieren", mouseX, mouseY);
        bx += 80;
        drawButton(g, bx, 15, 54, 24, this.sidebarOpen ? "Liste ⯈" : "Liste ⯇", mouseX, mouseY);
    }

    private void renderSidebar(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int sideW = 240;
        int sideX = this.width - sideW - 10;
        int sideY = 50;
        int sideH = this.height - 60;

        Glass.fillRound(g, sideX, sideY, sideW, sideH, 8, 0xEE11141A);
        Glass.ring(g, sideX, sideY, sideW, sideH, 8, 1.0f, 0x33FFFFFF);

        Glass.text(g, this.font, "Wegpunkte", sideX + 12, sideY + 12, 0xFFFFFFFF, true);
        drawButton(g, sideX + sideW - 74, sideY + 8, 64, 20, "+ Neu", mouseX, mouseY);

        int searchY = sideY + 34;
        Glass.fillRound(g, sideX + 10, searchY, sideW - 20, 20, 5, 0xAA0B0D11);
        Glass.ring(g, sideX + 10, searchY, sideW - 20, 20, 5, 0.5f, 0x22FFFFFF);
        String sTxt = this.searchQuery.isEmpty() ? "§7Suchen..." : this.searchQuery;
        Glass.text(g, this.font, sTxt, sideX + 16, searchY + 6, 0xFFFFFFFF, false);

        int listY = searchY + 26;
        int listH = sideH - 68;
        g.enableScissor(sideX + 6, listY, sideX + sideW - 6, listY + listH);

        List<Waypoint> all = WaypointManager.getForCurrentDimension();
        List<Waypoint> filtered = new ArrayList<>();
        for (Waypoint wp : all) {
            if (this.searchQuery.isEmpty() || wp.name.toLowerCase().contains(this.searchQuery.toLowerCase())) {
                filtered.add(wp);
            }
        }

        int itemH = 32;
        int startY = listY - this.sidebarScroll;

        for (int i = 0; i < filtered.size(); i++) {
            Waypoint wp = filtered.get(i);
            int iy = startY + i * (itemH + 4);
            if (iy + itemH < listY || iy > listY + listH) continue;

            boolean hover = mouseX >= sideX + 10 && mouseX <= sideX + sideW - 10 && mouseY >= iy && mouseY <= iy + itemH;
            Glass.fillRound(g, sideX + 10, iy, sideW - 20, itemH, 6, hover ? 0xDD2A3342 : 0xAA181F2C);
            Glass.ring(g, sideX + 10, iy, sideW - 20, itemH, 6, 0.5f, hover ? 0x66FFFFFF : 0x22FFFFFF);

            int col = wp.getEffectiveColor();
            Glass.disc(g, sideX + 22, iy + itemH / 2.0f, 4.5f, col);
            Glass.disc(g, sideX + 22, iy + itemH / 2.0f, 2.0f, 0xFFFFFFFF);

            Glass.text(g, this.font, wp.name, sideX + 34, iy + 6, 0xFFFFFFFF, true);
            double dist = this.minecraft.player != null ? wp.distanceTo(this.minecraft.player.getX(), this.minecraft.player.getY(), this.minecraft.player.getZ()) : 0.0;
            String sub = wp.formatDistance(dist) + " · " + (int) wp.x + ", " + (int) wp.z;
            Glass.text(g, this.font, sub, sideX + 34, iy + 18, 0xFF94A3B8, false);

            int eyeX = sideX + sideW - 44;
            int eyeY = iy + 6;
            String eyeIcon = wp.visible ? "§a👁" : "§8👁";
            Glass.text(g, this.font, eyeIcon, eyeX, eyeY + 4, 0xFFFFFFFF, false);

            int editX = sideX + sideW - 24;
            Glass.text(g, this.font, "✎", editX, eyeY + 4, 0xFF94A3B8, false);
        }

        g.disableScissor();
    }

    private void renderTooltip(GuiGraphicsExtractor g, Waypoint wp, int mouseX, int mouseY) {
        int tx = mouseX + 12;
        int ty = mouseY - 12;
        int tw = Math.max(120, this.font.width(wp.name) + 20);
        int th = 48;

        if (tx + tw > this.width) tx = mouseX - tw - 12;
        if (ty + th > this.height) ty = this.height - th - 10;

        Glass.fillRound(g, tx, ty, tw, th, 6, 0xEE11141A);
        Glass.ring(g, tx, ty, tw, th, 6, 1.0f, 0x44FFFFFF);

        int col = wp.getEffectiveColor();
        Glass.disc(g, tx + 12, ty + 12, 4.0f, col);
        Glass.text(g, this.font, wp.name, tx + 20, ty + 8, 0xFFFFFFFF, true);

        String posStr = String.format(Locale.ROOT, "XYZ: %.0f, %.0f, %.0f", wp.x, wp.y, wp.z);
        Glass.text(g, this.font, posStr, tx + 10, ty + 22, 0xFF94A3B8, false);

        double dist = this.minecraft.player != null ? wp.distanceTo(this.minecraft.player.getX(), this.minecraft.player.getY(), this.minecraft.player.getZ()) : 0.0;
        Glass.text(g, this.font, "Distanz: " + wp.formatDistance(dist), tx + 10, ty + 34, 0xFF38BDF8, false);
    }

    private void drawButton(GuiGraphicsExtractor g, int x, int y, int w, int h, String txt, int mx, int my) {
        boolean hover = mx >= x && mx <= x + w && my >= y && my <= y + h;
        Glass.fillRound(g, x, y, w, h, 5, hover ? 0xDD334155 : 0xAA1E293B);
        Glass.ring(g, x, y, w, h, 5, 0.5f, hover ? 0x66FFFFFF : 0x22FFFFFF);
        Glass.textC(g, this.font, txt, x + w / 2, y + (h - 8) / 2, 0xFFFFFFFF, true);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        int mx = (int) event.x();
        int my = (int) event.y();

        if (my >= 10 && my <= 44) {
            int bx = this.width - 240;
            if (mx >= bx && mx <= bx + 24) {
                this.zoom = Math.max(0.25f, this.zoom - 0.25f);
                this.textureDirty = true;
                return true;
            }
            bx += 64;
            if (mx >= bx && mx <= bx + 24) {
                this.zoom = Math.min(4.0f, this.zoom + 0.25f);
                this.textureDirty = true;
                return true;
            }
            bx += 32;
            if (mx >= bx && mx <= bx + 74) {
                if (this.minecraft.player != null) {
                    this.viewX = this.minecraft.player.getX();
                    this.viewZ = this.minecraft.player.getZ();
                    this.textureDirty = true;
                }
                return true;
            }
            bx += 80;
            if (mx >= bx && mx <= bx + 54) {
                this.sidebarOpen = !this.sidebarOpen;
                return true;
            }
        }

        if (this.sidebarOpen) {
            int sideW = 240;
            int sideX = this.width - sideW - 10;
            int sideY = 50;
            int sideH = this.height - 60;

            if (mx >= sideX && mx <= sideX + sideW && my >= sideY && my <= sideY + sideH) {
                if (mx >= sideX + sideW - 74 && mx <= sideX + sideW - 10 && my >= sideY + 8 && my <= sideY + 28) {
                    double px = this.minecraft.player != null ? this.minecraft.player.getX() : this.viewX;
                    double py = this.minecraft.player != null ? this.minecraft.player.getY() : 64.0;
                    double pz = this.minecraft.player != null ? this.minecraft.player.getZ() : this.viewZ;
                    String dim = this.minecraft.level != null ? this.minecraft.level.dimension().identifier().toString() : "minecraft:overworld";
                    this.minecraft.setScreenAndShow(new AddEditWaypointScreen(this, new Waypoint("", px, py, pz, dim, 0xFF3B82F6, false, true)));
                    return true;
                }

                int listY = sideY + 60;
                int itemH = 32;
                int startY = listY - this.sidebarScroll;
                List<Waypoint> filtered = new ArrayList<>();
                for (Waypoint wp : WaypointManager.getForCurrentDimension()) {
                    if (this.searchQuery.isEmpty() || wp.name.toLowerCase().contains(this.searchQuery.toLowerCase())) {
                        filtered.add(wp);
                    }
                }

                for (int i = 0; i < filtered.size(); i++) {
                    Waypoint wp = filtered.get(i);
                    int iy = startY + i * (itemH + 4);
                    if (my >= iy && my <= iy + itemH && mx >= sideX + 10 && mx <= sideX + sideW - 10) {
                        if (mx >= sideX + sideW - 46 && mx <= sideX + sideW - 28) {
                            wp.visible = !wp.visible;
                            WaypointManager.save();
                            return true;
                        }
                        if (mx >= sideX + sideW - 26 && mx <= sideX + sideW - 10) {
                            this.minecraft.setScreenAndShow(new AddEditWaypointScreen(this, wp));
                            return true;
                        }
                        this.viewX = wp.x;
                        this.viewZ = wp.z;
                        this.textureDirty = true;
                        return true;
                    }
                }
                return true;
            }
        }

        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            double worldX = this.viewX + (mx - this.width / 2.0) / this.zoom;
            double worldZ = this.viewZ + (my - this.height / 2.0) / this.zoom;
            double worldY = this.minecraft.player != null ? this.minecraft.player.getY() : 64.0;
            String dim = this.minecraft.level != null ? this.minecraft.level.dimension().identifier().toString() : "minecraft:overworld";
            this.minecraft.setScreenAndShow(new AddEditWaypointScreen(this, new Waypoint("", worldX, worldY, worldZ, dim, 0xFF3B82F6, false, true)));
            return true;
        }

        return super.mouseClicked(event, bl);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            this.viewX -= dx / this.zoom;
            this.viewZ -= dy / this.zoom;
            this.textureDirty = true;
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (this.sidebarOpen && mx >= this.width - 250) {
            this.sidebarScroll = Math.max(0, this.sidebarScroll - (int) (v * 18));
            return true;
        }
        float factor = v > 0 ? 1.25f : 0.8f;
        this.zoom = Math.max(0.25f, Math.min(4.0f, this.zoom * factor));
        this.textureDirty = true;
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent ki) {
        if (ki.key() == InputConstants.KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        if (WaypointsModule.INSTANCE != null && WaypointsModule.INSTANCE.hotkeyMap.value >= 0 && ki.key() == WaypointsModule.INSTANCE.hotkeyMap.value) {
            this.onClose();
            return true;
        }
        if (ki.key() == InputConstants.KEY_C || ki.key() == InputConstants.KEY_SPACE) {
            if (this.minecraft != null && this.minecraft.player != null) {
                this.viewX = this.minecraft.player.getX();
                this.viewZ = this.minecraft.player.getZ();
                this.textureDirty = true;
                return true;
            }
        }
        if (ki.key() == InputConstants.KEY_BACKSPACE && !this.searchQuery.isEmpty()) {
            this.searchQuery = this.searchQuery.substring(0, this.searchQuery.length() - 1);
            return true;
        }
        return super.keyPressed(ki);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        if (this.sidebarOpen && event.isAllowedChatCharacter()) {
            this.searchQuery += event.codepointAsString();
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public void onClose() {
        if (this.mapImage != null) {
            this.mapImage.close();
            this.mapTexture.close();
            this.mapImage = null;
            this.mapTexture = null;
        }
        if (this.minecraft != null) {
            this.minecraft.setScreenAndShow(this.parent);
        }
    }
}
