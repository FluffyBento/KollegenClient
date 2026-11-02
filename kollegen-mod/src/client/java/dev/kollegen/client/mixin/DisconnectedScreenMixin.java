package dev.kollegen.client.mixin;

import dev.kollegen.client.gui.AutoReconnectStatusWidget;
import dev.kollegen.client.mods.modules.AutoReconnect;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DisconnectedScreen.class)
public abstract class DisconnectedScreenMixin extends Screen {

    @Shadow
    @Final
    private Screen parent;

    private Button kollegen$reconnectBtn;
    private Button kollegen$cancelBtn;
    private AutoReconnectStatusWidget kollegen$statusWidget;

    protected DisconnectedScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void kollegen$onInit(CallbackInfo ci) {
        DisconnectedScreen self = (DisconnectedScreen) (Object) this;
        AutoReconnect.onScreenInit(self, this.parent);

        if (!AutoReconnect.isReady()) return;

        int totalW = 140 + 8 + 90;
        int startX = (this.width - totalW) / 2;
        int startY = this.height - 30;

        this.kollegen$reconnectBtn = Button.builder(
                Component.literal("Jetzt verbinden"),
                b -> AutoReconnect.reconnectNow(this.minecraft)
        ).bounds(startX, startY, 140, 20).build();

        this.kollegen$cancelBtn = Button.builder(
                Component.literal("Abbrechen"),
                b -> AutoReconnect.cancel()
        ).bounds(startX + 148, startY, 90, 20).build();

        this.kollegen$statusWidget = new AutoReconnectStatusWidget(startX, startY - 14, totalW, 12, this.font);

        this.addRenderableWidget(this.kollegen$reconnectBtn);
        this.addRenderableWidget(this.kollegen$cancelBtn);
        this.addRenderableWidget(this.kollegen$statusWidget);

        AutoReconnect.attachButtons(this.kollegen$reconnectBtn, this.kollegen$cancelBtn);
    }

    @Inject(method = "repositionElements", at = @At("RETURN"))
    private void kollegen$onReposition(CallbackInfo ci) {
        if (this.kollegen$reconnectBtn == null) return;
        int totalW = 140 + 8 + 90;
        int startX = (this.width - totalW) / 2;
        int startY = this.height - 30;
        this.kollegen$reconnectBtn.setX(startX);
        this.kollegen$reconnectBtn.setY(startY);
        if (this.kollegen$cancelBtn != null) {
            this.kollegen$cancelBtn.setX(startX + 148);
            this.kollegen$cancelBtn.setY(startY);
        }
        if (this.kollegen$statusWidget != null) {
            this.kollegen$statusWidget.setX(startX);
            this.kollegen$statusWidget.setY(startY - 14);
            this.kollegen$statusWidget.setWidth(totalW);
        }
    }
}
