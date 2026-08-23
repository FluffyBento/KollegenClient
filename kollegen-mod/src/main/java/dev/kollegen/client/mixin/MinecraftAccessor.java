package dev.kollegen.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Zugriff auf Minecraft.getCurrentServer() ohne Reflection. Die Methode wird ueber
 * den refmap des Mods remapped – ein Class.getMethod("getCurrentServer") wuerde
 * unter Intermediary (Fabric-Laufzeit) mit NoSuchMethodException scheitern.
 */
@Mixin(Minecraft.class)
public interface MinecraftAccessor {

    @Accessor("getCurrentServer")
    ServerData kollegen$getCurrentServer();
}
