package dev.kollegen.client.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractSelectionList.class)
public abstract class AbstractSelectionListMixin<E> {

    @Inject(method = "extractSelection", at = @At("HEAD"), cancellable = true)
    private void kollegen$suppressVanillaServerSelection(GuiGraphicsExtractor g, E entry, int color, CallbackInfo ci) {
        if ((Object) this instanceof ServerSelectionList) {
            ci.cancel();
        }
    }
}
