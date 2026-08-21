package dev.kollegen.client.mixin;

import dev.kollegen.client.KollegenMod;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tick-Hook OHNE fabric-api: Ziel ist der Client selbst. Jeder Client-Tick läuft
 * durch {@code KollegenMod.onTick()} (Menü-Taste, Module, Keybinds, RPC).
 */
@Mixin(Minecraft.class)
public class MinecraftClientMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void kollegen_client$onTick(CallbackInfo ci) {
        KollegenMod.onTick();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void kollegen_client$onTickTail(CallbackInfo ci) {
        dev.kollegen.client.mods.modules.FreeCamState.apply();
    }
}
