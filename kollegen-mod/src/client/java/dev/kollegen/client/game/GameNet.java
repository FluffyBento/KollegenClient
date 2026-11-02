package dev.kollegen.client.game;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.function.Consumer;

/**
 * Spiel-Kommunikation zwischen Kollegen auf demselben Server.
 *
 * Transport ist der Minecraft-Chat: die Zugehove tragen ein kurzes JSON mit
 * Prefix, das der Server ganz normal an alle Spieler weiterleitet. Damit
 * funktioniert der Austausch auf jedem Vanilla-Server ohne Server-Mod.
 * Die Nachrichten werden clientseitig aus dem Chat gefiltert (siehe GameNet.consume),
 * tauchen also nicht als Spam auf.
 *
 * Achtung: Chat laesst sich von jedem Spieler faelschen. Das ist fuer den
 * Freundeskreis-Acceptabel, aber kein sicherer Kanal.
 */
public final class GameNet {
    public static final String PREFIX = "[KLG]";
    /** Maximale Chatlaenge - wir halten die Nutzlast bewusst klein. */
    private static final int MAX_PAYLOAD = 180;

    private GameNet() {
    }

    /** Aktueller Raum eines Spiels: nur Spieler auf demselben Server teilen ihn. */
    public static String room(String gameId) {
        String server = "?";
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getCurrentServer() != null) {
                server = mc.getCurrentServer().ip;
            }
        } catch (Throwable ignored) {
        }
        int h = 0x811c9dc5;
        String seed = gameId + "|" + server;
        for (int i = 0; i < seed.length(); i++) {
            h ^= seed.charAt(i);
            h *= 0x01000193;
        }
        return gameId + "-" + Integer.toHexString(h & 0x7fffffff);
    }

    /** Eigene Kurz-ID (UUID-Anfang), damit Zuege einem Spieler zugeordnet werden. */
    public static String meId() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.player.getUUID() == null) {
                return "?";
            }
            return mc.player.getUUID().toString().substring(0, 8);
        } catch (Throwable t) {
            return "?";
        }
    }

    public static String playerName() {
        try {
            Minecraft mc = Minecraft.getInstance();
            return mc.player == null ? "?" : mc.player.getGameProfile().name();
        } catch (Throwable t) {
            return "?";
        }
    }

    /** UUID eines Chat-Absenders ueber die Spielerliste (null = unbekannt). */
    public static String idOf(String name) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() == null) {
                return null;
            }
            for (PlayerInfo pi : mc.getConnection().getOnlinePlayers()) {
                if (pi.getProfile() != null && name.equals(pi.getProfile().name())) {
                    return pi.getProfile().id().toString().substring(0, 8);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** Sendet eine Spiel-Nachricht. Gibt false zurueck, wenn sie nicht abging. */
    public static boolean send(JsonObject msg) {
        try {
            String payload = msg.toString();
            if (payload.length() > MAX_PAYLOAD) {
                return false;
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.getConnection() == null) {
                return false;
            }
            mc.getConnection().sendChat(PREFIX + payload);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static JsonObject msg(String game, String type, String room) {
        JsonObject o = new JsonObject();
        o.addProperty("g", game);
        o.addProperty("t", type);
        o.addProperty("r", room);
        o.addProperty("p", meId());
        return o;
    }

    /**
     * Prueft eine Chat-Nachricht auf Spiel-Nutzwert.
     *
     * @return true, wenn die Nachricht eine Spielnachricht war (und aus dem Chat
     *         entfernt werden soll). Die Nutzlast wird an {@code sink} gereicht.
     */
    public static boolean consume(String senderName, String text, Consumer<JsonObject> sink) {
        if (text == null || !text.startsWith(PREFIX)) {
            return false;
        }
        try {
            String json = text.substring(PREFIX.length()).trim();
            if (json.isEmpty() || json.length() > 512) {
                return true;
            }
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            // Absender muss zu seiner behaupteten ID passen (grob, aber filtert
            // die meisten faelschungen durch Zufall).
            String real = idOf(senderName);
            if (o.has("p") && real != null && !real.equals(o.get("p").getAsString())) {
                return true;
            }
            sink.accept(o);
        } catch (Throwable ignored) {
        }
        return true;
    }

    /** Entfernt unser Prefix, damit fremde Clients nichts Anzeigen. */
    public static String stripPrefix(String text) {
        return text.startsWith(PREFIX) ? text.substring(PREFIX.length()).trim() : text;
    }

    /** Aktuell offene Multiplayer-Partie (empfaengt Netzwerk-Nachrichten). */
    private static volatile java.util.function.Consumer<JsonObject> sink = null;

    public static void setSink(java.util.function.Consumer<JsonObject> s) {
        sink = s;
    }

    /**
     * Zentrale Verteilung eingehender Chat-Nachrichten. Wird vom ChatMixin fuer
     * jede Spielernachricht aufgerufen.
     *
     * @return true, wenn es eine Spielnachricht war (dann aus dem Chat entfernen)
     */
    public static boolean handleChat(String senderName, Object[] args) {
        if (args == null) {
            return false;
        }
        for (Object a : args) {
            if (!(a instanceof net.minecraft.network.chat.Component c)) {
                continue;
            }
            String text;
            try {
                text = c.getString();
            } catch (Throwable t) {
                continue;
            }
            if (text == null || !text.startsWith(PREFIX)) {
                continue;
            }
            java.util.function.Consumer<JsonObject> target = sink;
            consume(senderName, text, target == null ? o -> {
            } : target);
            return true;
        }
        return false;
    }
}