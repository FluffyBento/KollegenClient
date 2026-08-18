package dev.kollegen.client.mixin;

import dev.kollegen.client.presence.Presence;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hängt das Kollegen-Icon (Glyph der "kollegen"-Schriftart) vor den Anzeigenamen
 * von Mitspielern, die laut Presence-Backend ebenfalls den Kollegen-Client nutzen.
 * Falls die Entry keinen eigenen Display-Namen hat (der Normalfall), wird aus dem
 * Profil-Namen einer erzeugt, damit die Tab-Liste ihn einheitlich rendert.
 */
@Mixin(PlayerListEntry.class)
public class PlayerListEntryMixin {

    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void kollegen_presence_displayName(CallbackInfoReturnable<Text> cir) {
        Text t = cir.getReturnValue();
        if (t == null) {
            try {
                PlayerListEntry self = (PlayerListEntry) (Object) this;
                Object profile = self.getClass().getMethod("getProfile").invoke(self);
                String name = (String) profile.getClass().getMethod("getName").invoke(profile);
                t = Text.literal(name);
            } catch (Throwable ignored) {
                return;
            }
        }
        Text decorated = Presence.decorateName(t);
        if (decorated != null) {
            cir.setReturnValue(decorated);
        }
    }
}
