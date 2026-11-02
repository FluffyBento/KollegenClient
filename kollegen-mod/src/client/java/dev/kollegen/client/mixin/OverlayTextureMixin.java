package dev.kollegen.client.mixin;

import dev.kollegen.client.mods.modules.HitColorManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OverlayTexture.class)
public class OverlayTextureMixin {

    @Shadow @Final private DynamicTexture texture;

    @Inject(method = "<init>", at = @At("RETURN"), require = 0)
    private void kollegen$onOverlayInit(CallbackInfo ci) {
        HitColorManager.register((OverlayTexture) (Object) this, this.texture);
    }
}
