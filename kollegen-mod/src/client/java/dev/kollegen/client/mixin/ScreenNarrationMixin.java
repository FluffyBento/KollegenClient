package dev.kollegen.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.NarratorStatus;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public class ScreenNarrationMixin {

    @Inject(method = "scheduleNarration", at = @At("HEAD"), cancellable = true)
    private void kollegen$cancelScheduleNarration(CallbackInfo ci) {
        if (kollegen$isMuted()) {
            ci.cancel();
        }
    }

    @Inject(method = "handleDelayedNarration", at = @At("HEAD"), cancellable = true)
    private void kollegen$cancelDelayedNarration(CallbackInfo ci) {
        if (kollegen$isMuted()) {
            ci.cancel();
        }
    }

    @Inject(method = "triggerImmediateNarration", at = @At("HEAD"), cancellable = true)
    private void kollegen$cancelImmediateNarration(boolean bl, CallbackInfo ci) {
        if (kollegen$isMuted()) {
            ci.cancel();
        }
    }

    private static boolean kollegen$isMuted() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return false;
        return mc.options.narrator().get() == NarratorStatus.OFF || mc.options.getSoundSourceVolume(SoundSource.VOICE) <= 0.0f;
    }
}
