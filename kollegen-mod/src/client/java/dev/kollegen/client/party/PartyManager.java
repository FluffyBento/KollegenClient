package dev.kollegen.client.party;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kollegen.client.menu.ServerSwitcherScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PartyManager {

    private static String leader = null;
    private static final List<String> members = new ArrayList<>();
    private static final List<String> pendingInvites = new ArrayList<>();
    private static final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();

    private PartyManager() {
    }

    public static boolean inParty() {
        return leader != null || !members.isEmpty();
    }

    public static boolean isLeader() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        String myName = mc.player.getGameProfile().name();
        return myName.equalsIgnoreCase(leader);
    }

    public static String getLeader() {
        return leader;
    }

    public static List<String> getMembers() {
        return Collections.unmodifiableList(members);
    }

    public static List<String> getPendingInvites() {
        return Collections.unmodifiableList(pendingInvites);
    }

    public static void createParty() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        String myName = mc.player.getGameProfile().name();
        leader = myName;
        members.clear();
        members.add(myName);
        sendChatMessage("🎉 Party erstellt! Lade Freunde über das Menü ein.");
    }

    public static void invite(String friendName) {
        if (friendName == null || friendName.trim().isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        String myName = mc.player.getGameProfile().name();

        if (!inParty()) {
            createParty();
        }
        if (!isLeader()) {
            sendChatMessage("❌ Nur der Party-Anführer kann Spieler einladen.");
            return;
        }

        sendPartyNetworkPayload(friendName, "[KOLLEGEN_PARTY_INVITE:" + myName + "]");
        sendChatMessage("✉ Party-Einladung an " + friendName + " gesendet!");
    }

    public static void acceptInvite(String fromLeader) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        String myName = mc.player.getGameProfile().name();

        leader = fromLeader;
        members.clear();
        members.add(fromLeader);
        members.add(myName);
        pendingInvites.remove(fromLeader);

        sendPartyNetworkPayload(fromLeader, "[KOLLEGEN_PARTY_JOIN:" + myName + "]");
        sendChatMessage("🎉 Du bist der Party von " + fromLeader + " beigetreten!");
    }

    public static void leaveParty() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        String myName = mc.player.getGameProfile().name();

        if (leader != null && !isLeader()) {
            sendPartyNetworkPayload(leader, "[KOLLEGEN_PARTY_LEAVE:" + myName + "]");
        } else if (isLeader()) {
            for (String m : members) {
                if (!m.equalsIgnoreCase(myName)) {
                    sendPartyNetworkPayload(m, "[KOLLEGEN_PARTY_DISBAND]");
                }
            }
        }

        leader = null;
        members.clear();
        sendChatMessage("🚪 Du hast die Party verlassen.");
    }

    public static void onLeaderSwitchServer(String serverIp) {
        if (!inParty() || !isLeader() || serverIp == null || serverIp.trim().isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        String myName = mc.player.getGameProfile().name();

        for (String m : members) {
            if (!m.equalsIgnoreCase(myName)) {
                sendPartyNetworkPayload(m, "[KOLLEGEN_PARTY_WARP:" + serverIp.trim() + "]");
            }
        }
    }

    public static boolean handleIncomingMessage(String sender, String text) {
        if (text == null) return false;
        Minecraft mc = Minecraft.getInstance();

        if (text.startsWith("[KOLLEGEN_PARTY_WARP:")) {
            String targetServer = text.substring(21, text.length() - 1).trim();
            if (leader != null && leader.equalsIgnoreCase(sender)) {
                mc.execute(() -> {
                    sendChatMessage("🚀 Party-Anführer wechselt Server (" + targetServer + "). Folge Party…");
                    ServerSwitcherScreen.switchServer(mc, targetServer, "Party: " + targetServer);
                });
                return true;
            }
        }

        if (text.startsWith("[KOLLEGEN_PARTY_INVITE:")) {
            String partyLeader = text.substring(23, text.length() - 1).trim();
            if (!pendingInvites.contains(partyLeader)) {
                pendingInvites.add(partyLeader);
            }
            mc.execute(() -> {
                sendChatMessage("✉ " + partyLeader + " hat dich in eine Party eingeladen! Öffne das Freunde-Menü zum Beitreten.");
            });
            return true;
        }

        if (text.startsWith("[KOLLEGEN_PARTY_JOIN:")) {
            String newMember = text.substring(21, text.length() - 1).trim();
            if (isLeader() && !members.contains(newMember)) {
                members.add(newMember);
                mc.execute(() -> sendChatMessage("🎉 " + newMember + " ist der Party beigetreten!"));
            }
            return true;
        }

        if (text.startsWith("[KOLLEGEN_PARTY_LEAVE:")) {
            String left = text.substring(22, text.length() - 1).trim();
            members.remove(left);
            mc.execute(() -> sendChatMessage("🚪 " + left + " hat die Party verlassen."));
            return true;
        }

        if (text.startsWith("[KOLLEGEN_PARTY_DISBAND]")) {
            leader = null;
            members.clear();
            mc.execute(() -> sendChatMessage("💥 Die Party wurde aufgelöst."));
            return true;
        }

        return false;
    }

    private static void sendPartyNetworkPayload(String recipient, String message) {
        Thread t = new Thread(() -> {
            try {
                Path p = Path.of(System.getProperty("user.home"), ".kollegen", "client.json");
                if (!Files.exists(p)) return;
                JsonObject o = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
                String backend = o.has("backend") ? o.get("backend").getAsString() : "";
                String session = o.has("session") ? o.get("session").getAsString() : "";
                if (backend.isEmpty() || session.isEmpty()) return;

                JsonObject body = new JsonObject();
                body.addProperty("recipient", recipient);
                body.addProperty("content", message);

                HttpRequest req = HttpRequest.newBuilder(URI.create(backend + "/internal/dm/send"))
                        .timeout(Duration.ofSeconds(4))
                        .header("Authorization", "Bearer " + session)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                        .build();
                http.send(req, HttpResponse.BodyHandlers.discarding());
            } catch (Throwable ignored) {
            }
        }, "kollegen-party-net");
        t.setDaemon(true);
        t.start();
    }

    private static void sendChatMessage(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
            mc.gui.hud.getChat().addClientSystemMessage(Component.literal("§d[Kollegen-Party] §r" + msg));
        }
    }
}
