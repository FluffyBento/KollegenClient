package dev.kollegen.client.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Erlaubt dem Controller-Cursor (SteamDeck) das synthetische "Klicken".
 * <p>
 * Die Maus-Klick-Methode von {@link MouseHandler} ist privat und trägt unter
 * Intermediary den Namen {@code method_22684}. Statt den fragilen Interna-Namen
 * hier hart einzutragen, nutzen wir den identischen Reference-Wert wie in
 * {@code MouseHandlerMixin} – der Refmap des Mods remappt ihn zuverlässig auf
 * den Mojang-Namen der jeweiligen Minecraft-Version.
 */
@Mixin(MouseHandler.class)
public interface MouseHandlerAccessor {

    @Invoker("method_22684")
    void kollegen$click(long window, int button, int action, int mods);
}
