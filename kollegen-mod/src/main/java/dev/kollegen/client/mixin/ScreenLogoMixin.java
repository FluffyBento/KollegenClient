package dev.kollegen.client.mixin;

import dev.kollegen.client.ui.LogoDraw;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Blendet unten rechts das Kollegen-Logo ein. Das Ziel sind die konkreten
 * Screen-Klassen (Inventar, Truhen, Titel-Screen), weil ein Injekt auf
 * AbstractContainerScreen.render ueber die render-Ueberschreibungskette der
 * Unterklassen nicht zuverlaessig feuert. Kern-Kosmetik, daher nie crashn.
 */
@Mixin({InventoryScreen.class, ContainerScreen.class, TitleScreen.class})
public class ScreenLogoMixin {

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("RETURN"))
    private void kollegen$drawLogo(GuiGraphics gui, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        try {
            int[] dim = LogoDraw.dims();
            int targetW = 72;
            int targetH = (int) (targetW * (dim[1] / (float) dim[0]));
            int x = gui.guiWidth() - targetW - 10;
            int y = gui.guiHeight() - targetH - 10;
            LogoDraw.draw(gui, x, y, targetW);
        } catch (Exception ignored) {
            // Kosmetik-Feature: darf niemals zum Crash fuehren.
        }
    }
}
