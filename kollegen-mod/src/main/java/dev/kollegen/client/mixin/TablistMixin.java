package dev.kollegen.client.mixin;

import dev.kollegen.client.presence.CosmeticData;
import dev.kollegen.client.presence.CosmeticText;
import dev.kollegen.client.presence.KollegenPresence;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(PlayerTabOverlay.class)
public class TablistMixin {

    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void kollegen$decorate(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
        try {
            if (info == null || info.getProfile() == null) return;
            CosmeticData d = KollegenPresence.getCosmetics(info.getProfile().id());
            if (d == null || d.isEmpty()) return;
            Component current = cir.getReturnValue();
            if (current == null) return;
            cir.setReturnValue(CosmeticText.decorate(current, d));
        } catch (Throwable ignored) {
        }
    }
}
