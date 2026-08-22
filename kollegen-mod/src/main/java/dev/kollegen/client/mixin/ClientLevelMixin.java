package dev.kollegen.client.mixin;

import dev.kollegen.client.mods.modules.WeatherState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Laesst den Client fuer ausgewaehlte Wetter-Modi glauben, er befinde sich in
 * einer anderen Dimension – rein client-seitig, ohne den Server zu beruehren.
 *  - End-Blitz (mode 5)  -> End-Dimension (schwarzer Himmel, Sterne, End-Flash,
 *                           dunkle End-Beleuchtung)
 *  - Basalt-Delta (mode 6) -> Nether-Dimension (Nether-Himmel, Nether-Nebel,
 *                           Nether-Beleuchtung)
 * Die visuelle Pipeline fragt pro Frame level.dimensionType() ab, daher genuegt
 * das Ueberschreiben dieser einen Methode.
 */
@Mixin(net.minecraft.client.multiplayer.ClientLevel.class)
public abstract class ClientLevelMixin {

    @Inject(method = "dimensionType()Lnet/minecraft/world/level/dimension/DimensionType;", at = @At("HEAD"), cancellable = true)
    private void kollegen$fakeDimension(CallbackInfoReturnable<DimensionType> cir) {
        try {
            int mode = WeatherState.mode;
            if (mode != 5 && mode != 6) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;
            Registry<DimensionType> reg = mc.level.registryAccess().lookupOrThrow(Registries.DIMENSION_TYPE);
            BuiltinDimensionTypes key = (mode == 5) ? BuiltinDimensionTypes.END : BuiltinDimensionTypes.NETHER;
            DimensionType t = reg.getValue(key);
            if (t != null) cir.setReturnValue(t);
        } catch (Throwable ignored) {
        }
    }
}
