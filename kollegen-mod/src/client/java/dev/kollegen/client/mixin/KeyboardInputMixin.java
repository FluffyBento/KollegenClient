package dev.kollegen.client.mixin;

import dev.kollegen.client.input.GamepadInput;
import dev.kollegen.client.mods.modules.Freecam;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(KeyboardInput.class)
public class KeyboardInputMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void kollegen_client$controllerOverride(CallbackInfo ci) {
        if (Freecam.isFreezePlayer()) {
            ((ClientInputAccessor) (Object) this).kollegen$setKeyPresses(new Input(false, false, false, false, false, false, false));
            ((ClientInputAccessor) (Object) this).kollegen$setMoveVector(new Vec2(0.0f, 0.0f));
            ci.cancel();
            return;
        }
        if (GamepadInput.takeOverMovement()) {
            GamepadInput.applyMovement();
            ci.cancel();
        }
    }
}