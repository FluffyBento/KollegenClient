package dev.kollegen.client.mixin;

import dev.kollegen.client.scenery.F2HoldHandler;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void kollegen$interceptF2(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (F2HoldHandler.onKey(action, event)) {
            ci.cancel();
        }
    }
}
