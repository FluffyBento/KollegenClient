package dev.kollegen.client.mixin;

import net.minecraft.client.multiplayer.ServerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Zugriff auf ServerData.ip ohne Reflection. Das Feld wird ueber den refmap des
 * Mods remapped – ein Class.getField("ip") wuerde unter Intermediary mit
 * NoSuchFieldException scheitern.
 */
@Mixin(ServerData.class)
public interface ServerDataAccessor {

    @Accessor("ip")
    String kollegen$getIp();
}
