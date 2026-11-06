package dev.kollegen.client.ui;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.NativeImage;
import dev.kollegen.client.menu.SkinCapeTextureManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class SkinFaceRenderer {

    private static final Map<String, Identifier> TEXTURE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Supplier<PlayerSkin>> LOOKUPS = new ConcurrentHashMap<>();
    private static final Set<String> FETCHING = ConcurrentHashMap.newKeySet();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();

    private SkinFaceRenderer() {
    }

    public static void drawFace(GuiGraphicsExtractor g, String username, UUID uuid, int x, int y, int size) {
        if (username == null || username.isEmpty()) {
            username = "Spieler";
        }
        String key = username.toLowerCase();
        Identifier tex = TEXTURE_CACHE.get(key);

        if (tex != null) {
            try {
                PlayerFaceExtractor.extractRenderState(g, tex, x, y, size, true, false, -1);
                return;
            } catch (Throwable ignored) {
            }
        }

        Minecraft mc = Minecraft.getInstance();

        if (mc.player != null && mc.player.getGameProfile().name().equalsIgnoreCase(username)) {
            try {
                Identifier pId = mc.player.getSkin().body().texturePath();
                if (pId != null) {
                    TEXTURE_CACHE.put(key, pId);
                    PlayerFaceExtractor.extractRenderState(g, pId, x, y, size, true, false, -1);
                    return;
                }
            } catch (Throwable ignored) {
            }
        }

        try {
            Identifier localSkin = SkinCapeTextureManager.getOrLoadSkin(username, null);
            if (localSkin != null) {
                TEXTURE_CACHE.put(key, localSkin);
                PlayerFaceExtractor.extractRenderState(g, localSkin, x, y, size, true, false, -1);
                return;
            }
        } catch (Throwable ignored) {
        }

        UUID u = uuid != null ? uuid : UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
        GameProfile prof = new GameProfile(u, username);

        if (mc.getSkinManager() != null) {
            try {
                Supplier<PlayerSkin> sup = LOOKUPS.computeIfAbsent(key, k -> mc.getSkinManager().createLookup(prof, false));
                PlayerSkin ps = sup.get();
                if (ps != null && ps.body() != null && ps.body().texturePath() != null) {
                    Identifier sId = ps.body().texturePath();
                    PlayerFaceExtractor.extractRenderState(g, sId, x, y, size, true, false, -1);
                } else {
                    PlayerSkin def = DefaultPlayerSkin.get(prof);
                    PlayerFaceExtractor.extractRenderState(g, def.body().texturePath(), x, y, size, true, false, -1);
                }
            } catch (Throwable ignored) {
            }
        }

        fetchSkinAsync(username, uuid);
    }

    private static void fetchSkinAsync(String username, UUID uuid) {
        String key = username.toLowerCase();
        if (!FETCHING.add(key)) return;

        Thread t = new Thread(() -> {
            try {
                String home = System.getProperty("user.home", "");
                Path cacheDir = Path.of(home, ".local", "share", "kollegenclient", "skins", "cache");
                Path cachedPng = cacheDir.resolve(key + ".png");

                byte[] bytes = null;
                if (Files.exists(cachedPng) && Files.size(cachedPng) > 100) {
                    try {
                        bytes = Files.readAllBytes(cachedPng);
                    } catch (Throwable ignored) {
                    }
                }

                if (bytes == null) {
                    String[] urls = new String[]{
                            "https://minotar.net/skin/" + username,
                            "https://mc-heads.net/skin/" + username
                    };
                    for (String url : urls) {
                        try {
                            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                                    .timeout(Duration.ofSeconds(6))
                                    .header("User-Agent", "KollegenClient/1.25")
                                    .GET()
                                    .build();
                            HttpResponse<byte[]> res = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
                            if (res.statusCode() == 200 && res.body() != null && res.body().length > 100) {
                                bytes = res.body();
                                Files.createDirectories(cacheDir);
                                Files.write(cachedPng, bytes);
                                break;
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                }

                if (bytes != null && bytes.length > 0) {
                    final byte[] fBytes = bytes;
                    Minecraft.getInstance().execute(() -> {
                        try {
                            NativeImage img = NativeImage.read(fBytes);
                            String safe = key.replaceAll("[^a-z0-9_.-]", "_");
                            Identifier id = Identifier.tryParse("kollegen:face_" + safe);
                            DynamicTexture tex = new DynamicTexture(() -> "face_" + safe, img);
                            Minecraft.getInstance().getTextureManager().register(id, tex);
                            TEXTURE_CACHE.put(key, id);
                        } catch (Throwable ignored) {
                        }
                    });
                }
            } catch (Throwable ignored) {
            }
        });
        t.setDaemon(true);
        t.setName("Kollegen-SkinFaceFetcher-" + username);
        t.start();
    }
}
