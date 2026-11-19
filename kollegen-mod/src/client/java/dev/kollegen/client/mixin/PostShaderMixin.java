package dev.kollegen.client.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import dev.kollegen.client.mods.modules.KollegenShader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
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

    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private RenderTarget mainRenderTarget;
    @Shadow @Final private CrossFrameResourcePool resourcePool;
    @Shadow @Final private List<PostChain> appliedPostEffects;

    @Inject(method = "preparePostEffects", at = @At("HEAD"))
    private void kollegen$injectPrepare(List<Identifier> list, CallbackInfo ci) {
        if (KollegenShader.isShaderActive()) {
            try {
                if (!list.contains(KollegenShader.SHADER_ID)) {
                    list.add(KollegenShader.SHADER_ID);
                }
                if (KollegenShader.isMotionBlurActive() && !list.contains(KollegenShader.BLUR_ID)) {
                    list.add(KollegenShader.BLUR_ID);
                }
            } catch (UnsupportedOperationException ignored) {
            }
        }
    }

    @Inject(method = "applyPostEffects", at = @At("HEAD"))
    private void kollegen$beforeApply(CallbackInfo ci) {
        if (!KollegenShader.isShaderActive()) {
            return;
        }
        try {
            if (this.minecraft != null && this.minecraft.getShaderManager() != null) {
                PostChain chain = this.minecraft.getShaderManager().getPostChain(KollegenShader.SHADER_ID, LevelTargetBundle.MAIN_TARGETS);
                if (chain != null) {
                    KollegenShader.updateShaderUniforms(chain);
                }
                if (KollegenShader.isMotionBlurActive()) {
                    PostChain blurChain = this.minecraft.getShaderManager().getPostChain(KollegenShader.BLUR_ID, LevelTargetBundle.MAIN_TARGETS);
                    if (blurChain != null) {
                        KollegenShader.updateBlurUniforms(blurChain);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "applyPostEffects", at = @At("TAIL"), require = 0)
    private void kollegen$injectApply(CallbackInfo ci) {
        if (!KollegenShader.isShaderActive()) {
            return;
        }
        try {
            if (this.minecraft != null && this.minecraft.getShaderManager() != null && this.mainRenderTarget != null && this.resourcePool != null) {
                PostChain chain = this.minecraft.getShaderManager().getPostChain(KollegenShader.SHADER_ID, LevelTargetBundle.MAIN_TARGETS);
                if (chain != null) {
                    boolean alreadyApplied = false;
                    if (this.appliedPostEffects != null) {
                        for (PostChain pc : this.appliedPostEffects) {
                            if (KollegenShader.SHADER_ID.equals(pc.id())) {
                                alreadyApplied = true;
                                break;
                            }
                        }
                    }
                    if (!alreadyApplied) {
                        KollegenShader.updateShaderUniforms(chain);
                        chain.process(this.mainRenderTarget, this.resourcePool);
                    }
                }
                if (KollegenShader.isMotionBlurActive()) {
                    PostChain blurChain = this.minecraft.getShaderManager().getPostChain(KollegenShader.BLUR_ID, LevelTargetBundle.MAIN_TARGETS);
                    if (blurChain != null) {
                        boolean blurApplied = false;
                        if (this.appliedPostEffects != null) {
                            for (PostChain pc : this.appliedPostEffects) {
                                if (KollegenShader.BLUR_ID.equals(pc.id())) {
                                    blurApplied = true;
                                    break;
                                }
                            }
                        }
                        if (!blurApplied) {
                            KollegenShader.updateBlurUniforms(blurChain);
                            blurChain.process(this.mainRenderTarget, this.resourcePool);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
