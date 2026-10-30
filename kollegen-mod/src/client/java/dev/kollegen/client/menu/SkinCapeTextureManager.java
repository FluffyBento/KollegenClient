package dev.kollegen.client.menu;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SkinCapeTextureManager {
    private static final Map<String, Identifier> CACHE = new ConcurrentHashMap<>();

    private SkinCapeTextureManager() {
    }

    public static Identifier getOrLoadSkin(String skinName, String fileName) {
        if (skinName == null || skinName.isEmpty()) {
            return null;
        }
        String key = "skin_" + skinName;
        Identifier hit = CACHE.get(key);
        if (hit != null) {
            return hit;
        }

        try {
            String home = System.getProperty("user.home", "");
            Path skinsDir = Path.of(home, ".local", "share", "kollegenclient", "skins");
            Path p = null;
            if (fileName != null && !fileName.isEmpty()) {
                p = skinsDir.resolve(fileName);
                if (!Files.exists(p)) {
                    p = Path.of(home, ".local", "share", "kollegen-launcher", "skins", fileName);
                }
            }
            if ((p == null || !Files.exists(p)) && Files.exists(skinsDir)) {
                // Try to find file containing skinName in skins directory
                try (DirectoryStream<Path> ds = Files.newDirectoryStream(skinsDir, "*.png")) {
                    for (Path entry : ds) {
                        String fn = entry.getFileName().toString().toLowerCase();
                        if (fn.contains(skinName.toLowerCase())) {
                            p = entry;
                            break;
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
            if (p != null && Files.exists(p)) {
                try (InputStream in = Files.newInputStream(p)) {
                    NativeImage img = NativeImage.read(in);
                    String safeName = skinName.toLowerCase().replaceAll("[^a-z0-9_.-]", "_");
                    Identifier id = Identifier.tryParse("kollegen:skin_" + safeName);
                    DynamicTexture tex = new DynamicTexture(() -> "skin_" + skinName, img);
                    Minecraft.getInstance().getTextureManager().register(id, tex);
                    CACHE.put(key, id);
                    return id;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static Identifier getOrLoadCape(String capeId, String rawData) {
        if (capeId == null || capeId.isEmpty() || "none".equalsIgnoreCase(capeId)) {
            return null;
        }
        String key = "cape_" + capeId;
        Identifier hit = CACHE.get(key);
        if (hit != null) {
            return hit;
        }

        // Direct mod asset identifier?
        if (rawData != null && rawData.toLowerCase().startsWith("kollegen:")) {
            Identifier id = Identifier.tryParse(rawData.toLowerCase());
            if (id != null) {
                CACHE.put(key, id);
                return id;
            }
        }

        // Creator package cape shortcuts
        String lowerId = capeId.toLowerCase();
        if (lowerId.contains("fluffybento")) {
            Identifier id = Identifier.tryParse("kollegen:cosmetics/fluffybento/fluffybento_cape.png");
            if (id != null) { CACHE.put(key, id); return id; }
        } else if (lowerId.contains("limitlos") || lowerId.contains("limit_los")) {
            Identifier id = Identifier.tryParse("kollegen:cosmetics/limit_los/limit_los_cape.png");
            if (id != null) { CACHE.put(key, id); return id; }
        } else if (lowerId.contains("zerocraft77")) {
            Identifier id = Identifier.tryParse("kollegen:cosmetics/zerocraft77/zerocraft77_cape.png");
            if (id != null) { CACHE.put(key, id); return id; }
        } else if (lowerId.contains("irongirl")) {
            Identifier id = Identifier.tryParse("kollegen:cosmetics/irongirl_lp/irongirl_lp_cape.png");
            if (id != null) { CACHE.put(key, id); return id; }
        } else if (lowerId.contains("tson") || lowerId.contains("t_son")) {
            Identifier id = Identifier.tryParse("kollegen:cosmetics/t_son_/t_son__cape.png");
            if (id != null) { CACHE.put(key, id); return id; }
        } else if (lowerId.contains("rixhii")) {
            Identifier id = Identifier.tryParse("kollegen:cosmetics/rixhii/rixhii_cape.png");
            if (id != null) { CACHE.put(key, id); return id; }
        }

        try {
            byte[] bytes = null;
            if (rawData != null && rawData.startsWith("data:image/")) {
                int comma = rawData.indexOf(',');
                if (comma > 0) {
                    bytes = Base64.getDecoder().decode(rawData.substring(comma + 1));
                }
            } else if (rawData != null && !rawData.isEmpty() && !rawData.startsWith("http")) {
                Path p = Path.of(rawData);
                if (Files.exists(p)) {
                    bytes = Files.readAllBytes(p);
                }
            }
            if (bytes != null && bytes.length > 0) {
                NativeImage img = NativeImage.read(bytes);
                String safeId = capeId.toLowerCase().replaceAll("[^a-z0-9_.-]", "_");
                Identifier id = Identifier.tryParse("kollegen:cape_" + safeId);
                DynamicTexture tex = new DynamicTexture(() -> "cape_" + capeId, img);
                Minecraft.getInstance().getTextureManager().register(id, tex);
                CACHE.put(key, id);
                return id;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
