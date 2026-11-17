package dev.kollegen.client.mixin;

import dev.kollegen.client.mods.modules.Performance;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Options.class)
public class OptionsMixin {

    @ModifyArg(
            method = "buildPlayerInformation",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ClientInformation;<init>(Ljava/lang/String;ILnet/minecraft/world/entity/player/ChatVisiblity;ZILnet/minecraft/world/entity/HumanoidArm;ZZLnet/minecraft/server/level/ParticleStatus;)V"),
            index = 1
    )
    private int kollegen$overrideViewDistance(int viewDistance) {
        if (Performance.KollegenDistance.isDistanceEnabled()) {
            return Performance.KollegenDistance.getActualDistance();
        }
        return viewDistance;
    }

    @Inject(method = "getEffectiveRenderDistance", at = @At("RETURN"), cancellable = true)
    private void kollegen$overrideEffectiveRenderDistance(CallbackInfoReturnable<Integer> cir) {
        if (Performance.KollegenDistance.isDistanceEnabled()) {
            cir.setReturnValue(Performance.KollegenDistance.getDistanceLimit());
        }
    }
}
