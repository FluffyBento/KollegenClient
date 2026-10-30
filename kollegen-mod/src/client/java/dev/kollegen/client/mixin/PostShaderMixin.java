package dev.kollegen.client.mixin;

import dev.kollegen.client.mods.modules.KollegenShader;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(GameRenderer.class)
public class PostShaderMixin {

    @Shadow @Final private List<Identifier> requestedPostEffects;

    @Inject(method = "update", at = @At("TAIL"), require = 0)
    private void kollegen$injectCustomShaders(DeltaTracker deltaTracker, CallbackInfo ci) {
        try {
            if (KollegenShader.isShaderActive()) {
                this.requestedPostEffects.add(KollegenShader.SHADER_ID);
            }
            if (KollegenShader.isMotionBlurActive()) {
                this.requestedPostEffects.add(KollegenShader.BLUR_ID);
            }
        } catch (Throwable ignored) {
        }
    }
}
