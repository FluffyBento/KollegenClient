package dev.kollegen.client.mixin;

import dev.kollegen.client.mods.modules.Performance;
import java.util.Optional;
import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(OptionInstance.IntRange.class)
public class OptionInstanceIntRangeMixin {

    @Shadow
    @Final
    private int minInclusive;

    @Shadow
    @Final
    private int maxInclusive;

    @Inject(method = "maxInclusive", at = @At("HEAD"), cancellable = true)
    private void kollegen$overrideMaxInclusive(CallbackInfoReturnable<Integer> cir) {
        if (this.minInclusive == 2 && this.maxInclusive <= 32) {
            int limit = 128;
            try {
                if (Performance.KollegenDistance.isDistanceEnabled()) {
                    limit = Math.max(32, Math.min(128, Performance.KollegenDistance.getDistanceLimit()));
                }
            } catch (Throwable ignored) {
            }
            cir.setReturnValue(Math.max(this.maxInclusive, limit));
        }
    }

    @Inject(method = "validateValue(Ljava/lang/Integer;)Ljava/util/Optional;", at = @At("HEAD"), cancellable = true)
    private void kollegen$validateValue(Integer value, CallbackInfoReturnable<Optional<Integer>> cir) {
        if (value != null && this.minInclusive == 2 && this.maxInclusive <= 32) {
            int limit = 128;
            try {
                if (Performance.KollegenDistance.isDistanceEnabled()) {
                    limit = Math.max(32, Math.min(128, Performance.KollegenDistance.getDistanceLimit()));
                }
            } catch (Throwable ignored) {
            }
            if (value >= this.minInclusive && value <= Math.max(this.maxInclusive, limit)) {
                cir.setReturnValue(Optional.of(value));
            }
        }
    }
}
