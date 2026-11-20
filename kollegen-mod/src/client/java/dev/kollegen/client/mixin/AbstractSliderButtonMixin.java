package dev.kollegen.client.mixin;

import dev.kollegen.client.ui.GuiCustomizationRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractSliderButton.class)
public abstract class AbstractSliderButtonMixin {

    @Inject(method = "extractWidgetRenderState", at = @At("HEAD"), cancellable = true)
    private void kollegen$onExtractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (GuiCustomizationRenderer.renderSlider(g, (AbstractSliderButton) (Object) this, mouseX, mouseY)) {
            ci.cancel();
        }
    }
}
