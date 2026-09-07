package dev.kollegen.client.mixin;

import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Zugriff auf die Eingabe-Felder von {@link ClientInput}.
 * <p>
 * Seit MC 1.21 werden Bewegungs-Impulse über den {@code Input}-Record
 * ({@code keyPresses}) und das {@code Vec2} {@code moveVector} transportiert.
 * Beides wird normalerweise in {@code KeyboardInput.tick()} aus Tastatur-
 * Keybinds berechnet. Um im SteamDeck-Modus einen Gamepad-Override zu
 * ermöglichen, exponiert dieser Accessor beide Felder zum Schreiben.
 * <p>
 * Die Feld-Namen sind Mojang-Mappings; der Refmap des Mods remappt sie
 * zuverlässig auf die jeweilige Minecraft-Version.
 */
@Mixin(ClientInput.class)
public interface ClientInputAccessor {

    @Accessor("keyPresses")
    void kollegen$setKeyPresses(Input keyPresses);

    @Accessor("moveVector")
    void kollegen$setMoveVector(Vec2 moveVector);
}