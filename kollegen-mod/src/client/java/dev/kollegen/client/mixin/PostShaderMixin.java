package dev.kollegen.client.mixin;

import dev.kollegen.client.mods.modules.KollegenShader;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.state.GameRenderState;
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
    @Shadow @Final private List<Identifier> failedPostEffects;
    @Shadow @Final private List<PostChain> appliedPostEffects;
    @Shadow private GameRenderState gameRenderState;

    @Inject(method = "update", at = @At("TAIL"), require = 0)
    private void kollegen$injectUpdate(DeltaTracker deltaTracker, CallbackInfo ci) {
        try {
            if (this.failedPostEffects != null) {
                this.failedPostEffects.removeIf(id -> KollegenShader.SHADER_ID.equals(id) || KollegenShader.BLUR_ID.equals(id));
            }
            if (KollegenShader.isShaderActive()) {
                if (!this.requestedPostEffects.contains(KollegenShader.SHADER_ID)) {
                    this.requestedPostEffects.add(KollegenShader.SHADER_ID);
                }
            } else {
                this.requestedPostEffects.removeIf(KollegenShader.SHADER_ID::equals);
            }
            if (KollegenShader.isMotionBlurActive()) {
                if (!this.requestedPostEffects.contains(KollegenShader.BLUR_ID)) {
                    this.requestedPostEffects.add(KollegenShader.BLUR_ID);
                }
            } else {
                this.requestedPostEffects.removeIf(KollegenShader.BLUR_ID::equals);
            }
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "extract", at = @At("TAIL"), require = 0)
    private void kollegen$injectExtract(DeltaTracker deltaTracker, boolean bl, CallbackInfo ci) {
        try {
            if (this.failedPostEffects != null) {
                this.failedPostEffects.removeIf(id -> KollegenShader.SHADER_ID.equals(id) || KollegenShader.BLUR_ID.equals(id));
            }
            if (this.gameRenderState != null && this.gameRenderState.requestedPostEffects != null) {
                if (KollegenShader.isShaderActive()) {
                    if (!this.gameRenderState.requestedPostEffects.contains(KollegenShader.SHADER_ID)) {
                        this.gameRenderState.requestedPostEffects.add(KollegenShader.SHADER_ID);
                    }
                } else {
                    this.gameRenderState.requestedPostEffects.removeIf(KollegenShader.SHADER_ID::equals);
                }
                if (KollegenShader.isMotionBlurActive()) {
                    if (!this.gameRenderState.requestedPostEffects.contains(KollegenShader.BLUR_ID)) {
                        this.gameRenderState.requestedPostEffects.add(KollegenShader.BLUR_ID);
                    }
                } else {
                    this.gameRenderState.requestedPostEffects.removeIf(KollegenShader.BLUR_ID::equals);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "preparePostEffects", at = @At("HEAD"), require = 0)
    private void kollegen$injectPrepare(List<Identifier> list, CallbackInfo ci) {
        try {
            if (this.failedPostEffects != null) {
                this.failedPostEffects.removeIf(id -> KollegenShader.SHADER_ID.equals(id) || KollegenShader.BLUR_ID.equals(id));
            }
            if (list != null) {
                if (KollegenShader.isShaderActive()) {
                    if (!list.contains(KollegenShader.SHADER_ID)) {
                        list.add(KollegenShader.SHADER_ID);
                    }
                } else {
                    list.removeIf(KollegenShader.SHADER_ID::equals);
                }
                if (KollegenShader.isMotionBlurActive()) {
                    if (!list.contains(KollegenShader.BLUR_ID)) {
                        list.add(KollegenShader.BLUR_ID);
                    }
                } else {
                    list.removeIf(KollegenShader.BLUR_ID::equals);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "applyPostEffects", at = @At("HEAD"), require = 0)
    private void kollegen$injectApply(CallbackInfo ci) {
        try {
            if (this.appliedPostEffects != null && !this.appliedPostEffects.isEmpty()) {
                if (!KollegenShader.isShaderActive()) {
                    this.appliedPostEffects.removeIf(chain -> chain != null && KollegenShader.SHADER_ID.equals(chain.id()));
                }
                if (!KollegenShader.isMotionBlurActive()) {
                    this.appliedPostEffects.removeIf(chain -> chain != null && KollegenShader.BLUR_ID.equals(chain.id()));
                }
                for (PostChain chain : this.appliedPostEffects) {
                    if (chain == null) continue;
                    if (KollegenShader.SHADER_ID.equals(chain.id())) {
                        KollegenShader.updateShaderUniforms(chain);
                    } else if (KollegenShader.BLUR_ID.equals(chain.id())) {
                        KollegenShader.updateBlurUniforms(chain);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
