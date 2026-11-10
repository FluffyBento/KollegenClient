package dev.kollegen.client.mixin;

import dev.kollegen.client.mods.modules.Performance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(OptionInstance.IntRange.class)
public class OptionInstanceIntRangeMixin {

    @Inject(method = "maxInclusive", at = @At("HEAD"), cancellable = true)
    private void kollegen$overrideMaxInclusive(CallbackInfoReturnable<Integer> cir) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.options != null && mc.options.renderDistance() != null) {
                if (mc.options.renderDistance().values() == (Object) this) {
                    if (!Performance.KollegenDistance.isDistanceEnabled()) {
                        cir.setReturnValue(32);
                    } else {
                        cir.setReturnValue(Performance.KollegenDistance.getDistanceLimit());
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
