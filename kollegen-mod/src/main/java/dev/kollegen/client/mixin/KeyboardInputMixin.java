package dev.kollegen.client.mixin;

import dev.kollegen.client.input.GamepadInput;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * SteamDeck-Gameplay-Controller: übernimmt die Spielsteuerung im Spiel.
 * <p>
 * MC 1.21 ruft pro Spieler-Tick {@code KeyboardInput.tick()} auf (aus
 * {@code LocalPlayer.aiStep()}), das aus den Tastatur-Keybinds den
 * {@code Input}-Record und den {@code moveVector} berechnet. Ist der
 * SteamDeck-Modus aktiv und wird gerade ein Gamepad benutzt, überschreiben
 * wir die Berechnung am HEAD und cancellen die Tastatur-Auswertung.
 * <p>
 * Bewegung (linker Stick), Blick (rechter Stick) und die Aktions-Buttons
 * werden in {@link GamepadInput} gelesen und hier ins Eingabe-Modell
 * eingespeist – analog zum Controlify-Prinzip (KeyboardInput als Injections-
 * Punkt, kein fabric-api nötig).
 */
@Mixin(KeyboardInput.class)
public class KeyboardInputMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void kollegen_client$controllerOverride(CallbackInfo ci) {
        if (GamepadInput.takeOverMovement()) {
            GamepadInput.applyMovement();
            ci.cancel();
        }
    }
}