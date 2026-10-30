package dev.kollegen.client.mixin;

import dev.kollegen.client.mods.modules.FovModifier;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public class AbstractClientPlayerMixin {

    @Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
    private void kollegen$modifyFov(boolean isFirstPerson, float fovEffectScale, CallbackInfoReturnable<Float> cir) {
        AbstractClientPlayer player = (AbstractClientPlayer) (Object) this;
        float original = cir.getReturnValueF();
        boolean isSprinting = player.isSprinting();
        boolean isUsingBow = player.isUsingItem() && (player.getUseItem().is(Items.BOW) || player.getUseItem().is(Items.CROSSBOW));
        boolean isFlying = player.getAbilities().flying;

        float modified = FovModifier.modifyFov(original, isSprinting, isUsingBow, isFlying);
        if (modified != original) {
            cir.setReturnValue(modified);
        }
    }
}
