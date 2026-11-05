package dev.kollegen.client.mixin;

import dev.kollegen.client.mods.modules.Performance;
import net.minecraft.client.FramerateLimiter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FramerateLimiter.class)
public class FramerateLimiterMixin {

    @Inject(method = "limitDisplayFPS", at = @At("HEAD"), cancellable = true)
    private static void kollegen$uncapDisplayFps(int maxFps, CallbackInfo ci) {
        if (Performance.KollegenTricks.isEnabled()) {
            ci.cancel();
        }
    }
}
