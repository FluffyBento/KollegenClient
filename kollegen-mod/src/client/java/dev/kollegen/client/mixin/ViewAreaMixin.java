package dev.kollegen.client.mixin;

import dev.kollegen.client.mods.modules.Performance;
import net.minecraft.client.renderer.ViewArea;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ViewArea.class)
public class ViewAreaMixin {

    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true, ordinal = 4)
    private static int kollegen$clampViewDistance(int viewDistance) {
        if (!Performance.KollegenDistance.isDistanceEnabled()) {
            return Math.min(viewDistance, 32);
        }
        return Math.min(viewDistance, 128);
    }
}
