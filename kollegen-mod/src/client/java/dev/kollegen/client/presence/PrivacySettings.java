package dev.kollegen.client.presence;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class PrivacySettings {

    private static boolean hideServer = false;
    private static boolean disallowJoin = false;
    private static boolean loaded = false;
    private static long lastSettingsCheck = 0L;
    private static final List<String> hiddenIps = new ArrayList<>();
    private static final Set<String> hiddenNormalized = new HashSet<>();

    private PrivacySettings() {
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("kollegen-privacy.json");
    }

    public static boolean isHideServer() {
        ensureLoaded();
        return hideServer;
    }

    public static void setHideServer(boolean hide) {
        ensureLoaded();
        hideServer = hide;
        save();
    }

    public static void toggleHideServer() {
        setHideServer(!isHideServer());
    }

    public static boolean isDisallowJoin() {
        ensureLoaded();
        return disallowJoin;
    }

    public static void setDisallowJoin(boolean disallow) {
        ensureLoaded();
        disallowJoin = disallow;
        save();
    }

    public static List<String> getHiddenServerIps() {
        refreshLauncherHiddenIps();
        synchronized (hiddenIps) {
            return Collections.unmodifiableList(new ArrayList<>(hiddenIps));
        }
    }

    public static boolean isServerIpHidden(String ip) {
        if (ip == null || ip.trim().isEmpty()) return false;
        refreshLauncherHiddenIps();
        String target = ip.trim().toLowerCase();
        String targetHost = target.contains(":") ? target.substring(0, target.indexOf(':')) : target;
        synchronized (hiddenIps) {
            if (hiddenNormalized.contains(target) || hiddenNormalized.contains(targetHost)) {
                return true;
            }
            for (String h : hiddenIps) {
                String hl = h.toLowerCase();
                String hHost = hl.contains(":") ? hl.substring(0, hl.indexOf(':')) : hl;
                if (target.equals(hl) || targetHost.equals(hl) || target.equals(hHost) || targetHost.equals(hHost)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static String maskIp(String ip) {
        if (ip == null || ip.isEmpty()) return "";
        return isServerIpHidden(ip) ? "••••••••" : ip;
    }

    public static String censorIps(String text) {
        if (text == null || text.isEmpty()) return "";
        refreshLauncherHiddenIps();
        String result = text;
        List<String> current;
        synchronized (hiddenIps) {
            current = new ArrayList<>(hiddenIps);
        }
        for (String h : current) {
            if (h.isEmpty()) continue;
            try {
                result = result.replaceAll("(?i)" + Pattern.quote(h), "••••••••");
                if (h.contains(":")) {
                    String host = h.substring(0, h.indexOf(':'));
                    if (host.length() >= 4) {
                        result = result.replaceAll("(?i)" + Pattern.quote(host), "••••••••");
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return result;
    }

    private static void refreshLauncherHiddenIps() {
        long now = System.currentTimeMillis();
        if (now - lastSettingsCheck < 3000L) {
            return;
        }
        lastSettingsCheck = now;

        List<String> list = new ArrayList<>();
        try {
            String home = System.getProperty("user.home", "");
            List<Path> paths = List.of(
                    Path.of(home, ".local", "share", "kollegenclient", "settings.json"),
                    Path.of(home, "AppData", "Roaming", "kollegenclient", "settings.json"),
                    Path.of(home, "AppData", "Local", "kollegenclient", "settings.json"),
                    Path.of(home, "Library", "Application Support", "kollegenclient", "settings.json"),
                    Path.of(home, ".kollegen", "settings.json")
            );

            for (Path p : paths) {
                if (Files.exists(p)) {
                    String content = Files.readString(p);
                    JsonElement parsed = JsonParser.parseString(content);
                    if (parsed.isJsonObject()) {
                        JsonObject obj = parsed.getAsJsonObject();
                        if (obj.has("hidden_server_ips") && obj.get("hidden_server_ips").isJsonArray()) {
                            JsonArray arr = obj.getAsJsonArray("hidden_server_ips");
                            for (JsonElement el : arr) {
                                if (el.isJsonPrimitive()) {
                                    String val = el.getAsString().trim();
                                    if (!val.isEmpty() && !list.contains(val)) {
                                        list.add(val);
                                    }
                                }
                            }
                        }
                    }
                    if (!list.isEmpty()) {
                        break;
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        synchronized (hiddenIps) {
            hiddenIps.clear();
            hiddenIps.addAll(list);
            hiddenNormalized.clear();
            for (String ip : list) {
                String norm = ip.toLowerCase().trim();
                hiddenNormalized.add(norm);
                if (norm.contains(":")) {
                    hiddenNormalized.add(norm.substring(0, norm.indexOf(':')));
                }
            }
        }
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try {
            Path p = configPath();
            if (!Files.exists(p)) return;
            String text = Files.readString(p);
            JsonObject o = JsonParser.parseString(text).getAsJsonObject();
            if (o.has("hide_server")) hideServer = o.get("hide_server").getAsBoolean();
            if (o.has("disallow_join")) disallowJoin = o.get("disallow_join").getAsBoolean();
        } catch (Throwable ignored) {
        }
        refreshLauncherHiddenIps();
    }

    private static void save() {
        try {
            Path p = configPath();
            JsonObject o = new JsonObject();
            o.addProperty("hide_server", hideServer);
            o.addProperty("disallow_join", disallowJoin);
            if (p.getParent() != null) Files.createDirectories(p.getParent());
            Files.writeString(p, o.toString());
        } catch (Throwable ignored) {
        }
    }
}
