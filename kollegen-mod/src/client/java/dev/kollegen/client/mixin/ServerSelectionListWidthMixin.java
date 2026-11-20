package dev.kollegen.client.mixin;

import dev.kollegen.client.server.ServerListSettings;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(ServerSelectionList.class)
public abstract class ServerSelectionListWidthMixin {

    @Inject(method = "getRowWidth", at = @At("HEAD"), cancellable = true)
    private void kollegen$getRowWidth(CallbackInfoReturnable<Integer> cir) {
        ServerSelectionList ssl = (ServerSelectionList) (Object) this;
        cir.setReturnValue(ServerListSettings.getActualWidth(ssl.getWidth()));
    }

    @Inject(method = "updateOnlineServers", at = @At("RETURN"))
    private void kollegen$onUpdateOnlineServers(ServerList list, CallbackInfo ci) {
        ServerListSettings.applyLayout((ServerSelectionList) (Object) this);
    }

    @Inject(method = "updateNetworkServers", at = @At("RETURN"))
    private void kollegen$onUpdateNetworkServers(List<?> servers, CallbackInfo ci) {
        ServerListSettings.applyLayout((ServerSelectionList) (Object) this);
    }
}
