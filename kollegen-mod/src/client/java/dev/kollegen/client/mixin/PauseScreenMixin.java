package dev.kollegen.client.mixin;

import dev.kollegen.client.menu.KollegenSocialScreen;
import dev.kollegen.client.menu.ServerSwitcherScreen;
import dev.kollegen.client.ui.GlassButton;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {

    protected PauseScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void kollegen$addPauseMenuButtons(CallbackInfo ci) {
        int btnH = 22;
        int topY = 10;
        int switchW = 128;
        int friendW = 96;

        this.addRenderableWidget(new GlassButton(this.width - switchW - 10, topY, switchW, btnH,
                Component.literal("🌐 Server wechseln"), btn -> {
            if (this.minecraft != null) {
                this.minecraft.setScreenAndShow(new ServerSwitcherScreen(this));
            }
        }));

        this.addRenderableWidget(new GlassButton(this.width - switchW - friendW - 18, topY, friendW, btnH,
                Component.literal("👥 Freunde"), btn -> {
            if (this.minecraft != null) {
                this.minecraft.setScreenAndShow(new KollegenSocialScreen(this));
            }
        }));
    }
}
