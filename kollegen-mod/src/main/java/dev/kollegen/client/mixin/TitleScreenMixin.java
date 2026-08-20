package dev.kollegen.client.mixin;

import dev.kollegen.client.KollegenMod;
import dev.kollegen.client.menu.SocialButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class TitleScreenMixin {

    @Inject(method = "render", at = @At("RETURN"))
    private void onRender(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (KollegenMod.CONFIG != null && !KollegenMod.CONFIG.replaceLogo) return;

        Minecraft mc = Minecraft.getInstance();
        Identifier logo = Identifier.fromNamespaceAndPath("kollegen", "textures/gui/logo.png");

        int w = 220;
        int h = (int) (w * 400.0 / 1200.0); // Logo.png is 1200x400 (3:1 aspect ratio)
        int x = (mc.getWindow().getGuiScaledWidth() - w) / 2;
        int y = (int) (mc.getWindow().getGuiScaledHeight() * 0.18);

        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, logo, x, y, 0.0f, 0.0f, w, h, 1200, 400);

        // Essential-artiger „Freunde"-Button oben rechts (statt Mitte).
        SocialButton.draw(guiGraphics, mc, mouseX, mouseY);
    }
}
