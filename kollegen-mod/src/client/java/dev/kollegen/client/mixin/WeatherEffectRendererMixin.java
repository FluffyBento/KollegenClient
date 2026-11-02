package dev.kollegen.client.mixin;

import dev.kollegen.client.mods.modules.Performance;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(WeatherEffectRenderer.class)
public class WeatherEffectRendererMixin {

    @Redirect(
            method = "extractRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/ClientLevel;getPrecipitationAt(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/biome/Biome$Precipitation;"
            )
    )
    private Biome.Precipitation kollegen$cullRainColumn(ClientLevel level, BlockPos pos) {
        if (!Performance.KollegenTricks.shouldCullRain()) {
            return level.getPrecipitationAt(pos);
        }

        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.gameRenderer != null) {
                Camera camera = mc.gameRenderer.mainCamera();
                if (camera != null) {
                    Frustum frustum = camera.getCullFrustum();
                    if (frustum != null) {
                        double camX = camera.position().x;
                        double camZ = camera.position().z;
                        double dx = (pos.getX() + 0.5) - camX;
                        double dz = (pos.getZ() + 0.5) - camZ;
                        double distSq = dx * dx + dz * dz;

                        // Columns immediately around the player (within 4 blocks) are always kept to avoid popping overhead
                        if (distSq > 16.0) {
                            int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
                            int topY = level.getMaxY();
                            if (topY > groundY) {
                                if (frustum instanceof FrustumAccessor fa) {
                                    int res = fa.kollegen$cubeInFrustum(pos.getX(), groundY, pos.getZ(), pos.getX() + 1.0, topY, pos.getZ() + 1.0);
                                    if (res >= 0) {
                                        return Biome.Precipitation.NONE;
                                    }
                                } else {
                                    AABB columnBox = new AABB(
                                            pos.getX(), groundY, pos.getZ(),
                                            pos.getX() + 1.0, topY, pos.getZ() + 1.0
                                    );
                                    if (!frustum.isVisible(columnBox)) {
                                        return Biome.Precipitation.NONE;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        return level.getPrecipitationAt(pos);
    }
}
