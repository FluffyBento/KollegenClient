package dev.kollegen.client.mods;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;


public final class BundleManager {

    private static boolean loaded = false;
    public static boolean spotify = true;
    public static boolean chatheads = true;

    private BundleManager() {
    }

    public static Path file() {
        return FabricLoader.getInstance().getGameDir()
                .resolve("mods").resolve(".kollegen-bundles.json");
    }

    public static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try {
            Path p = file();
            if (Files.exists(p)) {
                JsonObject o = new Gson().fromJson(Files.readString(p), JsonObject.class);
                if (o != null) {
                    if (o.has("spotify")) spotify = o.get("spotify").getAsBoolean();
                    if (o.has("chatheads")) chatheads = o.get("chatheads").getAsBoolean();
                }
            }
        } catch (Exception ignored) {
        }
    }

    public static synchronized void save() {
        try {
            JsonObject o = new JsonObject();
            o.addProperty("spotify", spotify);
            o.addProperty("chatheads", chatheads);
            Files.writeString(file(), new GsonBuilder().setPrettyPrinting().create().toJson(o));
        } catch (Exception ignored) {
        }
    }
}
