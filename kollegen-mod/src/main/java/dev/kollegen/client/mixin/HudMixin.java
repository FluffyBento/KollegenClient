package dev.kollegen.client.mixin;

import dev.kollegen.client.mods.ModuleManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * HUD-Render-Hook (kein fabric-api): nachdem Vanilla das HUD gezeichnet hat,
 * werden alle aktiven HUD-Module gezeichnet.
 */
@Mixin(Gui.class)
public class HudMixin {

    @Inject(method = "render", at = @At("RETURN"))
    private void kollegen_client$hud(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        ModuleManager.renderHud(guiGraphics, deltaTracker.getRealtimeDeltaTicks());
    }
}
