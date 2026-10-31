package dev.kollegen.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kollegen.client.mods.ClickTracker;
import dev.kollegen.client.mods.HudModule;
import dev.kollegen.client.mods.ModuleManager;
import dev.kollegen.client.mods.modules.Freecam;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;
    @Shadow private net.minecraft.util.SmoothDouble smoothTurnX;
    @Shadow private net.minecraft.util.SmoothDouble smoothTurnY;

    private boolean kollegen$lastSmoothState = false;
    private static double kollegen$cursorRawX = 0, kollegen$cursorRawY = 0;

    @Redirect(
            method = "turnPlayer",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/Options;smoothCamera:Z")
    )
    private boolean kollegen$redirectSmoothCamera(net.minecraft.client.Options options) {
        boolean smooth = dev.kollegen.client.mods.modules.Visual.isZoomActive()
                ? dev.kollegen.client.mods.modules.Visual.isZoomSmoothCamera()
                : options.smoothCamera;
        if (smooth != this.kollegen$lastSmoothState) {
            this.kollegen$lastSmoothState = smooth;
            if (this.smoothTurnX != null) this.smoothTurnX.reset();
            if (this.smoothTurnY != null) this.smoothTurnY.reset();
        }
        return smooth;
    }

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void kollegen_client$freecamTurn(double timeDelta, CallbackInfo ci) {
        if (Freecam.isActive()) {
            Freecam.onTurn(this.accumulatedDX, this.accumulatedDY);
            this.accumulatedDX = 0.0;
            this.accumulatedDY = 0.0;
            ci.cancel();
        }
    }

    @Inject(method = "onMove", at = @At("HEAD"))
    private void kollegen_client$cursor(long window, double x, double y, CallbackInfo ci) {
        kollegen$cursorRawX = x;
        kollegen$cursorRawY = y;
        Minecraft mc = Minecraft.getInstance();
        try {
            int sw = mc.getWindow().getScreenWidth();
            int gw = mc.getWindow().getGuiScaledWidth();
            HudModule.cursorX = x * gw / sw;
            HudModule.cursorY = y * gw / sw;
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void kollegen$zoomScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (dev.kollegen.client.mods.modules.Visual.isZoomScrollEnabled()) {
            if (vertical != 0.0) {
                dev.kollegen.client.mods.modules.Visual.onZoomScroll(vertical);
                ci.cancel();
            }
        }
    }

    @Inject(method = "onButton(JLnet/minecraft/client/input/MouseButtonInfo;I)V", at = @At("HEAD"))
    private void kollegen_client$cps(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        int button = info.button();
        int mods = info.modifiers();
        if (action != 0 && action != 1) return;
        boolean press = action == 1;
        if (button == InputConstants.MOUSE_BUTTON_LEFT) {
            if (press) ClickTracker.pressLeft();
            else ClickTracker.releaseLeft();
        } else if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
            if (press) ClickTracker.pressRight();
            else ClickTracker.releaseRight();
        }

        
        if (button == InputConstants.MOUSE_BUTTON_LEFT) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() == null) {
                if (press) {
                    HudModule hm = HudModule.moduleAt(HudModule.cursorX, HudModule.cursorY);
                    if (hm != null) {
                        HudModule.dragging = hm;
                        HudModule.dragOffX = (int) HudModule.cursorX - (int) hm.offsetX.value;
                        HudModule.dragOffY = (int) HudModule.cursorY - (int) hm.offsetY.value;
                    }
                } else {
                    if (HudModule.dragging != null) {
                        HudModule.dragging = null;
                        
                        
                        ModuleManager.save();
                    }
                }
            }
        }
    }
}
