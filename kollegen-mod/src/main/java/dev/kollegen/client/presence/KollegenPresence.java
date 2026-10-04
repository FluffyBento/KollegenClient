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


public final class KollegenPresence {
    private static final Set<UUID> USERS = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, CosmeticData> COSMETICS = new ConcurrentHashMap<>();
    private static volatile boolean registered = false;
    private static volatile UUID selfId = null;
    private static volatile String selfName = null;
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

    public static CosmeticData getCosmetics(UUID id) {
        return id == null ? null : COSMETICS.get(id);
    }

    
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
        return "https://kollegen.me"; 
    }

    
    public static void join(Minecraft mc) {
        if (mc.player == null) return;
        UUID id = mc.player.getUUID();
        String name = mc.player.getName().getString();
        selfId = id;
        selfName = name;
        registered = true;
        String url = base() + "/presence/" + id;
        String body = "{\"name\":\"" + name.replace("\"", "") + "\"}";
        thread(() -> {
            while (registered) {
                post(url, body);
                fetch();
                try {
                    Thread.sleep(30000);
                } catch (InterruptedException e) {
                    return;
                } catch (Throwable ignored) {
                    return;
                }
            }
        });
    }

    
    public static void leave() {
        if (!registered) return;
        registered = false;
        selfId = null;
        selfName = null;
        Minecraft mc = Minecraft.getInstance();
        UUID id = mc.player != null ? mc.player.getUUID() : null;
        if (id != null) {
            UUID finalId = id;
            thread(() -> delete(base() + "/presence/" + finalId));
        }
        USERS.clear();
        COSMETICS.clear();
    }

    private static void fetch() {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(base() + "/presence/uuids"))
                    .timeout(Duration.ofSeconds(5))
                    .GET().build();
            HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200 || res.body() == null) return;
            JsonElement e = JsonParser.parseString(res.body());
            if (!e.isJsonArray()) return;
            JsonArray arr = e.getAsJsonArray();
            USERS.clear();
            COSMETICS.clear();
            for (JsonElement el : arr) {
                if (el.isJsonObject()) {
                    JsonObject o = el.getAsJsonObject();
                    if (o.has("uuid")) {
                        try {
                            UUID uid = UUID.fromString(o.get("uuid").getAsString());
                            USERS.add(uid);
                            CosmeticData d = CosmeticData.fromJson(o);
                            if (d != null) COSMETICS.put(uid, d);
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
