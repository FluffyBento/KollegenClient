package dev.kollegen.client.mods.modules;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.objects.AtlasSprite;
import net.minecraft.resources.Identifier;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class TwitchBadges {

    public static final String LOGO_KEY = "twitch_logo";
    private static final String LOGO_RESOURCE = "/assets/kollegen/textures/font/twitch.png";

    private static final int[] DEFAULT_COLORS = new int[]{
            0xFFFF0000, 0xFF0000FF, 0xFF00FF00, 0xFFB22222, 0xFFFF7F50,
            0xFF9ACD32, 0xFFFF4500, 0xFF2E8B57, 0xFFDAA520, 0xFFD2691E,
            0xFF5F9EA0, 0xFF1E90FF, 0xFFFF69B4, 0xFF8A2BE2, 0xFF00FF7F
    };

    private static final Map<String, BadgeImage> GLOBAL = new ConcurrentHashMap<>();
    private static final Map<String, BadgeImage> CHANNEL = new ConcurrentHashMap<>();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static volatile boolean globalLoaded = false;
    private static volatile String activeRoomId = "";
    private static volatile int channelGeneration = 0;
    private static volatile boolean logoRequested = false;

    private TwitchBadges() {
    }

    public static void init() {
        ensureLogo();
        loadGlobal();
    }

    public static void ensureLogo() {
        if (logoRequested) return;
        logoRequested = true;
        SevenTvEmoteManager.ensureBuiltinTexture(LOGO_KEY, LOGO_RESOURCE, 10.0f);
    }

    public static void setChannel(String roomId) {
        if (roomId == null || roomId.isEmpty()) return;
        if (roomId.equals(activeRoomId)) return;
        activeRoomId = roomId;
        CHANNEL.clear();
        loadChannel(roomId, ++channelGeneration);
    }

    public static Component logo() {
        ensureLogo();
        MutableComponent component = Component.object(
                new AtlasSprite(Identifier.tryParse("kollegen:7tv"), Identifier.tryParse("kollegen:" + LOGO_KEY)),
                Component.literal("[Twitch]")
        );
        component.withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(Component.literal("Twitch"))));
        return component;
    }

    public static Component component(Badge badge) {
        if (badge == null) return Component.empty();
        if (badge.hasImage) {
            MutableComponent component = Component.object(
                    new AtlasSprite(Identifier.tryParse("kollegen:7tv"), Identifier.tryParse("kollegen:" + badge.key)),
                    Component.literal("[" + badge.shortLabel() + "]")
            );
            component.withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(Component.literal(badge.label))));
            return component;
        }
        return Component.literal("[" + badge.shortLabel() + "]").withStyle(s -> s.withColor(badge.color()));
    }

    public static Identifier texture(Badge badge) {
        if (badge == null || !badge.hasImage) return null;
        return SevenTvEmoteManager.getTextureByKey(badge.key);
    }

    public static int defaultColor(String name) {
        if (name == null || name.isEmpty()) return DEFAULT_COLORS[0];
        int n = name.charAt(0) + name.charAt(name.length() - 1);
        return DEFAULT_COLORS[Math.abs(n) % DEFAULT_COLORS.length];
    }

    public static List<Badge> parse(String tag) {
        List<Badge> out = new ArrayList<>();
        if (tag == null || tag.isEmpty()) return out;
        for (String part : tag.split(",")) {
            if (part.isEmpty()) continue;
            int slash = part.indexOf('/');
            String setId = slash >= 0 ? part.substring(0, slash) : part;
            String version = slash >= 0 ? part.substring(slash + 1) : "1";
            setId = setId.trim();
            version = version.trim();
            if (setId.isEmpty()) continue;
            out.add(resolve(setId, version));
        }
        return out;
    }

    private static Badge resolve(String rawSetId, String version) {
        String setId = alias(rawSetId);
        BadgeImage image = CHANNEL.get(setId + "/" + version);
        if (image == null) image = GLOBAL.get(setId + "/" + version);
        if (image == null) image = findBySet(CHANNEL, setId);
        if (image == null) image = findBySet(GLOBAL, setId);

        String badgeVersion = image != null ? image.version : version;
        String key = SevenTvEmoteManager.getSafeKey("badge:" + setId + ":" + badgeVersion);
        if (image != null) {
            SevenTvEmoteManager.ensureTextureFromUrl(key, image.url, 9.0f);
        }
        String label = image != null ? image.title : prettify(rawSetId);
        return new Badge(setId, version, key, label, image != null);
    }

    private static BadgeImage findBySet(Map<String, BadgeImage> map, String setId) {
        BadgeImage fallback = null;
        for (Map.Entry<String, BadgeImage> entry : map.entrySet()) {
            BadgeImage value = entry.getValue();
            if (!value.setId.equals(setId)) continue;
            if (fallback == null) fallback = value;
            if ("1".equals(value.version) || "0".equals(value.version)) {
                return value;
            }
        }
        return fallback;
    }

    private static String alias(String setId) {
        String s = setId.toLowerCase(Locale.ROOT);
        switch (s) {
            case "headmod":
            case "head-mod":
            case "head_moderator":
            case "head-moderator":
                return "lead_moderator";
            case "broadcaster":
                return "broadcaster";
            default:
                return s;
        }
    }

    private static String prettify(String setId) {
        if (setId == null || setId.isEmpty()) return "";
        String s = setId.replace('_', ' ').replace('-', ' ').trim();
        if (s.isEmpty()) return setId;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static void loadGlobal() {
        if (globalLoaded) return;
        globalLoaded = true;
        CompletableFuture.runAsync(() -> {
            try {
                JsonArray array = fetchArray("https://api.ivr.fi/v2/twitch/badges/global");
                for (JsonElement element : array) {
                    parseSet(element, GLOBAL);
                }
            } catch (Throwable ignored) {}
        });
    }

    private static void loadChannel(String roomId, int generation) {
        CompletableFuture.runAsync(() -> {
            try {
                JsonArray array = fetchArray("https://api.ivr.fi/v2/twitch/badges/channel?id=" + roomId);
                if (generation != channelGeneration) return;
                for (JsonElement element : array) {
                    parseSet(element, CHANNEL);
                }
            } catch (Throwable ignored) {}
        });
    }

    private static void parseSet(JsonElement element, Map<String, BadgeImage> target) {
        if (element == null || !element.isJsonObject()) return;
        JsonObject object = element.getAsJsonObject();
        if (!object.has("set_id") || !object.has("versions") || !object.get("versions").isJsonArray()) return;
        String setId = object.get("set_id").getAsString();
        if (setId == null || setId.isEmpty()) return;
        for (JsonElement versionElement : object.getAsJsonArray("versions")) {
            if (!versionElement.isJsonObject()) continue;
            JsonObject versionObject = versionElement.getAsJsonObject();
            String version = versionObject.has("id") ? versionObject.get("id").getAsString() : "1";
            String url = null;
            if (versionObject.has("image_url_2x")) {
                url = versionObject.get("image_url_2x").getAsString();
            } else if (versionObject.has("image_url_1x")) {
                url = versionObject.get("image_url_1x").getAsString();
            }
            if (url == null || url.isEmpty()) continue;
            String title = versionObject.has("title") ? versionObject.get("title").getAsString() : prettify(setId);
            target.put(setId + "/" + version, new BadgeImage(setId, version, url, title));
        }
    }

    private static JsonArray fetchArray(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) return new JsonArray();
            JsonElement element = JsonParser.parseString(response.body());
            return element != null && element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
        } catch (Throwable t) {
            return new JsonArray();
        }
    }

    private static final class BadgeImage {
        final String setId;
        final String version;
        final String url;
        final String title;

        BadgeImage(String setId, String version, String url, String title) {
            this.setId = setId;
            this.version = version;
            this.url = url;
            this.title = title;
        }
    }

    public static final class Badge {
        public final String setId;
        public final String version;
        public final String key;
        public final String label;
        public final boolean hasImage;

        Badge(String setId, String version, String key, String label, boolean hasImage) {
            this.setId = setId;
            this.version = version;
            this.key = key;
            this.label = label;
            this.hasImage = hasImage;
        }

        public String shortLabel() {
            switch (setId) {
                case "broadcaster":
                    return "HOST";
                case "moderator":
                    return "MOD";
                case "lead_moderator":
                    return "HEAD";
                case "vip":
                    return "VIP";
                case "editor":
                    return "EDIT";
                case "subscriber":
                    return "SUB";
                case "staff":
                    return "STAFF";
                case "admin":
                    return "ADMIN";
                case "global_mod":
                    return "GMOD";
                case "partner":
                    return "OK";
                case "premium":
                    return "PRIME";
                case "turbo":
                    return "TURBO";
                default:
                    String text = label != null && !label.isEmpty() ? label : setId;
                    return text.length() > 6 ? text.substring(0, 6) : text;
            }
        }

        public int color() {
            switch (setId) {
                case "broadcaster":
                    return 0xFFFF3864;
                case "moderator":
                case "lead_moderator":
                    return 0xFF00D26A;
                case "vip":
                    return 0xFFFF66CC;
                case "editor":
                    return 0xFF9146FF;
                case "subscriber":
                    return 0xFF00E5FF;
                case "staff":
                case "admin":
                    return 0xFFFFB300;
                default:
                    return 0xFF8888FF;
            }
        }
    }
}
