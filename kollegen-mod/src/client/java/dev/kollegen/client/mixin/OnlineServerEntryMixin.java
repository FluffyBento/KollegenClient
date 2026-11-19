package dev.kollegen.client.mixin;

import dev.kollegen.client.server.ServerBannerManager;
import dev.kollegen.client.ui.Glass;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerSelectionList.OnlineServerEntry.class)
public abstract class OnlineServerEntryMixin extends ServerSelectionList.Entry {

    @Shadow public abstract ServerData getServerData();

    @Inject(method = "extractContent", at = @At("HEAD"))
    private void kollegen$renderBanner(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float partialTick, CallbackInfo ci) {
        ServerData data = this.getServerData();
        if (data == null) return;

        int x = this.getX() + 1;
        int y = this.getY() + 1;
        int w = this.getWidth() - 2;
        int h = this.getHeight() - 2;

        boolean isDragged = ServerSelectionListMixin.DRAGGED_SERVER == data;
        Identifier banner = ServerBannerManager.getBanner(data.ip);

        if (banner != null) {
            g.blit(banner, x, y, w, h, 0.0f, 0.2f, 1.0f, 0.8f);
            g.fill(x, y, x + w, y + h, 0x5508090E);
            g.fillGradient(x, y, x + w, y + h, 0x88000000, 0xBB000000);
            g.fillGradient(x, y, x + 160, y + h, 0xAA08090E, 0x0008090E);
        } else {
            g.fillGradient(x, y, x + w, y + h, 0xDD181B26, 0xEE10121C);
            g.fill(x, y, x + w, y + h, 0x440C0E16);
        }

        if (isDragged) {
            Glass.drawHollowRect(g, x, y, w, h, 0xFFFFCC00);
            Glass.drawHollowRect(g, x + 1, y + 1, w - 2, h - 2, 0x88FFCC00);
        } else if (hovered) {
            Glass.drawHollowRect(g, x, y, w, h, 0xAA8A5CF6);
        } else {
            Glass.drawHollowRect(g, x, y, w, h, 0x22FFFFFF);
        }
    }
}
