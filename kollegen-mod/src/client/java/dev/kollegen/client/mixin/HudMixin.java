package dev.kollegen.client.mixin;

import dev.kollegen.client.mods.ModuleManager;
import dev.kollegen.client.mods.modules.InventoryEnhancements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public class HudMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At("RETURN"))
    private void kollegen_client$hud(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        ModuleManager.renderHud(guiGraphics, deltaTracker.getRealtimeDeltaTicks());
        if (InventoryEnhancements.isLockEnabled()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.gui != null && mc.gui.screen() == null) {
                int gw = mc.getWindow().getGuiScaledWidth();
                int gh = mc.getWindow().getGuiScaledHeight();
                int startX = gw / 2 - 90;
                int startY = gh - 22;
                for (int i = 0; i < 9; i++) {
                    if (InventoryEnhancements.isIndexLocked(i)) {
                        int sx = startX + i * 20;
                        int sy = startY;
                        guiGraphics.fill(RenderPipelines.GUI, sx, sy, sx + 20, sy + 20, 0x25FFAA00);
                        guiGraphics.fill(RenderPipelines.GUI, sx + 12, sy, sx + 19, sy + 7, 0xDD000000);
                        guiGraphics.fill(RenderPipelines.GUI, sx + 14, sy + 1, sx + 17, sy + 3, 0xFFFFD700);
                        guiGraphics.fill(RenderPipelines.GUI, sx + 13, sy + 3, sx + 18, sy + 6, 0xFFFFD700);
                        guiGraphics.fill(RenderPipelines.GUI, sx + 15, sy + 4, sx + 16, sy + 5, 0xFF000000);
                    }
                }
            }
        }
    }
}
