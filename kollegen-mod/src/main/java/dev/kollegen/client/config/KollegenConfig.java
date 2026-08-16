package dev.kollegen.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class KollegenConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("kollegen.json");

    /** Whether the in-game Minecraft title logo is replaced by Logo.png. */
    public boolean replaceLogo = true;

    public static KollegenConfig load() {
        KollegenConfig cfg = new KollegenConfig();
        try {
            if (Files.exists(PATH)) {
                KollegenConfig loaded = GSON.fromJson(Files.readString(PATH), KollegenConfig.class);
                if (loaded != null) cfg = loaded;
            }
        } catch (Exception ignored) {
            // fall back to defaults
        }
        return cfg;
    }

    public void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(this));
        } catch (IOException ignored) {
        }
    }
}
