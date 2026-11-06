package dev.kollegen.client.presence;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public final class PrivacySettings {

    private static boolean hideServer = false;
    private static boolean disallowJoin = false;
    private static boolean loaded = false;

    private PrivacySettings() {
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("kollegen-privacy.json");
    }

    public static boolean isHideServer() {
        ensureLoaded();
        return hideServer;
    }

    public static void setHideServer(boolean hide) {
        ensureLoaded();
        hideServer = hide;
        save();
    }

    public static void toggleHideServer() {
        setHideServer(!isHideServer());
    }

    public static boolean isDisallowJoin() {
        ensureLoaded();
        return disallowJoin;
    }

    public static void setDisallowJoin(boolean disallow) {
        ensureLoaded();
        disallowJoin = disallow;
        save();
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try {
            Path p = configPath();
            if (!Files.exists(p)) return;
            String text = Files.readString(p);
            JsonObject o = JsonParser.parseString(text).getAsJsonObject();
            if (o.has("hide_server")) hideServer = o.get("hide_server").getAsBoolean();
            if (o.has("disallow_join")) disallowJoin = o.get("disallow_join").getAsBoolean();
        } catch (Throwable ignored) {
        }
    }

    private static void save() {
        try {
            Path p = configPath();
            JsonObject o = new JsonObject();
            o.addProperty("hide_server", hideServer);
            o.addProperty("disallow_join", disallowJoin);
            if (p.getParent() != null) Files.createDirectories(p.getParent());
            Files.writeString(p, o.toString());
        } catch (Throwable ignored) {
        }
    }
}
