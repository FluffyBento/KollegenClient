package dev.kollegen.client.mixin;

import dev.kollegen.client.lock.ItemLockManager;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public class LocalPlayerMixin {

    @Inject(method = "canDropItems", at = @At("HEAD"), cancellable = true)
    private void kollegen$preventLockedItemDrop(CallbackInfoReturnable<Boolean> cir) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        if (ItemLockManager.isLocked(self.getInventory().getSelectedSlot())) {
            cir.setReturnValue(false);
        }
    }
}
