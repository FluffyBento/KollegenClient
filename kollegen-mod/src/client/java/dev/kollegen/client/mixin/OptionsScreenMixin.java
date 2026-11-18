package dev.kollegen.client.mixin;

import dev.kollegen.client.menu.KollegenVideoSettingsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = OptionsScreen.class, priority = 2000)
public abstract class OptionsScreenMixin extends Screen {

    protected OptionsScreenMixin() {
        super(null);
    }

    @Inject(method = "lambda$init$2", at = @At("HEAD"), cancellable = true, require = 0)
    private void kollegen$openKollegenVideoSettings(CallbackInfoReturnable<Screen> cir) {
        cir.setReturnValue(new KollegenVideoSettingsScreen(this));
    }
}