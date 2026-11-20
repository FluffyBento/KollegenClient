package dev.kollegen.client.mixin;

import dev.kollegen.client.server.ServerBannerManager;
import dev.kollegen.client.server.ServerListSettings;
import dev.kollegen.client.ui.Glass;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerSelectionList.OnlineServerEntry.class)
public abstract class OnlineServerEntryMixin extends ServerSelectionList.Entry {

    @Shadow @Final ServerSelectionList this$0;
    @Shadow public abstract ServerData getServerData();

    @Inject(method = "extractContent", at = @At("HEAD"))
    private void kollegen$renderBanner(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float partialTick, CallbackInfo ci) {
        ServerData data = this.getServerData();
        if (data == null) return;

        int x = this.getX() + 1;
        int y = this.getY() + 1;
        int w = this.getWidth() - 2;
        int h = this.getHeight() - 2;

        boolean isDragged = ServerBannerManager.DRAGGED_SERVER == data;
        boolean isSelected = this.this$0 != null && this.this$0.getSelected() == this;
        Identifier banner = ServerBannerManager.getBanner(data);

        if (banner != null) {
            float imgAspect = 16.0f / 9.0f;
            float boxAspect = (float) w / (float) h;
            float u0 = 0.0f;
            float u1 = 1.0f;
            float v0 = 0.0f;
            float v1 = 1.0f;
            if (boxAspect >= imgAspect) {
                float vSpan = imgAspect / boxAspect;
                v0 = 0.5f - (vSpan * 0.5f);
                v1 = 0.5f + (vSpan * 0.5f);
            } else {
                float uSpan = boxAspect / imgAspect;
                u0 = 0.5f - (uSpan * 0.5f);
                u1 = 0.5f + (uSpan * 0.5f);
            }
            g.blit(banner, x, y, x + w, y + h, u0, u1, v0, v1);
            g.fillGradient(x, y, x + w, y + h, 0x15000000, 0x45000000);
        } else {
            g.fillGradient(x, y, x + w, y + h, 0xDD181B26, 0xEE10121C);
            g.fill(x, y, x + w, y + h, 0x440C0E16);
        }

        if (isDragged) {
            Glass.drawHollowRect(g, x, y, w, h, 0xFFFFCC00);
            Glass.drawHollowRect(g, x + 1, y + 1, w - 2, h - 2, 0x88FFCC00);
        } else if (isSelected) {
            Glass.drawHollowRect(g, x, y, w, h, 0xFF8A5CF6);
            Glass.drawHollowRect(g, x + 1, y + 1, w - 2, h - 2, 0x888A5CF6);
        } else if (hovered) {
            Glass.drawHollowRect(g, x, y, w, h, 0xAA8A5CF6);
        } else {
            Glass.drawHollowRect(g, x, y, w, h, 0x22FFFFFF);
        }
    }

    @Inject(method = "extractIcon", at = @At("HEAD"), cancellable = true)
    private void kollegen$extractIcon(GuiGraphicsExtractor g, int x, int y, Identifier texture, CallbackInfo ci) {
        int iconSize = ServerListSettings.getEffectiveIconSize(this.getContentHeight());
        g.blit(texture, x, y, x + iconSize, y + iconSize, 0.0f, 1.0f, 0.0f, 1.0f);
        ci.cancel();
    }

    @ModifyConstant(method = "extractContent", constant = @Constant(intValue = 32))
    private int kollegen$modifyIconSizeInExtractContent(int original) {
        return ServerListSettings.getEffectiveIconSize(this.getContentHeight());
    }

    @ModifyConstant(method = "mouseClicked", constant = @Constant(intValue = 32))
    private int kollegen$modifyIconSizeInMouseClicked(int original) {
        return ServerListSettings.getEffectiveIconSize(this.getContentHeight());
    }
}
