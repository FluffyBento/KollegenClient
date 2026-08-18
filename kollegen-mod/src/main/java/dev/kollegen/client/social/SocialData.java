package dev.kollegen.client.social;

import com.google.gson.Gson;
import dev.kollegen.client.KollegenMod;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Liest das gemeinsame Profil/Freundes-Datenblatt, das der Launcher nach
 * {@code ~/.kollegen/social.json} schreibt (siehe backend presence.rs).
 *
 * Struktur (best-effort, alle Felder optional):
 * {
 *   "me":   { "name": "...", "uuid": "...", "code": "...", "accounts": [ {"type":"microsoft","name":"..."} ] },
 *   "friends": [ { "name": "...", "uuid": "...", "server": "play.example" | null, "online": true } ]
 * }
 */
public final class SocialData {
    private static final Gson GSON = new Gson();

    public Me me;
    public List<Friend> friends = new ArrayList<>();

    public static class Me {
        public String name;
        public String uuid;
        public String code;
        public String friend_code;
        public List<Account> accounts;
    }

    public static class Account {
        public String type;
        public String name;
    }

    public static class Friend {
        public String name;
        public String uuid;
        public String server;
        public Boolean online;

        public boolean online() {
            if (Boolean.TRUE.equals(online)) return true;
            return server != null && !server.isEmpty();
        }
    }

    public String meName() {
        return me != null ? me.name : null;
    }

    public String meUuid() {
        return me != null ? me.uuid : null;
    }

    public String meCode() {
        if (me == null) return null;
        if (me.code != null && !me.code.isEmpty()) return me.code;
        return me.friend_code;
    }

    public List<Account> accounts() {
        if (me == null || me.accounts == null) return new ArrayList<>();
        return me.accounts;
    }

    public List<Friend> friends() {
        return friends == null ? new ArrayList<>() : friends;
    }

    public int friendCount() {
        return friends == null ? 0 : friends.size();
    }

    public static SocialData load() {
        for (Path p : candidatePaths()) {
            try {
                if (!Files.exists(p)) continue;
                String text = Files.readString(p);
                if (text == null || text.isBlank()) continue;
                SocialData d = GSON.fromJson(text, SocialData.class);
                if (d != null) return d;
            } catch (Exception e) {
                KollegenMod.LOGGER.warn("Konnte social.json nicht lesen ({}): {}", p, e.getMessage());
            }
        }
        return new SocialData();
    }

    private static List<Path> candidatePaths() {
        List<Path> out = new ArrayList<>();
        out.add(Path.of(".kollegen", "social.json"));
        String home = System.getProperty("user.home", ".");
        out.add(Path.of(home, ".kollegen", "social.json"));
        out.add(Path.of(home, ".local", "share", "dev.kollegen.KollegenClient", ".kollegen", "social.json"));
        String appdata = System.getenv("APPDATA");
        if (appdata != null && !appdata.isEmpty()) {
            out.add(Path.of(appdata, "dev.kollegen.KollegenClient", ".kollegen", "social.json"));
        }
        return out;
    }
}
