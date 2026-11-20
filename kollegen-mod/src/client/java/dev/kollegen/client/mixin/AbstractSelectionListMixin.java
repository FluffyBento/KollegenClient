package dev.kollegen.client.mixin;

import dev.kollegen.client.server.ServerListSettings;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractSelectionList.class)
public abstract class AbstractSelectionListMixin {

    @Inject(method = "contentHeight", at = @At("HEAD"), cancellable = true)
    private void kollegen$contentHeight(CallbackInfoReturnable<Integer> cir) {
        if ((Object) this instanceof ServerSelectionList ssl) {
            cir.setReturnValue(ServerListSettings.calculateContentHeight(ssl));
        }
    }

    @Inject(method = "setScrollAmount", at = @At("RETURN"))
    private void kollegen$onSetScrollAmount(double amount, CallbackInfo ci) {
        if ((Object) this instanceof ServerSelectionList ssl) {
            ServerListSettings.applyLayout(ssl);
        }
    }

    @Inject(method = "extractWidgetRenderState", at = @At("HEAD"))
    private void kollegen$onExtractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if ((Object) this instanceof ServerSelectionList ssl) {
            ServerListSettings.applyLayout(ssl);
        }
    }
}
