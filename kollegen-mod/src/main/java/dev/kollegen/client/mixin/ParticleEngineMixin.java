package dev.kollegen.client.mixin;

import dev.kollegen.client.mods.modules.Visual;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bricht sämtliche Partikel ab, wenn das Setting "Partikel → Aus" aktiv ist
 * (Modul "Partikel" in der Visual-Kategorie).
 */
@Mixin(ParticleEngine.class)
public class ParticleEngineMixin {

    @Inject(method = "add(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V", at = @At("HEAD"), cancellable = true)
    private void kollegen$cancelParticles(ParticleOptions options, double x, double y, double z, double xs, double ys, double zs, CallbackInfo ci) {
        if (Visual.particleCancelAll) ci.cancel();
    }
}
