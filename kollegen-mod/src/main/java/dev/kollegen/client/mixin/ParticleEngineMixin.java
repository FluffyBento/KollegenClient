package dev.kollegen.client.mixin;

import dev.kollegen.client.mods.modules.Visual;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bricht sämtliche Partikel ab, wenn das Setting "Partikel → Aus" aktiv ist
 * (Modul "Partikel" in der Visual-Kategorie).
 *
 * Einstiegspunkt ist {@code ParticleEngine#add(Particle)} – dort laufen alle
 * Partikel zusammen (auch die direkt erzeugten), bevor sie in die
 * Renderlisten aufgenommen werden.
 */
@Mixin(ParticleEngine.class)
public class ParticleEngineMixin {

    @Inject(method = "add(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"), cancellable = true)
    private void kollegen$cancelParticles(Particle particle, CallbackInfo ci) {
        if (Visual.particleCancelAll) ci.cancel();
    }
}
