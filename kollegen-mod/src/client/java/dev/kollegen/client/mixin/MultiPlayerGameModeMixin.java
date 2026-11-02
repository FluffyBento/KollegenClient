package dev.kollegen.client.mixin;

import dev.kollegen.client.mods.modules.InventoryEnhancements;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {

    @Inject(method = "dropItem", at = @At("HEAD"), cancellable = true)
    private void kollegen$preventDropLocked(LocalPlayer player, boolean entireStack, CallbackInfo ci) {
        if (player != null && player.getInventory() != null) {
            int selected = player.getInventory().getSelectedSlot();
            if (InventoryEnhancements.isIndexLocked(selected)) {
                ci.cancel();
            }
        }
    }
}
