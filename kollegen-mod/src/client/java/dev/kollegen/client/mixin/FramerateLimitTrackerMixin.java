package dev.kollegen.client.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import dev.kollegen.client.mods.modules.Performance;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FramerateLimitTracker.class)
public class FramerateLimitTrackerMixin {

    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "getFramerateLimit", at = @At("HEAD"), cancellable = true)
    private void kollegen$dynamicFps(CallbackInfoReturnable<Integer> cir) {
        if (!Performance.KollegenTricks.shouldUseDynamicFps()) return;

        if (this.minecraft != null && this.minecraft.getWindow() != null) {
            if (!this.minecraft.getWindow().isFocused()) {
                cir.setReturnValue(15);
            }
        }
    }

    @Inject(method = "isHeavilyThrottled", at = @At("HEAD"), cancellable = true)
    private void kollegen$heavyThrottle(CallbackInfoReturnable<Boolean> cir) {
        if (!Performance.KollegenTricks.shouldUseDynamicFps()) return;

        if (this.minecraft != null && this.minecraft.getWindow() != null) {
            if (!this.minecraft.getWindow().isFocused()) {
                cir.setReturnValue(true);
            }
        }
    }
}
