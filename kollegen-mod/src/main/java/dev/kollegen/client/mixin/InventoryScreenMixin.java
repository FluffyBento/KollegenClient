package dev.kollegen.client.mixin;

import dev.kollegen.client.mods.modules.InventoryLayout;
import dev.kollegen.client.mixin.SlotAccessor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(AbstractContainerScreen.class)
public class InventoryScreenMixin {

    @Shadow
    protected int leftPos;

    @Shadow
    protected int topPos;

    @Shadow
    protected AbstractContainerMenu menu;

    @Unique
    private int kollegen$appliedX = 0;

    @Unique
    private int kollegen$appliedY = 0;

    @Inject(method = "init", at = @At("RETURN"))
    private void kollegen$shift(CallbackInfo ci) {
        if (!((Object) this instanceof InventoryScreen)) return;
        int ox = InventoryLayout.offX, oy = InventoryLayout.offY;

        
        int prevX = kollegen$appliedX, prevY = kollegen$appliedY;
        this.leftPos -= prevX;
        this.topPos -= prevY;
        AbstractContainerMenu m = this.menu;
        if (m != null && m.slots != null) {
            for (Slot s : m.slots) {
                SlotAccessor sa = (SlotAccessor) s;
                sa.kollegen$setX(sa.kollegen$getX() - prevX);
                sa.kollegen$setY(sa.kollegen$getY() - prevY);
            }
        }

        
        this.leftPos += ox;
        this.topPos += oy;
        if (m != null && m.slots != null) {
            for (Slot s : m.slots) {
                SlotAccessor sa = (SlotAccessor) s;
                sa.kollegen$setX(sa.kollegen$getX() + ox);
                sa.kollegen$setY(sa.kollegen$getY() + oy);
            }
        }
        kollegen$appliedX = ox;
        kollegen$appliedY = oy;
    }
}
