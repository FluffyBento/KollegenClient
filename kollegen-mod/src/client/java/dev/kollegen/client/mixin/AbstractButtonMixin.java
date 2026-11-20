package dev.kollegen.client.mixin;

import dev.kollegen.client.ui.GuiCustomizationRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractButton.class)
public abstract class AbstractButtonMixin {

    @Inject(method = "extractDefaultSprite", at = @At("HEAD"), cancellable = true)
    private void kollegen$onExtractDefaultSprite(GuiGraphicsExtractor g, CallbackInfo ci) {
        if (GuiCustomizationRenderer.renderButtonBackground(g, (AbstractButton) (Object) this)) {
            ci.cancel();
        }
    }
}
