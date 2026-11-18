package dev.kollegen.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

public class MinimapTexture {
    public static final int RES = 256;

    private final NativeImage image;
    private final DynamicTexture texture;
    public final Identifier textureId;

    private double lastX = Double.MIN_VALUE;
    private double lastZ = Double.MIN_VALUE;
    private float lastZoom = -1.0f;
    private boolean lastCircle = false;
    private long lastUploadTime = 0L;

    public MinimapTexture() {
        this.image = new NativeImage(RES, RES, false);
        this.texture = new DynamicTexture(() -> "kollegen_minimap", this.image);
        this.textureId = Identifier.tryParse("kollegen:minimap");
        Minecraft.getInstance().getTextureManager().register(this.textureId, this.texture);
    }

    public void update(ClientLevel level, double playerX, double playerZ, int playerY, float zoom, boolean circle) {
        if (level == null) return;
        long now = System.currentTimeMillis();
        boolean moved = Math.abs(playerX - this.lastX) > 0.25 || Math.abs(playerZ - this.lastZ) > 0.25;
        boolean zoomChanged = Math.abs(zoom - this.lastZoom) > 0.04f;
        boolean shapeChanged = circle != this.lastCircle;
        boolean timeElapsed = (now - this.lastUploadTime) >= 200L;

        if (!shapeChanged && !zoomChanged && (!moved || !timeElapsed)) {
            return;
        }

        float half = RES / 2.0f;
        float rad = half - 1.0f;
        float radSq = rad * rad;

        for (int py = 0; py < RES; py++) {
            float offZ = py - half;
            for (int px = 0; px < RES; px++) {
                float offX = px - half;
                if (circle && (offX * offX + offZ * offZ) > radSq) {
                    this.image.setPixel(px, py, 0x00000000);
                } else {
                    int wx = (int) Math.floor(playerX + (offX / zoom));
                    int wz = (int) Math.floor(playerZ + (offZ / zoom));
                    int col = MapEngine.INSTANCE.getBlockColor(level, wx, wz, playerY);
                    this.image.setPixel(px, py, col != 0 ? col : 0xFF181B20);
                }
            }
        }

        this.texture.upload();
        this.lastX = playerX;
        this.lastZ = playerZ;
        this.lastZoom = zoom;
        this.lastCircle = circle;
        this.lastUploadTime = now;
    }
}
