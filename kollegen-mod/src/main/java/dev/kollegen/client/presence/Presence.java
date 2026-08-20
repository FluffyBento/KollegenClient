package dev.kollegen.client.presence;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.kollegen.client.KollegenMod;
import dev.kollegen.client.config.KollegenConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Presence: erkennt andere Kollegen-Client-Nutzer auf dem aktuellen Server über
 * einen externen Backend-Service und liefert das Icon-Glyph für die Anzeige.
 *
 *  - Die Mod schreibt {@code ~/.kollegen/presence.json} (Server + Name), damit
 *    der Launcher diese Information an das Backend melden kann.
 *  - Die Mod fragt das Backend periodisch ab ({@code GET {backend}/presence?server=…})
 *    und merkt sich die Liste der Kollegen-Namen (TTL-gecached).
 *
 * Kein fabric-api nötig – nur net.minecraft.* und java.net.http.
 */
public final class Presence {
    /** Privates Unicode-Zeichen, das über die Kollegen-Schriftart das Icon rendert. */
    public static final String GLYPH = "";
    private static final String FONT = "kollegen:kollegen";
    private static final long REFRESH_INTERVAL_MS = 8_000;
    private static final long HTTP_TIMEOUT_MS = 5_000;
    private static final long FILE_WRITE_THROTTLE_MS = 2_000;

    private static final AtomicReference<Set<String>> kollegenNames = new AtomicReference<>(new HashSet<>());
    private static final AtomicLong lastFetch = new AtomicLong(0);
    private static final AtomicLong lastFileWrite = new AtomicLong(0);
    private static final AtomicBoolean refreshRunning = new AtomicBoolean(false);
    private static volatile boolean active = false;

    private Presence() {
    }

    public static boolean isActive() {
        return active;
    }

    /** Wird jeden Client-Tick aus {@code KollegenMod.onTick} aufgerufen. */
    public static void tick() {
        KollegenConfig cfg = KollegenMod.CONFIG;
        String backend = backendUrl();
        if (!cfg.presenceEnabled || backend.isEmpty()) {
            if (active) {
                active = false;
                writePresenceFile(null, null); // löscht die Datei → Launcher meldet "offline"
            }
            return;
        }

        String server = detectServer();
        String name = detectName();
        if (server == null || name == null) {
            if (active) {
                active = false;
                writePresenceFile(null, null);
            }
            return;
        }

        active = true;
        writePresenceFile(server, name);

        long now = System.currentTimeMillis();
        if (now - lastFetch.get() > REFRESH_INTERVAL_MS && refreshRunning.compareAndSet(false, true)) {
            lastFetch.set(now);
            fetchAsync(backend, server);
        }
    }

    /** Liefert den (ggf. glyph-dekorierten) Anzeigenamen für die Tab-Liste. */
    public static Component decorateName(Component original) {
        if (original == null) return null;
        if (!isKollege(original.getString())) return original;
        Component glyph = Component.literal(GLYPH).setStyle(Style.EMPTY.withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath("kollegen", "kollegen"))));
        return Component.empty().append(glyph).append(original);
    }

    public static boolean isKollege(String name) {
        if (name == null || name.isEmpty()) return false;
        String clean = stripFormat(name);
        for (String k : kollegenNames.get()) {
            if (stripFormat(k).equalsIgnoreCase(clean)) return true;
        }
        return false;
    }

    private static String backendUrl() {
        String env = System.getenv("KOLLEGEN_PRESENCE_BACKEND");
        if (env != null && !env.trim().isEmpty()) return env.trim().replaceAll("/$", "");
        String cfg = KollegenMod.CONFIG.presenceBackend;
        return cfg == null ? "" : cfg.trim().replaceAll("/$", "");
    }

    private static String token() {
        String t = KollegenMod.CONFIG.presenceToken;
        return t == null ? "" : t.trim();
    }

    private static String detectServer() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null || mc.getConnection() == null) return null;
            Object sd = mc.getCurrentServer();
            if (sd == null) return null;
            try {
                return (String) sd.getClass().getField("ip").get(sd);
            } catch (NoSuchFieldException ignored) {
                // Fallback: irgendein String-Feld namens ip per Reflection
                for (java.lang.reflect.Field f : sd.getClass().getDeclaredFields()) {
                    if (f.getType() == String.class && f.getName().toLowerCase().contains("ip")) {
                        f.setAccessible(true);
                        return (String) f.get(sd);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String detectName() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null) return null;
            return mc.player.getName().getString();
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void fetchAsync(String backend, String server) {
        Thread t = new Thread(() -> {
            try {
                HttpClient client = HttpClient.newHttpClient();
                String url = backend + "/presence?server=" + URLEncoder.encode(server, java.nio.charset.StandardCharsets.UTF_8);
                HttpRequest.Builder b = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofMillis(HTTP_TIMEOUT_MS));
                String tok = token();
                if (!tok.isEmpty()) b.header("Authorization", "Bearer " + tok);
                HttpResponse<String> resp = client.send(b.build(), HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200 && resp.body() != null && !resp.body().isBlank()) {
                    Set<String> names = new HashSet<>();
                    JsonArray arr = JsonParser.parseString(resp.body()).getAsJsonArray();
                    for (JsonElement e : arr) {
                        if (e.isJsonPrimitive()) names.add(e.getAsString());
                    }
                    kollegenNames.set(names);
                }
            } catch (Throwable ex) {
                KollegenMod.LOGGER.debug("Presence-Abfrage fehlgeschlagen: {}", ex.getMessage());
            } finally {
                refreshRunning.set(false);
            }
        }, "kollegen-presence");
        t.setDaemon(true);
        t.start();
    }

    private static void writePresenceFile(String server, String name) {
        long now = System.currentTimeMillis();
        if (server != null && name != null) {
            if (now - lastFileWrite.get() < FILE_WRITE_THROTTLE_MS) return;
        }
        lastFileWrite.set(now);
        Path dir = Path.of(System.getProperty("user.home", "."), ".kollegen");
        Path file = dir.resolve("presence.json");
        try {
            if (server == null || name == null) {
                Files.deleteIfExists(file);
                return;
            }
            Files.createDirectories(dir);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("server", server);
            m.put("name", name);
            m.put("timestamp", Instant.now().toEpochMilli());
            Files.writeString(file, new com.google.gson.Gson().toJson(m));
        } catch (Throwable ignored) {
        }
    }

    private static String stripFormat(String s) {
        if (s == null) return "";
        return s.replaceAll("§[0-9a-fk-or]", "").replaceAll("&[0-9a-fk-or]", "").trim();
    }
}
