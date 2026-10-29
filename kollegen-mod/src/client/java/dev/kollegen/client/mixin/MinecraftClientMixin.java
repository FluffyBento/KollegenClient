package dev.kollegen.client.mixin;

import dev.kollegen.client.KollegenMod;
import dev.kollegen.client.mods.modules.Freecam;
import dev.kollegen.client.mods.modules.Performance;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(Minecraft.class)
public class MinecraftClientMixin {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void kollegen_client$onInit(CallbackInfo ci) {
        if (Performance.KollegenTricks.shouldBoostThread()) {
            try {
                Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
            } catch (Throwable ignored) {
            }
        }
        dev.kollegen.client.perf.KollegenStutterDetective.init();
    }

    @Inject(method = "renderFrame", at = @At("HEAD"))
    private void kollegen_client$renderFrameStart(boolean renderLevel, CallbackInfo ci) {
        dev.kollegen.client.perf.KollegenStutterDetective.onFrameStart();
    }

    @Inject(method = "renderFrame", at = @At("TAIL"))
    private void kollegen_client$renderFrameEnd(boolean renderLevel, CallbackInfo ci) {
        dev.kollegen.client.perf.KollegenStutterDetective.onFrameEnd();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void kollegen_client$onTick(CallbackInfo ci) {
        KollegenMod.onTick();
    }

    
    @Inject(method = "tick", at = @At("TAIL"))
    private void kollegen_client$onFrame(CallbackInfo ci) {
        KollegenMod.onFrame();
    }

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void kollegen_client$freecamAttack(CallbackInfoReturnable<Boolean> cir) {
        if (Freecam.isActive()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "startAttack", at = @At("RETURN"))
    private void kollegen_client$trackAttackReach(CallbackInfoReturnable<Boolean> cir) {
        try {
            if (cir.getReturnValue()) {
                Minecraft mc = (Minecraft) (Object) this;
                if (mc.player != null && mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult ehr) {
                    double dist = mc.player.getEyePosition().distanceTo(ehr.getLocation());
                    dev.kollegen.client.mods.modules.Hud.recordAttackHit(dist);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void kollegen_client$freecamUse(CallbackInfo ci) {
        if (Freecam.isActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void kollegen_client$freecamContinueAttack(boolean leftClick, CallbackInfo ci) {
        if (Freecam.isActive()) {
            ci.cancel();
        }
    }
}
