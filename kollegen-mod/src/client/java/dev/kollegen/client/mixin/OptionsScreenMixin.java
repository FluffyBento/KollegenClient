package dev.kollegen.client.mixin;

import dev.kollegen.client.menu.KollegenVideoSettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Supplier;

@Mixin(value = OptionsScreen.class, priority = 500)
public abstract class OptionsScreenMixin extends Screen {

    protected OptionsScreenMixin() {
        super(null);
    }

    @Dynamic
    @Inject(method = "lambda$init$2", at = @At("HEAD"), cancellable = true, require = 0)
    private void kollegen$openKollegenVideoSettings(CallbackInfoReturnable<Screen> cir) {
        cir.setReturnValue(new KollegenVideoSettingsScreen(this));
    }

    @Inject(method = "openScreenButton", at = @At("HEAD"), cancellable = true)
    private void kollegen$overrideVideoBtn(Component title, Supplier<Screen> supplier, CallbackInfoReturnable<Button> cir) {
        if (title != null && (title.equals(Component.translatable("options.video"))
                || title.getString().equals(Component.translatable("options.video").getString())
                || title.getString().contains("Video")
                || title.getString().contains("Grafik"))) {
            cir.setReturnValue(Button.builder(title, btn -> {
                Minecraft.getInstance().setScreenAndShow(new KollegenVideoSettingsScreen(this));
            }).build());
        }
    }
}
