package dev.kollegen.client.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class ServerRestrictions {
    private static final String REMOTE_URL = "https://raw.githubusercontent.com/FluffyBento/KollegenClient/main/freecam-restrictions.json";
    private static final Set<String> DISALLOWED_SERVERS = Collections.synchronizedSet(new HashSet<>());
    private static volatile boolean initialized = false;

    private static final String[] DEFAULT_BLOCKED = {
            "gommehd.net",
            "cytooxien.net",
            "melvin501.de",
            "heldeninsel.net",
            "xolia.net",
            "mcpvp.club",
            "castcrafter.de",
            "survivalfreunde.de",
            "veylor.net",
            "planetsmp.com"
    };

    public static void init() {
        if (initialized) return;
        initialized = true;

        for (String s : DEFAULT_BLOCKED) {
            DISALLOWED_SERVERS.add(s.toLowerCase(Locale.ROOT));
        }

        loadBundled();
        loadCache();
        fetchRemoteAsync();
    }

    private static void loadBundled() {
        try (InputStream in = ServerRestrictions.class.getResourceAsStream("/dev/kollegen/client/freecam-restrictions.json")) {
            if (in != null) {
                parseJson(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (Throwable ignored) {
        }
    }

    private static Path getCachePath() {
        try {
            return FabricLoader.getInstance().getConfigDir().resolve("kollegen-freecam-restrictions.json");
        } catch (Throwable t) {
            return null;
        }
    }

    private static void loadCache() {
        try {
            Path cache = getCachePath();
            if (cache != null && Files.isRegularFile(cache)) {
                try (InputStream in = Files.newInputStream(cache)) {
                    parseJson(new InputStreamReader(in, StandardCharsets.UTF_8));
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void fetchRemoteAsync() {
        Thread t = new Thread(() -> {
            try {
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(3))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(REMOTE_URL))
                        .timeout(Duration.ofSeconds(4))
                        .header("User-Agent", "KollegenClient")
                        .GET()
                        .build();
                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (resp.statusCode() == 200 && resp.body() != null) {
                    String json = resp.body();
                    parseJsonString(json);
                    Path cache = getCachePath();
                    if (cache != null) {
                        Files.writeString(cache, json, StandardCharsets.UTF_8);
                    }
                }
            } catch (Throwable ignored) {
            }
        }, "Kollegen-ServerRestrictions-Fetch");
        t.setDaemon(true);
        t.start();
    }

    private static void parseJsonString(String str) {
        try {
            JsonObject obj = JsonParser.parseString(str).getAsJsonObject();
            applyJson(obj);
        } catch (Throwable ignored) {
        }
    }

    private static void parseJson(InputStreamReader reader) {
        try {
            JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
            applyJson(obj);
        } catch (Throwable ignored) {
        }
    }

    private static void applyJson(JsonObject obj) {
        if (obj == null) return;
        if (obj.has("disallowed_servers")) {
            JsonArray arr = obj.getAsJsonArray("disallowed_servers");
            for (JsonElement el : arr) {
                if (el.isJsonPrimitive()) {
                    String s = el.getAsString().trim().toLowerCase(Locale.ROOT);
                    if (!s.isEmpty()) {
                        DISALLOWED_SERVERS.add(s);
                    }
                }
            }
        }
    }

    public static boolean isFreecamBlocked(String rawAddress) {
        if (rawAddress == null || rawAddress.isEmpty()) return false;
        String host = rawAddress.trim();
        int slash = host.indexOf('/');
        if (slash >= 0) {
            host = host.substring(slash + 1);
        }
        int colon = host.indexOf(':');
        if (colon >= 0) {
            host = host.substring(0, colon);
        }
        host = host.trim().toLowerCase(Locale.ROOT);
        if (host.isEmpty()) return false;

        synchronized (DISALLOWED_SERVERS) {
            for (String blocked : DISALLOWED_SERVERS) {
                if (host.equals(blocked) || host.endsWith("." + blocked)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isFreecamBlockedCurrent() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return false;
        if (mc.hasSingleplayerServer()) return false;
        ServerData server = mc.getCurrentServer();
        if (server != null && server.ip != null) {
            return isFreecamBlocked(server.ip);
        }
        return false;
    }

    public static Set<String> getDisallowedServers() {
        return Collections.unmodifiableSet(new HashSet<>(DISALLOWED_SERVERS));
    }
}
