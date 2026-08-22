package dev.kollegen.client.mixin;

import dev.kollegen.client.mods.modules.Chat;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Erfasst das Chat-Eingabefeld, damit AutoText (Schnellantwort) die Vorlage
 * direkt in ein bereits offenes Chat-Feld einfügen kann.
 */
@Mixin(ChatScreen.class)
public class ChatScreenMixin {

    @Shadow
    protected EditBox input;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void kollegen$captureInput(CallbackInfo ci) {
        Chat.activeChatInput = this.input;
    }
}
