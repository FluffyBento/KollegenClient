package dev.kollegen.client.mixin;

import dev.kollegen.client.KollegenMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.terraformersmc.modmenu.gui.ModsScreen", remap = false)
public class ModsScreenMixin {

    @Inject(method = "init", at = @At("HEAD"), remap = false)
    private void kollegen$scrubInternalMods(CallbackInfo ci) {
        KollegenMod.cleanModMenu();
    }
}
