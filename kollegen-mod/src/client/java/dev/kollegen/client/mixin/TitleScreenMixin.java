package dev.kollegen.client.mixin;

import dev.kollegen.client.menu.AccountSwitcherScreen;
import dev.kollegen.client.ui.AccountButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void kollegen$addAccountButton(CallbackInfo ci) {
        this.addRenderableWidget(new AccountButton(this.width, 10, btn -> {
            if (this.minecraft != null) {
                this.minecraft.setScreenAndShow(new AccountSwitcherScreen(this));
            }
        }));
    }
}
