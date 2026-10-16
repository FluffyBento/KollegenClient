package dev.kollegen.client.mixin;

import dev.kollegen.client.presence.CosmeticRenderLayer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class PlayerCosmeticsMixin {
    @Shadow
    protected abstract boolean addLayer(RenderLayer<?, ?> layer);

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Inject(method = "<init>(Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;Lnet/minecraft/client/model/EntityModel;F)V", at = @At("TAIL"))
    private void kollegen$addCosmetics(EntityRendererProvider.Context context, EntityModel<?> model, float shadowRadius, CallbackInfo ci) {
        try {
            if ((Object) this instanceof AvatarRenderer) {
                addLayer(new CosmeticRenderLayer((RenderLayerParent<AvatarRenderState, PlayerModel>) (Object) this));
            }
        } catch (Throwable ignored) {
        }
    }
}
