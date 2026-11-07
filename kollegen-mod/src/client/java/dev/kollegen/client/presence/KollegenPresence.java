package dev.kollegen.client.presence;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kollegen.client.menu.SkinCapeTextureManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

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
    private static volatile CosmeticData localCapeData = null;
    private static volatile boolean localCapeLoaded = false;
    private static long lastCapeConfigModified = -1L;
    public static volatile String dbgRender = "init";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private KollegenPresence() {
    }

    public static UUID getSelfId() {
        if (selfId != null) return selfId;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            if (mc.player != null) {
                selfId = mc.player.getUUID();
                return selfId;
            }
            if (mc.getUser() != null) {
                selfId = mc.getUser().getProfileId();
                return selfId;
            }
        }
        return null;
    }

    private static long lastSignalSeen = 0;
    private static int signalTickCounter = 0;

    public static void checkRefreshSignal() {
        try {
            if ((signalTickCounter++ % 20) != 0) return;
            Path p = FabricLoader.getInstance().getConfigDir().resolve("kollegen-refresh.txt");
            if (Files.exists(p)) {
                long m = Files.getLastModifiedTime(p).toMillis();
                if (m > lastSignalSeen) {
                    lastSignalSeen = m;
                    refreshAsync();
                }
            }
            Path cp = FabricLoader.getInstance().getConfigDir().resolve("kollegen-cape.json");
            if (!Files.exists(cp)) {
                String home = System.getProperty("user.home", "");
                Path lp = Path.of(home, ".local", "share", "kollegenclient", "capes", "kollegen-cape.json");
                if (Files.exists(lp)) cp = lp;
            }
            if (Files.exists(cp)) {
                long cm = Files.getLastModifiedTime(cp).toMillis();
                UUID self = getSelfId();
                boolean hasCape = (self != null && COSMETICS.containsKey(self) && COSMETICS.get(self).capeTexture != null);
                if (cm != lastCapeConfigModified || !hasCape) {
                    loadLocalCapeConfig();
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public static void refreshAsync() {
        try {
            Thread t = new Thread(() -> {
                try {
                    fetch();
                } catch (Throwable ignored) {
                }
            }, "kollegen-refresh");
            t.setDaemon(true);
            t.start();
        } catch (Throwable ignored) {
        }
    }

    public static boolean isKollegen(UUID id) {
        return id != null && USERS.contains(id);
    }

    public static Set<UUID> users() {
        return USERS;
    }

    public static CosmeticData getCosmetics(UUID id) {
        if (id == null) return null;
        UUID self = getSelfId();
        if (self != null && id.equals(self)) {
            if (!localCapeLoaded) {
                loadLocalCapeConfig();
            }
            CosmeticData cur = COSMETICS.get(id);
            if (cur != null && cur.capeTexture != null) {
                return cur;
            }
            if (localCapeData != null) {
                if (cur != null) {
                    CosmeticData merged = new CosmeticData(
                        cur.level, cur.titleText, cur.titleColor, cur.nameColor,
                        cur.badgeIcon, cur.badgeColor, cur.stickerIcon, cur.stickerColor,
                        cur.font, cur.petKind, cur.petScale, cur.petMode,
                        cur.petName, cur.petNameColor, cur.petVariant,
                        localCapeData.capeTexture, localCapeData.capeModel, localCapeData.capeOwner,
                        cur.backAccessoryTexture, cur.backAccessoryModel,
                        cur.headAccessoryTexture, cur.headAccessoryModel,
                        cur.shieldTexture, cur.shieldModel,
                        cur.faceAccessoryTexture, cur.faceAccessoryModel,
                        cur.auraTexture, cur.auraModel,
                        cur.shoulderAccessoryTexture, cur.shoulderAccessoryModel,
                        cur.torsoAccessoryTexture, cur.torsoAccessoryModel,
                        cur.legsAccessoryTexture, cur.legsAccessoryModel,
                        cur.glovesTexture, cur.glovesModel,
                        cur.maceTexture, cur.maceModel
                    );
                    COSMETICS.put(id, merged);
                    return merged;
                }
                COSMETICS.put(id, localCapeData);
                return localCapeData;
            }
        }
        return COSMETICS.get(id);
    }

    public static void setLocalCape(String texture) {
        setLocalCape(texture, null, null);
    }

    public static void setLocalCape(String texture, String model) {
        setLocalCape(texture, model, null);
    }

    public static void setLocalCape(String texture, String model, String capeOwner) {
        UUID id = getSelfId();
        CosmeticData old = (id != null) ? COSMETICS.get(id) : localCapeData;
        String resolvedModel = model;
        if ((resolvedModel == null || resolvedModel.isEmpty()) && texture != null) {
            String lower = texture.toLowerCase(java.util.Locale.ROOT);
            if (lower.startsWith("kollegen:cosmetics/") || lower.startsWith("cape_")) {
                if (lower.contains("fluffybento")) {
                    resolvedModel = "kollegen:cosmetics/fluffybento/fluffybento_cape.bbmodel";
                } else if (lower.contains("limitlos") || lower.contains("limit_los")) {
                    resolvedModel = "kollegen:cosmetics/limit_los/limit_los_cape.bbmodel";
                } else if (lower.contains("zerocraft77")) {
                    resolvedModel = "kollegen:cosmetics/zerocraft77/zerocraft77_cape.bbmodel";
                } else if (lower.contains("irongirl")) {
                    resolvedModel = "kollegen:cosmetics/irongirl_lp/irongirl_lp_cape.bbmodel";
                } else if (lower.contains("tson") || lower.contains("t_son")) {
                    resolvedModel = "kollegen:cosmetics/t_son_/t_son__cape.bbmodel";
                } else if (lower.contains("rixhii")) {
                    resolvedModel = "kollegen:cosmetics/rixhii/rixhii_cape.bbmodel";
                } else if (lower.contains("phantom")) {
                    resolvedModel = "kollegen:cosmetics/store/phantomumhang.bbmodel";
                } else if (lower.contains("sternen")) {
                    resolvedModel = "kollegen:cosmetics/store/sternenhimmel_umhang.bbmodel";
                } else if (lower.endsWith(".png")) {
                    resolvedModel = texture.substring(0, texture.length() - 4) + ".bbmodel";
                }
            }
        }
        CosmeticData updated = new CosmeticData(
            old != null ? old.level : 0,
            old != null ? old.titleText : null,
            old != null ? old.titleColor : null,
            old != null ? old.nameColor : null,
            old != null ? old.badgeIcon : null,
            old != null ? old.badgeColor : null,
            old != null ? old.stickerIcon : null,
            old != null ? old.stickerColor : null,
            old != null ? old.font : null,
            old != null ? old.petKind : null,
            old != null ? old.petScale : 0.2,
            old != null ? old.petMode : null,
            old != null ? old.petName : null,
            old != null ? old.petNameColor : null,
            old != null ? old.petVariant : null,
            texture, resolvedModel,
            capeOwner != null ? capeOwner : (old != null ? old.capeOwner : null),
            old != null ? old.backAccessoryTexture : null,
            old != null ? old.backAccessoryModel : null,
            old != null ? old.headAccessoryTexture : null,
            old != null ? old.headAccessoryModel : null,
            old != null ? old.shieldTexture : null,
            old != null ? old.shieldModel : null,
            old != null ? old.faceAccessoryTexture : null,
            old != null ? old.faceAccessoryModel : null,
            old != null ? old.auraTexture : null,
            old != null ? old.auraModel : null,
            old != null ? old.shoulderAccessoryTexture : null,
            old != null ? old.shoulderAccessoryModel : null,
            old != null ? old.torsoAccessoryTexture : null,
            old != null ? old.torsoAccessoryModel : null,
            old != null ? old.legsAccessoryTexture : null,
            old != null ? old.legsAccessoryModel : null,
            old != null ? old.glovesTexture : null,
            old != null ? old.glovesModel : null,
            old != null ? old.maceTexture : null,
            old != null ? old.maceModel : null
        );
        localCapeData = updated;
        if (id != null) {
            COSMETICS.put(id, updated);
            USERS.add(id);
        }
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
        loadLocalCapeConfig();
        String url = base() + "/presence/" + id;
        String mod = "unknown";
        try {
            mod = dev.kollegen.client.Version.get().replace("\"", "");
        } catch (Throwable ignored) {
        }
        String cleanName = name.replace("\"", "");
        String cleanMod = mod.replace("\"", "");
        syncServerPresence(dev.kollegen.client.rpc.KollegenRPC.currentServerIp(mc), cleanName);
        thread(() -> {
            while (registered) {
                String dbg = String.valueOf(dbgRender).replace("\"", "");
                if (dbg.length() > 48) dbg = dbg.substring(0, 48);
                post(url, "{\"name\":\"" + cleanName + "\",\"mod\":\"" + cleanMod + "\",\"dbg\":\"" + dbg + "\",\"cos\":" + COSMETICS.size() + "}");
                syncServerPresence(dev.kollegen.client.rpc.KollegenRPC.currentServerIp(mc), cleanName);
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
        syncServerPresence(null, null);
        Minecraft mc = Minecraft.getInstance();
        UUID id = mc.player != null ? mc.player.getUUID() : null;
        if (id != null) {
            UUID finalId = id;
            thread(() -> delete(base() + "/presence/" + finalId));
        }
        USERS.clear();
        COSMETICS.clear();
        PetRenderer.clear();
        try {
            BbModel.clearCaches();
        } catch (Throwable ignored) {
        }
        try {
            PetModel.clearCaches();
        } catch (Throwable ignored) {
        }
        dbgRender = "init";
        loadLocalCapeConfig();
    }

    private static UUID parseUuid(String s) {
        if (s == null) return null;
        String t = s.trim();
        try {
            return UUID.fromString(t);
        } catch (Throwable ignored) {
        }
        String hex = t.toLowerCase().replaceAll("[^0-9a-f]", "");
        if (hex.length() != 32) return null;
        String dashed = hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-"
                + hex.substring(12, 16) + "-" + hex.substring(16, 20) + "-" + hex.substring(20);
        try {
            return UUID.fromString(dashed);
        } catch (Throwable ignored) {
            return null;
        }
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
            UUID self = getSelfId();
            CosmeticData localBefore = (self != null) ? COSMETICS.get(self) : localCapeData;
            if (localBefore == null) localBefore = localCapeData;

            USERS.clear();
            COSMETICS.clear();
            Minecraft mc = Minecraft.getInstance();
            Level level = mc != null ? mc.level : null;
            for (JsonElement el : arr) {
                if (el.isJsonObject()) {
                    JsonObject o = el.getAsJsonObject();
                    if (o.has("uuid")) {
                        try {
                            UUID uid = parseUuid(o.get("uuid").getAsString());
                            if (uid == null) continue;
                            USERS.add(uid);
                            CosmeticData d = CosmeticData.fromJson(o);
                            if (d != null) {
                                COSMETICS.put(uid, d);
                                if (level != null && d.petKind != null) {
                                    PetRenderer.ensurePetExists(uid, level, d.petKind, d.petVariant);
                                }
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
            if (self != null && localBefore != null) {
                CosmeticData serverCos = COSMETICS.get(self);
                if (serverCos != null) {
                    CosmeticData merged = new CosmeticData(
                        serverCos.level, serverCos.titleText, serverCos.titleColor, serverCos.nameColor,
                        serverCos.badgeIcon, serverCos.badgeColor, serverCos.stickerIcon, serverCos.stickerColor,
                        serverCos.font, serverCos.petKind, serverCos.petScale, serverCos.petMode,
                        serverCos.petName, serverCos.petNameColor, serverCos.petVariant,
                        localBefore.capeTexture != null ? localBefore.capeTexture : serverCos.capeTexture,
                        localBefore.capeModel != null ? localBefore.capeModel : serverCos.capeModel,
                        localBefore.capeOwner != null ? localBefore.capeOwner : serverCos.capeOwner,
                        serverCos.backAccessoryTexture, serverCos.backAccessoryModel,
                        serverCos.headAccessoryTexture, serverCos.headAccessoryModel,
                        serverCos.shieldTexture, serverCos.shieldModel,
                        serverCos.faceAccessoryTexture, serverCos.faceAccessoryModel,
                        serverCos.auraTexture, serverCos.auraModel,
                        serverCos.shoulderAccessoryTexture, serverCos.shoulderAccessoryModel,
                        serverCos.torsoAccessoryTexture, serverCos.torsoAccessoryModel,
                        serverCos.legsAccessoryTexture, serverCos.legsAccessoryModel,
                        serverCos.glovesTexture, serverCos.glovesModel,
                        serverCos.maceTexture, serverCos.maceModel
                    );
                    COSMETICS.put(self, merged);
                } else {
                    COSMETICS.put(self, localBefore);
                }
                USERS.add(self);
            }
            loadLocalCapeConfig();
        } catch (Throwable ignored) {
        }
    }

    public static void loadLocalCapeConfig() {
        try {
            Path p = FabricLoader.getInstance().getConfigDir().resolve("kollegen-cape.json");
            if (!Files.exists(p)) {
                String home = System.getProperty("user.home", "");
                Path lp = Path.of(home, ".local", "share", "kollegenclient", "capes", "kollegen-cape.json");
                if (Files.exists(lp)) p = lp;
            }
            if (!Files.exists(p)) return;

            long mtime = -1L;
            try {
                mtime = Files.getLastModifiedTime(p).toMillis();
            } catch (Throwable ignored) {
            }

            UUID self = getSelfId();
            boolean hasCapeInCosmetics = (self != null && COSMETICS.containsKey(self) && COSMETICS.get(self).capeTexture != null);

            if (mtime == lastCapeConfigModified && localCapeLoaded && hasCapeInCosmetics) {
                return;
            }

            String s = Files.readString(p);
            JsonObject o = JsonParser.parseString(s).getAsJsonObject();
            lastCapeConfigModified = mtime;
            localCapeLoaded = true;

            if (o.has("all_capes") && o.get("all_capes").isJsonArray()) {
                for (JsonElement el : o.getAsJsonArray("all_capes")) {
                    if (el.isJsonObject()) {
                        JsonObject ac = el.getAsJsonObject();
                        String cid = ac.has("id") && !ac.get("id").isJsonNull() ? ac.get("id").getAsString() : "";
                        if (!cid.isEmpty()) {
                            if (ac.has("frames") && ac.get("frames").isJsonArray() && ac.getAsJsonArray("frames").size() > 0) {
                                java.util.List<String> fl = new java.util.ArrayList<>();
                                for (JsonElement fe : ac.getAsJsonArray("frames")) {
                                    if (fe.isJsonPrimitive()) fl.add(fe.getAsString());
                                }
                                int fps = ac.has("fps") && !ac.get("fps").isJsonNull() ? ac.get("fps").getAsInt() : 10;
                                SkinCapeTextureManager.registerAnimatedCape(cid, fl, fps);
                            } else {
                                String raw = ac.has("texture") && !ac.get("texture").isJsonNull() ? ac.get("texture").getAsString() : (ac.has("preview") && !ac.get("preview").isJsonNull() ? ac.get("preview").getAsString() : null);
                                if (raw != null && !raw.isEmpty()) {
                                    SkinCapeTextureManager.registerStaticCape(cid, raw);
                                }
                            }
                        }
                    }
                }
            }
            if (o.has("active") && !o.get("active").isJsonNull()) {
                String activeId = o.get("active").getAsString();
                if ("none".equalsIgnoreCase(activeId) || activeId.isEmpty()) {
                    setLocalCape(null, null, null);
                } else if (o.has("cape") && o.get("cape").isJsonObject()) {
                    JsonObject c = o.getAsJsonObject("cape");
                    String capeId = c.has("id") && !c.get("id").isJsonNull() ? c.get("id").getAsString() : activeId;
                    if (c.has("frames") && c.get("frames").isJsonArray() && c.getAsJsonArray("frames").size() > 0) {
                        java.util.List<String> fl = new java.util.ArrayList<>();
                        for (JsonElement fe : c.getAsJsonArray("frames")) {
                            if (fe.isJsonPrimitive()) fl.add(fe.getAsString());
                        }
                        int fps = c.has("fps") && !c.get("fps").isJsonNull() ? c.get("fps").getAsInt() : 10;
                        SkinCapeTextureManager.registerAnimatedCape(capeId, fl, fps);
                    } else {
                        String raw = c.has("texture") && !c.get("texture").isJsonNull() ? c.get("texture").getAsString() : (c.has("preview") && !c.get("preview").isJsonNull() ? c.get("preview").getAsString() : null);
                        if (raw != null && !raw.isEmpty()) {
                            SkinCapeTextureManager.registerStaticCape(capeId, raw);
                        }
                    }
                    String tex = capeId;
                    String model = c.has("model") && !c.get("model").isJsonNull() ? c.get("model").getAsString() : null;
                    String author = c.has("creator") && !c.get("creator").isJsonNull() ? c.get("creator").getAsString() : (c.has("author") && !c.get("author").isJsonNull() ? c.get("author").getAsString() : null);
                    setLocalCape(tex, model, author);
                } else {
                    setLocalCape(activeId, null, null);
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

    public static void syncServerPresence(String currentServer, String playerName) {
        thread(() -> {
            try {
                Path p = Path.of(System.getProperty("user.home"), ".kollegen", "client.json");
                if (!Files.exists(p)) return;
                JsonObject o = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
                String backend = o.has("backend") ? o.get("backend").getAsString() : "";
                String session = o.has("session") ? o.get("session").getAsString() : "";
                if (backend.isEmpty() || session.isEmpty()) return;

                if (currentServer == null || currentServer.isEmpty() || PrivacySettings.isHideServer()) {
                    HttpRequest req = HttpRequest.newBuilder(URI.create(backend + "/presence"))
                            .timeout(Duration.ofSeconds(5))
                            .header("Authorization", "Bearer " + session)
                            .DELETE().build();
                    HTTP.send(req, HttpResponse.BodyHandlers.discarding());
                    return;
                }

                JsonObject body = new JsonObject();
                body.addProperty("server", currentServer);
                body.addProperty("name", playerName != null ? playerName : "Player");
                body.addProperty("timestamp", System.currentTimeMillis());

                HttpRequest req = HttpRequest.newBuilder(URI.create(backend + "/presence"))
                        .timeout(Duration.ofSeconds(5))
                        .header("Authorization", "Bearer " + session)
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(body.toString()))
                        .build();
                HTTP.send(req, HttpResponse.BodyHandlers.discarding());
            } catch (Throwable ignored) {
            }
        });
    }

    private static void thread(Runnable r) {
        Thread t = new Thread(r, "kollegen-presence");
        t.setDaemon(true);
        t.start();
    }
}
