package dev.kollegen.client.mixin;

import net.minecraft.client.renderer.DynamicGpuDataStorageNonMapped;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(DynamicGpuDataStorageNonMapped.class)
public class DynamicGpuDataStorageNonMappedMixin {

    @ModifyVariable(
            method = "<init>(Ljava/lang/String;III)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 2
    )
    private static int kollegen$boostNonMappedCapacity(int initialCapacity) {
        return Math.max(initialCapacity, 128);
    }
}
