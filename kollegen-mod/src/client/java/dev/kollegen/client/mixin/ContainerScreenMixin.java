package dev.kollegen.client.mixin;

import dev.kollegen.client.mods.modules.InventoryColor;
import dev.kollegen.client.mods.modules.InventoryEnhancements;
import dev.kollegen.client.ui.Glass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(AbstractContainerScreen.class)
public class ContainerScreenMixin {

    @Shadow
    protected int leftPos;

    @Shadow
    protected int topPos;

    @Shadow
    protected Slot hoveredSlot;

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
            at = @At("RETURN"))
    private void kollegen$tintBackground(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTick,
                                         CallbackInfo ci) {
        if (InventoryColor.enabled) {
            int lx = this.leftPos;
            int ty = this.topPos;
            int alpha = Math.max(8, (int) (255 * InventoryColor.opacity.value / 100.0));
            int argb = (alpha << 24) | (InventoryColor.color.value & 0xFF_FF_FF);
            gui.fill(RenderPipelines.GUI, lx, ty, lx + 176, ty + 166, argb);
        }

        if (InventoryEnhancements.isShulkerPreviewEnabled() && this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
            ItemStack stack = this.hoveredSlot.getItem();
            ItemContainerContents container = stack.get(DataComponents.CONTAINER);
            if (container != null) {
                NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
                container.copyInto(items);
                int previewW = 9 * 18 + 8;
                int previewH = 3 * 18 + 8;
                int px = mouseX + 12;
                int py = mouseY - previewH - 4;
                Minecraft mc = Minecraft.getInstance();
                if (py < 4) py = mouseY + 18;
                if (px + previewW > mc.getWindow().getGuiScaledWidth()) {
                    px = mc.getWindow().getGuiScaledWidth() - previewW - 4;
                }
                Glass.fillRound(gui, px, py, previewW, previewH, 6, 0xEE12141F);
                Glass.ring(gui, px, py, previewW, previewH, 6, 1.0f, 0x55FFFFFF);
                for (int i = 0; i < 27; i++) {
                    ItemStack item = items.get(i);
                    if (!item.isEmpty()) {
                        int ix = px + 4 + (i % 9) * 18;
                        int iy = py + 4 + (i / 9) * 18;
                        gui.item(item, ix, iy);
                        gui.itemDecorations(mc.font, item, ix, iy);
                    }
                }
            } else {
                BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
                if (bundle != null && !bundle.isEmpty()) {
                    List<ItemStack> items = bundle.itemCopies().toList();
                    int count = items.size();
                    int cols = Math.min(count, 8);
                    int rows = (count + cols - 1) / Math.max(1, cols);
                    int previewW = cols * 18 + 8;
                    int previewH = rows * 18 + 8;
                    int px = mouseX + 12;
                    int py = mouseY - previewH - 4;
                    Minecraft mc = Minecraft.getInstance();
                    if (py < 4) py = mouseY + 18;
                    if (px + previewW > mc.getWindow().getGuiScaledWidth()) {
                        px = mc.getWindow().getGuiScaledWidth() - previewW - 4;
                    }
                    Glass.fillRound(gui, px, py, previewW, previewH, 6, 0xEE12141F);
                    Glass.ring(gui, px, py, previewW, previewH, 6, 1.0f, 0x55FFFFFF);
                    for (int i = 0; i < count; i++) {
                        ItemStack item = items.get(i);
                        int ix = px + 4 + (i % cols) * 18;
                        int iy = py + 4 + (i / cols) * 18;
                        gui.item(item, ix, iy);
                        gui.itemDecorations(mc.font, item, ix, iy);
                    }
                }
            }
        }
    }
}
