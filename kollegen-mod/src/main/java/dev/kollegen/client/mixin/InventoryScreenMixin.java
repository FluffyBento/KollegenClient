package dev.kollegen.client.mixin;

import dev.kollegen.client.mods.modules.InventoryLayout;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.Unique;

import java.lang.reflect.Field;

/**
 * Verschiebt das Inventar-Panel (Hintergrund, Slots, Ruestung, Crafting) um den
 * in InventoryLayout konfigurierten Versatz. Slots werden per Reflection
 * verschoben, damit Anzeige und Klick-Trefferzonen zusammen bleiben.
 */
@Mixin(net.minecraft.client.gui.screens.inventory.InventoryScreen.class)
public class InventoryScreenMixin {

    @Unique
    private static final Field KOLLEGEN_SLOT_X;
    @Unique
    private static final Field KOLLEGEN_SLOT_Y;
    @Unique
    private static final Field KOLLEGEN_LEFT;
    @Unique
    private static final Field KOLLEGEN_TOP;
    @Unique
    private static final Field KOLLEGEN_MENU;
    @Unique
    private int kollegen$appliedX = 0;
    @Unique
    private int kollegen$appliedY = 0;

    static {
        Field x = null, y = null, left = null, top = null, menu = null;
        try {
            Class<?> acs = Class.forName("net.minecraft.client.gui.screens.inventory.AbstractContainerScreen");
            x = Slot.class.getDeclaredField("x");
            y = Slot.class.getDeclaredField("y");
            left = acs.getDeclaredField("leftPos");
            top = acs.getDeclaredField("topPos");
            menu = acs.getDeclaredField("menu");
            for (Field f : new Field[]{x, y, left, top, menu}) f.setAccessible(true);
        } catch (ReflectiveOperationException ignored) {
        }
        KOLLEGEN_SLOT_X = x;
        KOLLEGEN_SLOT_Y = y;
        KOLLEGEN_LEFT = left;
        KOLLEGEN_TOP = top;
        KOLLEGEN_MENU = menu;
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void kollegen$shift(CallbackInfo ci) {
        if (KOLLEGEN_SLOT_X == null || KOLLEGEN_SLOT_Y == null
                || KOLLEGEN_LEFT == null || KOLLEGEN_TOP == null || KOLLEGEN_MENU == null) return;

        Object self = this;
        try {
            // vorherigen Versatz rueckgaengig machen (Resize-/Mehrfach-Init sicher)
            int prevX = kollegen$appliedX, prevY = kollegen$appliedY;
            KOLLEGEN_LEFT.set(self, (int) KOLLEGEN_LEFT.get(self) - prevX);
            KOLLEGEN_TOP.set(self, (int) KOLLEGEN_TOP.get(self) - prevY);
            AbstractContainerMenu menu = (AbstractContainerMenu) KOLLEGEN_MENU.get(self);
            for (Slot s : menu.slots) {
                KOLLEGEN_SLOT_X.set(s, s.x - prevX);
                KOLLEGEN_SLOT_Y.set(s, s.y - prevY);
            }

            // neuen Versatz anwenden
            int ox = InventoryLayout.offX, oy = InventoryLayout.offY;
            KOLLEGEN_LEFT.set(self, (int) KOLLEGEN_LEFT.get(self) + ox);
            KOLLEGEN_TOP.set(self, (int) KOLLEGEN_TOP.get(self) + oy);
            for (Slot s : menu.slots) {
                KOLLEGEN_SLOT_X.set(s, s.x + ox);
                KOLLEGEN_SLOT_Y.set(s, s.y + oy);
            }
            kollegen$appliedX = ox;
            kollegen$appliedY = oy;
        } catch (ReflectiveOperationException ignored) {
        }
    }
}
