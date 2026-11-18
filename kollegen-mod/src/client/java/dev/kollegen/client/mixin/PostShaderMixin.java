package dev.kollegen.client.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import dev.kollegen.client.KollegenMod;
import dev.kollegen.client.mods.modules.KollegenShader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class PostShaderMixin {

    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private RenderTarget mainRenderTarget;
    @Shadow @Final private CrossFrameResourcePool resourcePool;

    private static int kollegen$logCooldown = 0;

    @Inject(method = "applyPostEffects", at = @At("TAIL"), require = 0)
    private void kollegen$injectApply(CallbackInfo ci) {
        if (!KollegenShader.isShaderActive()) {
            return;
        }
        try {
            if (this.minecraft != null && this.minecraft.getShaderManager() != null && this.mainRenderTarget != null && this.resourcePool != null) {
                PostChain chain = this.minecraft.getShaderManager().getPostChain(KollegenShader.SHADER_ID, LevelTargetBundle.MAIN_TARGETS);
                if (chain != null) {
                    KollegenShader.updateShaderUniforms(chain);
                    chain.process(this.mainRenderTarget, this.resourcePool);
                    if (kollegen$logCooldown++ % 300 == 0) {
                        KollegenMod.LOGGER.info("KollegenShader: post effect processed successfully");
                    }
                } else if (kollegen$logCooldown++ % 300 == 0) {
                    KollegenMod.LOGGER.warn("KollegenShader: getPostChain returned null for {}", KollegenShader.SHADER_ID);
                }

                if (KollegenShader.isMotionBlurActive()) {
                    PostChain blurChain = this.minecraft.getShaderManager().getPostChain(KollegenShader.BLUR_ID, LevelTargetBundle.MAIN_TARGETS);
                    if (blurChain != null) {
                        KollegenShader.updateBlurUniforms(blurChain);
                        blurChain.process(this.mainRenderTarget, this.resourcePool);
                    }
                }
            }
        } catch (Throwable t) {
            if (kollegen$logCooldown++ % 300 == 0) {
                KollegenMod.LOGGER.error("KollegenShader applyPostEffects error", t);
            }
        }
    }
}
