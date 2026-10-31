package dev.kollegen.client.mixin;

import dev.kollegen.client.mods.modules.InventoryEnhancements;
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
        if (!InventoryEnhancements.canAcceptItem((Slot) (Object) this, stack)) {
            cir.setReturnValue(false);
        }
    }
}
