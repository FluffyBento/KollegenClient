package dev.kollegen.client.feed;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kollegen.client.account.AccountManager;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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
        public String imagePath;
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
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .build();

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
        boolean modified = false;
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
                    if (author.equalsIgnoreCase("Iz4nLeAstra") || author.equalsIgnoreCase("akkuniizu")) {
                        modified = true;
                        continue;
                    }
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
        if (modified) {
            save();
        }
        fetchRemotePosts(null);
    }

    public static void fetchRemotePosts(Runnable onDone) {
        Thread t = new Thread(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("https://kollegen.me/feed"))
                        .timeout(Duration.ofSeconds(6))
                        .GET()
                        .build();
                HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    mergeRemote(resp.body());
                }
            } catch (Throwable ignored) {
            } finally {
                if (onDone != null) onDone.run();
            }
        }, "kollegen-feed-sync");
        t.setDaemon(true);
        t.start();
    }

    private static synchronized void mergeRemote(String body) {
        try {
            JsonArray arr = JsonParser.parseString(body).getAsJsonArray();
            String myName = AccountManager.getActiveUsername();
            String myUuid = AccountManager.getActiveProfile() != null && AccountManager.getActiveProfile().id() != null
                    ? AccountManager.getActiveProfile().id().toString()
                    : "";
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
                boolean liked = false;
                if (o.has("likedBy") && o.get("likedBy").isJsonArray()) {
                    for (JsonElement u : o.getAsJsonArray("likedBy")) {
                        String s = u.getAsString();
                        if (s.equalsIgnoreCase(myName) || (!myUuid.isEmpty() && s.equalsIgnoreCase(myUuid))) {
                            liked = true;
                            break;
                        }
                    }
                }
                List<String> comments = new ArrayList<>();
                if (o.has("comments") && o.get("comments").isJsonArray()) {
                    for (JsonElement c : o.getAsJsonArray("comments")) {
                        comments.add(c.getAsString());
                    }
                }

                FeedPost existing = null;
                for (FeedPost p : POSTS) {
                    if (p.id.equals(id)) {
                        existing = p;
                        break;
                    }
                }
                if (existing != null) {
                    existing.likes = likes;
                    existing.likedByMe = liked;
                    existing.comments.clear();
                    existing.comments.addAll(comments);
                    if (img != null && !img.isEmpty() && (existing.imagePath == null || existing.imagePath.isEmpty())) {
                        existing.imagePath = img;
                    }
                } else {
                    FeedPost p = new FeedPost(id, author, uuid, cap, srv, img, jn, late, coins, streak, time, likes, liked);
                    p.comments.addAll(comments);
                    POSTS.add(p);
                }
            }
            POSTS.sort((a, b) -> Long.compare(b.timestamp, a.timestamp));
            save();

            for (FeedPost p : POSTS) {
                if (p.authorName.equalsIgnoreCase(myName)) {
                    boolean foundOnServer = false;
                    for (JsonElement el : arr) {
                        if (el.isJsonObject() && el.getAsJsonObject().has("id") && el.getAsJsonObject().get("id").getAsString().equals(p.id)) {
                            foundOnServer = true;
                            break;
                        }
                    }
                    if (!foundOnServer) {
                        syncPostToServer(p);
                    }
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
        syncPostToServer(p);
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
        syncPostToServer(p);
        return p;
    }

    private static void syncPostToServer(FeedPost p) {
        Thread t = new Thread(() -> {
            try {
                String imageUrl = p.imagePath;
                if (imageUrl != null && !imageUrl.trim().isEmpty() && !imageUrl.startsWith("http://") && !imageUrl.startsWith("https://")) {
                    File f = new File(imageUrl.trim());
                    if (f.exists() && f.isFile()) {
                        byte[] bytes = Files.readAllBytes(f.toPath());
                        HttpRequest uploadReq = HttpRequest.newBuilder()
                                .uri(URI.create("https://kollegen.me/feed/upload"))
                                .header("Content-Type", imageUrl.toLowerCase().endsWith(".jpg") ? "image/jpeg" : "image/png")
                                .timeout(Duration.ofSeconds(15))
                                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
                                .build();
                        HttpResponse<String> uResp = HTTP_CLIENT.send(uploadReq, HttpResponse.BodyHandlers.ofString());
                        if (uResp.statusCode() == 200) {
                            JsonObject uo = JsonParser.parseString(uResp.body()).getAsJsonObject();
                            if (uo.has("url")) {
                                imageUrl = uo.get("url").getAsString();
                                p.imagePath = imageUrl;
                                save();
                            }
                        }
                    }
                }

                JsonObject o = new JsonObject();
                o.addProperty("id", p.id);
                o.addProperty("author", p.authorName);
                o.addProperty("uuid", p.authorUuid);
                o.addProperty("caption", p.caption);
                o.addProperty("server", p.serverTag);
                if (imageUrl != null) o.addProperty("image", imageUrl);
                o.addProperty("isJustNow", p.isJustNow);
                o.addProperty("isLate", p.isLate);
                o.addProperty("coins", p.coinsEarned);
                o.addProperty("streak", p.streakDay);
                o.addProperty("time", p.timestamp);

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("https://kollegen.me/feed/post"))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(10))
                        .POST(HttpRequest.BodyPublishers.ofString(o.toString()))
                        .build();
                HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.discarding());
            } catch (Throwable ignored) {
            }
        }, "kollegen-feed-publisher");
        t.setDaemon(true);
        t.start();
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
                syncLikeToServer(postId);
                break;
            }
        }
    }

    private static void syncLikeToServer(String postId) {
        Thread t = new Thread(() -> {
            try {
                String myName = AccountManager.getActiveUsername();
                String myUuid = AccountManager.getActiveProfile() != null && AccountManager.getActiveProfile().id() != null
                        ? AccountManager.getActiveProfile().id().toString()
                        : "";
                JsonObject o = new JsonObject();
                o.addProperty("postId", postId);
                o.addProperty("uuid", myUuid);
                o.addProperty("name", myName);
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("https://kollegen.me/feed/like"))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(8))
                        .POST(HttpRequest.BodyPublishers.ofString(o.toString()))
                        .build();
                HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.discarding());
            } catch (Throwable ignored) {
            }
        }, "kollegen-feed-like");
        t.setDaemon(true);
        t.start();
    }

    public static synchronized void deletePost(String postId) {
        ensureLoaded();
        POSTS.removeIf(p -> p.id.equals(postId));
        save();
        syncDeleteToServer(postId);
    }

    private static void syncDeleteToServer(String postId) {
        Thread t = new Thread(() -> {
            try {
                String myName = AccountManager.getActiveUsername();
                String myUuid = AccountManager.getActiveProfile() != null && AccountManager.getActiveProfile().id() != null
                        ? AccountManager.getActiveProfile().id().toString()
                        : "";
                JsonObject o = new JsonObject();
                o.addProperty("postId", postId);
                o.addProperty("uuid", myUuid);
                o.addProperty("author", myName);
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("https://kollegen.me/feed/delete"))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(8))
                        .POST(HttpRequest.BodyPublishers.ofString(o.toString()))
                        .build();
                HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.discarding());
            } catch (Throwable ignored) {
            }
        }, "kollegen-feed-delete");
        t.setDaemon(true);
        t.start();
    }

    public static synchronized void addComment(String postId, String comment) {
        if (comment == null || comment.trim().isEmpty()) return;
        ensureLoaded();
        for (FeedPost p : POSTS) {
            if (p.id.equals(postId)) {
                String author = AccountManager.getActiveUsername();
                p.comments.add(author + ": " + comment.trim());
                save();
                syncCommentToServer(postId, author, comment.trim());
                break;
            }
        }
    }

    private static void syncCommentToServer(String postId, String author, String comment) {
        Thread t = new Thread(() -> {
            try {
                JsonObject o = new JsonObject();
                o.addProperty("postId", postId);
                o.addProperty("author", author);
                o.addProperty("comment", comment);
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("https://kollegen.me/feed/comment"))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(8))
                        .POST(HttpRequest.BodyPublishers.ofString(o.toString()))
                        .build();
                HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.discarding());
            } catch (Throwable ignored) {
            }
        }, "kollegen-feed-comment");
        t.setDaemon(true);
        t.start();
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
