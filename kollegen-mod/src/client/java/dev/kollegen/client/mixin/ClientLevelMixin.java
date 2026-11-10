package dev.kollegen.client.mixin;

import dev.kollegen.client.distance.KollegenDistanceManager;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void kollegen$tickDistance(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
    }

    @Inject(method = "disconnect(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"))
    private void kollegen$disconnectDistance(Component reason, CallbackInfo ci) {
    }
}
