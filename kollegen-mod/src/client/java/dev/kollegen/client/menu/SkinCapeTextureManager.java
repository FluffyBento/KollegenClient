package dev.kollegen.client.menu;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class SkinCapeTextureManager {
    private static final Map<String, Identifier> CACHE = new ConcurrentHashMap<>();
    public static final Map<String, int[]> DIMS = new ConcurrentHashMap<>();
    private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();

    public static final class AnimatedCape {
        public final java.util.List<Identifier> frames;
        public final int fps;

        public AnimatedCape(java.util.List<Identifier> frames, int fps) {
            this.frames = frames;
            this.fps = Math.max(1, fps);
        }

        public Identifier getCurrentFrame() {
            if (frames.isEmpty()) return null;
            if (frames.size() == 1) return frames.get(0);
            long now = System.currentTimeMillis();
            long frameMs = 1000L / fps;
            int idx = (int) ((now / frameMs) % frames.size());
            return frames.get(idx);
        }
    }

    private static final Map<String, AnimatedCape> ANIMATED_CAPES = new ConcurrentHashMap<>();

    public static void registerAnimatedCape(String capeId, java.util.List<String> frameDataUrls, int fps) {
        if (capeId == null || capeId.isEmpty() || frameDataUrls == null || frameDataUrls.isEmpty()) return;
        String safeId = capeId.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        if (ANIMATED_CAPES.containsKey(safeId)) {
            return;
        }
        java.util.List<Identifier> ids = new java.util.ArrayList<>();
        Minecraft mc = Minecraft.getInstance();

        for (int i = 0; i < frameDataUrls.size(); i++) {
            String raw = frameDataUrls.get(i);
            String frameKey = "cape_" + safeId + "_f" + i;
            Identifier existing = CACHE.get(frameKey);
            if (existing != null) {
                ids.add(existing);
                continue;
            }

            try {
                byte[] bytes = null;
                if (raw != null && raw.startsWith("data:image/")) {
                    int comma = raw.indexOf(',');
                    if (comma > 0) {
                        bytes = Base64.getDecoder().decode(raw.substring(comma + 1));
                    }
                } else if (raw != null && !raw.isEmpty()) {
                    Path p = Path.of(raw);
                    if (Files.exists(p)) bytes = Files.readAllBytes(p);
                }

                if (bytes != null && bytes.length > 0) {
                    NativeImage img = NativeImage.read(bytes);
                    int[] dims = new int[]{img.getWidth(), img.getHeight()};
                    DIMS.put(frameKey, dims);
                    DIMS.put("cape_" + safeId, dims);
                    int frameIndex = i;
                    Identifier id = Identifier.tryParse("kollegen:cape_" + safeId + "_f" + frameIndex);
                    DynamicTexture tex = new DynamicTexture(() -> "cape_" + safeId + "_f" + frameIndex, img);
                    if (mc != null) {
                        mc.execute(() -> {
                            if (mc.getTextureManager() != null) {
                                mc.getTextureManager().register(id, tex);
                            }
                        });
                    }
                    CACHE.put(frameKey, id);
                    ids.add(id);
                }
            } catch (Throwable ignored) {
            }
        }

        if (!ids.isEmpty()) {
            AnimatedCape anim = new AnimatedCape(ids, fps);
            ANIMATED_CAPES.put(safeId, anim);
            ANIMATED_CAPES.put(capeId, anim);
            CACHE.put("cape_" + safeId, ids.get(0));
            CACHE.put("cape_" + capeId, ids.get(0));
        }
    }

    public static boolean isAnimatedCape(String capeId) {
        if (capeId == null) return false;
        String safeId = capeId.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        return ANIMATED_CAPES.containsKey(safeId) || ANIMATED_CAPES.containsKey(capeId);
    }

    public static int[] getDimensions(String capeId) {
        if (capeId == null) return null;
        return DIMS.get("cape_" + capeId);
    }

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
        String safeId = capeId.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        AnimatedCape anim = ANIMATED_CAPES.get(safeId);
        if (anim == null) {
            anim = ANIMATED_CAPES.get(capeId);
        }
        if (anim != null) {
            return anim.getCurrentFrame();
        }

        String key = "cape_" + capeId;
        Identifier hit = CACHE.get(key);
        if (hit != null) {
            return hit;
        }

        try {
            byte[] bytes = null;
            if (rawData != null && rawData.startsWith("data:image/")) {
                int comma = rawData.indexOf(',');
                if (comma > 0) {
                    bytes = Base64.getDecoder().decode(rawData.substring(comma + 1));
                }
            } else if (rawData != null && !rawData.isEmpty() && !rawData.startsWith("http") && !rawData.startsWith("kollegen:")) {
                Path p = Path.of(rawData);
                if (Files.exists(p)) {
                    bytes = Files.readAllBytes(p);
                }
            }
            if (bytes != null && bytes.length > 0) {
                NativeImage img = NativeImage.read(bytes);
                DIMS.put(key, new int[]{img.getWidth(), img.getHeight()});
                Identifier id = Identifier.tryParse("kollegen:cape_" + safeId);
                DynamicTexture tex = new DynamicTexture(() -> "cape_" + capeId, img);
                Minecraft.getInstance().getTextureManager().register(id, tex);
                CACHE.put(key, id);
                return id;
            }
        } catch (Throwable ignored) {
        }

        // 2. HTTP/HTTPS Mojang Vanilla or Community Cape URL
        if (rawData != null && (rawData.startsWith("http://") || rawData.startsWith("https://"))) {
            String home = System.getProperty("user.home", "");
            Path cacheDir = Path.of(home, ".local", "share", "kollegenclient", "capes", "cache");
            Path cachedFile = cacheDir.resolve(safeId + ".png");
            if (Files.exists(cachedFile)) {
                try {
                    byte[] bytes = Files.readAllBytes(cachedFile);
                    NativeImage img = NativeImage.read(bytes);
                    DIMS.put(key, new int[]{img.getWidth(), img.getHeight()});
                    Identifier id = Identifier.tryParse("kollegen:cape_" + safeId);
                    DynamicTexture tex = new DynamicTexture(() -> "cape_" + capeId, img);
                    Minecraft.getInstance().getTextureManager().register(id, tex);
                    CACHE.put(key, id);
                    return id;
                } catch (Throwable ignored) {
                }
            } else if (PENDING.add(key)) {
                Thread t = new Thread(() -> {
                    try {
                        Files.createDirectories(cacheDir);
                        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
                        HttpRequest req = HttpRequest.newBuilder(URI.create(rawData)).timeout(Duration.ofSeconds(10)).GET().build();
                        HttpResponse<byte[]> res = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
                        if (res.statusCode() >= 200 && res.statusCode() < 300 && res.body() != null && res.body().length > 0) {
                            Files.write(cachedFile, res.body());
                            Minecraft.getInstance().execute(() -> {
                                try {
                                    NativeImage img = NativeImage.read(res.body());
                                    DIMS.put(key, new int[]{img.getWidth(), img.getHeight()});
                                    Identifier id = Identifier.tryParse("kollegen:cape_" + safeId);
                                    DynamicTexture tex = new DynamicTexture(() -> "cape_" + capeId, img);
                                    Minecraft.getInstance().getTextureManager().register(id, tex);
                                    CACHE.put(key, id);
                                } catch (Throwable ignored) {}
                            });
                        }
                    } catch (Throwable ignored) {
                    } finally {
                        PENDING.remove(key);
                    }
                }, "kollegen-cape-dl-" + safeId);
                t.setDaemon(true);
                t.start();
            }
            return null;
        }

        // 3. Mod Asset Identifiers & Store Cape Shortcuts
        String assetPath = null;
        if (rawData != null && rawData.toLowerCase(java.util.Locale.ROOT).startsWith("kollegen:")) {
            assetPath = rawData.toLowerCase(java.util.Locale.ROOT);
        } else if (capeId.toLowerCase(java.util.Locale.ROOT).startsWith("kollegen:")) {
            assetPath = capeId.toLowerCase(java.util.Locale.ROOT);
        } else {
            String lowerId = capeId.toLowerCase(java.util.Locale.ROOT);
            if (lowerId.contains("phantom")) {
                assetPath = "kollegen:cosmetics/store/phantomumhang.png";
            } else if (lowerId.contains("sternen")) {
                assetPath = "kollegen:cosmetics/store/sternenhimmel_umhang.png";
            } else if (lowerId.contains("fluffybento")) {
                assetPath = "kollegen:cosmetics/fluffybento/fluffybento_cape.png";
            } else if (lowerId.contains("limitlos") || lowerId.contains("limit_los")) {
                assetPath = "kollegen:cosmetics/limit_los/limit_los_cape.png";
            } else if (lowerId.contains("zerocraft77")) {
                assetPath = "kollegen:cosmetics/zerocraft77/zerocraft77_cape.png";
            } else if (lowerId.contains("irongirl")) {
                assetPath = "kollegen:cosmetics/irongirl_lp/irongirl_lp_cape.png";
            } else if (lowerId.contains("tson") || lowerId.contains("t_son")) {
                assetPath = "kollegen:cosmetics/t_son_/t_son__cape.png";
            } else if (lowerId.contains("rixhii")) {
                assetPath = "kollegen:cosmetics/rixhii/rixhii_cape.png";
            }
        }

        if (assetPath != null) {
            Identifier resId = Identifier.tryParse(assetPath);
            if (resId != null) {
                // In Minecraft TextureManager als DynamicTexture registrieren, damit GUI-Renderpipelines es sauber anzeigen
                try {
                    InputStream in = null;
                    try {
                        in = SkinCapeTextureManager.class.getResourceAsStream("/assets/" + resId.getNamespace() + "/" + resId.getPath());
                    } catch (Throwable ignored) {}
                    Minecraft mc = Minecraft.getInstance();
                    if (in == null && mc != null && mc.getResourceManager() != null) {
                        try {
                            in = mc.getResourceManager().open(resId);
                        } catch (Throwable ignored) {}
                    }
                    if (in != null) {
                        try (InputStream is = in) {
                            NativeImage img = NativeImage.read(is);
                            DIMS.put(key, new int[]{img.getWidth(), img.getHeight()});
                            Identifier dynId = Identifier.tryParse("kollegen:cape_asset_" + safeId);
                            DynamicTexture tex = new DynamicTexture(() -> "cape_asset_" + capeId, img);
                            if (mc != null && mc.getTextureManager() != null) {
                                mc.getTextureManager().register(dynId, tex);
                            }
                            CACHE.put(key, dynId);
                            return dynId;
                        }
                    }
                } catch (Throwable ignored) {
                }
                CACHE.put(key, resId);
                return resId;
            }
        }

        return null;
    }
}
