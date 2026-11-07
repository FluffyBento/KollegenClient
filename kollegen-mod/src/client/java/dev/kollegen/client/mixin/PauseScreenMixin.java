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
        int switchW = 128;
        int friendW = 96;
        int hostW = 110;

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

        this.addRenderableWidget(new GlassButton(this.width - switchW - friendW - 18, topY, friendW, btnH,
                Component.literal("👥 Freunde"), btn -> {
            if (this.minecraft != null) {
                this.minecraft.setScreenAndShow(new KollegenSocialScreen(this));
            }
        }));

        if (this.minecraft != null && this.minecraft.hasSingleplayerServer()) {
            boolean active = this.minecraft.getSingleplayerServer() != null && this.minecraft.getSingleplayerServer().isPublished();
            String hostText = active ? "🌍 Welt (Aktiv)" : "🌍 Welt hosten";
            this.addRenderableWidget(new GlassButton(this.width - switchW - friendW - hostW - 26, topY, hostW, btnH,
                    Component.literal(hostText), btn -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreenAndShow(new HostWorldScreen(this));
                }
            }));
        }
    }
}
