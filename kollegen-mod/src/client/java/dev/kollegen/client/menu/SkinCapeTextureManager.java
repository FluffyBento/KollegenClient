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
    private static final Map<Identifier, DynamicTexture> PENDING_REGISTRATIONS = new ConcurrentHashMap<>();
    private static final Map<String, String> STATIC_RAW = new ConcurrentHashMap<>();

    public static void ensureRegistered(Identifier id) {
        if (id == null) return;
        DynamicTexture dt = PENDING_REGISTRATIONS.remove(id);
        if (dt != null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getTextureManager() != null) {
                mc.getTextureManager().register(id, dt);
            }
        }
    }

    public static final class AnimatedCape {
        public final String safeId;
        public final java.util.List<String> rawFrames;
        public final java.util.Map<Integer, Identifier> frameTextures = new ConcurrentHashMap<>();
        public final int fps;

        public AnimatedCape(String safeId, java.util.List<String> rawFrames, int fps) {
            this.safeId = safeId;
            this.rawFrames = rawFrames != null ? rawFrames : java.util.Collections.emptyList();
            this.fps = Math.max(1, fps);
        }

        public Identifier getCurrentFrame() {
            if (rawFrames.isEmpty()) return null;
            int size = rawFrames.size();
            int idx = 0;
            if (size > 1) {
                long now = System.currentTimeMillis();
                long frameMs = 1000L / fps;
                idx = (int) ((now / frameMs) % size);
            }
            Identifier id = frameTextures.get(idx);
            if (id != null) {
                ensureRegistered(id);
                return id;
            }
            String raw = rawFrames.get(idx);
            id = createAndRegisterDynamicTexture("cape_" + safeId + "_f" + idx, "kollegen:cape_" + safeId + "_f" + idx, raw);
            if (id != null) {
                frameTextures.put(idx, id);
                CACHE.put("cape_" + safeId + "_f" + idx, id);
                if (idx == 0) {
                    CACHE.put("cape_" + safeId, id);
                    CACHE.put(safeId, id);
                }
            }
            return id;
        }
    }

    private static final Map<String, AnimatedCape> ANIMATED_CAPES = new ConcurrentHashMap<>();

    public static Identifier createAndRegisterDynamicTexture(String label, String idStr, String rawData) {
        if (rawData == null || rawData.isEmpty()) return null;
        try {
            byte[] bytes = null;
            if (rawData.startsWith("data:image/")) {
                int comma = rawData.indexOf(',');
                if (comma > 0) {
                    bytes = Base64.getDecoder().decode(rawData.substring(comma + 1));
                }
            } else if (!rawData.startsWith("http") && !rawData.startsWith("kollegen:")) {
                Path p = Path.of(rawData);
                if (Files.exists(p)) {
                    bytes = Files.readAllBytes(p);
                }
            }
            if (bytes != null && bytes.length > 0) {
                NativeImage img = NativeImage.read(bytes);
                int[] dims = new int[]{img.getWidth(), img.getHeight()};
                DIMS.put(label, dims);
                DIMS.put(idStr, dims);
                String safeKey = label.startsWith("cape_") ? label.substring(5) : label;
                DIMS.put("cape_" + safeKey, dims);
                DIMS.put(safeKey, dims);
                if (safeKey.contains("_f")) {
                    String baseKey = safeKey.substring(0, safeKey.lastIndexOf("_f"));
                    DIMS.put(baseKey, dims);
                    DIMS.put("cape_" + baseKey, dims);
                }
                Identifier id = Identifier.tryParse(idStr);
                if (id == null) return null;
                DynamicTexture tex = new DynamicTexture(() -> label, img);
                PENDING_REGISTRATIONS.put(id, tex);
                Minecraft mc = Minecraft.getInstance();
                if (mc != null && mc.getTextureManager() != null) {
                    mc.getTextureManager().register(id, tex);
                    PENDING_REGISTRATIONS.remove(id);
                }
                return id;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static void registerAnimatedCape(String capeId, java.util.List<String> frameDataUrls, int fps) {
        if (capeId == null || capeId.isEmpty() || frameDataUrls == null || frameDataUrls.isEmpty()) return;
        String safeId = capeId.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        AnimatedCape anim = new AnimatedCape(safeId, frameDataUrls, fps);
        ANIMATED_CAPES.put(safeId, anim);
        ANIMATED_CAPES.put(capeId, anim);
        if (frameDataUrls.size() > 0) {
            try {
                String first = frameDataUrls.get(0);
                if (first.startsWith("data:image/")) {
                    int comma = first.indexOf(',');
                    if (comma > 0) {
                        byte[] b = Base64.getDecoder().decode(first.substring(comma + 1));
                        NativeImage img = NativeImage.read(b);
                        int[] dims = new int[]{img.getWidth(), img.getHeight()};
                        DIMS.put(safeId, dims);
                        DIMS.put("cape_" + safeId, dims);
                        DIMS.put(capeId, dims);
                        DIMS.put("cape_" + capeId, dims);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }

    public static Identifier registerStaticCape(String capeId, String rawData) {
        if (capeId == null || capeId.isEmpty() || "none".equalsIgnoreCase(capeId) || rawData == null || rawData.isEmpty()) {
            return null;
        }
        String safeId = capeId.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        STATIC_RAW.put(safeId, rawData);
        STATIC_RAW.put(capeId, rawData);
        Identifier existing = CACHE.get("cape_" + safeId);
        if (existing == null) existing = CACHE.get("cape_" + capeId);
        if (existing == null) existing = CACHE.get(safeId);
        if (existing == null) existing = CACHE.get(capeId);
        if (existing != null) {
            ensureRegistered(existing);
            return existing;
        }
        return getOrLoadCape(capeId, rawData);
    }

    public static boolean isAnimatedCape(String capeId) {
        if (capeId == null) return false;
        String safeId = capeId.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        return ANIMATED_CAPES.containsKey(safeId) || ANIMATED_CAPES.containsKey(capeId);
    }

    public static int[] getDimensions(String capeId) {
        if (capeId == null) return null;
        String safeId = capeId.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        int[] d = DIMS.get("cape_" + safeId);
        if (d == null) d = DIMS.get("cape_" + capeId);
        if (d == null) d = DIMS.get(safeId);
        if (d == null) d = DIMS.get(capeId);
        return d;
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

        Identifier hit = CACHE.get("cape_" + safeId);
        if (hit == null) hit = CACHE.get("cape_" + capeId);
        if (hit == null) hit = CACHE.get(safeId);
        if (hit == null) hit = CACHE.get(capeId);
        if (hit != null) {
            ensureRegistered(hit);
            return hit;
        }

        String actualRaw = (rawData != null && !rawData.isEmpty() && !rawData.equalsIgnoreCase(capeId) && !rawData.equalsIgnoreCase(safeId)) ? rawData : STATIC_RAW.get(safeId);
        if (actualRaw == null) actualRaw = STATIC_RAW.get(capeId);

        if (actualRaw != null && (actualRaw.startsWith("data:image/") || (!actualRaw.startsWith("http") && !actualRaw.startsWith("kollegen:")))) {
            Identifier id = createAndRegisterDynamicTexture("cape_" + safeId, "kollegen:cape_" + safeId, actualRaw);
            if (id != null) {
                CACHE.put("cape_" + safeId, id);
                CACHE.put("cape_" + capeId, id);
                CACHE.put(safeId, id);
                CACHE.put(capeId, id);
                return id;
            }
        }

        if (actualRaw != null && (actualRaw.startsWith("http://") || actualRaw.startsWith("https://"))) {
            String key = "cape_" + safeId;
            String home = System.getProperty("user.home", "");
            Path cacheDir = Path.of(home, ".local", "share", "kollegenclient", "capes", "cache");
            Path targetFile = cacheDir.resolve(safeId + ".png");
            if (!Files.exists(targetFile)) {
                String altName = safeId.startsWith("vanilla_") ? safeId.substring(8) + ".png" : safeId + ".png";
                if (Files.exists(cacheDir.resolve(altName))) {
                    targetFile = cacheDir.resolve(altName);
                }
            }
            final Path cachedFile = targetFile;
            if (Files.exists(cachedFile)) {
                try {
                    byte[] bytes = Files.readAllBytes(cachedFile);
                    NativeImage img = NativeImage.read(bytes);
                    int[] dims = new int[]{img.getWidth(), img.getHeight()};
                    DIMS.put(key, dims);
                    DIMS.put(safeId, dims);
                    DIMS.put(capeId, dims);
                    Identifier id = Identifier.tryParse("kollegen:cape_" + safeId);
                    DynamicTexture tex = new DynamicTexture(() -> "cape_" + capeId, img);
                    Minecraft mc = Minecraft.getInstance();
                    if (mc != null && mc.getTextureManager() != null) {
                        mc.getTextureManager().register(id, tex);
                    }
                    CACHE.put(key, id);
                    CACHE.put(safeId, id);
                    CACHE.put(capeId, id);
                    return id;
                } catch (Throwable ignored) {
                }
            } else if (PENDING.add(key)) {
                final String downloadUrl = actualRaw;
                Thread t = new Thread(() -> {
                    try {
                        Files.createDirectories(cacheDir);
                        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
                        HttpRequest req = HttpRequest.newBuilder(URI.create(downloadUrl)).timeout(Duration.ofSeconds(10)).GET().build();
                        HttpResponse<byte[]> res = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
                        if (res.statusCode() >= 200 && res.statusCode() < 300 && res.body() != null && res.body().length > 0) {
                            Files.write(cachedFile, res.body());
                            Minecraft mc = Minecraft.getInstance();
                            if (mc != null) {
                                mc.execute(() -> {
                                    try {
                                        NativeImage img = NativeImage.read(res.body());
                                        DIMS.put(key, new int[]{img.getWidth(), img.getHeight()});
                                        DIMS.put(safeId, new int[]{img.getWidth(), img.getHeight()});
                                        DIMS.put(capeId, new int[]{img.getWidth(), img.getHeight()});
                                        Identifier id = Identifier.tryParse("kollegen:cape_" + safeId);
                                        DynamicTexture tex = new DynamicTexture(() -> "cape_" + capeId, img);
                                        if (mc.getTextureManager() != null) {
                                            mc.getTextureManager().register(id, tex);
                                        }
                                        CACHE.put(key, id);
                                        CACHE.put(safeId, id);
                                        CACHE.put(capeId, id);
                                    } catch (Throwable ignored) {}
                                });
                            }
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
                            DIMS.put("cape_" + safeId, new int[]{img.getWidth(), img.getHeight()});
                            DIMS.put(safeId, new int[]{img.getWidth(), img.getHeight()});
                            DIMS.put(capeId, new int[]{img.getWidth(), img.getHeight()});
                            Identifier dynId = Identifier.tryParse("kollegen:cape_asset_" + safeId);
                            DynamicTexture tex = new DynamicTexture(() -> "cape_asset_" + capeId, img);
                            if (mc != null && mc.getTextureManager() != null) {
                                mc.getTextureManager().register(dynId, tex);
                            }
                            CACHE.put("cape_" + safeId, dynId);
                            CACHE.put(safeId, dynId);
                            CACHE.put(capeId, dynId);
                            return dynId;
                        }
                    }
                } catch (Throwable ignored) {
                }
                CACHE.put("cape_" + safeId, resId);
                CACHE.put(safeId, resId);
                CACHE.put(capeId, resId);
                return resId;
            }
        }

        return null;
    }
}
