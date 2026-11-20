package dev.kollegen.client.mixin;

import dev.kollegen.client.server.ServerListSettings;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerSelectionList.class)
public abstract class ServerSelectionListWidthMixin {

    @Inject(method = "getRowWidth", at = @At("HEAD"), cancellable = true)
    private void kollegen$getRowWidth(CallbackInfoReturnable<Integer> cir) {
        ServerSelectionList ssl = (ServerSelectionList) (Object) this;
        cir.setReturnValue(ServerListSettings.getActualWidth(ssl.getWidth()));
    }
}
