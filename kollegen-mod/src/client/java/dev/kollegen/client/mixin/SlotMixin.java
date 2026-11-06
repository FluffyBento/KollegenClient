package dev.kollegen.client.mixin;

import dev.kollegen.client.lock.ItemLockManager;
import dev.kollegen.client.mods.modules.InventoryEnhancements;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class)
public class SlotMixin {

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void kollegen$mayPlace(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        Slot self = (Slot) (Object) this;
        if (!InventoryEnhancements.canAcceptItem(self, stack)) {
            cir.setReturnValue(false);
            return;
        }
        if (ItemLockManager.isLocked(self) && !ItemLockManager.matches(self, stack)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void kollegen$mayPickup(Player player, CallbackInfoReturnable<Boolean> cir) {
        Slot self = (Slot) (Object) this;
        if (ItemLockManager.isLocked(self)) {
            cir.setReturnValue(false);
        }
    }
}
