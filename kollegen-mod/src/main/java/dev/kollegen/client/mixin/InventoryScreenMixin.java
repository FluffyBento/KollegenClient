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

/**
 * Verschiebt das Inventar-Panel (Hintergrund, Slots, Ruestung, Crafting) um den
 * in InventoryLayout konfigurierten Versatz. Slots werden ueber SlotAccessor
 * verschoben (ihre Felder x/y sind final), damit Anzeige und Klick-Trefferzonen
 * zusammen bleiben.
 *
 * Das Kollegen-Logo unten rechts liegt in ScreenLogoMixin (greift ueber
 * konkrete Zielklassen, damit es auch auf Inventar/Truhen/Titel-Screen
 * zuverlaessig feuert und nicht ueber die render-Ueberschreibungskette
 * verloren geht).
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
}
