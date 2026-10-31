package dev.kollegen.client.mixin;

import dev.kollegen.client.mods.modules.InventoryColor;
import dev.kollegen.client.mods.modules.InventoryEnhancements;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenMixin extends Screen {

    @Shadow
    protected int leftPos;

    @Shadow
    protected int topPos;

    @Shadow
    @Final
    protected int imageWidth;

    @Shadow
    @Final
    protected int imageHeight;

    @Shadow
    @Final
    protected AbstractContainerMenu menu;

    @Shadow
    protected Slot hoveredSlot;

    private EditBox kollegen$searchBox;

    protected ContainerScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void kollegen$onInit(CallbackInfo ci) {
        if (!InventoryEnhancements.isSearchEnabled()) return;
        int bw = 90;
        int bx = this.leftPos + (this.imageWidth - bw) / 2;
        int by = Math.min(this.height - 18, this.topPos + this.imageHeight + 4);
        this.kollegen$searchBox = new EditBox(this.font, bx, by, bw, 14, Component.literal(""));
        this.kollegen$searchBox.setBordered(true);
        this.kollegen$searchBox.setMaxLength(30);
        this.kollegen$searchBox.setHint(Component.literal("Suchen..."));
        this.addWidget(this.kollegen$searchBox);
    }

    private Slot kollegen$findSlotUnderMouse() {
        if (this.minecraft == null || this.minecraft.mouseHandler == null || this.minecraft.getWindow() == null) {
            return null;
        }
        double winW = this.minecraft.getWindow().getScreenWidth();
        double winH = this.minecraft.getWindow().getScreenHeight();
        if (winW <= 0 || winH <= 0) return null;
        double mx = this.minecraft.mouseHandler.xpos() * (double) this.minecraft.getWindow().getGuiScaledWidth() / winW;
        double my = this.minecraft.mouseHandler.ypos() * (double) this.minecraft.getWindow().getGuiScaledHeight() / winH;
        for (Slot slot : this.menu.slots) {
            int sx = this.leftPos + slot.x;
            int sy = this.topPos + slot.y;
            if (mx >= sx && mx < sx + 16 && my >= sy && my < sy + 16) {
                return slot;
            }
        }
        return null;
    }

    @Inject(method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V",
            at = @At("HEAD"), cancellable = true)
    private void kollegen$onSlotClicked(Slot slot, int slotId, int mouseButton, ContainerInput containerInput, CallbackInfo ci) {
        if (!InventoryEnhancements.isLockEnabled()) return;
        if (containerInput == ContainerInput.THROW) {
            Slot target = slot != null ? slot : (this.hoveredSlot != null ? this.hoveredSlot : (slotId >= 0 && slotId < this.menu.slots.size() ? this.menu.slots.get(slotId) : null));
            if (target != null && InventoryEnhancements.isSlotLocked(target)) {
                ci.cancel();
                return;
            }
        }
        if (slotId == -999) {
            Slot target = this.hoveredSlot != null ? this.hoveredSlot : null;
            if (target != null && InventoryEnhancements.isSlotLocked(target)) {
                ci.cancel();
                return;
            }
        }
        if (slot != null && InventoryEnhancements.isSlotLocked(slot)) {
            Item bound = InventoryEnhancements.getBoundItem(slot);
            ItemStack carried = this.menu.getCarried();

            if (containerInput == ContainerInput.QUICK_MOVE) {
                ci.cancel();
                return;
            }
            if (containerInput == ContainerInput.PICKUP_ALL) {
                ci.cancel();
                return;
            }
            if (containerInput == ContainerInput.SWAP) {
                ci.cancel();
                return;
            }

            if (slot.hasItem()) {
                InventoryEnhancements.ensureBound(slot);
                if (carried == null || carried.isEmpty()) {
                    ci.cancel();
                    return;
                }
                if (bound != null && !carried.is(bound)) {
                    ci.cancel();
                    return;
                }
            } else {
                if (carried != null && !carried.isEmpty()) {
                    if (bound != null && !carried.is(bound)) {
                        ci.cancel();
                        return;
                    }
                }
            }
        }
        if (containerInput == ContainerInput.SWAP && InventoryEnhancements.isIndexLocked(mouseButton)) {
            ci.cancel();
            return;
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void kollegen$onMouseClicked(MouseButtonEvent event, boolean bl, CallbackInfoReturnable<Boolean> cir) {
        if (this.kollegen$searchBox != null && InventoryEnhancements.isSearchEnabled()) {
            double mx = event.x();
            double my = event.y();
            int bx = this.kollegen$searchBox.getX();
            int by = this.kollegen$searchBox.getY();
            int bw = this.kollegen$searchBox.getWidth();
            int bh = this.kollegen$searchBox.getHeight();
            boolean inside = mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;
            if (inside && event.button() == 1) {
                this.kollegen$searchBox.setValue("");
                cir.setReturnValue(true);
                return;
            }
            if (!inside && this.kollegen$searchBox.isFocused()) {
                this.kollegen$searchBox.setFocused(false);
            }
        }

        if (event.button() == 0 && event.hasAltDown() && InventoryEnhancements.isLockEnabled()) {
            Slot target = this.hoveredSlot != null ? this.hoveredSlot : kollegen$findSlotUnderMouse();
            if (target != null) {
                InventoryEnhancements.toggleLock(target);
                cir.setReturnValue(true);
                return;
            }
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void kollegen$onKeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (this.kollegen$searchBox != null && this.kollegen$searchBox.isFocused()) {
            if (event.isEscape()) {
                this.kollegen$searchBox.setFocused(false);
                cir.setReturnValue(true);
                return;
            }
            if (this.kollegen$searchBox.keyPressed(event)) {
                cir.setReturnValue(true);
                return;
            }
            cir.setReturnValue(true);
            return;
        }

        Slot target = this.hoveredSlot != null ? this.hoveredSlot : kollegen$findSlotUnderMouse();

        int lk = InventoryEnhancements.getLockKeyCode();
        boolean matchesLock = (lk > 0 && (event.key() == lk || event.keycode() == lk))
                || ((lk == 15 || lk == 76 || lk == 49 || lk == com.mojang.blaze3d.platform.InputConstants.KEY_L)
                && (event.key() == 15 || event.keycode() == 108 || event.keycode() == 76 || event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_L));
        if (matchesLock) {
            if (target != null) {
                InventoryEnhancements.toggleLock(target);
                cir.setReturnValue(true);
                return;
            }
        }

        if (target != null && InventoryEnhancements.isSlotLocked(target)) {
            if (this.minecraft != null && this.minecraft.options != null
                    && this.minecraft.options.keyDrop.matches(event)) {
                cir.setReturnValue(true);
            }
        }
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (this.kollegen$searchBox != null && this.kollegen$searchBox.isFocused()) {
            if (this.kollegen$searchBox.charTyped(event)) {
                return true;
            }
        }
        return super.charTyped(event);
    }

    @Inject(method = "extractContents(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
            at = @At("RETURN"))
    private void kollegen$onExtractContents(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTick,
                                            CallbackInfo ci) {
        if (InventoryColor.enabled) {
            int lx = this.leftPos;
            int ty = this.topPos;
            int alpha = Math.max(8, (int) (255 * InventoryColor.opacity.value / 100.0));
            int argb = (alpha << 24) | (InventoryColor.color.value & 0xFF_FF_FF);
            gui.fill(RenderPipelines.GUI, lx, ty, lx + this.imageWidth, ty + this.imageHeight, argb);
        }

        if (InventoryEnhancements.isSearchEnabled()) {
            int bw = 90;
            int bx = this.leftPos + (this.imageWidth - bw) / 2;
            int by = Math.min(this.height - 18, this.topPos + this.imageHeight + 4);
            if (this.kollegen$searchBox == null) {
                this.kollegen$searchBox = new EditBox(this.font, bx, by, bw, 14, Component.literal(""));
                this.kollegen$searchBox.setBordered(true);
                this.kollegen$searchBox.setMaxLength(30);
                this.kollegen$searchBox.setHint(Component.literal("Suchen..."));
                this.addWidget(this.kollegen$searchBox);
            } else {
                this.kollegen$searchBox.setX(bx);
                this.kollegen$searchBox.setY(by);
            }
            this.kollegen$searchBox.extractRenderState(gui, mouseX, mouseY, partialTick);
        }

        String query = (this.kollegen$searchBox != null && InventoryEnhancements.isSearchEnabled())
                ? this.kollegen$searchBox.getValue().trim().toLowerCase()
                : "";
        Font font = this.font;

        for (Slot slot : this.menu.slots) {
            int sx = this.leftPos + slot.x;
            int sy = this.topPos + slot.y;

            if (InventoryEnhancements.isSlotLocked(slot)) {
                Item bound = InventoryEnhancements.getBoundItem(slot);
                if (bound != null && !slot.hasItem()) {
                    gui.fakeItem(new ItemStack(bound), sx, sy);
                    gui.nextStratum();
                    gui.fill(RenderPipelines.GUI, sx, sy, sx + 16, sy + 16, 0x808B8B8B);
                    if (this.hoveredSlot == slot) {
                        gui.setTooltipForNextFrame(Component.literal("§6[Reserviert] §f" + new ItemStack(bound).getHoverName().getString()), mouseX, mouseY);
                    }
                }
                gui.fill(RenderPipelines.GUI, sx, sy, sx + 16, sy + 1, 0xFFFFAA00);
                gui.fill(RenderPipelines.GUI, sx, sy + 15, sx + 16, sy + 16, 0xFFFFAA00);
                gui.fill(RenderPipelines.GUI, sx, sy, sx + 1, sy + 16, 0xFFFFAA00);
                gui.fill(RenderPipelines.GUI, sx + 15, sy, sx + 16, sy + 16, 0xFFFFAA00);

                gui.fill(RenderPipelines.GUI, sx + 9, sy, sx + 16, sy + 8, 0xDD000000);
                gui.fill(RenderPipelines.GUI, sx + 11, sy + 1, sx + 14, sy + 3, 0xFFFFD700);
                gui.fill(RenderPipelines.GUI, sx + 10, sy + 3, sx + 15, sy + 7, 0xFFFFD700);
                gui.fill(RenderPipelines.GUI, sx + 12, sy + 4, sx + 13, sy + 6, 0xFF000000);
            }

            if (!query.isEmpty()) {
                if (slot.hasItem()) {
                    String itemName = slot.getItem().getHoverName().getString().toLowerCase();
                    if (!itemName.contains(query)) {
                        gui.fill(RenderPipelines.GUI, sx, sy, sx + 16, sy + 16, 0xB0000000);
                    } else {
                        gui.fill(RenderPipelines.GUI, sx, sy, sx + 16, sy + 16, 0x4055FF55);
                    }
                } else {
                    gui.fill(RenderPipelines.GUI, sx, sy, sx + 16, sy + 16, 0xB0000000);
                }
            }
        }
    }
}
