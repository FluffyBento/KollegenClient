package dev.kollegen.client.mixin;

import dev.kollegen.client.mods.modules.Performance;
import net.minecraft.client.renderer.entity.state.ItemClusterRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemClusterRenderState.class)
public class ItemClusterRenderStateMixin {

    @Inject(method = "getRenderedAmount(I)I", at = @At("HEAD"), cancellable = true)
    private static void kollegen$singleItemRender(int count, CallbackInfoReturnable<Integer> cir) {
        if (Performance.KollegenTricks.shouldOptimizeItems()) {
            cir.setReturnValue(1);
        }
    }
}
