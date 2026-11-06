package dev.kollegen.client.account;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.UserApiService;
import dev.kollegen.client.mixin.MinecraftAccessor;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class AccountManager {

    public static final String DEFAULT_CLIENT_ID = "c36a9fb6-4f2a-41ff-90bd-ae7cc92031eb";

    public static class Account {
        public String username = "Spieler";
        public String uuid = "0";
        public String accessToken = "";
        public String refreshToken = null;
        public Long expiresAt = null;
        public String avatarId = null;
        public String xuid = null;
        public String clientId = DEFAULT_CLIENT_ID;
        public boolean isOffline = false;

        public boolean isExpired() {
            if (isOffline) return false;
            if (accessToken == null || accessToken.isEmpty()) return true;
            if (expiresAt == null) return false;
            return (System.currentTimeMillis() / 1000L) >= (expiresAt - 60L);
        }

        public UUID getParsedUuid() {
            try {
                String clean = uuid != null ? uuid.replace("-", "").trim() : "";
                if (clean.length() == 32) {
                    return UUID.fromString(clean.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
                }
                if (uuid != null && !uuid.isEmpty()) {
                    return UUID.fromString(uuid);
                }
            } catch (Throwable ignored) {
            }
            return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
        }

        public GameProfile toGameProfile() {
            return new GameProfile(getParsedUuid(), username);
        }
    }

    public static class DeviceCodeSession {
        public String userCode;
        public String verificationUri;
        public String deviceCode;
        public int interval = 5;
        public int expiresIn = 900;
        public String clientId = DEFAULT_CLIENT_ID;
        public volatile boolean finished = false;
        public volatile boolean cancelled = false;
        public volatile String error = null;
    }

    private static final List<Account> ACCOUNTS = new ArrayList<>();
    private static Path accountsFilePath = null;
    private static boolean loaded = false;

    private AccountManager() {
    }

    public static synchronized List<Account> getAccounts() {
        if (!loaded) {
            loadAccounts();
        }
        return new ArrayList<>(ACCOUNTS);
    }

    public static synchronized String getActiveUsername() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getUser() != null) {
            String name = mc.getUser().getName();
            if (name != null && !name.isEmpty()) return name;
        }
        if (!ACCOUNTS.isEmpty()) {
            return ACCOUNTS.get(0).username;
        }
        return "Spieler";
    }

    public static synchronized GameProfile getActiveProfile() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getUser() != null) {
            return new GameProfile(mc.getUser().getProfileId(), mc.getUser().getName());
        }
        if (!ACCOUNTS.isEmpty()) {
            return ACCOUNTS.get(0).toGameProfile();
        }
        return new GameProfile(UUID.randomUUID(), "Spieler");
    }

    public static synchronized boolean isActive(Account acc) {
        if (acc == null) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getUser() != null) {
            if (acc.username.equalsIgnoreCase(mc.getUser().getName())) return true;
            if (acc.getParsedUuid().equals(mc.getUser().getProfileId())) return true;
        }
        return !ACCOUNTS.isEmpty() && ACCOUNTS.get(0) == acc;
    }

    public static Path findAccountsPath() {
        if (accountsFilePath != null) return accountsFilePath;
        List<Path> candidates = new ArrayList<>();
        try {
            Path gameDir = FabricLoader.getInstance().getGameDir();
            if (gameDir != null) {
                if (gameDir.getParent() != null && gameDir.getParent().getParent() != null) {
                    candidates.add(gameDir.getParent().getParent().resolve("accounts.json"));
                }
                candidates.add(gameDir.resolve("accounts.json"));
                candidates.add(gameDir.resolve("config").resolve("accounts.json"));
            }
        } catch (Throwable ignored) {
        }
        String home = System.getProperty("user.home", "");
        if (!home.isEmpty()) {
            candidates.add(Path.of(home, ".local", "share", "kollegenclient", "accounts.json"));
            candidates.add(Path.of(home, "AppData", "Roaming", "kollegenclient", "accounts.json"));
            candidates.add(Path.of(home, "AppData", "Local", "kollegenclient", "accounts.json"));
        }
        for (Path p : candidates) {
            if (Files.isRegularFile(p)) {
                accountsFilePath = p;
                return p;
            }
        }
        if (!candidates.isEmpty()) {
            accountsFilePath = candidates.get(0);
            return accountsFilePath;
        }
        accountsFilePath = Path.of("accounts.json");
        return accountsFilePath;
    }

    public static synchronized void loadAccounts() {
        ACCOUNTS.clear();
        loaded = true;
        Path p = findAccountsPath();
        if (p != null && Files.isRegularFile(p)) {
            try {
                String content = Files.readString(p, StandardCharsets.UTF_8);
                JsonElement elem = JsonParser.parseString(content);
                if (elem.isJsonArray()) {
                    JsonArray arr = elem.getAsJsonArray();
                    for (JsonElement item : arr) {
                        if (item.isJsonObject()) {
                            JsonObject o = item.getAsJsonObject();
                            Account a = new Account();
                            if (o.has("username")) a.username = o.get("username").getAsString();
                            if (o.has("uuid")) a.uuid = o.get("uuid").getAsString();
                            if (o.has("access_token")) a.accessToken = o.get("access_token").getAsString();
                            if (o.has("refresh_token") && !o.get("refresh_token").isJsonNull()) a.refreshToken = o.get("refresh_token").getAsString();
                            if (o.has("expires_at") && !o.get("expires_at").isJsonNull()) a.expiresAt = o.get("expires_at").getAsLong();
                            if (o.has("avatar_id") && !o.get("avatar_id").isJsonNull()) a.avatarId = o.get("avatar_id").getAsString();
                            if (o.has("xuid") && !o.get("xuid").isJsonNull()) a.xuid = o.get("xuid").getAsString();
                            if (o.has("client_id") && !o.get("client_id").isJsonNull()) a.clientId = o.get("client_id").getAsString();
                            if (o.has("offline")) a.isOffline = o.get("offline").getAsBoolean();
                            else if (a.refreshToken == null && (a.accessToken == null || a.accessToken.isEmpty())) a.isOffline = true;
                            ACCOUNTS.add(a);
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getUser() != null) {
            String curName = mc.getUser().getName();
            UUID curUuid = mc.getUser().getProfileId();
            boolean found = false;
            for (Account a : ACCOUNTS) {
                if (a.username.equalsIgnoreCase(curName) || a.getParsedUuid().equals(curUuid)) {
                    found = true;
                    break;
                }
            }
            if (!found && curName != null && !curName.isEmpty()) {
                Account cur = new Account();
                cur.username = curName;
                cur.uuid = curUuid != null ? curUuid.toString().replace("-", "") : "0";
                cur.accessToken = mc.getUser().getAccessToken();
                cur.avatarId = cur.uuid;
                cur.isOffline = cur.accessToken == null || cur.accessToken.isEmpty();
                ACCOUNTS.add(0, cur);
                saveAccounts();
            }
        }
    }

    public static synchronized void saveAccounts() {
        Path p = findAccountsPath();
        if (p == null) return;
        try {
            if (p.getParent() != null) {
                Files.createDirectories(p.getParent());
            }
            JsonArray arr = new JsonArray();
            for (Account a : ACCOUNTS) {
                JsonObject o = new JsonObject();
                o.addProperty("username", a.username);
                o.addProperty("uuid", a.uuid);
                o.addProperty("access_token", a.accessToken != null ? a.accessToken : "");
                if (a.refreshToken != null) o.addProperty("refresh_token", a.refreshToken);
                if (a.expiresAt != null) o.addProperty("expires_at", a.expiresAt);
                if (a.avatarId != null) o.addProperty("avatar_id", a.avatarId);
                if (a.xuid != null) o.addProperty("xuid", a.xuid);
                if (a.clientId != null) o.addProperty("client_id", a.clientId);
                if (a.isOffline) o.addProperty("offline", true);
                arr.add(o);
            }
            Files.writeString(p, arr.toString(), StandardCharsets.UTF_8);

            try {
                Path instConfig = FabricLoader.getInstance().getConfigDir().resolve("accounts.json");
                if (!instConfig.equals(p)) {
                    Files.writeString(instConfig, arr.toString(), StandardCharsets.UTF_8);
                }
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
    }

    public static CompletableFuture<Boolean> switchAccountAsync(Account target) {
        return CompletableFuture.supplyAsync(() -> {
            if (target.isExpired() && target.refreshToken != null && !target.refreshToken.isEmpty()) {
                boolean refreshed = refreshMicrosoftToken(target);
                if (!refreshed) {
                    target.isOffline = true;
                    target.accessToken = "";
                }
            } else if (target.accessToken == null || target.accessToken.isEmpty()) {
                target.isOffline = true;
            }

            synchronized (AccountManager.class) {
                ACCOUNTS.remove(target);
                ACCOUNTS.add(0, target);
                saveAccounts();
            }

            Minecraft.getInstance().execute(() -> applyUserSession(target));
            return true;
        });
    }

    public static void applyUserSession(Account acc) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        UUID u = acc.getParsedUuid();
        String token = (acc.accessToken != null && !acc.accessToken.isEmpty()) ? acc.accessToken : "0";
        Optional<String> xuidOpt = (acc.xuid != null && !acc.xuid.isEmpty()) ? Optional.of(acc.xuid) : Optional.empty();
        Optional<String> cidOpt = (acc.clientId != null && !acc.clientId.isEmpty()) ? Optional.of(acc.clientId) : Optional.empty();

        User newUser = new User(acc.username, u, token, xuidOpt, cidOpt);
        ((MinecraftAccessor) mc).kollegen$setUser(newUser);

        try {
            UserApiService apiService = ((MinecraftAccessor) mc).kollegen$getUserApiService();
            if (apiService == null || acc.isOffline) apiService = UserApiService.OFFLINE;
            ProfileKeyPairManager kp = ProfileKeyPairManager.create(apiService, newUser, mc.gameDirectory.toPath());
            ((MinecraftAccessor) mc).kollegen$setProfileKeyPairManager(kp);
        } catch (Throwable ignored) {
        }
    }

    public static boolean refreshMicrosoftToken(Account acc) {
        if (acc.refreshToken == null || acc.refreshToken.isEmpty()) return false;
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .build();

            String cid = (acc.clientId != null && !acc.clientId.isEmpty()) ? acc.clientId : DEFAULT_CLIENT_ID;

            String body = "client_id=" + URLEncoder.encode(cid, StandardCharsets.UTF_8)
                    + "&grant_type=refresh_token&refresh_token="
                    + URLEncoder.encode(acc.refreshToken, StandardCharsets.UTF_8);

            HttpRequest req1 = HttpRequest.newBuilder(URI.create("https://login.microsoftonline.com/consumers/oauth2/v2.0/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> res1 = client.send(req1, HttpResponse.BodyHandlers.ofString());
            if (res1.statusCode() != 200) return false;
            JsonObject msa = JsonParser.parseString(res1.body()).getAsJsonObject();
            String msaToken = msa.get("access_token").getAsString();
            if (msa.has("refresh_token")) {
                acc.refreshToken = msa.get("refresh_token").getAsString();
            }

            JsonObject xblBody = new JsonObject();
            JsonObject xblProps = new JsonObject();
            xblProps.addProperty("AuthMethod", "RPS");
            xblProps.addProperty("SiteName", "user.auth.xboxlive.com");
            xblProps.addProperty("RpsTicket", "d=" + msaToken);
            xblBody.add("Properties", xblProps);
            xblBody.addProperty("RelyingParty", "http://auth.xboxlive.com");
            xblBody.addProperty("TokenType", "JWT");

            HttpRequest req2 = HttpRequest.newBuilder(URI.create("https://user.auth.xboxlive.com/user/authenticate"))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(xblBody.toString()))
                    .build();

            HttpResponse<String> res2 = client.send(req2, HttpResponse.BodyHandlers.ofString());
            if (res2.statusCode() != 200) return false;
            JsonObject xbl = JsonParser.parseString(res2.body()).getAsJsonObject();
            String xblToken = xbl.get("Token").getAsString();

            JsonObject xstsBody = new JsonObject();
            JsonObject xstsProps = new JsonObject();
            xstsProps.addProperty("SandboxId", "RETAIL");
            JsonArray ut = new JsonArray();
            ut.add(xblToken);
            xstsProps.add("UserTokens", ut);
            xstsBody.add("Properties", xstsProps);
            xstsBody.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
            xstsBody.addProperty("TokenType", "JWT");

            HttpRequest req3 = HttpRequest.newBuilder(URI.create("https://xsts.auth.xboxlive.com/xsts/authorize"))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(xstsBody.toString()))
                    .build();

            HttpResponse<String> res3 = client.send(req3, HttpResponse.BodyHandlers.ofString());
            if (res3.statusCode() != 200) return false;
            JsonObject xsts = JsonParser.parseString(res3.body()).getAsJsonObject();
            String xstsToken = xsts.get("Token").getAsString();
            String uhs = xsts.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject().get("uhs").getAsString();

            JsonObject mcBody = new JsonObject();
            mcBody.addProperty("identityToken", "XBL3.0 x=" + uhs + ";" + xstsToken);

            HttpRequest req4 = HttpRequest.newBuilder(URI.create("https://api.minecraftservices.com/authentication/login_with_xbox"))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(mcBody.toString()))
                    .build();

            HttpResponse<String> res4 = client.send(req4, HttpResponse.BodyHandlers.ofString());
            if (res4.statusCode() != 200) return false;
            JsonObject mcLogin = JsonParser.parseString(res4.body()).getAsJsonObject();
            String mcToken = mcLogin.get("access_token").getAsString();

            HttpRequest req5 = HttpRequest.newBuilder(URI.create("https://api.minecraftservices.com/minecraft/profile"))
                    .header("Authorization", "Bearer " + mcToken)
                    .timeout(Duration.ofSeconds(20))
                    .GET()
                    .build();

            HttpResponse<String> res5 = client.send(req5, HttpResponse.BodyHandlers.ofString());
            if (res5.statusCode() != 200) return false;
            JsonObject prof = JsonParser.parseString(res5.body()).getAsJsonObject();

            acc.username = prof.get("name").getAsString();
            acc.uuid = prof.get("id").getAsString();
            acc.accessToken = mcToken;
            acc.avatarId = acc.uuid;
            acc.expiresAt = (System.currentTimeMillis() / 1000L) + 24 * 3600L;
            acc.isOffline = false;

            saveAccounts();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static synchronized void addOfflineAccount(String username) {
        if (username == null || username.trim().isEmpty()) return;
        String name = username.trim();
        for (Account a : ACCOUNTS) {
            if (a.username.equalsIgnoreCase(name)) {
                switchAccountAsync(a);
                return;
            }
        }
        Account acc = new Account();
        acc.username = name;
        UUID u = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        acc.uuid = u.toString().replace("-", "");
        acc.accessToken = "";
        acc.avatarId = acc.uuid;
        acc.isOffline = true;
        ACCOUNTS.add(0, acc);
        saveAccounts();
        Minecraft.getInstance().execute(() -> applyUserSession(acc));
    }

    public static synchronized void removeAccount(Account acc) {
        if (acc == null) return;
        ACCOUNTS.remove(acc);
        saveAccounts();
    }

    public static DeviceCodeSession startDeviceCodeAuth() {
        DeviceCodeSession session = new DeviceCodeSession();
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .build();

            String body = "client_id=" + URLEncoder.encode(session.clientId, StandardCharsets.UTF_8)
                    + "&scope=" + URLEncoder.encode("XboxLive.Signin XboxLive.offline_access", StandardCharsets.UTF_8);

            HttpRequest req = HttpRequest.newBuilder(URI.create("https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 200) {
                JsonObject obj = JsonParser.parseString(res.body()).getAsJsonObject();
                session.deviceCode = obj.get("device_code").getAsString();
                session.userCode = obj.get("user_code").getAsString();
                session.verificationUri = obj.get("verification_uri").getAsString();
                if (obj.has("interval")) session.interval = obj.get("interval").getAsInt();
                if (obj.has("expires_in")) session.expiresIn = obj.get("expires_in").getAsInt();
                startDeviceCodePoll(session);
            } else {
                session.error = "Fehler beim Anfordern des Codes (" + res.statusCode() + ")";
            }
        } catch (Throwable t) {
            session.error = "Netzwerkfehler: " + t.getMessage();
        }
        return session;
    }

    private static void startDeviceCodePoll(DeviceCodeSession session) {
        Thread t = new Thread(() -> {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .build();

            long deadline = System.currentTimeMillis() + (session.expiresIn * 1000L);
            String body = "client_id=" + URLEncoder.encode(session.clientId, StandardCharsets.UTF_8)
                    + "&grant_type=" + URLEncoder.encode("urn:ietf:params:oauth:grant-type:device_code", StandardCharsets.UTF_8)
                    + "&device_code=" + URLEncoder.encode(session.deviceCode, StandardCharsets.UTF_8);

            while (!session.cancelled && System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(Math.max(2000, session.interval * 1000L));
                } catch (InterruptedException e) {
                    break;
                }
                if (session.cancelled) break;

                try {
                    HttpRequest req = HttpRequest.newBuilder(URI.create("https://login.microsoftonline.com/consumers/oauth2/v2.0/token"))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .header("Accept", "application/json")
                            .timeout(Duration.ofSeconds(20))
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build();

                    HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
                    JsonObject obj = JsonParser.parseString(res.body()).getAsJsonObject();
                    if (obj.has("error")) {
                        String err = obj.get("error").getAsString();
                        if ("authorization_pending".equals(err)) {
                            continue;
                        }
                        if ("slow_down".equals(err)) {
                            session.interval += 5;
                            continue;
                        }
                        session.error = obj.has("error_description") ? obj.get("error_description").getAsString() : err;
                        break;
                    }

                    if (obj.has("access_token")) {
                        String msaToken = obj.get("access_token").getAsString();
                        String refreshToken = obj.has("refresh_token") ? obj.get("refresh_token").getAsString() : null;

                        Account acc = new Account();
                        acc.clientId = session.clientId;
                        acc.refreshToken = refreshToken;
                        acc.accessToken = msaToken;

                        boolean ok = refreshMicrosoftToken(acc);
                        if (ok) {
                            synchronized (AccountManager.class) {
                                for (int i = 0; i < ACCOUNTS.size(); i++) {
                                    if (ACCOUNTS.get(i).uuid.equalsIgnoreCase(acc.uuid)) {
                                        ACCOUNTS.remove(i);
                                        break;
                                    }
                                }
                                ACCOUNTS.add(0, acc);
                                saveAccounts();
                            }
                            Minecraft.getInstance().execute(() -> applyUserSession(acc));
                            session.finished = true;
                        } else {
                            session.error = "Minecraft-Profil konnte nicht geladen werden.";
                        }
                        break;
                    }
                } catch (Throwable ex) {
                    session.error = "Verbindungsfehler: " + ex.getMessage();
                    break;
                }
            }
            if (!session.finished && session.error == null && !session.cancelled) {
                session.error = "Zeitüberschreitung bei der Anmeldung.";
            }
        }, "kollegen-device-auth");
        t.setDaemon(true);
        t.start();
    }
}
