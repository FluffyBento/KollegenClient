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
        public final String imagePath;
        public final boolean isJustNow;
        public final boolean isLate;
        public final int coinsEarned;
        public final int streakDay;
        public final long timestamp;
        public int likes;
        public boolean likedByMe;
        public final List<String> comments = new ArrayList<>();

        public FeedPost(String id, String authorName, String authorUuid, String caption, String serverTag,
                        String imagePath, boolean isJustNow, boolean isLate, int coinsEarned, int streakDay,
                        long timestamp, int likes, boolean likedByMe) {
            this.id = id;
            this.authorName = authorName;
            this.authorUuid = authorUuid;
            this.caption = caption;
            this.serverTag = serverTag;
            this.imagePath = imagePath;
            this.isJustNow = isJustNow;
            this.isLate = isLate;
            this.coinsEarned = coinsEarned;
            this.streakDay = streakDay;
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

    public static synchronized List<FeedPost> getJustNowPosts() {
        ensureLoaded();
        List<FeedPost> list = new ArrayList<>();
        for (FeedPost p : POSTS) {
            if (p.isJustNow) list.add(p);
        }
        return list;
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
                    String cap = o.has("caption") ? o.get("caption").getAsString() : "";
                    String srv = o.has("server") ? o.get("server").getAsString() : "Minecraft";
                    String img = o.has("image") && !o.get("image").isJsonNull() ? o.get("image").getAsString() : null;
                    boolean jn = o.has("isJustNow") && o.get("isJustNow").getAsBoolean();
                    boolean late = o.has("isLate") && o.get("isLate").getAsBoolean();
                    int coins = o.has("coins") ? o.get("coins").getAsInt() : 0;
                    int streak = o.has("streak") ? o.get("streak").getAsInt() : 0;
                    long time = o.has("time") ? o.get("time").getAsLong() : System.currentTimeMillis();
                    int likes = o.has("likes") ? o.get("likes").getAsInt() : 0;
                    boolean liked = o.has("liked") && o.get("liked").getAsBoolean();

                    FeedPost post = new FeedPost(id, author, uuid, cap, srv, img, jn, late, coins, streak, time, likes, liked);
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
    }

    public static synchronized FeedPost addPost(String caption, String serverTag) {
        return addFeedPost(caption, null, serverTag);
    }

    public static synchronized FeedPost addFeedPost(String caption, String imagePath, String serverTag) {
        ensureLoaded();
        String name = AccountManager.getActiveUsername();
        String uuid = AccountManager.getActiveProfile() != null && AccountManager.getActiveProfile().id() != null
                ? AccountManager.getActiveProfile().id().toString()
                : "";
        String srv = (serverTag != null && !serverTag.trim().isEmpty()) ? serverTag.trim() : "Minecraft";
        FeedPost p = new FeedPost(UUID.randomUUID().toString(), name, uuid, caption != null ? caption.trim() : "", srv,
                imagePath, false, false, 0, 0, System.currentTimeMillis(), 0, false);
        POSTS.add(0, p);
        save();
        return p;
    }

    public static synchronized FeedPost addJustNowPost(String caption, String imagePath, boolean onTime, int streak, int coins) {
        ensureLoaded();
        String name = AccountManager.getActiveUsername();
        String uuid = AccountManager.getActiveProfile() != null && AccountManager.getActiveProfile().id() != null
                ? AccountManager.getActiveProfile().id().toString()
                : "";
        String srv = "JustNow";
        FeedPost p = new FeedPost(UUID.randomUUID().toString(), name, uuid, caption != null ? caption.trim() : "", srv,
                imagePath, true, !onTime, coins, streak, System.currentTimeMillis(), 0, false);
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

    public static synchronized void deletePost(String postId) {
        ensureLoaded();
        POSTS.removeIf(p -> p.id.equals(postId));
        save();
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
                if (post.imagePath != null) {
                    o.addProperty("image", post.imagePath);
                }
                o.addProperty("isJustNow", post.isJustNow);
                o.addProperty("isLate", post.isLate);
                o.addProperty("coins", post.coinsEarned);
                o.addProperty("streak", post.streakDay);
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
