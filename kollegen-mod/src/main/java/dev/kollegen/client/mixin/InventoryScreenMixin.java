package dev.kollegen.client.mixin;

import dev.kollegen.client.mods.modules.InventoryLayout;
import dev.kollegen.client.ui.LogoDraw;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Shadow;

import java.lang.reflect.Field;

/**
 * Verschiebt das Inventar-Panel (Hintergrund, Slots, Ruestung, Crafting) um den
 * in InventoryLayout konfigurierten Versatz. Slots werden per Reflection
 * verschoben, damit Anzeige und Klick-Trefferzonen zusammen bleiben. Ausserdem
 * wird unten rechts das Kollegen-Logo eingeblendet und der Inventar-Hintergrund
 * mit der in InventoryColor konfigurierten Farbe eingefaerbt.
 */
@Mixin(net.minecraft.client.gui.screens.inventory.InventoryScreen.class)
public class InventoryScreenMixin {

    @Shadow
    protected int leftPos;
    @Shadow
    protected int topPos;
    @Shadow
    protected AbstractContainerMenu menu;

    @Unique
    private static final Field KOLLEGEN_SLOT_X;
    @Unique
    private static final Field KOLLEGEN_SLOT_Y;
    @Unique
    private int kollegen$appliedX = 0;
    @Unique
    private int kollegen$appliedY = 0;

    static {
        Field x = null, y = null;
        try {
            x = Slot.class.getDeclaredField("x");
            y = Slot.class.getDeclaredField("y");
            for (Field f : new Field[]{x, y}) f.setAccessible(true);
        } catch (ReflectiveOperationException ignored) {
        }
        KOLLEGEN_SLOT_X = x;
        KOLLEGEN_SLOT_Y = y;
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void kollegen$shift(CallbackInfo ci) {
        if (KOLLEGEN_SLOT_X == null || KOLLEGEN_SLOT_Y == null) return;

        try {
            // vorherigen Versatz rueckgaengig machen (Resize-/Mehrfach-Init sicher)
            int prevX = kollegen$appliedX, prevY = kollegen$appliedY;
            leftPos -= prevX;
            topPos -= prevY;
            for (Slot s : menu.slots) {
                KOLLEGEN_SLOT_X.set(s, (int) KOLLEGEN_SLOT_X.get(s) - prevX);
                KOLLEGEN_SLOT_Y.set(s, (int) KOLLEGEN_SLOT_Y.get(s) - prevY);
            }

            // neuen Versatz anwenden
            int ox = InventoryLayout.offX, oy = InventoryLayout.offY;
            leftPos += ox;
            topPos += oy;
            for (Slot s : menu.slots) {
                KOLLEGEN_SLOT_X.set(s, (int) KOLLEGEN_SLOT_X.get(s) + ox);
                KOLLEGEN_SLOT_Y.set(s, (int) KOLLEGEN_SLOT_Y.get(s) + oy);
            }
            kollegen$appliedX = ox;
            kollegen$appliedY = oy;
        } catch (ReflectiveOperationException ignored) {
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("RETURN"))
    private void kollegen$drawLogo(GuiGraphics gui, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        int[] dim = LogoDraw.dims();
        int targetW = 72;
        int targetH = (int) (targetW * (dim[1] / (float) dim[0]));
        int x = gui.guiWidth() - targetW - 10;
        int y = gui.guiHeight() - targetH - 10;
        LogoDraw.draw(gui, x, y, targetW);
    }
}
