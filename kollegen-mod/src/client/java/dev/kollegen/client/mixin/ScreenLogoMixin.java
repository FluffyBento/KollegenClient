package dev.kollegen.client.mixin;

import dev.kollegen.client.ui.LogoDraw;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin({InventoryScreen.class, ContainerScreen.class, TitleScreen.class})
public class ScreenLogoMixin {

    private void kollegen$drawLogoInner(GuiGraphicsExtractor gui) {
        try {
            int[] dim = LogoDraw.dims();
            int targetW = 72;
            int targetH = (int) (targetW * (dim[1] / (float) dim[0]));
            int x = gui.guiWidth() - targetW - 10;
            int y = gui.guiHeight() - targetH - 10;
            LogoDraw.draw(gui, x, y, targetW);
        } catch (Exception ignored) {
            
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("RETURN"), require = 0)
    private void kollegen$drawLogoLegacy(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        kollegen$drawLogoInner(gui);
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("RETURN"), require = 0)
    private void kollegen$drawLogoExtract(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        kollegen$drawLogoInner(gui);
    }
}
