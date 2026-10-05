package dev.kollegen.client.mixin;

import dev.kollegen.client.KollegenMod;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(Minecraft.class)
public class MinecraftClientMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void kollegen_client$onTick(CallbackInfo ci) {
        KollegenMod.onTick();
    }

    
    @Inject(method = "runTick", at = @At("TAIL"))
    private void kollegen_client$onFrame(boolean tickIn, CallbackInfo ci) {
        KollegenMod.onFrame();
    }
}
