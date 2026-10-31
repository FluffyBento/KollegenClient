package dev.kollegen.client.mixin;

import com.mojang.authlib.GameProfile;
import dev.kollegen.client.mods.modules.TwitchChat;
import dev.kollegen.client.presence.CosmeticText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(ChatComponent.class)
public class ChatMixin {

    @Shadow
    @Final
    private List<GuiMessage> allMessages;

    @Unique
    private List<GuiMessage> kollegen$keptTwitch = new ArrayList<>();

    private static boolean kollegen$busy = false;
    private static boolean kollegen$gameMsg = false;

    @Shadow
    private void addMessageToDisplayQueue(GuiMessage message) {
        throw new AssertionError();
    }

    @Shadow
    private void addMessageToQueue(GuiMessage message) {
        throw new AssertionError();
    }

    @Inject(method = "addPlayerMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
            at = @At("HEAD"), cancellable = true)
    private void kollegen$chat(Component message, MessageSignature signature, GuiMessageTag tag, CallbackInfo ci) {
        if (TwitchChat.isFilterActive()) {
            int mode = TwitchChat.getFilterMode();
            if (mode == 1 || mode == 2) {
                ci.cancel();
                return;
            }
        }
        if (kollegen$busy) return;
        try {
            kollegen$gameMsg = false;
            Component out = kollegen$decorateChat(message);
            if (kollegen$gameMsg) {
                ci.cancel();
                return;
            }
            if (out == null && TwitchChat.is7tvActive() && message != null && TwitchChat.containsEmote(message.getString())) {
                out = TwitchChat.formatComponent(message);
            }
            if (out == null) return;
            ci.cancel();
            kollegen$busy = true;
            try {
                ((ChatComponent) (Object) this).addPlayerMessage(out, signature, tag);
            } finally {
                kollegen$busy = false;
            }
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "addServerSystemMessage(Lnet/minecraft/network/chat/Component;)V",
            at = @At("HEAD"), cancellable = true)
    private void kollegen$onServerSystemMessage(Component message, CallbackInfo ci) {
        if (TwitchChat.isFilterActive() && TwitchChat.getFilterMode() == 1) {
            ci.cancel();
            return;
        }
        if (kollegen$busy) return;
        if (TwitchChat.is7tvActive() && message != null && TwitchChat.containsEmote(message.getString())) {
            ci.cancel();
            kollegen$busy = true;
            try {
                ((ChatComponent) (Object) this).addServerSystemMessage(TwitchChat.formatComponent(message));
            } finally {
                kollegen$busy = false;
            }
        }
    }

    @Inject(method = "addClientSystemMessage(Lnet/minecraft/network/chat/Component;)V",
            at = @At("HEAD"), cancellable = true)
    private void kollegen$onClientSystemMessage(Component message, CallbackInfo ci) {
        if (TwitchChat.isFilterActive() && TwitchChat.getFilterMode() == 1) {
            if (message != null && !message.getString().contains("[Twitch]")) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "clearMessages(Z)V", at = @At("HEAD"))
    private void kollegen$captureTwitchOnClear(boolean clearHistory, CallbackInfo ci) {
        kollegen$keptTwitch.clear();
        if (!clearHistory || !TwitchChat.isKeepOnServerSwitch()) return;
        for (GuiMessage message : allMessages) {
            if (TwitchChat.isTwitchMessage(message.content())) {
                kollegen$keptTwitch.add(message);
            }
        }
    }

    @Inject(method = "clearMessages(Z)V", at = @At("TAIL"))
    private void kollegen$restoreTwitchOnClear(boolean clearHistory, CallbackInfo ci) {
        if (kollegen$keptTwitch.isEmpty()) return;
        try {
            for (int i = kollegen$keptTwitch.size() - 1; i >= 0; i--) {
                GuiMessage message = kollegen$keptTwitch.get(i);
                addMessageToDisplayQueue(message);
                addMessageToQueue(message);
            }
        } catch (Throwable ignored) {
        } finally {
            kollegen$keptTwitch.clear();
        }
    }

    @Redirect(method = "addMessageToQueue", at = @At(value = "INVOKE", target = "Ljava/util/List;removeLast()Ljava/lang/Object;"))
    private Object kollegen$removeOldestNonTwitchMessage(List<GuiMessage> list) {
        if (TwitchChat.isKeepVisible()) {
            for (int i = list.size() - 1; i >= 0; i--) {
                GuiMessage message = list.get(i);
                if (!TwitchChat.isTwitchMessage(message.content())) {
                    return list.remove(i);
                }
            }
        }
        return list.removeLast();
    }

    @Redirect(method = "addMessageToDisplayQueue", at = @At(value = "INVOKE", target = "Ljava/util/List;removeLast()Ljava/lang/Object;"))
    private Object kollegen$removeOldestNonTwitchLine(List<GuiMessage.Line> list) {
        if (TwitchChat.isKeepVisible()) {
            for (int i = list.size() - 1; i >= 0; i--) {
                GuiMessage.Line line = list.get(i);
                if (!TwitchChat.isTwitchMessage(line.parent().content())) {
                    return list.remove(i);
                }
            }
        }
        return list.removeLast();
    }

    private static Component kollegen$decorateChat(Component message) {
        if (message == null) return null;
        if (!(message.getContents() instanceof TranslatableContents tc)) return null;
        String key = tc.getKey();
        if (!key.equals("chat.type.text") && !key.equals("chat.type.team.sent") && !key.equals("chat.type.team.text")) return null;
        Object[] args = tc.getArgs();
        if (args.length < 1 || !(args[0] instanceof Component name)) return null;
        String plain;
        try {
            plain = name.getString();
        } catch (Throwable ignored) {
            return null;
        }
        if (plain == null || plain.isEmpty()) return null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return null;
        for (PlayerInfo pi : mc.getConnection().getOnlinePlayers()) {
            GameProfile profile = pi.getProfile();
            if (profile == null || !plain.equals(profile.name())) continue;
            kollegen$gameMsg |= dev.kollegen.client.game.GameNet.handleChat(plain, args);
            Component next = CosmeticText.decoratePlayer(name, profile.id());
            Object[] rebuilt = args.clone();
            boolean changed = false;
            if (next != name) {
                rebuilt[0] = next;
                changed = true;
            }
            if (TwitchChat.is7tvActive() && rebuilt.length >= 2) {
                Object msgArg = rebuilt[1];
                if (msgArg instanceof Component c) {
                    Component formatted = TwitchChat.formatComponent(c);
                    if (formatted != c) {
                        rebuilt[1] = formatted;
                        changed = true;
                    }
                } else if (msgArg instanceof String s) {
                    if (TwitchChat.containsEmote(s)) {
                        rebuilt[1] = TwitchChat.formatTextToComponent(s, Style.EMPTY);
                        changed = true;
                    }
                }
            }
            if (!changed) return null;
            return Component.translatable(key, rebuilt).withStyle(message.getStyle());
        }
        kollegen$gameMsg |= dev.kollegen.client.game.GameNet.handleChat(plain, args);
        return null;
    }
}
