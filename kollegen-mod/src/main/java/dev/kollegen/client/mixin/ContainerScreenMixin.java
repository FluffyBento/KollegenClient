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
 * erfolgt in AbstractContainerScreen.render direkt NACH dem renderBg-Aufruf,
 * damit die Faerbung unter den Items liegt. Deckkraft ueber InventoryColor.
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
            Class<?> acs = Class.forName("net.minecraft.client.gui.screens.inventory.AbstractContainerScreen");
            left = acs.getDeclaredField("leftPos");
            top = acs.getDeclaredField("topPos");
            for (Field f : new Field[]{left, top}) f.setAccessible(true);
        } catch (ReflectiveOperationException ignored) {
        }
        KOLLEGEN_LEFT = left;
        KOLLEGEN_TOP = top;
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V",
                    shift = At.Shift.AFTER))
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
        } catch (ReflectiveOperationException ignored) {
        }
    }
}
