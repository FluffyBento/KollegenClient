package dev.kollegen.client.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

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
import java.util.concurrent.ConcurrentHashMap;

public final class ServerBannerManager {

    private static final Map<String, String> BANNER_URLS = new ConcurrentHashMap<>();
    private static final Map<String, Identifier> TEXTURE_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();
    private static volatile boolean initialized = false;

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private ServerBannerManager() {
    }

    public static void init() {
        if (initialized) return;
        synchronized (ServerBannerManager.class) {
            if (initialized) return;
            initialized = true;
        }

        Thread t = new Thread(() -> {
            try (InputStream in = ServerBannerManager.class.getResourceAsStream("/assets/kollegen/server_banners.json")) {
                if (in != null) {
                    JsonObject obj = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                    for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                        if (entry.getValue().isJsonPrimitive()) {
                            BANNER_URLS.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue().getAsString());
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }, "Kollegen-ServerBanner-Init");
        t.setDaemon(true);
        t.start();
    }

    public static String cleanHost(String rawAddress) {
        if (rawAddress == null || rawAddress.isBlank()) return "";
        String host = rawAddress.trim().toLowerCase(Locale.ROOT);
        int slash = host.indexOf('/');
        if (slash >= 0) {
            host = host.substring(slash + 1);
        }
        int colon = host.indexOf(':');
        if (colon >= 0) {
            host = host.substring(0, colon);
        }
        while (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        return host.trim();
    }

    public static String resolveBannerUrl(String host) {
        if (host == null || host.isEmpty()) return null;
        String direct = BANNER_URLS.get(host);
        if (direct != null) return direct;

        String stripped = host;
        while (stripped.contains(".")) {
            int dot = stripped.indexOf('.');
            stripped = stripped.substring(dot + 1);
            if (stripped.contains(".")) {
                String match = BANNER_URLS.get(stripped);
                if (match != null) return match;
            } else {
                break;
            }
        }

        for (Map.Entry<String, String> e : BANNER_URLS.entrySet()) {
            String key = e.getKey();
            if (key.length() >= 4 && (host.equals(key) || host.contains(key))) {
                return e.getValue();
            }
        }
        return null;
    }

    public static Identifier getBanner(String rawAddress) {
        if (!initialized) {
            init();
        }
        String host = cleanHost(rawAddress);
        if (host.isEmpty()) return null;

        String url = resolveBannerUrl(host);
        if (url == null) return null;

        String safeKey = "srv_banner_" + host.replaceAll("[^a-z0-9_.-]", "_");
        Identifier cached = TEXTURE_CACHE.get(safeKey);
        if (cached != null) return cached;

        if (PENDING.add(safeKey)) {
            Thread loader = new Thread(() -> {
                try {
                    Path dir = FabricLoader.getInstance().getConfigDir().resolve("kollegen-server-banners");
                    Files.createDirectories(dir);
                    Path file = dir.resolve(safeKey + ".png");

                    if (!Files.exists(file) || Files.size(file) == 0) {
                        HttpRequest req = HttpRequest.newBuilder()
                                .uri(URI.create(url))
                                .timeout(Duration.ofSeconds(6))
                                .header("User-Agent", "KollegenClient")
                                .GET()
                                .build();
                        HttpResponse<byte[]> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofByteArray());
                        if (resp.statusCode() == 200 && resp.body() != null && resp.body().length > 0) {
                            Files.write(file, resp.body());
                        }
                    }

                    if (Files.exists(file) && Files.size(file) > 0) {
                        try (InputStream in = Files.newInputStream(file)) {
                            NativeImage img = NativeImage.read(in);
                            if (img != null) {
                                Minecraft.getInstance().execute(() -> {
                                    try {
                                        Identifier id = Identifier.tryParse("kollegen:" + safeKey.toLowerCase(Locale.ROOT));
                                        DynamicTexture tex = new DynamicTexture(() -> safeKey, img);
                                        Minecraft.getInstance().getTextureManager().register(id, tex);
                                        TEXTURE_CACHE.put(safeKey, id);
                                    } catch (Throwable ignored) {
                                    } finally {
                                        PENDING.remove(safeKey);
                                    }
                                });
                                return;
                            }
                        }
                    }
                    PENDING.remove(safeKey);
                } catch (Throwable t) {
                    PENDING.remove(safeKey);
                }
            }, "Kollegen-ServerBanner-Download-" + safeKey);
            loader.setDaemon(true);
            loader.start();
        }

        return null;
    }
}
