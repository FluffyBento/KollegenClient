package dev.kollegen.client.mixin;

import dev.kollegen.client.mods.ModuleManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(Gui.class)
public class HudMixin {

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V", at = @At("RETURN"))
    private void kollegen_client$hud(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        ModuleManager.renderHud(guiGraphics, deltaTracker.getRealtimeDeltaTicks());
    }
}
