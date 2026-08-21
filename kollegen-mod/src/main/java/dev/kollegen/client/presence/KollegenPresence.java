package dev.kollegen.client.presence;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.player.Player;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.WeakHashMap;

/**
 * Verwaltet die "Kollegen-Client-Online-Liste" auf EUEREM Backend-Server.
 *
 * Vertrag (vom Launcher/Backend bereitzustellen):
 *   POST   {base}/presence/{uuid}   Body: {"name":"..."}   (beitreten)
 *   DELETE {base}/presence/{uuid}                              (offline)
 *   GET    {base}/presence          → JSON-Array [{uuid,name}]
 *
 * Die Basis-URL wird aus {@code config/kollegen-server.txt} gelesen (eine Zeile),
 * sonst aus dem Konstanten-Default. Der Launcher kann diese Datei schreiben.
 */
public final class KollegenPresence {
    private static final Set<UUID> USERS = ConcurrentHashMap.newKeySet();
    private static boolean registered = false;
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private KollegenPresence() {
    }

    public static boolean isKollegen(UUID id) {
        return id != null && USERS.contains(id);
    }

    public static Set<UUID> users() {
        return USERS;
    }

    /**
     * Render-State-basierte Markierung fuer das Namensschild-Badge.
     * Render-States sind pro Entity stabil, daher reicht eine WeakHashMap.
     */
    private static final Map<EntityRenderState, Boolean> STATE_KOLLEGEN = new WeakHashMap<>();

    public static void markKollegen(EntityRenderState state, boolean value) {
        if (state != null) STATE_KOLLEGEN.put(state, value);
    }

    public static boolean isKollegen(EntityRenderState state) {
        return state != null && Boolean.TRUE.equals(STATE_KOLLEGEN.get(state));
    }

    private static String base() {
        try {
            Path p = FabricLoader.getInstance().getConfigDir().resolve("kollegen-server.txt");
            if (Files.exists(p)) {
                String s = Files.readString(p).trim();
                if (!s.isEmpty()) return s.replaceAll("/+$", "");
            }
        } catch (Throwable ignored) {
        }
        return "https://kollegen.example/api"; // ← vom Launcher/Backend setzen
    }

    /** Wird beim Betreten eines Servers / einer Welt aufgerufen. */
    public static void join(Minecraft mc) {
        if (mc.player == null) return;
        UUID id = mc.player.getUUID();
        String name = mc.player.getName().getString();
        registered = true;
        String url = base() + "/presence/" + id;
        String body = "{\"name\":\"" + name.replace("\"", "") + "\"}";
        thread(() -> {
            post(url, body);
            fetch();
        });
    }

    /** Wird beim Verlassen (Disconnect / Menü) aufgerufen. */
    public static void leave() {
        if (!registered) return;
        registered = false;
        Minecraft mc = Minecraft.getInstance();
        UUID id = mc.player != null ? mc.player.getUUID() : null;
        if (id != null) {
            UUID finalId = id;
            thread(() -> delete(base() + "/presence/" + finalId));
        }
        USERS.clear();
    }

    private static void fetch() {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(base() + "/presence"))
                    .timeout(Duration.ofSeconds(5))
                    .GET().build();
            HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200 || res.body() == null) return;
            JsonElement e = JsonParser.parseString(res.body());
            if (!e.isJsonArray()) return;
            JsonArray arr = e.getAsJsonArray();
            USERS.clear();
            for (JsonElement el : arr) {
                if (el.isJsonObject()) {
                    JsonObject o = el.getAsJsonObject();
                    if (o.has("uuid")) {
                        try {
                            USERS.add(UUID.fromString(o.get("uuid").getAsString()));
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void post(String url, String body) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (Throwable ignored) {
        }
    }

    private static void delete(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .DELETE().build();
            HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (Throwable ignored) {
        }
    }

    private static void thread(Runnable r) {
        Thread t = new Thread(r, "kollegen-presence");
        t.setDaemon(true);
        t.start();
    }
}
