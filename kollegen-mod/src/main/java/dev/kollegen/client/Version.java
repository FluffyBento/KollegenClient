package dev.kollegen.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.io.InputStreamReader;

public final class Version {
    private static String cachedVersion = null;

    private Version() {
    }

    public static String get() {
        if (cachedVersion != null) {
            return cachedVersion;
        }
        try {
            var loader = FabricLoader.getInstance();
            var modContainer = loader.getModContainer("kollegen-client");
            if (modContainer.isPresent()) {
                var metadata = modContainer.get().getMetadata();
                cachedVersion = metadata.getVersion().getFriendlyString();
                return cachedVersion;
            }
        } catch (Throwable ignored) {
        }
        try (InputStream is = Version.class.getResourceAsStream("/fabric.mod.json")) {
            if (is != null) {
                JsonObject obj = JsonParser.parseReader(new InputStreamReader(is)).getAsJsonObject();
                if (obj.has("version")) {
                    cachedVersion = obj.get("version").getAsString();
                    return cachedVersion;
                }
            }
        } catch (Throwable ignored) {
        }
        cachedVersion = "unknown";
        return cachedVersion;
    }
}