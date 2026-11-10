package dev.kollegen.client.friend;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class FriendManager {

    private static final Set<String> FRIEND_NAMES = ConcurrentHashMap.newKeySet();
    private static final Set<String> FRIEND_UUIDS = ConcurrentHashMap.newKeySet();
    private static long lastLoadTime = 0;
    private static boolean loading = false;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .build();

    private FriendManager() {
    }

    public static boolean isFriend(String username) {
        if (username == null || username.trim().isEmpty()) return false;
        ensureLoaded(false);
        return FRIEND_NAMES.contains(username.trim().toLowerCase(Locale.ROOT));
    }

    public static boolean isFriendByUuid(String uuid) {
        if (uuid == null || uuid.trim().isEmpty()) return false;
        ensureLoaded(false);
        String clean = uuid.replace("-", "").toLowerCase(Locale.ROOT);
        return FRIEND_UUIDS.contains(clean);
    }

    public static synchronized void ensureLoaded(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastLoadTime < 15_000L && !FRIEND_NAMES.isEmpty()) {
            return;
        }
        lastLoadTime = now;
        loadFromDisk();
        fetchAsync();
    }

    public static void addFriendAsync(String username, String uuid, Consumer<Boolean> callback) {
        if (username == null || username.trim().isEmpty()) {
            if (callback != null) callback.accept(false);
            return;
        }
        String cleanName = username.trim();
        FRIEND_NAMES.add(cleanName.toLowerCase(Locale.ROOT));
        if (uuid != null && !uuid.trim().isEmpty()) {
            FRIEND_UUIDS.add(uuid.replace("-", "").toLowerCase(Locale.ROOT));
        }

        Thread t = new Thread(() -> {
            boolean success = false;
            try {
                Path p = Path.of(System.getProperty("user.home"), ".kollegen", "client.json");
                if (Files.exists(p)) {
                    JsonObject o = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
                    String backend = o.has("backend") ? o.get("backend").getAsString() : "";
                    String session = o.has("session") ? o.get("session").getAsString() : "";
                    if (!backend.isEmpty() && !session.isEmpty()) {
                        String friendCode = null;
                        try {
                            String encoded = URLEncoder.encode(cleanName, StandardCharsets.UTF_8);
                            HttpRequest searchReq = HttpRequest.newBuilder(URI.create(backend + "/profiles?search=" + encoded))
                                    .timeout(Duration.ofSeconds(4))
                                    .header("Authorization", "Bearer " + session)
                                    .GET()
                                    .build();
                            HttpResponse<String> sRes = HTTP.send(searchReq, HttpResponse.BodyHandlers.ofString());
                            if (sRes.statusCode() == 200 && sRes.body() != null) {
                                JsonElement el = JsonParser.parseString(sRes.body());
                                if (el.isJsonObject()) {
                                    JsonObject sj = el.getAsJsonObject();
                                    if (sj.has("items") && sj.get("items").isJsonArray()) {
                                        for (JsonElement item : sj.getAsJsonArray("items")) {
                                            if (item.isJsonObject()) {
                                                JsonObject pj = item.getAsJsonObject();
                                                String pn = pj.has("name") ? pj.get("name").getAsString() : "";
                                                String pmn = pj.has("mc_name") ? pj.get("mc_name").getAsString() : "";
                                                if (pn.equalsIgnoreCase(cleanName) || pmn.equalsIgnoreCase(cleanName)) {
                                                    if (pj.has("code") && !pj.get("code").isJsonNull()) {
                                                        friendCode = pj.get("code").getAsString();
                                                    } else if (pj.has("friend_code") && !pj.get("friend_code").isJsonNull()) {
                                                        friendCode = pj.get("friend_code").getAsString();
                                                    }
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (Throwable ignored) {
                        }

                        JsonObject body = new JsonObject();
                        if (friendCode != null && !friendCode.isEmpty()) {
                            body.addProperty("code", friendCode);
                        }
                        body.addProperty("name", cleanName);
                        body.addProperty("mc_name", cleanName);
                        if (uuid != null && !uuid.isEmpty()) {
                            body.addProperty("uuid", uuid);
                        }

                        HttpRequest req = HttpRequest.newBuilder(URI.create(backend + "/friends"))
                                .timeout(Duration.ofSeconds(4))
                                .header("Authorization", "Bearer " + session)
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                                .build();
                        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                        success = res.statusCode() >= 200 && res.statusCode() < 300;
                    }
                }
            } catch (Throwable ignored) {
            }
            ensureLoaded(true);
            if (callback != null) {
                final boolean res = success;
                Minecraft.getInstance().execute(() -> callback.accept(res));
            }
        }, "kollegen-add-friend");
        t.setDaemon(true);
        t.start();
    }

    private static void loadFromDisk() {
        try {
            Path p = Path.of(System.getProperty("user.home"), ".kollegen", "client.json");
            if (Files.exists(p)) {
                JsonObject o = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
                if (o.has("friends") && o.get("friends").isJsonArray()) {
                    parseFriendsArray(o.getAsJsonArray("friends"));
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void fetchAsync() {
        if (loading) return;
        loading = true;
        Thread t = new Thread(() -> {
            try {
                Path p = Path.of(System.getProperty("user.home"), ".kollegen", "client.json");
                if (Files.exists(p)) {
                    JsonObject o = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
                    String backend = o.has("backend") ? o.get("backend").getAsString() : "";
                    String session = o.has("session") ? o.get("session").getAsString() : "";
                    if (!backend.isEmpty() && !session.isEmpty()) {
                        HttpRequest req = HttpRequest.newBuilder(URI.create(backend + "/friends"))
                                .timeout(Duration.ofSeconds(4))
                                .header("Authorization", "Bearer " + session)
                                .GET()
                                .build();
                        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                        if (res.statusCode() == 200 && res.body() != null) {
                            JsonElement el = JsonParser.parseString(res.body());
                            if (el.isJsonArray()) {
                                parseFriendsArray(el.getAsJsonArray());
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {
            } finally {
                loading = false;
            }
        }, "kollegen-friends-cache-loader");
        t.setDaemon(true);
        t.start();
    }

    private static void parseFriendsArray(JsonArray array) {
        if (array == null) return;
        Set<String> names = ConcurrentHashMap.newKeySet();
        Set<String> uuids = ConcurrentHashMap.newKeySet();
        for (JsonElement el : array) {
            if (!el.isJsonObject()) continue;
            JsonObject f = el.getAsJsonObject();
            if (f.has("name") && !f.get("name").isJsonNull()) {
                names.add(f.get("name").getAsString().trim().toLowerCase(Locale.ROOT));
            }
            if (f.has("mc_name") && !f.get("mc_name").isJsonNull()) {
                names.add(f.get("mc_name").getAsString().trim().toLowerCase(Locale.ROOT));
            }
            if (f.has("uuid") && !f.get("uuid").isJsonNull()) {
                uuids.add(f.get("uuid").getAsString().replace("-", "").toLowerCase(Locale.ROOT));
            }
        }
        if (!names.isEmpty()) {
            FRIEND_NAMES.clear();
            FRIEND_NAMES.addAll(names);
        }
        if (!uuids.isEmpty()) {
            FRIEND_UUIDS.clear();
            FRIEND_UUIDS.addAll(uuids);
        }
    }
}
