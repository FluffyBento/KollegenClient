package dev.kollegen.client.mixin;

import dev.kollegen.client.mods.modules.InventoryColor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(AbstractContainerScreen.class)
public class ContainerScreenMixin {

    @Shadow
    protected int leftPos;

    @Shadow
    protected int topPos;

    
    
    
    
    
    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
            at = @At("RETURN"))
    private void kollegen$tintBackground(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTick,
                                         CallbackInfo ci) {
        if (!InventoryColor.enabled) return;
        int lx = this.leftPos;
        int ty = this.topPos;
        
        int alpha = Math.max(8, (int) (255 * InventoryColor.opacity.value / 100.0));
        int argb = (alpha << 24) | (InventoryColor.color.value & 0xFF_FF_FF);
        gui.fill(RenderPipelines.GUI, lx, ty, lx + 176, ty + 166, argb);
    }
}
