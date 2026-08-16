package dev.kollegen.client.mixin;

import dev.kollegen.client.theme.ThemeSync;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.option.OptionsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OptionsScreen.class)
public class OptionsScreenMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void onRender(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ThemeSync.refresh();
        int bg = ThemeSync.argb(ThemeSync.get("bg", "#0d0d12"), 0xff0d0d12);
        int accent = ThemeSync.argb(ThemeSync.get("accent", "#f5a623"), 0xfff5a623);

        OptionsScreen screen = (OptionsScreen) (Object) this;
        guiGraphics.fill(0, 0, screen.width, screen.height, bg);
        guiGraphics.fill(0, 0, screen.width, 3, accent);
    }
}
