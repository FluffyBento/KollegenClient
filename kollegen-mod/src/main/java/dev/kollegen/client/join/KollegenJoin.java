package dev.kollegen.client.join;

import com.google.gson.Gson;
import dev.kollegen.client.KollegenMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lauscht auf `join_request.json`-Dateien (vom Launcher / von Freunden
 * geschrieben, z. B. wenn jemand in unserer Presence auf „Beitreten“ klickt)
 * und verbindet sofort mit dem Server.
 *
 * Die Verbindung läuft über Reflection ({@code ConnectScreen.startConnecting}),
 * damit der Mod auch bei Mapping-Unterschieden zwischen MC-Versionen
 * funktioniert. Schlägt etwas fehl, wird es nur geloggt – der Spielbetrieb
 * bleibt unberührt.
 */
public final class KollegenJoin {
    private static final Gson GSON = new Gson();
    private static long lastCheck = 0;
    private static long cooldownUntil = 0;

    private KollegenJoin() {
    }

    public static void tick(Minecraft mc) {
        // Nur im Spiel / ganz im Menü; nie mitten in einer laufenden Verbindung.
        if (mc.getConnection() != null) return;
        long now = System.currentTimeMillis();
        if (now < cooldownUntil) return;
        if (now - lastCheck < 1500) return;
        lastCheck = now;

        String secret = readJoinSecret();
        if (secret == null || secret.isEmpty()) return;

        KollegenMod.LOGGER.info("Join-Request gefunden: {}", secret);
        cooldownUntil = System.currentTimeMillis() + 3000;
        connect(mc, secret);
        deleteJoinFiles();
    }

    private static List<Path> candidatePaths() {
        List<Path> out = new ArrayList<>();
        out.add(Path.of(".kollegen", "join_request.json"));
        String home = System.getProperty("user.home", ".");
        out.add(Path.of(home, ".kollegen", "join_request.json"));
        // Tauri-Datendir (Linux): ~/.local/share/dev.kollegen.KollegenClient
        out.add(Path.of(home, ".local", "share", "dev.kollegen.KollegenClient", ".kollegen", "join_request.json"));
        // Windows: %APPDATA%\dev.kollegen.KollegenClient
        String appdata = System.getenv("APPDATA");
        if (appdata != null && !appdata.isEmpty()) {
            out.add(Path.of(appdata, "dev.kollegen.KollegenClient", ".kollegen", "join_request.json"));
        }
        return out;
    }

    private static String readJoinSecret() {
        for (Path p : candidatePaths()) {
            try {
                if (!Files.exists(p)) continue;
                String content = Files.readString(p);
                if (content == null || content.isBlank()) continue;
                Map<?, ?> m = GSON.fromJson(content, Map.class);
                if (m == null) continue;
                Object s = m.get("secret");
                if (s != null && !String.valueOf(s).isBlank()) {
                    return String.valueOf(s).trim();
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static void deleteJoinFiles() {
        for (Path p : candidatePaths()) {
            try {
                Files.deleteIfExists(p);
            } catch (Exception ignored) {
            }
        }
    }

    private static void connect(Minecraft mc, String hostPort) {
        try {
            String host = hostPort;
            int port = 25565;
            int idx = hostPort.lastIndexOf(':');
            if (idx > 0) {
                host = hostPort.substring(0, idx);
                String p = hostPort.substring(idx + 1).replaceAll("[^0-9]", "");
                if (!p.isEmpty()) port = Integer.parseInt(p);
            }

            // ServerAddress per Reflection, damit keine Mapping-Festlegung nötig ist.
            Class<?> addrCls = null;
            for (String cn : new String[]{
                    "net.minecraft.client.multiplayer.resolver.ServerAddress"}) {
                addrCls = Class.forName(cn);
                if (addrCls != null) break;
            }
            Object addr = addrCls.getConstructor(String.class, int.class).newInstance(host, port);

            Class<?> screenCls = Class.forName("net.minecraft.client.gui.screens.ConnectScreen");
            Method start = null;
            for (Class<?>[] sig : new Class<?>[][]{
                    {Screen.class, Minecraft.class, addrCls}}) {
                try {
                    start = screenCls.getMethod("startConnecting", sig);
                    break;
                } catch (NoSuchMethodException ignored) {
                }
            }
            if (start != null) {
                Object screen = start.invoke(null, mc.screen, mc, addr);
                if (screen instanceof Screen s) {
                    mc.setScreen(s);
                }
            } else {
                KollegenMod.LOGGER.warn(
                        "Konnte ConnectScreen-API nicht finden – bitte manuell zu '{}' verbinden.",
                        hostPort);
            }
        } catch (Throwable t) {
            KollegenMod.LOGGER.warn("Auto-Join fehlgeschlagen ({}): {}", hostPort, t.getMessage());
        }
    }
}