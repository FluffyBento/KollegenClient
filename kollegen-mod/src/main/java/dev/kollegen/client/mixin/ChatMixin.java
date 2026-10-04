package dev.kollegen.client.mixin;

import com.mojang.authlib.GameProfile;
import dev.kollegen.client.presence.CosmeticData;
import dev.kollegen.client.presence.CosmeticText;
import dev.kollegen.client.presence.KollegenPresence;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.client.GuiMessageTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(ChatComponent.class)
public class ChatMixin {

    private static boolean kollegen$busy = false;

    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
            at = @At("HEAD"), cancellable = true)
    private void kollegen$chat(Component message, MessageSignature signature, GuiMessageTag tag, CallbackInfo ci) {
        if (kollegen$busy) return;
        try {
            Component out = kollegen$decorateChat(message);
            if (out == null) return;
            ci.cancel();
            kollegen$busy = true;
            try {
                ((ChatComponent) (Object) this).addMessage(out, signature, tag);
            } finally {
                kollegen$busy = false;
            }
        } catch (Throwable ignored) {
        }
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
            CosmeticData d = KollegenPresence.getCosmetics(profile.id());
            if (d == null || d.isEmpty()) return null;
            Object[] rebuilt = args.clone();
            rebuilt[0] = CosmeticText.decorate(name, d);
            return Component.translatable(key, rebuilt).withStyle(message.getStyle());
        }
        return null;
    }
}
