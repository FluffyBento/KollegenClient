package dev.kollegen.client.mixin;

import net.minecraft.client.RotatingSectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(RotatingSectionStorage.class)
public class RotatingSectionStorageMixin {

    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static int kollegen$clampRadius(int radius) {
        return Math.min(Math.max(radius, 2), 32);
    }
}
