package dev.kollegen.client.mixin;

import dev.kollegen.client.server.ServerBannerManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(AbstractContainerWidget.class)
public abstract class ServerSelectionListMixin {

    public static ServerData DRAGGED_SERVER = null;
    public static boolean IS_DRAGGING = false;

    @Unique private int kollegen$draggedIndex = -1;
    @Unique private double kollegen$dragStartY = 0.0;

    @Inject(method = "mouseClicked", at = @At("HEAD"))
    private void kollegen$onMouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerSelectionList ssl && event.button() == 0) {
            ServerBannerManager.init();
            int clicked = -1;
            List<?> children = ssl.children();
            for (int i = 0; i < children.size(); i++) {
                Object obj = children.get(i);
                if (obj instanceof ServerSelectionList.OnlineServerEntry entry) {
                    if (event.y() >= entry.getY() && event.y() < entry.getY() + entry.getHeight()
                            && event.x() >= ssl.getRowLeft() && event.x() <= ssl.getRowRight()) {
                        clicked = i;
                        break;
                    }
                }
            }
            this.kollegen$draggedIndex = clicked;
            this.kollegen$dragStartY = event.y();
            IS_DRAGGING = false;
            DRAGGED_SERVER = (clicked >= 0 && clicked < children.size() && children.get(clicked) instanceof ServerSelectionList.OnlineServerEntry online)
                    ? online.getServerData() : null;
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void kollegen$onMouseDragged(MouseButtonEvent event, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerSelectionList ssl && event.button() == 0 && this.kollegen$draggedIndex >= 0) {
            if (!IS_DRAGGING && Math.abs(event.y() - this.kollegen$dragStartY) > 3.0) {
                IS_DRAGGING = true;
            }
            if (IS_DRAGGING) {
                int targetIdx = -1;
                List<?> children = ssl.children();
                int firstOnlineIdx = -1;
                int lastOnlineIdx = -1;
                for (int i = 0; i < children.size(); i++) {
                    Object obj = children.get(i);
                    if (obj instanceof ServerSelectionList.OnlineServerEntry entry) {
                        if (firstOnlineIdx < 0) firstOnlineIdx = i;
                        lastOnlineIdx = i;
                        if (event.y() >= entry.getY() && event.y() < entry.getY() + entry.getHeight()) {
                            targetIdx = i;
                        }
                    }
                }
                if (targetIdx < 0 && firstOnlineIdx >= 0 && lastOnlineIdx >= 0) {
                    ServerSelectionList.OnlineServerEntry firstEntry = (ServerSelectionList.OnlineServerEntry) children.get(firstOnlineIdx);
                    ServerSelectionList.OnlineServerEntry lastEntry = (ServerSelectionList.OnlineServerEntry) children.get(lastOnlineIdx);
                    if (event.y() < firstEntry.getY()) {
                        targetIdx = firstOnlineIdx;
                    } else if (event.y() >= lastEntry.getY() + lastEntry.getHeight()) {
                        targetIdx = lastOnlineIdx;
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
                            this.kollegen$draggedIndex = targetIdx;
                            if (targetIdx < ssl.children().size() && ssl.children().get(targetIdx) instanceof ServerSelectionList.OnlineServerEntry online) {
                                DRAGGED_SERVER = online.getServerData();
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
            if (IS_DRAGGING) {
                IS_DRAGGING = false;
                this.kollegen$draggedIndex = -1;
                DRAGGED_SERVER = null;
                cir.setReturnValue(true);
            } else {
                this.kollegen$draggedIndex = -1;
                DRAGGED_SERVER = null;
            }
        }
    }
}
