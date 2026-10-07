package dev.kollegen.client.mixin;

import dev.kollegen.client.input.GamepadInput;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(KeyboardInput.class)
public class KeyboardInputMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void kollegen_client$controllerOverride(CallbackInfo ci) {
        if (GamepadInput.takeOverMovement()) {
            GamepadInput.applyMovement();
            ci.cancel();
        }
    }
}