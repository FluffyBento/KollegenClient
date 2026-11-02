package dev.kollegen.client.mods.modules;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;

public final class HitColorManager {

    private static OverlayTexture overlayTextureInstance = null;
    private static DynamicTexture dynamicTextureInstance = null;

    private HitColorManager() {}

    public static void register(OverlayTexture overlay, DynamicTexture texture) {
        overlayTextureInstance = overlay;
        dynamicTextureInstance = texture;
        update();
    }

    public static void update() {
        if (dynamicTextureInstance == null) return;
        try {
            NativeImage pixels = dynamicTextureInstance.getPixels();
            if (pixels == null) return;

            int hitColor = HitColor.getEffectiveColor();

            for (int row = 0; row < 8; row++) {
                for (int col = 0; col < 16; col++) {
                    pixels.setPixel(col, row, hitColor);
                }
            }
            dynamicTextureInstance.upload();
        } catch (Throwable ignored) {}
    }
}
