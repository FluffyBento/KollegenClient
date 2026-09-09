package dev.kollegen.client.mixin;

import dev.kollegen.client.mods.modules.Visual;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(ParticleEngine.class)
public class ParticleEngineMixin {

    @Inject(method = "add(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"), cancellable = true)
    private void kollegen$cancelParticles(Particle particle, CallbackInfo ci) {
        if (Visual.particleCancelAll) ci.cancel();
    }
}
