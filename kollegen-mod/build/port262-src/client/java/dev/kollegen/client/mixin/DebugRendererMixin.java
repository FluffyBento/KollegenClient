package dev.kollegen.client.mixin;

import dev.kollegen.client.mods.modules.HitboxSettings;
import dev.kollegen.client.mods.modules.LightLevelOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(DebugRenderer.class)
public class DebugRendererMixin {

    @Shadow
    @Final
    private List<DebugRenderer.SimpleDebugRenderer> renderers;

    @Inject(method = "refreshRendererList", at = @At("RETURN"))
    private void kollegen$onRefresh(CallbackInfo ci) {
        if (LightLevelOverlay.INSTANCE != null && !this.renderers.contains(LightLevelOverlay.INSTANCE)) {
            this.renderers.add(LightLevelOverlay.INSTANCE);
        }
        if (HitboxSettings.shouldAlwaysRender()) {
            boolean hasHitboxes = false;
            for (DebugRenderer.SimpleDebugRenderer r : this.renderers) {
                if (r instanceof EntityHitboxDebugRenderer) {
                    hasHitboxes = true;
                    break;
                }
            }
            if (!hasHitboxes) {
                this.renderers.add(new EntityHitboxDebugRenderer(Minecraft.getInstance()));
            }
        }
    }

    @Inject(method = "emitGizmos", at = @At("HEAD"))
    private void kollegen$onEmitGizmos(Frustum frustum, double cameraX, double cameraY, double cameraZ, float tickDelta, CallbackInfo ci) {
        if (LightLevelOverlay.INSTANCE != null && !this.renderers.contains(LightLevelOverlay.INSTANCE)) {
            this.renderers.add(LightLevelOverlay.INSTANCE);
        }
    }
}
