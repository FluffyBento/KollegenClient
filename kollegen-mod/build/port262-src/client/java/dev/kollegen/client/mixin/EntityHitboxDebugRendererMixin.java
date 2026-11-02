package dev.kollegen.client.mixin;

import dev.kollegen.client.mods.modules.HitboxSettings;
import net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityHitboxDebugRenderer.class)
public class EntityHitboxDebugRendererMixin {

    @Inject(method = "showHitboxes", at = @At("HEAD"), cancellable = true)
    private void kollegen$onShowHitboxes(Entity entity, float partialTick, boolean isServer, CallbackInfo ci) {
        if (HitboxSettings.isCustom()) {
            HitboxSettings.renderCustomHitbox(entity, partialTick, isServer);
            ci.cancel();
        }
    }
}
