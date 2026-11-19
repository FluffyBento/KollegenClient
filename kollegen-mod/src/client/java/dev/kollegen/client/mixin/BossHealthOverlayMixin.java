package dev.kollegen.client.mixin;

import dev.kollegen.client.mods.modules.Bossbar;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BossHealthOverlay.class)
public class BossHealthOverlayMixin {

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void kollegen$extractBossHealth(GuiGraphicsExtractor guiGraphicsExtractor, CallbackInfo ci) {
        Bossbar b = Bossbar.INSTANCE;
        if (b != null && b.enabled && b.hide.value) {
            ci.cancel();
        }
    }
}
