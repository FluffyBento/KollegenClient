package dev.kollegen.client.mixin;

import dev.kollegen.client.mods.modules.InventoryLayout;
import dev.kollegen.client.mixin.SlotAccessor;
import dev.kollegen.client.ui.LogoDraw;
import net.minecraft.client.gui.GuiGraphics;
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

/**
 * Verschiebt das Inventar-Panel (Hintergrund, Slots, Ruestung, Crafting) um den
 * in InventoryLayout konfigurierten Versatz. Slots werden ueber SlotAccessor
 * verschoben (ihre Felder x/y sind final), damit Anzeige und Klick-Trefferzonen
 * zusammen bleiben. Ausserdem wird unten rechts das Kollegen-Logo eingeblendet
 * und der Inventar-Hintergrund mit der in InventoryColor konfigurierten Farbe
 * eingefaerbt.
 *
 * Alle Vanilla-Feldzugriffe (leftPos/topPos/menu ueber @Shadow, Slot ueber
 * SlotAccessor) werden ueber den refmap des Mods remapped. Reflection ueber
 * Class.getDeclaredField(...) wuerde unter Intermediary mit NoSuchFieldException
 * crashn, weil String-Literale NICHT vom refmap erfasst werden.
 */
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

        // vorherigen Versatz rueckgaengig machen (Resize-/Mehrfach-Init sicher)
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

        // neuen Versatz anwenden
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

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("RETURN"))
    private void kollegen$drawLogo(GuiGraphics gui, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!((Object) this instanceof InventoryScreen)) return;
        try {
            int[] dim = LogoDraw.dims();
            int targetW = 72;
            int targetH = (int) (targetW * (dim[1] / (float) dim[0]));
            int x = gui.guiWidth() - targetW - 10;
            int y = gui.guiHeight() - targetH - 10;
            LogoDraw.draw(gui, x, y, targetW);
        } catch (Exception ignored) {
            // Kosmetik-Feature: darf niemals zum Crash fuehren.
        }
    }
}
