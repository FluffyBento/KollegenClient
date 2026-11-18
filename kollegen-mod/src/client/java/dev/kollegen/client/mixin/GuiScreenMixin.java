package dev.kollegen.client.mixin;

import dev.kollegen.client.menu.KollegenVideoSettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Gui.class, priority = 1500)
public abstract class GuiScreenMixin {

    @Shadow private Screen screen;
    @Shadow public abstract void setScreen(Screen screen);

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void kollegen$interceptSetScreen(Screen newScreen, CallbackInfo ci) {
        if (newScreen != null && (newScreen instanceof VideoSettingsScreen
                || newScreen.getClass().getName().contains("sodium.client.gui.VideoSettingsScreen"))) {
            if (this.screen instanceof KollegenVideoSettingsScreen) {
                return;
            }
            ci.cancel();
            Screen parent = this.screen;
            if (parent == null) {
                parent = new OptionsScreen(null, Minecraft.getInstance().options);
            }
            this.setScreen(new KollegenVideoSettingsScreen(parent));
        }
    }
}