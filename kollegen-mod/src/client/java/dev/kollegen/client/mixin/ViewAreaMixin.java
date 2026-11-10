package dev.kollegen.client.mixin;

import net.minecraft.client.renderer.ViewArea;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ViewArea.class)
public class ViewAreaMixin {

    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true, ordinal = 4)
    private static int kollegen$clampViewDistance(int viewDistance) {
        return Math.min(viewDistance, 64);
    }
}
