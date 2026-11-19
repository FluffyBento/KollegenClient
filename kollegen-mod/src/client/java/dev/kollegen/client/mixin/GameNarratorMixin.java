package dev.kollegen.client.mixin;

import net.minecraft.client.GameNarrator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.NarratorStatus;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameNarrator.class)
public class GameNarratorMixin {

    @Inject(method = "isActive", at = @At("HEAD"), cancellable = true)
    private void kollegen$checkActive(CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.options != null) {
            if (mc.options.narrator().get() == NarratorStatus.OFF || mc.options.getSoundSourceVolume(SoundSource.VOICE) <= 0.0f) {
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = "sayChatQueued", at = @At("HEAD"), cancellable = true)
    private void kollegen$suppressSayChatQueued(Component component, CallbackInfo ci) {
        if (kollegen$shouldMute()) {
            ci.cancel();
        }
    }

    @Inject(method = "saySystemChatQueued", at = @At("HEAD"), cancellable = true)
    private void kollegen$suppressSaySystemChatQueued(Component component, CallbackInfo ci) {
        if (kollegen$shouldMute()) {
            ci.cancel();
        }
    }

    @Inject(method = "saySystemQueued", at = @At("HEAD"), cancellable = true)
    private void kollegen$suppressSaySystemQueued(Component component, CallbackInfo ci) {
        if (kollegen$shouldMute()) {
            ci.cancel();
        }
    }

    @Inject(method = "saySystemNow(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"), cancellable = true)
    private void kollegen$suppressSaySystemNowComp(Component component, CallbackInfo ci) {
        if (kollegen$shouldMute()) {
            ci.cancel();
        }
    }

    @Inject(method = "saySystemNow(Ljava/lang/String;)V", at = @At("HEAD"), cancellable = true)
    private void kollegen$suppressSaySystemNowStr(String string, CallbackInfo ci) {
        if (kollegen$shouldMute()) {
            ci.cancel();
        }
    }

    private static boolean kollegen$shouldMute() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return false;
        return mc.options.narrator().get() == NarratorStatus.OFF || mc.options.getSoundSourceVolume(SoundSource.VOICE) <= 0.0f;
    }
}
