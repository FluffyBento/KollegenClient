package dev.kollegen.client.feed;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kollegen.client.account.AccountManager;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class FeedManager {

    public static final class FeedPost {
        public final String id;
        public final String authorName;
        public final String authorUuid;
        public final String caption;
        public final String serverTag;
        public final long timestamp;
        public int likes;
        public boolean likedByMe;
        public final List<String> comments = new ArrayList<>();

        public FeedPost(String id, String authorName, String authorUuid, String caption, String serverTag, long timestamp, int likes, boolean likedByMe) {
            this.id = id;
            this.authorName = authorName;
            this.authorUuid = authorUuid;
            this.caption = caption;
            this.serverTag = serverTag;
            this.timestamp = timestamp;
            this.likes = likes;
            this.likedByMe = likedByMe;
        }

        public String getTimeAgo() {
            long diff = System.currentTimeMillis() - this.timestamp;
            if (diff < 60_000L) {
                return "Gerade eben";
            }
            long minutes = diff / 60_000L;
            if (minutes < 60) {
                return "vor " + minutes + " Min.";
            }
            long hours = minutes / 60;
            if (hours < 24) {
                return "vor " + hours + " Std.";
            }
            long days = hours / 24;
            return "vor " + days + " T.";
        }
    }

    private static final List<FeedPost> POSTS = new ArrayList<>();
    private static boolean loaded = false;

    private FeedManager() {
    }

    private static Path getFilePath() {
        return FabricLoader.getInstance().getConfigDir().resolve("kollegen-feed.json");
    }

    public static synchronized List<FeedPost> getPosts() {
        ensureLoaded();
        return Collections.unmodifiableList(POSTS);
    }

    public static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        POSTS.clear();
        try {
            Path p = getFilePath();
            if (Files.isRegularFile(p)) {
                String content = Files.readString(p);
                JsonArray arr = JsonParser.parseString(content).getAsJsonArray();
                for (JsonElement el : arr) {
                    if (!el.isJsonObject()) continue;
                    JsonObject o = el.getAsJsonObject();
                    String id = o.get("id").getAsString();
                    String author = o.get("author").getAsString();
                    String uuid = o.has("uuid") ? o.get("uuid").getAsString() : "";
                    String cap = o.get("caption").getAsString();
                    String srv = o.has("server") ? o.get("server").getAsString() : "Minecraft";
                    long time = o.has("time") ? o.get("time").getAsLong() : System.currentTimeMillis();
                    int likes = o.has("likes") ? o.get("likes").getAsInt() : 0;
                    boolean liked = o.has("liked") && o.get("liked").getAsBoolean();
                    FeedPost post = new FeedPost(id, author, uuid, cap, srv, time, likes, liked);
                    if (o.has("comments") && o.get("comments").isJsonArray()) {
                        for (JsonElement c : o.getAsJsonArray("comments")) {
                            post.comments.add(c.getAsString());
                        }
                    }
                    POSTS.add(post);
                }
            }
        } catch (Throwable ignored) {
        }

        if (POSTS.isEmpty()) {
            initDefaultCommunityPosts();
            save();
        }
    }

    private static void initDefaultCommunityPosts() {
        long now = System.currentTimeMillis();
        POSTS.add(new FeedPost(
                UUID.randomUUID().toString(),
                "FluffyBento",
                "",
                "Endlich die Netherite-Rüstung voll verzaubert! 🔥 Bereit für den Enderdrachen.",
                "GommeHD.net",
                now - 12 * 60_000L,
                38,
                false
        ));
        POSTS.add(new FeedPost(
                UUID.randomUUID().toString(),
                "Iz4nLeAstra",
                "",
                "Bedwars Win-Streak auf 12 gebracht mit den Kollegen! 🏆🎮",
                "Hypixel.net",
                now - 45 * 60_000L,
                52,
                false
        ));
        POSTS.add(new FeedPost(
                UUID.randomUUID().toString(),
                "akkuniizu",
                "",
                "Neues Schloss-Tor im Survival gebaut – sieht ultra episch aus 🏰✨",
                "Singleplayer",
                now - 140 * 60_000L,
                64,
                false
        ));
    }

    public static synchronized FeedPost addPost(String caption, String serverTag) {
        ensureLoaded();
        String name = AccountManager.getActiveUsername();
        String uuid = AccountManager.getActiveProfile() != null && AccountManager.getActiveProfile().id() != null
                ? AccountManager.getActiveProfile().id().toString()
                : "";
        String srv = (serverTag != null && !serverTag.trim().isEmpty()) ? serverTag.trim() : "Minecraft";
        FeedPost p = new FeedPost(UUID.randomUUID().toString(), name, uuid, caption.trim(), srv, System.currentTimeMillis(), 1, true);
        POSTS.add(0, p);
        save();
        return p;
    }

    public static synchronized void toggleLike(String postId) {
        ensureLoaded();
        for (FeedPost p : POSTS) {
            if (p.id.equals(postId)) {
                if (p.likedByMe) {
                    p.likedByMe = false;
                    p.likes = Math.max(0, p.likes - 1);
                } else {
                    p.likedByMe = true;
                    p.likes++;
                }
                save();
                break;
            }
        }
    }

    public static synchronized void addComment(String postId, String comment) {
        if (comment == null || comment.trim().isEmpty()) return;
        ensureLoaded();
        for (FeedPost p : POSTS) {
            if (p.id.equals(postId)) {
                String author = AccountManager.getActiveUsername();
                p.comments.add(author + ": " + comment.trim());
                save();
                break;
            }
        }
    }

    public static synchronized void save() {
        try {
            Path p = getFilePath();
            if (p.getParent() != null) Files.createDirectories(p.getParent());
            JsonArray arr = new JsonArray();
            for (FeedPost post : POSTS) {
                JsonObject o = new JsonObject();
                o.addProperty("id", post.id);
                o.addProperty("author", post.authorName);
                o.addProperty("uuid", post.authorUuid);
                o.addProperty("caption", post.caption);
                o.addProperty("server", post.serverTag);
                o.addProperty("time", post.timestamp);
                o.addProperty("likes", post.likes);
                o.addProperty("liked", post.likedByMe);
                JsonArray comm = new JsonArray();
                for (String c : post.comments) {
                    comm.add(c);
                }
                o.add("comments", comm);
                arr.add(o);
            }
            Files.writeString(p, arr.toString());
        } catch (Throwable ignored) {
        }
    }
}
