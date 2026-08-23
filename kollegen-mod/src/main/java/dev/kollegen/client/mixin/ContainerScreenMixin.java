package dev.kollegen.client.mixin;

import dev.kollegen.client.mods.modules.InventoryColor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

/**
 * Faerbt den Hintergrund ALLER Container-Bildschirme (Inventar, Kisten,
 * Werkbank, ...) mit der in InventoryColor konfigurierten Farbe. Der Eingriff
 * erfolgt in AbstractContainerScreen.renderContents direkt NACH dem renderBg-
 * Aufruf, damit die Faerbung unter den Items liegt. Deckkraft ueber InventoryColor.
 *
 * WICHTIG: Kein Class.forName() im static-Block (siehe InventoryScreenMixin).
 * Der direkte .class-Literal loest KEINEN <clinit> aus.
 */
@Mixin(AbstractContainerScreen.class)
public class ContainerScreenMixin {

    @Unique
    private static final Field KOLLEGEN_LEFT;
    @Unique
    private static final Field KOLLEGEN_TOP;

    static {
        Field left = null, top = null;
        try {
            // Direkter Klassen-Literal statt Class.forName (siehe Klassen-Javadoc).
            Class<?> acs = AbstractContainerScreen.class;
            left = acs.getDeclaredField("leftPos");
            top = acs.getDeclaredField("topPos");
            for (Field f : new Field[]{left, top}) f.setAccessible(true);
        } catch (ReflectiveOperationException ignored) {
        }
        KOLLEGEN_LEFT = left;
        KOLLEGEN_TOP = top;
    }

    // Seit dem Screen-Pipeline-Refactor orchestriert render() nur noch;
    // renderBg (Panel-Hintergrund der Unterklassen) wird am Anfang von
    // renderContents() aufgerufen. Wir injizieren direkt DANACH -> die Faerbung
    // liegt ueber dem Panel, aber unter Slots/Items. require=0: Falls sich ein
    // Update die Struktur anders umbaut, fehlt nur die Faerbung statt Crash.
    @Inject(method = "renderContents",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V",
                    shift = At.Shift.AFTER),
            require = 0)
    private void kollegen$tintBackground(GuiGraphics gui, int mouseX, int mouseY, float partialTick,
                                         CallbackInfo ci) {
        if (!InventoryColor.enabled || KOLLEGEN_LEFT == null || KOLLEGEN_TOP == null) return;
        try {
            int lx = (int) KOLLEGEN_LEFT.get(this);
            int ty = (int) KOLLEGEN_TOP.get(this);
            // Deckkraft-Einstellung (5-100%) auf die Farbe anwenden.
            int alpha = Math.max(8, (int) (255 * InventoryColor.opacity.value / 100.0));
            int argb = (alpha << 24) | (InventoryColor.color.value & 0xFF_FF_FF);
            gui.fill(RenderPipelines.GUI, lx, ty, lx + 176, ty + 166, argb);
        } catch (Exception ignored) {
            // Kosmetik-Feature: darf niemals zum Crash fuehren.
        }
    }
}
