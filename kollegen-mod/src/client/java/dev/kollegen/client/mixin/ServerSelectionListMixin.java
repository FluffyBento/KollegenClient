package dev.kollegen.client.mixin;

import dev.kollegen.client.server.ServerBannerManager;
import dev.kollegen.client.server.ServerListSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ServerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(AbstractContainerWidget.class)
public abstract class ServerSelectionListMixin {

    @Unique private int kollegen$draggedIndex = -1;
    @Unique private double kollegen$dragStartY = 0.0;

    @Inject(method = "maxScrollAmount", at = @At("HEAD"), cancellable = true)
    private void kollegen$maxScrollAmount(CallbackInfoReturnable<Integer> cir) {
        if ((Object) this instanceof ServerSelectionList ssl) {
            int contentH = ServerListSettings.calculateContentHeight(ssl);
            cir.setReturnValue(Math.max(0, contentH - ssl.getHeight()));
        }
    }

    @Inject(method = "setScrollAmount", at = @At("RETURN"))
    private void kollegen$onSetScrollAmount(double amount, CallbackInfo ci) {
        if ((Object) this instanceof ServerSelectionList ssl) {
            ServerListSettings.applyLayout(ssl);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"))
    private void kollegen$onMouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerSelectionList ssl && event.button() == 0) {
            ServerBannerManager.init();
            ServerListSettings.applyLayout(ssl);
            int clicked = -1;
            List<?> children = ssl.children();
            for (int i = 0; i < children.size(); i++) {
                Object obj = children.get(i);
                if (obj instanceof ServerSelectionList.OnlineServerEntry entry) {
                    if (entry.isMouseOver(event.x(), event.y())) {
                        clicked = i;
                        break;
                    }
                }
            }
            this.kollegen$draggedIndex = clicked;
            this.kollegen$dragStartY = event.y();
            ServerBannerManager.IS_DRAGGING = false;
            ServerBannerManager.DRAGGED_SERVER = (clicked >= 0 && clicked < children.size() && children.get(clicked) instanceof ServerSelectionList.OnlineServerEntry online)
                    ? online.getServerData() : null;
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void kollegen$onMouseDragged(MouseButtonEvent event, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerSelectionList ssl && event.button() == 0 && this.kollegen$draggedIndex >= 0) {
            if (!ServerBannerManager.IS_DRAGGING && Math.abs(event.y() - this.kollegen$dragStartY) > 3.0) {
                ServerBannerManager.IS_DRAGGING = true;
            }
            if (ServerBannerManager.IS_DRAGGING) {
                ServerListSettings.applyLayout(ssl);
                int targetIdx = -1;
                List<?> children = ssl.children();
                double closestDistSq = Double.MAX_VALUE;
                for (int i = 0; i < children.size(); i++) {
                    Object obj = children.get(i);
                    if (obj instanceof ServerSelectionList.OnlineServerEntry entry) {
                        if (entry.isMouseOver(event.x(), event.y())) {
                            targetIdx = i;
                            break;
                        }
                        double cx = entry.getX() + entry.getWidth() / 2.0;
                        double cy = entry.getY() + entry.getHeight() / 2.0;
                        double distSq = (event.x() - cx) * (event.x() - cx) + (event.y() - cy) * (event.y() - cy);
                        if (distSq < closestDistSq) {
                            closestDistSq = distSq;
                            targetIdx = i;
                        }
                    }
                }
                if (targetIdx >= 0 && targetIdx != this.kollegen$draggedIndex) {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.gui.screen() instanceof JoinMultiplayerScreen screen) {
                        ServerList serverList = screen.getServers();
                        if (serverList != null && this.kollegen$draggedIndex < serverList.size() && targetIdx < serverList.size()) {
                            serverList.swap(this.kollegen$draggedIndex, targetIdx);
                            serverList.save();
                            ssl.updateOnlineServers(serverList);
                            ServerListSettings.applyLayout(ssl);
                            this.kollegen$draggedIndex = targetIdx;
                            if (targetIdx < ssl.children().size() && ssl.children().get(targetIdx) instanceof ServerSelectionList.OnlineServerEntry online) {
                                ServerBannerManager.DRAGGED_SERVER = online.getServerData();
                                ssl.setSelected(online);
                            }
                            cir.setReturnValue(true);
                        }
                    }
                }
            }
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void kollegen$onMouseReleased(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerSelectionList ssl && event.button() == 0) {
            if (ServerBannerManager.IS_DRAGGING) {
                ServerBannerManager.IS_DRAGGING = false;
                this.kollegen$draggedIndex = -1;
                ServerBannerManager.DRAGGED_SERVER = null;
                ServerListSettings.applyLayout(ssl);
                cir.setReturnValue(true);
            } else {
                this.kollegen$draggedIndex = -1;
                ServerBannerManager.DRAGGED_SERVER = null;
            }
        }
    }
}
