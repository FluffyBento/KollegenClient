package dev.kollegen.client.mixin;

import dev.kollegen.client.mods.modules.Performance;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEngine.class)
public class SoundEngineMixin {

    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void kollegen$cullInaudibleSounds(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        if (sound == null) return;
        try {
            if (sound.getIdentifier() != null && sound.getIdentifier().getPath().contains("shield.break")) {
                dev.kollegen.client.mods.modules.ShieldCooldownTracker.onShieldBreak(sound.getX(), sound.getY(), sound.getZ());
            }
        } catch (Throwable ignored) {
        }
        if (!Performance.KollegenTricks.shouldCullSounds()) return;

        try {
            // Cull sounds with linear attenuation that are inaudible or out of range
            if (sound.getAttenuation() == SoundInstance.Attenuation.LINEAR) {
                float volume = sound.getVolume();
                if (volume <= 0.0001f) {
                    cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
                    return;
                }

                Minecraft mc = Minecraft.getInstance();
                if (mc != null && mc.gameRenderer != null) {
                    Camera camera = mc.gameRenderer.mainCamera();
                    if (camera != null) {
                        double camX = camera.position().x;
                        double camY = camera.position().y;
                        double camZ = camera.position().z;

                        double dx = sound.getX() - camX;
                        double dy = sound.getY() - camY;
                        double dz = sound.getZ() - camZ;
                        double distSq = dx * dx + dy * dy + dz * dz;

                        // Linear attenuation in Minecraft: effective max audible distance = max(volume, 1.0) * 16.0
                        double maxAudibleDist = Math.max(volume, 1.0f) * 16.0;
                        double cutoff = maxAudibleDist + 2.0;
                        if (distSq > cutoff * cutoff) {
                            cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
