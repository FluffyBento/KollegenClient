package dev.kollegen.client.mixin;

import dev.kollegen.client.mods.modules.TwitchChat;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GuiMessage.Line.class)
public class GuiMessageLineMixin {

    @Inject(method = "addedTime()I", at = @At("HEAD"), cancellable = true)
    private void kollegen$keepTwitchVisible(CallbackInfoReturnable<Integer> cir) {
        if (!TwitchChat.isKeepVisible()) return;
        GuiMessage.Line line = (GuiMessage.Line) (Object) this;
        if (TwitchChat.isTwitchMessage(line.parent().content())) {
            cir.setReturnValue(Integer.MAX_VALUE);
        }
    }
}
