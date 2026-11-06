package dev.kollegen.client.mixin;

import dev.kollegen.client.mods.modules.Chat;
import dev.kollegen.client.mods.modules.TwitchChat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public class ChatScreenMixin {

    @Shadow
    protected EditBox input;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void kollegen$captureInput(CallbackInfo ci) {
        Chat.activeChatInput = this.input;
    }

    @Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
    private void kollegen$handleChatInput(String message, boolean addToHistory, CallbackInfo ci) {
        if (message != null) {
            String trimmed = message.trim();
            String lower = trimmed.toLowerCase();
            if (lower.equals("/ttvc") || lower.startsWith("/ttvc ")) {
                ci.cancel();
                Minecraft mc = Minecraft.getInstance();
                if (addToHistory && mc != null && mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
                    mc.gui.hud.getChat().addRecentChat(message);
                }
                String args = trimmed.length() > 5 ? trimmed.substring(5).trim() : "";
                TwitchChat.handleTtvcCommand(args);
            } else if (lower.equals("/ttv") || lower.startsWith("/ttv ")) {
                ci.cancel();
                Minecraft mc = Minecraft.getInstance();
                if (addToHistory && mc != null && mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
                    mc.gui.hud.getChat().addRecentChat(message);
                }
                String args = trimmed.length() > 4 ? trimmed.substring(4).trim() : "";
                TwitchChat.handleTtvCommand(args);
            } else if (lower.equals("/clip") || lower.startsWith("/clip ") || lower.equals("/clips") || lower.startsWith("/clips ")) {
                ci.cancel();
                Minecraft mc = Minecraft.getInstance();
                if (addToHistory && mc != null && mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
                    mc.gui.hud.getChat().addRecentChat(message);
                }
                if (dev.kollegen.client.mods.modules.Clips.INSTANCE != null) {
                    dev.kollegen.client.mods.modules.Clips.INSTANCE.triggerClip();
                }
            }
        }
    }
}
