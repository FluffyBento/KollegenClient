package dev.kollegen.client.mixin;

import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ServerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

@Mixin(AbstractContainerWidget.class)
public abstract class ServerSelectionListMixin {

    @Unique private int kollegen$draggedIndex = -1;
    @Unique private double kollegen$dragStartY = 0.0;
    @Unique private boolean kollegen$isDragging = false;

    @Inject(method = "mouseClicked", at = @At("HEAD"))
    private void kollegen$onMouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerSelectionList ssl && event.button() == 0) {
            try {
                ServerSelectionList.Entry entry = kollegen$getEntry(ssl, event.x(), event.y());
                if (entry instanceof ServerSelectionList.OnlineServerEntry onlineEntry) {
                    List<?> onlineServers = kollegen$getOnlineServers(ssl);
                    if (onlineServers != null) {
                        this.kollegen$draggedIndex = onlineServers.indexOf(onlineEntry);
                        this.kollegen$dragStartY = event.y();
                        this.kollegen$isDragging = false;
                    }
                } else {
                    this.kollegen$draggedIndex = -1;
                }
            } catch (Throwable ignored) {
            }
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void kollegen$onMouseDragged(MouseButtonEvent event, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerSelectionList ssl && event.button() == 0 && this.kollegen$draggedIndex >= 0) {
            try {
                if (!this.kollegen$isDragging && Math.abs(event.y() - this.kollegen$dragStartY) > 5.0) {
                    this.kollegen$isDragging = true;
                }
                if (this.kollegen$isDragging) {
                    ServerSelectionList.Entry target = kollegen$getEntry(ssl, event.x(), event.y());
                    if (target instanceof ServerSelectionList.OnlineServerEntry targetOnline) {
                        List<?> onlineServers = kollegen$getOnlineServers(ssl);
                        if (onlineServers != null) {
                            int targetIdx = onlineServers.indexOf(targetOnline);
                            if (targetIdx >= 0 && targetIdx != this.kollegen$draggedIndex) {
                                JoinMultiplayerScreen screen = kollegen$getScreen(ssl);
                                if (screen != null) {
                                    ServerList serverList = screen.getServers();
                                    serverList.swap(this.kollegen$draggedIndex, targetIdx);
                                    serverList.save();
                                    ssl.updateOnlineServers(serverList);
                                    this.kollegen$draggedIndex = targetIdx;
                                    if (targetIdx < ssl.children().size()) {
                                        ssl.setSelected(ssl.children().get(targetIdx));
                                    }
                                    cir.setReturnValue(true);
                                }
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void kollegen$onMouseReleased(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerSelectionList ssl) {
            if (this.kollegen$isDragging) {
                this.kollegen$isDragging = false;
                this.kollegen$draggedIndex = -1;
                cir.setReturnValue(true);
            } else {
                this.kollegen$draggedIndex = -1;
            }
        }
    }

    @Unique
    private static ServerSelectionList.Entry kollegen$getEntry(ServerSelectionList ssl, double x, double y) {
        try {
            Method m = AbstractSelectionList.class.getDeclaredMethod("getEntryAtPosition", double.class, double.class);
            m.setAccessible(true);
            return (ServerSelectionList.Entry) m.invoke(ssl, x, y);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Unique
    private static List<?> kollegen$getOnlineServers(ServerSelectionList ssl) {
        try {
            Field f = ServerSelectionList.class.getDeclaredField("onlineServers");
            f.setAccessible(true);
            return (List<?>) f.get(ssl);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Unique
    private static JoinMultiplayerScreen kollegen$getScreen(ServerSelectionList ssl) {
        try {
            Field f = ServerSelectionList.class.getDeclaredField("screen");
            f.setAccessible(true);
            return (JoinMultiplayerScreen) f.get(ssl);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
