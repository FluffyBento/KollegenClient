package dev.kollegen.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Zugriff auf Minecraft.getCurrentServer() ohne Reflection. Die Methode wird ueber
 * den refmap des Mods remapped – ein Class.getMethod("getCurrentServer") wuerde
 * unter Intermediary mit NoSuchMethodException scheitern. ACHTUNG: @Accessor
 * funktioniert nur fuer Felder; fuer Methoden ist @Invoker noetig.
 */
@Mixin(Minecraft.class)
public interface MinecraftAccessor {

    @Invoker("getCurrentServer")
    ServerData kollegen$getCurrentServer();
}
