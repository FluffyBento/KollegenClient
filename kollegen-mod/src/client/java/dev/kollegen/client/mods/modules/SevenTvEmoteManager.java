package dev.kollegen.client.mods.modules;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.gui.font.PlainTextRenderable;
import net.minecraft.client.gui.font.SingleSpriteSource;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4fc;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public final class SevenTvEmoteManager {

    public static class EmoteData {
        public final String name;
        public final String id;
        public final boolean animated;
        public final int width;
        public final int height;

        public EmoteData(String name, String id, boolean animated, int width, int height) {
            this.name = name;
            this.id = id;
            this.animated = animated;
            this.width = width > 0 ? width : 32;
            this.height = height > 0 ? height : 32;
        }
    }

    public static final class AnimatedFrame {
        public final int[] pixels;
        public final int delayMs;
        public final int width;
        public final int height;

        public AnimatedFrame(int[] pixels, int delayMs, int width, int height) {
            this.pixels = pixels;
            this.delayMs = delayMs > 0 ? delayMs : 50;
            this.width = width;
            this.height = height;
        }
    }

    private static final class AnimatedTexture {
        final DynamicTexture texture;
        final List<AnimatedFrame> frames;
        final int width;
        final int height;
        final long startTime;
        int lastFrame = -1;

        AnimatedTexture(DynamicTexture texture, List<AnimatedFrame> frames, int width, int height) {
            this.texture = texture;
            this.frames = frames;
            this.width = width;
            this.height = height;
            this.startTime = System.currentTimeMillis();
        }

        int currentFrame(long now) {
            int total = 0;
            for (AnimatedFrame frame : this.frames) {
                total += frame.delayMs;
            }
            if (total <= 0) {
                return 0;
            }
            long elapsed = (now - this.startTime) % total;
            int acc = 0;
            for (int i = 0; i < this.frames.size(); i++) {
                acc += this.frames.get(i).delayMs;
                if (elapsed < acc) {
                    return i;
                }
            }
            return this.frames.size() - 1;
        }
    }

    public static class EmoteGlyphInstance implements PlainTextRenderable {
        private final GlyphRenderTypes renderTypes;
        private final GpuTextureView textureView;
        private final float x;
        private final float y;
        private final int color;
        private final Style style;
        private final float w;
        private final float h;

        public EmoteGlyphInstance(GlyphRenderTypes renderTypes, GpuTextureView textureView, float x, float y, int color, Style style, float w, float h) {
            this.renderTypes = renderTypes;
            this.textureView = textureView;
            this.x = x;
            this.y = y;
            this.color = color;
            this.style = style;
            this.w = w;
            this.h = h;
        }

        @Override
        public void renderSprite(Matrix4fc pose, VertexConsumer buffer, int packedLightCoords, float offsetX, float offsetY, float z, int col) {
            float x0 = offsetX + this.left();
            float x1 = offsetX + this.right();
            float y0 = offsetY + this.top();
            float y1 = offsetY + this.bottom();
            int c = (col & 0xFF000000) | 0x00FFFFFF;
            buffer.addVertex(pose, x0, y0, z).setUv(0.0f, 0.0f).setColor(c).setLight(packedLightCoords);
            buffer.addVertex(pose, x0, y1, z).setUv(0.0f, 1.0f).setColor(c).setLight(packedLightCoords);
            buffer.addVertex(pose, x1, y1, z).setUv(1.0f, 1.0f).setColor(c).setLight(packedLightCoords);
            buffer.addVertex(pose, x1, y0, z).setUv(1.0f, 0.0f).setColor(c).setLight(packedLightCoords);
        }

        @Override
        public float x() { return this.x; }

        @Override
        public float y() { return this.y; }

        @Override
        public int color() { return this.color; }

        @Override
        public int shadowColor() { return 0; }

        @Override
        public float shadowOffset() { return 0.0f; }

        @Override
        public Style style() { return this.style; }

        @Override
        public float width() { return this.w; }

        @Override
        public float height() { return this.h; }

        @Override
        public float ascent() { return 7.0f; }

        @Override
        public float activeLeft() { return this.x; }

        @Override
        public float activeTop() { return this.top(); }

        @Override
        public float activeRight() { return this.right(); }

        @Override
        public float activeBottom() { return this.bottom(); }

        @Override
        public RenderType renderType(Font.DisplayMode mode) {
            return this.renderTypes.select(mode);
        }

        @Override
        public RenderPipeline guiPipeline() {
            return this.renderTypes.guiPipeline();
        }

        @Override
        public GpuTextureView textureView() {
            return this.textureView;
        }
    }

    private static final Map<String, EmoteData> EMOTES_BY_NAME = new ConcurrentHashMap<>();
    private static final Map<String, String> NAME_TO_KEY = new ConcurrentHashMap<>();
    private static final Map<String, EmoteData> KEY_TO_EMOTE = new ConcurrentHashMap<>();
    private static final Map<String, Identifier> TEXTURES_BY_KEY = new ConcurrentHashMap<>();
    private static final Map<String, GlyphSource> GLYPH_SOURCES_BY_KEY = new ConcurrentHashMap<>();
    private static final Map<String, int[]> TEXTURE_SIZES = new ConcurrentHashMap<>();
    private static final List<AnimatedTexture> ANIMATIONS = new CopyOnWriteArrayList<>();
    private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();
    private static final Map<String, Long> RETRY_AFTER = new ConcurrentHashMap<>();
    private static final long RETRY_BACKOFF_MS = 120_000L;
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    static {
        seedPopular("catJAM", "01M3JV4YYGRMP05A8BWNYCH2B7", true, 32, 32);
        seedPopular("OMEGALUL", "583089f4737a8e002c1a3b57", false, 32, 32);
        seedPopular("Pog", "609aa47ab182ff5f58c70f08", false, 32, 32);
        seedPopular("KEKW", "5e0fa741b04535037dec5eb1", false, 32, 32);
        seedPopular("PepeHands", "59f27b3f4e24e5002ae7e762", false, 32, 32);
        seedPopular("monkaW", "5c701763bf4566085a66a7b7", false, 32, 32);
        seedPopular("EZ", "609aa469b182ff5f58c70f04", false, 32, 32);
        seedPopular("Clap", "01GF9MNXQ8000E85YTZ0P4TWJ7", true, 32, 32);
        seedPopular("AYAYA", "58ae8d00922f3000282f6f97", false, 32, 32);
        seedPopular("monkaS", "56e9f494fff3cc5c3ce5797b", false, 32, 32);
        seedPopular("FeelsBadMan", "566ca04265dbbdab32ec054a", false, 32, 32);
        seedPopular("FeelsGoodMan", "566ca05765dbbdab32ec054d", false, 32, 32);
        seedPopular("Sadge", "5e70eb99d6581c3724c0840b", false, 32, 32);
        seedPopular("pepeJAM", "01M5TMWV5WCF30541CH3KS34M9", true, 32, 32);
        seedPopular("WideHardo", "5c84138e65306e0a969b8849", false, 64, 32);
        seedPopular("LUL", "568be8c460d3d3c73bb1b8e4", false, 32, 32);
        seedPopular("ResidentSleeper", "566ca0b265dbbdab32ec0558", false, 32, 32);
        seedPopular("PogChamp", "566ca04265dbbdab32ec054a", false, 32, 32);
        seedPopular("BibleThump", "566ca08665dbbdab32ec0554", false, 32, 32);
        seedPopular("Kappa", "566ca05765dbbdab32ec054d", false, 32, 32);
        seedPopular("NotLikeThis", "566ca0cd65dbbdab32ec055b", false, 32, 32);
        seedPopular("WutFace", "566ca0e465dbbdab32ec055e", false, 32, 32);
        seedPopular("HeyGuys", "566ca0db65dbbdab32ec055d", false, 32, 32);
    }

    private static void seedPopular(String name, String id, boolean animated, int w, int h) {
        EmoteData data = new EmoteData(name, id, animated, w, h);
        EMOTES_BY_NAME.put(name, data);
        String key = getSafeKey(name);
        KEY_TO_EMOTE.put(key, data);
    }

    private SevenTvEmoteManager() {}

    public static String getSafeKey(String name) {
        if (name == null) return "e_null";
        String hit = NAME_TO_KEY.get(name);
        if (hit != null) return hit;
        String safe = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        String key = "e_" + safe + "_" + Integer.toHexString(name.hashCode());
        NAME_TO_KEY.put(name, key);
        return key;
    }

    public static void registerEmote(String name, String id, boolean animated, int width, int height) {
        if (name == null || name.isEmpty() || id == null || id.isEmpty()) return;
        EmoteData data = new EmoteData(name, id, animated, width, height);
        EMOTES_BY_NAME.put(name, data);
        String key = getSafeKey(name);
        KEY_TO_EMOTE.put(key, data);
    }

    public static void parseAndRegisterEmotes(JsonArray array) {
        if (array == null) return;
        for (JsonElement el : array) {
            if (!el.isJsonObject()) continue;
            JsonObject obj = el.getAsJsonObject();
            String id = obj.has("id") ? obj.get("id").getAsString() : null;
            String name = obj.has("name") ? obj.get("name").getAsString() : null;
            if (id == null || name == null) continue;

            boolean animated = false;
            int w = 32;
            int h = 32;

            if (obj.has("data") && obj.get("data").isJsonObject()) {
                JsonObject data = obj.getAsJsonObject("data");
                if (data.has("animated")) {
                    animated = data.get("animated").getAsBoolean();
                }
                if (data.has("host") && data.get("host").isJsonObject()) {
                    JsonObject host = data.getAsJsonObject("host");
                    if (host.has("files") && host.get("files").isJsonArray()) {
                        for (JsonElement fe : host.getAsJsonArray("files")) {
                            if (fe.isJsonObject()) {
                                JsonObject fo = fe.getAsJsonObject();
                                if (fo.has("name")) {
                                    String fn = fo.get("name").getAsString();
                                    if (fn.equals("1x.png") || fn.equals("1x.gif") || fn.equals("1x.webp")) {
                                        if (fo.has("width")) w = fo.get("width").getAsInt();
                                        if (fo.has("height")) h = fo.get("height").getAsInt();
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }

            registerEmote(name, id, animated, w, h);
        }
    }

    public static boolean isEmote(String name) {
        if (name == null || name.isEmpty()) return false;
        return EMOTES_BY_NAME.containsKey(name);
    }

    public static boolean containsEmote(String text) {
        if (text == null || text.isEmpty()) return false;
        String[] words = text.split("\\s+");
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (isEmote(w)) return true;
            int end = w.length();
            while (end > 0 && ",.!?~:;".indexOf(w.charAt(end - 1)) >= 0) end--;
            if (end > 0 && end < w.length() && isEmote(w.substring(0, end))) return true;
        }
        return false;
    }

    public static EmoteData getEmote(String name) {
        if (name == null) return null;
        return EMOTES_BY_NAME.get(name);
    }

    public static Identifier getTexture(String name) {
        if (name == null || name.isEmpty()) return null;
        String key = getSafeKey(name);
        Identifier hit = TEXTURES_BY_KEY.get(key);
        if (hit != null) return hit;
        ensureLoaded(name);
        return null;
    }

    public static Identifier getTextureByKey(String key) {
        if (key == null || key.isEmpty()) return null;
        return TEXTURES_BY_KEY.get(key);
    }

    public static int[] getTextureSize(String key) {
        if (key == null || key.isEmpty()) return null;
        return TEXTURE_SIZES.get(key);
    }

    public static GlyphSource getGlyphSource(String key) {
        if (key == null || key.isEmpty()) return null;
        GlyphSource hit = GLYPH_SOURCES_BY_KEY.get(key);
        if (hit != null) return hit;
        EmoteData data = KEY_TO_EMOTE.get(key);
        if (data != null) {
            ensureLoaded(data.name);
        }
        return null;
    }

    public static void ensureBuiltinTexture(String key, String resourcePath, float fontHeight) {
        if (key == null || key.isEmpty() || resourcePath == null || resourcePath.isEmpty()) return;
        if (TEXTURES_BY_KEY.containsKey(key)) return;
        if (backingOff(key)) return;
        if (!PENDING.add(key)) return;
        CompletableFuture.runAsync(() -> {
            try (InputStream in = SevenTvEmoteManager.class.getResourceAsStream(resourcePath)) {
                if (in == null) {
                    fail(key);
                    return;
                }
                byte[] bytes = in.readAllBytes();
                if (bytes.length == 0) {
                    fail(key);
                    return;
                }
                Minecraft.getInstance().execute(() -> registerStaticTexture(key, bytes, fontHeight));
            } catch (Throwable t) {
                fail(key);
            }
        });
    }

    public static void ensureTextureFromUrl(String key, String url, float fontHeight) {
        if (key == null || key.isEmpty() || url == null || url.isEmpty()) return;
        if (TEXTURES_BY_KEY.containsKey(key)) return;
        if (backingOff(key)) return;
        if (!PENDING.add(key)) return;
        CompletableFuture.runAsync(() -> {
            try {
                byte[] bytes = downloadBytes(url);
                if (bytes == null || bytes.length == 0) {
                    fail(key);
                    return;
                }
                if (!isPng(bytes)) {
                    bytes = convertToPng(bytes);
                }
                if (bytes == null || bytes.length == 0) {
                    fail(key);
                    return;
                }
                final byte[] finalBytes = bytes;
                Minecraft.getInstance().execute(() -> registerStaticTexture(key, finalBytes, fontHeight));
            } catch (Throwable t) {
                fail(key);
            }
        });
    }

    public static void ensureLoaded(String name) {
        if (name == null || name.isEmpty()) return;
        String key = getSafeKey(name);
        if (TEXTURES_BY_KEY.containsKey(key)) return;
        if (backingOff(key)) return;
        if (!PENDING.add(key)) return;

        EmoteData data = KEY_TO_EMOTE.get(key);
        if (data == null) {
            fail(key);
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                Path cacheDir = Path.of(System.getProperty("user.home", ""), ".local", "share", "kollegenclient", "7tv_cache");
                Path cachedGif = cacheDir.resolve(data.id + ".gif");
                Path cachedPng = cacheDir.resolve(data.id + ".png");
                byte[] bytes = null;

                if (data.animated) {
                    if (Files.isRegularFile(cachedGif)) {
                        try {
                            bytes = Files.readAllBytes(cachedGif);
                        } catch (Throwable ignored) {}
                        if (bytes != null && !isGif(bytes)) {
                            bytes = null;
                        }
                    }
                    if (bytes == null) {
                        bytes = downloadEmoteBytes(data, true);
                        if (bytes != null && isGif(bytes)) {
                            try {
                                Files.createDirectories(cacheDir);
                                Files.write(cachedGif, bytes);
                            } catch (Throwable ignored) {}
                        }
                    }
                }

                if (bytes == null) {
                    if (Files.isRegularFile(cachedPng)) {
                        try {
                            bytes = Files.readAllBytes(cachedPng);
                        } catch (Throwable ignored) {}
                    }
                    if (bytes == null) {
                        bytes = downloadEmoteBytes(data, false);
                        if (bytes != null) {
                            if (!isPng(bytes)) {
                                bytes = convertToPng(bytes);
                            }
                            if (bytes != null) {
                                try {
                                    Files.createDirectories(cacheDir);
                                    Files.write(cachedPng, bytes);
                                } catch (Throwable ignored) {}
                            }
                        }
                    }
                }

                if (bytes == null || bytes.length == 0) {
                    fail(key);
                    return;
                }

                final byte[] finalBytes = bytes;
                if (data.animated && isGif(finalBytes)) {
                    List<AnimatedFrame> frames = decodeGif(finalBytes);
                    if (frames.size() > 1) {
                        final List<AnimatedFrame> finalFrames = frames;
                        final int fw = frames.get(0).width;
                        final int fh = frames.get(0).height;
                        Minecraft.getInstance().execute(() -> registerAnimatedTexture(key, finalFrames, fw, fh));
                        return;
                    }
                    byte[] png = convertToPng(finalBytes);
                    if (png == null) {
                        fail(key);
                        return;
                    }
                    final byte[] pngFinal = png;
                    Minecraft.getInstance().execute(() -> registerStaticTexture(key, pngFinal, 9.0f));
                    return;
                }

                Minecraft.getInstance().execute(() -> registerStaticTexture(key, finalBytes, 9.0f));
            } catch (Throwable t) {
                fail(key);
            }
        });
    }

    public static void tickAnimations() {
        if (ANIMATIONS.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (AnimatedTexture anim : ANIMATIONS) {
            int idx = anim.currentFrame(now);
            if (idx == anim.lastFrame) continue;
            anim.lastFrame = idx;
            NativeImage img = anim.texture.getPixels();
            if (img == null) continue;
            int[] pixels = anim.frames.get(idx).pixels;
            int w = Math.min(img.getWidth(), anim.width);
            int h = Math.min(img.getHeight(), anim.height);
            for (int y = 0; y < h; y++) {
                int row = y * anim.width;
                for (int x = 0; x < w; x++) {
                    img.setPixel(x, y, pixels[row + x]);
                }
            }
            anim.texture.upload();
        }
    }

    private static void registerStaticTexture(String key, byte[] pngBytes, float fontHeight) {
        boolean ok = false;
        try {
            NativeImage nativeImg = NativeImage.read(pngBytes);
            int imgW = nativeImg.getWidth();
            int imgH = nativeImg.getHeight();
            Identifier texId = Identifier.tryParse("kollegen:" + key);
            DynamicTexture tex = new DynamicTexture(() -> "7tv_" + key, nativeImg);
            Minecraft.getInstance().getTextureManager().register(texId, tex);
            TEXTURES_BY_KEY.put(key, texId);
            TEXTURE_SIZES.put(key, new int[]{imgW, imgH});
            registerGlyphSource(key, tex, texId, imgW, imgH, fontHeight);
            ok = true;
        } catch (Throwable ignored) {
        } finally {
            PENDING.remove(key);
            if (ok) succeed(key);
            else fail(key);
        }
    }

    private static void registerAnimatedTexture(String key, List<AnimatedFrame> frames, int width, int height) {
        boolean ok = false;
        try {
            NativeImage first = toNativeImage(frames.get(0).pixels, width, height);
            Identifier texId = Identifier.tryParse("kollegen:" + key);
            DynamicTexture tex = new DynamicTexture(() -> "7tv_" + key, first);
            Minecraft.getInstance().getTextureManager().register(texId, tex);
            TEXTURES_BY_KEY.put(key, texId);
            TEXTURE_SIZES.put(key, new int[]{width, height});
            registerGlyphSource(key, tex, texId, width, height, 9.0f);
            ANIMATIONS.add(new AnimatedTexture(tex, frames, width, height));
            ok = true;
        } catch (Throwable ignored) {
        } finally {
            PENDING.remove(key);
            if (ok) succeed(key);
            else fail(key);
        }
    }

    private static void registerGlyphSource(String key, DynamicTexture tex, Identifier texId, int imgW, int imgH, float fontHeight) {
        float fontH = fontHeight;
        float fontW = (imgH > 0) ? Math.max(8.0f, Math.min(27.0f, Math.round(fontH * (float) imgW / (float) imgH))) : fontH;
        float advance = fontW + 1.0f;
        GlyphRenderTypes renderTypes = GlyphRenderTypes.createForColorTexture(texId);
        GpuTextureView texView = tex.getTextureView();

        BakedGlyph bakedGlyph = new BakedGlyph() {
            @Override
            public GlyphInfo info() {
                return GlyphInfo.simple(advance);
            }

            @Override
            public PlainTextRenderable createGlyph(
                    float x, float y, int color, int shadowColor, Style style, float boldOffset, float shadowOffset
            ) {
                return new EmoteGlyphInstance(renderTypes, texView, x, y, color, style, fontW, fontH);
            }
        };

        GLYPH_SOURCES_BY_KEY.put(key, new SingleSpriteSource(bakedGlyph));
    }

    private static boolean backingOff(String key) {
        Long until = RETRY_AFTER.get(key);
        return until != null && System.currentTimeMillis() < until;
    }

    private static void fail(String key) {
        PENDING.remove(key);
        RETRY_AFTER.put(key, System.currentTimeMillis() + RETRY_BACKOFF_MS);
    }

    private static void succeed(String key) {
        RETRY_AFTER.remove(key);
    }

    private static NativeImage toNativeImage(int[] argb, int width, int height) {
        NativeImage img = new NativeImage(width, height, false);
        for (int y = 0; y < height; y++) {
            int row = y * width;
            for (int x = 0; x < width; x++) {
                img.setPixel(x, y, argb[row + x]);
            }
        }
        return img;
    }

    private static byte[] downloadEmoteBytes(EmoteData data, boolean preferGif) {
        String[] urls;
        if (preferGif) {
            urls = new String[]{
                    "https://cdn.7tv.app/emote/" + data.id + "/1x.gif",
                    "https://cdn.7tv.app/emote/" + data.id + "/1x.png"
            };
        } else {
            urls = new String[]{
                    "https://cdn.7tv.app/emote/" + data.id + "/1x.png",
                    "https://cdn.7tv.app/emote/" + data.id + "/1x.gif"
            };
        }

        for (String urlStr : urls) {
            try {
                byte[] body = downloadBytes(urlStr);
                if (body != null && body.length > 0) {
                    return body;
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static byte[] downloadBytes(String urlStr) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(urlStr))
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();
            HttpResponse<byte[]> res = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() >= 200 && res.statusCode() < 300 && res.body() != null && res.body().length > 0) {
                return res.body();
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static List<AnimatedFrame> decodeGif(byte[] bytes) {
        List<AnimatedFrame> frames = new ArrayList<>();
        ImageReader reader = null;
        ImageInputStream in = null;
        try {
            in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes));
            if (in == null) return frames;
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
            if (!readers.hasNext()) return frames;
            reader = readers.next();
            reader.setInput(in, false, false);
            int count = reader.getNumImages(true);
            if (count <= 0) return frames;
            int width = reader.getWidth(0);
            int height = reader.getHeight(0);
            BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = canvas.createGraphics();
            g.setComposite(AlphaComposite.SrcOver);
            BufferedImage previous = null;

            for (int i = 0; i < count; i++) {
                BufferedImage frame = reader.read(i);
                IIOMetadata metadata = reader.getImageMetadata(i);
                IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(metadata.getNativeMetadataFormatName());

                int x = 0;
                int y = 0;
                int fw = frame.getWidth();
                int fh = frame.getHeight();
                NodeList desc = root.getElementsByTagName("ImageDescriptor");
                if (desc.getLength() > 0) {
                    Element d = (Element) desc.item(0);
                    x = parseInt(d.getAttribute("imageLeftPosition"), 0);
                    y = parseInt(d.getAttribute("imageTopPosition"), 0);
                    fw = parseInt(d.getAttribute("imageWidth"), fw);
                    fh = parseInt(d.getAttribute("imageHeight"), fh);
                }

                String disposal = "none";
                int delay = 10;
                NodeList gce = root.getElementsByTagName("GraphicControlExtension");
                if (gce.getLength() > 0) {
                    Element e = (Element) gce.item(0);
                    disposal = e.getAttribute("disposalMethod");
                    delay = parseInt(e.getAttribute("delayTime"), 10) * 10;
                }
                if (delay < 20) {
                    delay = 20;
                }

                if ("restoreToPrevious".equals(disposal)) {
                    previous = copyImage(canvas);
                }

                g.drawImage(frame, x, y, null);
                int[] pixels = canvas.getRGB(0, 0, width, height, null, 0, width);
                frames.add(new AnimatedFrame(pixels, delay, width, height));

                if ("restoreToBackgroundColor".equals(disposal)) {
                    g.setComposite(AlphaComposite.Clear);
                    g.fillRect(x, y, fw, fh);
                    g.setComposite(AlphaComposite.SrcOver);
                } else if ("restoreToPrevious".equals(disposal) && previous != null) {
                    g.setComposite(AlphaComposite.Src);
                    g.drawImage(previous, 0, 0, null);
                    g.setComposite(AlphaComposite.SrcOver);
                }
            }
            g.dispose();
        } catch (Throwable ignored) {
        } finally {
            if (reader != null) {
                reader.dispose();
            }
            if (in != null) {
                try {
                    in.close();
                } catch (Throwable ignored) {}
            }
        }
        return frames;
    }

    private static BufferedImage copyImage(BufferedImage src) {
        BufferedImage copy = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = copy.createGraphics();
        g.setComposite(AlphaComposite.Src);
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return copy;
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (Throwable t) {
            return def;
        }
    }

    private static byte[] convertToPng(byte[] bytes) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
            if (img == null) return null;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            return baos.toByteArray();
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean isGif(byte[] b) {
        return b != null && b.length >= 6 &&
                (b[0] & 0xFF) == 'G' && (b[1] & 0xFF) == 'I' &&
                (b[2] & 0xFF) == 'F' && (b[3] & 0xFF) == '8';
    }

    private static boolean isPng(byte[] b) {
        return b != null && b.length >= 8 &&
                (b[0] & 0xFF) == 0x89 && (b[1] & 0xFF) == 0x50 &&
                (b[2] & 0xFF) == 0x4E && (b[3] & 0xFF) == 0x47 &&
                (b[4] & 0xFF) == 0x0D && (b[5] & 0xFF) == 0x0A &&
                (b[6] & 0xFF) == 0x1A && (b[7] & 0xFF) == 0x0A;
    }
}
