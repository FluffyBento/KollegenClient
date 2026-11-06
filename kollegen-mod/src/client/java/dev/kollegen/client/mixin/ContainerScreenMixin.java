package dev.kollegen.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kollegen.client.lock.ItemLockManager;
import dev.kollegen.client.mods.modules.InventoryColor;
import dev.kollegen.client.mods.modules.InventoryEnhancements;
import dev.kollegen.client.ui.Glass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(AbstractContainerScreen.class)
public class ContainerScreenMixin {

    @Shadow
    protected int leftPos;

    @Shadow
    protected int topPos;

    @Shadow
    protected Slot hoveredSlot;

    @Shadow
    protected AbstractContainerMenu menu;

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void kollegen$handleItemLockKey(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (this.hoveredSlot != null && this.hoveredSlot.container instanceof Inventory) {
            if (event.key() == InputConstants.KEY_L) {
                ItemLockManager.toggleLock(this.hoveredSlot);
                cir.setReturnValue(true);
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.options.keyDrop.matches(event)) {
                if (ItemLockManager.isLocked(this.hoveredSlot)) {
                    cir.setReturnValue(true);
                    return;
                }
            }
        }
    }

    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void kollegen$protectLockedSlot(Slot slot, int slotId, int mouseButton, ContainerInput clickType, CallbackInfo ci) {
        if (slot == null || !(slot.container instanceof Inventory)) return;
        if (!ItemLockManager.isLocked(slot)) return;

        if (clickType == ContainerInput.THROW || clickType == ContainerInput.SWAP || clickType == ContainerInput.QUICK_MOVE) {
            ci.cancel();
            return;
        }

        if (clickType == ContainerInput.PICKUP) {
            if (this.menu.getCarried().isEmpty()) {
                ci.cancel();
                return;
            }
            if (!ItemLockManager.matches(slot, this.menu.getCarried())) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
            at = @At("RETURN"))
    private void kollegen$tintBackground(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTick,
                                         CallbackInfo ci) {
        if (this.menu != null && this.menu.slots != null) {
            Minecraft mc = Minecraft.getInstance();
            for (Slot s : this.menu.slots) {
                if (s != null && s.container instanceof Inventory && ItemLockManager.isLocked(s.index)) {
                    int sx = this.leftPos + s.x;
                    int sy = this.topPos + s.y;
                    if (!s.hasItem()) {
                        Item reserved = ItemLockManager.getLockedItemType(s.index);
                        if (reserved != Items.AIR) {
                            gui.item(new ItemStack(reserved), sx, sy);
                            gui.fill(RenderPipelines.GUI, sx, sy, sx + 16, sy + 16, 0x50FF3333);
                        }
                    }
                    Glass.fillRound(gui, sx + 9, sy + 1, 7, 7, 2, 0xCCFFB300);
                    Glass.ring(gui, sx + 9, sy + 1, 7, 7, 2, 0.5f, 0xFFFFFFFF);
                }
            }
        }

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
