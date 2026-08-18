package dev.kollegen.client.mixin;

import dev.kollegen.client.KollegenMod;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tick-Hook OHNE fabric-api: make Target ist der Client selbst. Jeder
 * Client-Tick läuft durch {@code KollegenMod.onTick()} (Menü-Taste,
 * Farb-FX, Join-Requests, Rich Presence).
 */
@Mixin(Minecraft.class)
public class MinecraftClientMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void kollegen_client$onTick(CallbackInfo ci) {
        KollegenMod.onTick();
    }
}