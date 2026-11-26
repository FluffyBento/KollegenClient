package dev.kollegen.client.mixin;

import dev.kollegen.client.scenery.SceneryManager;
import net.minecraft.client.renderer.texture.CubeMapTexture;
import net.minecraft.client.renderer.texture.TextureContents;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CubeMapTexture.class)
public class CubeMapTextureMixin {

    @Inject(method = "loadContents", at = @At("HEAD"), cancellable = true)
    private void kollegen$loadCustomScenery(ResourceManager resourceManager, CallbackInfoReturnable<TextureContents> cir) {
        CubeMapTexture self = (CubeMapTexture) (Object) this;
        if (self.resourceId() == null || !self.resourceId().getPath().endsWith("panorama")) {
            return;
        }
        if (SceneryManager.hasActiveCustomScenery()) {
            TextureContents custom = SceneryManager.loadActiveSceneryTextureContents();
            if (custom != null) {
                cir.setReturnValue(custom);
            }
        }
    }
}
