package dev.kollegen.client.mixin;

import dev.kollegen.client.mods.modules.WeatherState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Erzwingt beim Wetter-Changer die passende Niederschlagart (Schnee):
 * sobald der Modus "Schnee" aktiv ist, liefert jeder Biome-Schnee statt Regen.
 */
@Mixin(Biome.class)
public class BiomeMixin {

    @Inject(method = "hasPrecipitation()Z", at = @At("HEAD"), cancellable = true)
    private void kollegen$hasPrecip(CallbackInfoReturnable<Boolean> cir) {
        if (WeatherState.mode == 3) cir.setReturnValue(true);
    }

    @Inject(method = "getPrecipitationAt(Lnet/minecraft/core/BlockPos;I)Lnet/minecraft/world/level/biome/Biome$Precipitation;",
            at = @At("HEAD"), cancellable = true)
    private void kollegen$precip(BlockPos pos, int packedLight, CallbackInfoReturnable<Biome.Precipitation> cir) {
        if (WeatherState.mode == 3) cir.setReturnValue(Biome.Precipitation.SNOW);
    }
}
