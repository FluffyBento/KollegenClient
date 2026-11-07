package dev.kollegen.client.mixin;

import dev.kollegen.client.menu.AccountSwitcherScreen;
import dev.kollegen.client.menu.HostWorldScreen;
import dev.kollegen.client.menu.KollegenSocialScreen;
import dev.kollegen.client.menu.ServerSwitcherScreen;
import dev.kollegen.client.ui.AccountButton;
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
        int switchW = 120;
        int friendW = 86;
        int feedW = 80;
        int hostW = 106;

        this.addRenderableWidget(new AccountButton(10, topY, false, btn -> {
            if (this.minecraft != null) {
                this.minecraft.setScreenAndShow(new AccountSwitcherScreen(this));
            }
        }));

        this.addRenderableWidget(new GlassButton(this.width - switchW - 10, topY, switchW, btnH,
                Component.literal("🌐 Server wechseln"), btn -> {
            if (this.minecraft != null) {
                this.minecraft.setScreenAndShow(new ServerSwitcherScreen(this));
            }
        }));

        this.addRenderableWidget(new GlassButton(this.width - switchW - friendW - 16, topY, friendW, btnH,
                Component.literal("👥 Freunde"), btn -> {
            if (this.minecraft != null) {
                this.minecraft.setScreenAndShow(new KollegenSocialScreen(this, 0));
            }
        }));

        this.addRenderableWidget(new GlassButton(this.width - switchW - friendW - feedW - 22, topY, feedW, btnH,
                Component.literal("📸 Feed"), btn -> {
            if (this.minecraft != null) {
                this.minecraft.setScreenAndShow(new KollegenSocialScreen(this, 1));
            }
        }));

        if (this.minecraft != null && this.minecraft.hasSingleplayerServer()) {
            boolean active = this.minecraft.getSingleplayerServer() != null && this.minecraft.getSingleplayerServer().isPublished();
            String hostText = active ? "🌍 Welt (Aktiv)" : "🌍 Welt hosten";
            this.addRenderableWidget(new GlassButton(this.width - switchW - friendW - feedW - hostW - 28, topY, hostW, btnH,
                    Component.literal(hostText), btn -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreenAndShow(new HostWorldScreen(this));
                }
            }));
        }
    }
}
