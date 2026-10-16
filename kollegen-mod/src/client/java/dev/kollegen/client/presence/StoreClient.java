package dev.kollegen.client.presence;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

public final class StoreClient {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private StoreClient() {
    }

    public static String token() {
        try {
            Path p = FabricLoader.getInstance().getConfigDir().resolve("kollegen-session.txt");
            if (!Files.exists(p)) return null;
            String s = Files.readString(p).trim();
            return s.isEmpty() ? null : s;
        } catch (Throwable ignored) {
            return null;
        }
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

    public static JsonObject catalog() {
        String base = base();
        boolean hasTok = false;
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + "/api/store"))
                    .timeout(Duration.ofSeconds(8)).GET();
            String tok = token();
            hasTok = tok != null;
            if (tok != null) b.header("Authorization", "Bearer " + tok);
            HttpResponse<String> res = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200 || res.body() == null) {
                System.out.println("[Kollegen-Store] Katalog-Fehler: HTTP " + res.statusCode() + " (" + base + ", token=" + (hasTok ? "ja" : "nein") + ")");
                return null;
            }
            JsonObject o = JsonParser.parseString(res.body()).getAsJsonObject();
            int n = o.has("catalog") && o.get("catalog").isJsonArray() ? o.getAsJsonArray("catalog").size() : -1;
            System.out.println("[Kollegen-Store] Katalog ok: items=" + n + " points=" + (o.has("points") ? String.valueOf(o.get("points")) : "-") + " (" + base + ", token=" + (hasTok ? "ja" : "nein") + ")");
            return o;
        } catch (Throwable e) {
            System.out.println("[Kollegen-Store] Katalog-Exception: " + e + " (" + base + ", token=" + (hasTok ? "ja" : "nein") + ")");
            return null;
        }
    }

    public static JsonObject buy(String itemId) {
        try {
            if (itemId == null || itemId.isEmpty()) return null;
            JsonObject body = new JsonObject();
            body.addProperty("item_id", itemId);
            return post("/api/store/buy", body);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static JsonObject equipItem(String itemId) {
        try {
            if (itemId == null || itemId.isEmpty()) return null;
            JsonObject body = new JsonObject();
            body.addProperty("item_id", itemId);
            return post("/api/store/equip", body);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static JsonObject unequipCategory(String category) {
        try {
            if (category == null || category.isEmpty()) return null;
            JsonObject body = new JsonObject();
            body.addProperty("category", category);
            return post("/api/store/equip", body);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static JsonObject petConfig(String pet, String mode) {
        return petConfig(pet, mode, null, null, null);
    }

    public static JsonObject petConfig(String pet, String mode, String name, String nameColor, String variant) {
        try {
            if (pet == null || pet.isEmpty()) return null;
            JsonObject body = new JsonObject();
            body.addProperty("pet", pet);
            if (mode != null) body.addProperty("mode", mode);
            if (name != null) body.addProperty("name", name);
            if (nameColor != null) body.addProperty("nameColor", nameColor);
            if (variant != null) body.addProperty("variant", variant);
            return post("/pet-config", body);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static JsonObject submitMinigame(String game, int score) {
        try {
            if (game == null || game.isEmpty()) return null;
            JsonObject body = new JsonObject();
            body.addProperty("game", game);
            body.addProperty("score", score);
            return post("/api/minigame", body);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static JsonObject post(String path, JsonObject body) {
        try {
            String tok = token();
            if (tok == null) return null;
            HttpRequest req = HttpRequest.newBuilder(URI.create(base() + path))
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + tok)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
            HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200 || res.body() == null) return null;
            return JsonParser.parseString(res.body()).getAsJsonObject();
        } catch (Throwable ignored) {
            return null;
        }
    }
}
