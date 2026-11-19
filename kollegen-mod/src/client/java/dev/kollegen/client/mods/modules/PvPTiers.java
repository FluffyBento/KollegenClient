package dev.kollegen.client.mods.modules;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kollegen.client.mods.BooleanSetting;
import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.ModeSetting;
import dev.kollegen.client.mods.Module;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PvPTiers extends Module {
    public static volatile PvPTiers INSTANCE;

    public final ModeSetting showMode = new ModeSetting(
            "Modus",
            "Bevorzugter PvP-Spielmodus für das angezeigte Tier.",
            new String[]{"Overall", "Sword", "NethPot", "UHC", "Shield", "Crystal"},
            0
    );

    public final BooleanSetting showOnNametag = new BooleanSetting(
            "Auf Nametag",
            "Zeigt das Tier-Badge über dem Spielerkopf.",
            true
    );

    public final BooleanSetting showInTab = new BooleanSetting(
            "Im Tab-Menü",
            "Zeigt das Tier-Badge in der Tab-Spielerliste.",
            true
    );

    public final BooleanSetting subTier = new BooleanSetting(
            "Sub-Tier (HT/LT)",
            "Unterscheidet zwischen High-Tier (HT) und Low-Tier (LT).",
            true
    );

    private static final Map<UUID, Map<String, String>> TIER_CACHE = new ConcurrentHashMap<>();
    private static final Set<UUID> PENDING = ConcurrentHashMap.newKeySet();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public PvPTiers() {
        super("pvptiers", "PvP Tiers", "Zeigt offizielle PvP-Ränge und Tiers (HT1-5, LT1-5) von Spielern an.", Category.GAMEPLAY);
        INSTANCE = this;
        add(showMode);
        add(showOnNametag);
        add(showInTab);
        add(subTier);
        loadDiskCache();
    }

    public static boolean isTiersActive() {
        return INSTANCE != null && INSTANCE.enabled;
    }

    public static String getPlayerTierBadge(UUID uuid) {
        if (!isTiersActive() || uuid == null) return null;
        Map<String, String> tiers = TIER_CACHE.get(uuid);
        if (tiers == null) {
            queryAsync(uuid);
            return null;
        }

        String modeKey = switch (INSTANCE.showMode.index) {
            case 1 -> "sword";
            case 2 -> "nethpot";
            case 3 -> "uhc";
            case 4 -> "shield";
            case 5 -> "crystal";
            default -> "overall";
        };

        String raw = tiers.get(modeKey);
        if (raw == null && !modeKey.equals("overall")) {
            raw = tiers.get("overall");
        }
        if (raw == null || raw.isEmpty() || raw.equals("NONE")) {
            return null;
        }

        String displayTier = raw;
        if (!INSTANCE.subTier.value) {
            if (raw.startsWith("HT") || raw.startsWith("LT")) {
                displayTier = "T" + raw.substring(2);
            }
        }

        String color = "§7";
        if (raw.contains("1")) color = "§b";
        else if (raw.contains("2")) color = "§a";
        else if (raw.contains("3")) color = "§e";
        else if (raw.contains("4")) color = "§6";
        else if (raw.contains("5")) color = "§7";

        return "§8[" + color + displayTier + "§8]";
    }

    public static Component decorateNametag(Component original, UUID uuid) {
        if (!isTiersActive() || INSTANCE == null || !INSTANCE.showOnNametag.value || uuid == null) {
            return original;
        }
        String badge = getPlayerTierBadge(uuid);
        if (badge == null) return original;
        MutableComponent comp = Component.literal(badge + " ");
        if (original != null) {
            comp.append(original);
        }
        return comp;
    }

    public static Component decorateTab(Component original, UUID uuid) {
        if (!isTiersActive() || INSTANCE == null || !INSTANCE.showInTab.value || uuid == null) {
            return original;
        }
        String badge = getPlayerTierBadge(uuid);
        if (badge == null) return original;
        MutableComponent comp = Component.empty();
        if (original != null) {
            comp.append(original);
        }
        comp.append(Component.literal(" " + badge));
        return comp;
    }

    private static void queryAsync(UUID uuid) {
        if (uuid == null || PENDING.contains(uuid)) return;
        PENDING.add(uuid);
        Thread t = new Thread(() -> {
            try {
                String cleanUuid = uuid.toString().replace("-", "");
                String url = "https://mctiers.com/api/v2/profile/" + cleanUuid;
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(4))
                        .header("User-Agent", "KollegenClient")
                        .GET()
                        .build();
                HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (resp.statusCode() == 200 && resp.body() != null && !resp.body().isBlank()) {
                    JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
                    parseAndStore(uuid, root);
                    saveDiskCache();
                } else {
                    Map<String, String> empty = new ConcurrentHashMap<>();
                    empty.put("overall", "NONE");
                    TIER_CACHE.put(uuid, empty);
                }
            } catch (Throwable t1) {
                Map<String, String> empty = new ConcurrentHashMap<>();
                empty.put("overall", "NONE");
                TIER_CACHE.put(uuid, empty);
            } finally {
                PENDING.remove(uuid);
            }
        }, "Kollegen-PvPTiers-Fetch");
        t.setDaemon(true);
        t.start();
    }

    private static void parseAndStore(UUID uuid, JsonObject obj) {
        Map<String, String> map = new ConcurrentHashMap<>();
        try {
            if (obj.has("rankings")) {
                JsonObject rankings = obj.getAsJsonObject("rankings");
                for (Map.Entry<String, JsonElement> e : rankings.entrySet()) {
                    String mode = e.getKey().toLowerCase(Locale.ROOT);
                    if (e.getValue().isJsonObject()) {
                        JsonObject data = e.getValue().getAsJsonObject();
                        int tier = data.has("tier") ? data.get("tier").getAsInt() : 0;
                        int pos = data.has("pos") ? data.get("pos").getAsInt() : 0;
                        if (tier > 0) {
                            String prefix = pos == 1 ? "HT" : (pos == 2 ? "LT" : "T");
                            map.put(mode, prefix + tier);
                        }
                    }
                }
            }
            if (obj.has("tier")) {
                int t = obj.get("tier").getAsInt();
                int pos = obj.has("pos") ? obj.get("pos").getAsInt() : 0;
                String prefix = pos == 1 ? "HT" : (pos == 2 ? "LT" : "T");
                map.put("overall", prefix + t);
            }
        } catch (Throwable ignored) {
        }
        if (map.isEmpty()) {
            map.put("overall", "NONE");
        }
        TIER_CACHE.put(uuid, map);
    }

    private static Path getCacheFile() {
        try {
            return FabricLoader.getInstance().getConfigDir().resolve("kollegen-pvptiers-cache.json");
        } catch (Throwable t) {
            return null;
        }
    }

    private static void loadDiskCache() {
        try {
            Path file = getCacheFile();
            if (file != null && Files.isRegularFile(file)) {
                try (InputStream in = Files.newInputStream(file);
                     InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                    for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                        try {
                            UUID id = UUID.fromString(e.getKey());
                            JsonObject modes = e.getValue().getAsJsonObject();
                            Map<String, String> map = new ConcurrentHashMap<>();
                            for (Map.Entry<String, JsonElement> me : modes.entrySet()) {
                                map.put(me.getKey(), me.getValue().getAsString());
                            }
                            TIER_CACHE.put(id, map);
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void saveDiskCache() {
        try {
            Path file = getCacheFile();
            if (file == null) return;
            JsonObject root = new JsonObject();
            for (Map.Entry<UUID, Map<String, String>> e : TIER_CACHE.entrySet()) {
                JsonObject modes = new JsonObject();
                for (Map.Entry<String, String> me : e.getValue().entrySet()) {
                    modes.addProperty(me.getKey(), me.getValue());
                }
                root.add(e.getKey().toString(), modes);
            }
            Files.writeString(file, root.toString(), StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
        }
    }
}
