package dev.kollegen.client.mixin;

import dev.kollegen.client.mods.modules.HealthIndicator;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>> extends EntityRenderer<T, S> {

    protected LivingEntityRendererMixin(EntityRendererProvider.Context context) {
        super(context);
    }

    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z", at = @At("HEAD"), cancellable = true)
    private void kollegen$showNameForHealthIndicator(T entity, double dist, CallbackInfoReturnable<Boolean> cir) {
        if (HealthIndicator.shouldShow(entity)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("RETURN"))
    private void kollegen$injectHealthIndicator(T entity, S state, float partialTick, CallbackInfo ci) {
        if (HealthIndicator.shouldShow(entity)) {
            state.nameTag = HealthIndicator.attachHealth(entity, state.nameTag);
            if (state.nameTagAttachment == null) {
                state.nameTagAttachment = new Vec3(0.0, entity.getBbHeight() + 0.35, 0.0);
            }
        }
    }
}
