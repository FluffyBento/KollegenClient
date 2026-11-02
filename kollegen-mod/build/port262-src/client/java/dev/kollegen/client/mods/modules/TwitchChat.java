package dev.kollegen.client.mods.modules;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kollegen.client.mods.BooleanSetting;
import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.HudModule;
import dev.kollegen.client.mods.ModeSetting;
import dev.kollegen.client.mods.Palette;
import dev.kollegen.client.mods.SliderSetting;
import dev.kollegen.client.mods.StringSetting;
import dev.kollegen.client.ui.Glass;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.chat.contents.objects.AtlasSprite;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TwitchChat extends HudModule {
    public static TwitchChat INSTANCE;

    public static class ChatMessage {
        public final String user;
        public final int color;
        public final String text;
        public final long time;
        public final boolean has7tv;
        public final List<TwitchBadges.Badge> badges;

        public ChatMessage(String user, int color, String text, long time, boolean has7tv, List<TwitchBadges.Badge> badges) {
            this.user = user;
            this.color = color;
            this.text = text;
            this.time = time;
            this.has7tv = has7tv;
            this.badges = badges;
        }
    }

    public final StringSetting channel = new StringSetting("Kanal", "Twitch-Kanalname für den Live-Chat.", "");
    public final BooleanSetting showHud = new BooleanSetting("HUD-Anzeige", "Zeigt den Twitch-Chat als schwebendes Element auf dem Bildschirm an.", false);
    public final BooleanSetting echoIngame = new BooleanSetting("Im Spielchat", "Sendet Twitch-Nachrichten direkt in den Minecraft-Chat.", true);
    public final BooleanSetting show7tv = new BooleanSetting("7TV Emotes", "Erkennt 7TV-Emotes und hebt sie im Chat und HUD hervor.", true);
    public final ModeSetting chatFilter = new ModeSetting("Chat-Filter", "Filtert Nachrichten im Minecraft-Chat.", new String[]{"Alle Nachrichten", "Nur Twitch-Chat", "Twitch + System/Befehle"}, 0);
    public final SliderSetting maxLines = new SliderSetting("Max. Zeilen", "Anzahl sichtbarer Chatzeilen im HUD.", 8.0, 3.0, 25.0, 1.0);
    public final SliderSetting fadeTime = new SliderSetting("Anzeigedauer", "Dauer in Sekunden bis alte Nachrichten ausblenden (0 = nie).", 30.0, 0.0, 120.0, 5.0,
            v -> v == 0 ? "Immer sichtbar" : Math.round(v) + "s", null);
    public final BooleanSetting keepVisible = new BooleanSetting("Dauerhaft anzeigen", "Blendet Twitch-Nachrichten im Minecraft-Chat nicht nach einiger Zeit aus.", false);
    public final BooleanSetting keepOnServerSwitch = new BooleanSetting("Serverwechsel", "Behält Twitch-Nachrichten beim Wechsel des Servers im Chat.", false);

    private final List<ChatMessage> messages = new CopyOnWriteArrayList<>();
    private final Set<String> emotes7tv = ConcurrentHashMap.newKeySet();
    private WebSocket webSocket = null;
    private String activeChannel = "";
    private String currentRoomId = "";
    private long lastConnectAttempt = 0;
    private boolean globalEmotesLoaded = false;
    private final Set<String> loadedChannelEmotes = ConcurrentHashMap.newKeySet();

    public TwitchChat() {
        super("twitchchat", "Twitch Chat", "Zeigt Live-Twitch-Chat mit 7TV-Unterstützung im Spielchat oder HUD an.");
        INSTANCE = this;
        add(channel);
        add(showHud);
        add(echoIngame);
        add(show7tv);
        add(chatFilter);
        add(maxLines);
        add(fadeTime);
        add(keepVisible);
        add(keepOnServerSwitch);

        String[] popular = new String[]{
                "catJAM", "OMEGALUL", "Pog", "KEKW", "PepeHands", "monkaW", "EZ", "Clap",
                "AYAYA", "monkaS", "FeelsBadMan", "FeelsGoodMan", "Sadge", "pepeJAM", "WideHardo",
                "LUL", "ResidentSleeper", "PogChamp", "BibleThump", "Kappa", "NotLikeThis", "WutFace", "HeyGuys"
        };
        Collections.addAll(emotes7tv, popular);

        TwitchBadges.init();
        loadDefaultChannel();
        loadGlobal7tvEmotes();
        if (!channel.value.trim().isEmpty()) {
            resolveAndLoadChannel7tv(channel.value.trim().toLowerCase());
        }
    }

    public void addEmoteName(String name) {
        if (name != null && !name.isEmpty()) {
            emotes7tv.add(name);
        }
    }

    public static boolean isFilterActive() {
        return INSTANCE != null && INSTANCE.enabled && INSTANCE.chatFilter.index > 0;
    }

    public static int getFilterMode() {
        if (INSTANCE == null || !INSTANCE.enabled) return 0;
        return INSTANCE.chatFilter.index;
    }

    public static boolean is7tvActive() {
        return INSTANCE != null && INSTANCE.enabled && INSTANCE.show7tv.value;
    }

    public static boolean isKeepVisible() {
        return INSTANCE != null && INSTANCE.enabled && INSTANCE.keepVisible.value;
    }

    public static boolean isKeepOnServerSwitch() {
        return INSTANCE != null && INSTANCE.enabled && INSTANCE.keepOnServerSwitch.value;
    }

    public static boolean isTwitchMessage(Component component) {
        if (component == null) return false;
        try {
            return component.getString().startsWith("[Twitch] ");
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isEmote(String word) {
        if (INSTANCE == null || !INSTANCE.enabled || !INSTANCE.show7tv.value) return false;
        return SevenTvEmoteManager.isEmote(word) || INSTANCE.emotes7tv.contains(word);
    }

    public static boolean containsEmote(String text) {
        if (text == null || text.isEmpty() || INSTANCE == null || !INSTANCE.enabled || !INSTANCE.show7tv.value) {
            return false;
        }
        return SevenTvEmoteManager.containsEmote(text);
    }

    public static String format7tv(String text) {
        if (text == null || text.isEmpty()) return text;
        if (INSTANCE == null || !INSTANCE.enabled || !INSTANCE.show7tv.value) {
            return text;
        }
        String[] words = text.split(" ");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) sb.append(" ");
            String w = words[i];
            if (isEmote(w)) {
                sb.append("§d§l[").append(w).append("]§r");
            } else {
                sb.append(w);
            }
        }
        return sb.toString();
    }

    public static MutableComponent formatTextToComponent(String text, Style baseStyle) {
        if (text == null || text.isEmpty()) return Component.empty();
        if (!is7tvActive() || !containsEmote(text)) {
            return Component.literal(text).withStyle(baseStyle);
        }
        MutableComponent root = Component.empty();
        Matcher m = Pattern.compile("\\S+|\\s+").matcher(text);
        while (m.find()) {
            String token = m.group();
            if (Character.isWhitespace(token.charAt(0))) {
                root.append(Component.literal(token).withStyle(baseStyle));
            } else {
                String clean = token;
                String punct = "";
                int end = token.length();
                while (end > 0 && ",.!?~:;".indexOf(token.charAt(end - 1)) >= 0) {
                    end--;
                }
                if (end > 0 && end < token.length()) {
                    clean = token.substring(0, end);
                    punct = token.substring(end);
                }
                final String emoteName = clean;
                if (isEmote(emoteName)) {
                    SevenTvEmoteManager.ensureLoaded(emoteName);
                    String key = SevenTvEmoteManager.getSafeKey(emoteName);
                    Identifier atlasId = Identifier.tryParse("kollegen:7tv");
                    Identifier spriteId = Identifier.tryParse("kollegen:" + key);
                    Component fallback = Component.literal("[" + emoteName + "]");
                    MutableComponent emote = Component.object(new AtlasSprite(atlasId, spriteId), fallback);
                    emote.withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(Component.literal(emoteName))));
                    root.append(emote);
                    if (!punct.isEmpty()) {
                        root.append(Component.literal(punct).withStyle(baseStyle));
                    }
                } else {
                    root.append(Component.literal(token).withStyle(baseStyle));
                }
            }
        }
        return root;
    }

    public static Component formatComponent(Component message) {
        if (message == null) return null;
        if (INSTANCE == null || !INSTANCE.enabled || !INSTANCE.show7tv.value) {
            return message;
        }
        if (message.getContents() instanceof TranslatableContents tc) {
            Object[] args = tc.getArgs();
            Object[] copy = args.clone();
            boolean changed = false;
            for (int i = 0; i < copy.length; i++) {
                if (copy[i] instanceof Component c) {
                    Component fc = formatComponent(c);
                    if (fc != c) {
                        copy[i] = fc;
                        changed = true;
                    }
                } else if (copy[i] instanceof String s) {
                    if (containsEmote(s)) {
                        copy[i] = formatTextToComponent(s, Style.EMPTY);
                        changed = true;
                    }
                }
            }
            if (changed) {
                MutableComponent res = Component.translatable(tc.getKey(), copy).withStyle(message.getStyle());
                for (Component sibling : message.getSiblings()) {
                    res.append(formatComponent(sibling));
                }
                return res;
            }
        } else if (message.getContents() instanceof PlainTextContents ptc) {
            String str = ptc.text();
            if (containsEmote(str)) {
                MutableComponent res = formatTextToComponent(str, message.getStyle());
                for (Component sibling : message.getSiblings()) {
                    res.append(formatComponent(sibling));
                }
                return res;
            }
        } else {
            String str = message.getString();
            if (containsEmote(str)) {
                MutableComponent res = formatTextToComponent(str, message.getStyle());
                for (Component sibling : message.getSiblings()) {
                    res.append(formatComponent(sibling));
                }
                return res;
            }
        }
        if (!message.getSiblings().isEmpty()) {
            boolean anyChanged = false;
            MutableComponent res = message.plainCopy();
            for (Component sibling : message.getSiblings()) {
                Component fs = formatComponent(sibling);
                if (fs != sibling) anyChanged = true;
                res.append(fs);
            }
            if (anyChanged) return res.withStyle(message.getStyle());
        }
        return message;
    }

    private void loadDefaultChannel() {
        try {
            Path[] candidates = new Path[]{
                    FabricLoader.getInstance().getConfigDir().resolve("twitch_account.json"),
                    FabricLoader.getInstance().getGameDir().getParent().getParent().resolve("twitch_account.json")
            };
            for (Path p : candidates) {
                if (Files.isRegularFile(p)) {
                    String json = Files.readString(p);
                    JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                    if (obj.has("connected") && obj.get("connected").getAsBoolean()) {
                        String user = obj.has("username") ? obj.get("username").getAsString() : "";
                        if (!user.isEmpty()) {
                            if (channel.value.isEmpty()) {
                                channel.value = user;
                            }
                            this.enabled = true;
                            break;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void onEnable() {
        loadDefaultChannel();
        connect();
        loadGlobal7tvEmotes();
        if (!channel.value.trim().isEmpty()) {
            resolveAndLoadChannel7tv(channel.value.trim().toLowerCase());
        }
    }

    @Override
    public void onTick() {
        if (!enabled) return;
        String cur = channel.value.trim().toLowerCase();
        if (cur.isEmpty()) {
            loadDefaultChannel();
            cur = channel.value.trim().toLowerCase();
        }
        if (!cur.isEmpty() && (!cur.equals(activeChannel) || webSocket == null)) {
            long now = System.currentTimeMillis();
            if (now - lastConnectAttempt > 6000) {
                connect();
            }
        }
    }

    @Override
    public void onDisable() {
        disconnect();
    }

    private synchronized void disconnect() {
        if (webSocket != null) {
            try {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
            } catch (Throwable ignored) {}
            webSocket = null;
        }
        activeChannel = "";
        currentRoomId = "";
    }

    private synchronized void connect() {
        lastConnectAttempt = System.currentTimeMillis();
        String target = channel.value.trim().toLowerCase();
        if (target.isEmpty()) return;

        disconnect();
        activeChannel = target;
        loadGlobal7tvEmotes();
        resolveAndLoadChannel7tv(target);

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(7))
                    .build();

            client.newWebSocketBuilder()
                    .buildAsync(URI.create("wss://irc-ws.chat.twitch.tv:443"), new WebSocket.Listener() {
                        private final StringBuilder buffer = new StringBuilder();

                        @Override
                        public void onOpen(WebSocket ws) {
                            ws.sendText("CAP REQ :twitch.tv/tags twitch.tv/commands", true);
                            ws.sendText("PASS oauth:justinfan" + (10000 + new Random().nextInt(89999)), true);
                            ws.sendText("NICK justinfan" + (10000 + new Random().nextInt(89999)), true);
                            ws.sendText("JOIN #" + target, true);
                            ws.request(1);
                        }

                        @Override
                        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                            buffer.append(data);
                            if (last) {
                                String text = buffer.toString();
                                buffer.setLength(0);
                                handleIrcMessages(ws, text);
                            }
                            ws.request(1);
                            return null;
                        }

                        @Override
                        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
                            webSocket = null;
                            return null;
                        }

                        @Override
                        public void onError(WebSocket ws, Throwable error) {
                            webSocket = null;
                        }
                    })
                    .thenAccept(ws -> webSocket = ws)
                    .exceptionally(ex -> {
                        webSocket = null;
                        return null;
                    });
        } catch (Throwable ignored) {}
    }

    private void handleIrcMessages(WebSocket ws, String raw) {
        String[] lines = raw.split("\r\n|\n");
        for (String line : lines) {
            if (line.isEmpty()) continue;
            if (line.startsWith("PING")) {
                ws.sendText("PONG :tmi.twitch.tv", true);
                continue;
            }
            if (line.contains("ROOMSTATE") && line.contains("room-id=")) {
                extractRoomId(line);
            }
            if (line.contains("PRIVMSG #")) {
                parsePrivMsg(line);
            }
        }
    }

    private void extractRoomId(String line) {
        int idx = line.indexOf("room-id=");
        if (idx >= 0) {
            int end = line.indexOf(';', idx);
            if (end < 0) end = line.indexOf(' ', idx);
            if (end > idx) {
                String rId = line.substring(idx + 8, end);
                if (!rId.isEmpty() && !rId.equals(currentRoomId)) {
                    currentRoomId = rId;
                    TwitchBadges.setChannel(rId);
                    fetch7tvByTwitchId(rId);
                }
            }
        }
    }

    private void parsePrivMsg(String line) {
        try {
            extractRoomId(line);

            String tags = "";
            String messagePart = line;
            if (line.startsWith("@")) {
                int space = line.indexOf(' ');
                if (space > 0) {
                    tags = line.substring(1, space);
                    messagePart = line.substring(space + 1);
                }
            }

            int privIdx = messagePart.indexOf("PRIVMSG #");
            if (privIdx < 0) return;
            int colonIdx = messagePart.indexOf(" :", privIdx);
            if (colonIdx < 0) return;
            String text = messagePart.substring(colonIdx + 2).trim();

            String displayName = "";
            String badgesTag = "";
            int color = 0;

            if (!tags.isEmpty()) {
                String[] tagParts = tags.split(";");
                for (String t : tagParts) {
                    if (t.startsWith("display-name=")) {
                        displayName = t.substring(13);
                    } else if (t.startsWith("color=") && t.length() > 7) {
                        try {
                            color = Integer.parseInt(t.substring(7), 16) | 0xFF000000;
                        } catch (Throwable ignored) {}
                    } else if (t.startsWith("badges=")) {
                        badgesTag = t.substring(7);
                    }
                }
            }

            if (displayName.isEmpty()) {
                int bang = messagePart.indexOf('!');
                if (bang > 1 && messagePart.startsWith(":")) {
                    displayName = messagePart.substring(1, bang);
                } else {
                    displayName = "Chatter";
                }
            }

            if (color == 0) {
                color = TwitchBadges.defaultColor(displayName);
            }

            List<TwitchBadges.Badge> badges = TwitchBadges.parse(badgesTag);

            boolean has7 = false;
            if (show7tv.value) {
                has7 = containsEmote(text);
            }

            ChatMessage msg = new ChatMessage(displayName, color, text, System.currentTimeMillis(), has7, badges);
            messages.add(msg);
            while (messages.size() > 50) {
                messages.remove(0);
            }

            final int userColor = color;
            if (echoIngame.value && mc != null && mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
                try {
                    MutableComponent msgComp = Component.empty();
                    msgComp.append(TwitchBadges.logo());
                    msgComp.append(Component.literal(" "));
                    for (TwitchBadges.Badge badge : badges) {
                        msgComp.append(TwitchBadges.component(badge));
                    }
                    msgComp.append(Component.literal(displayName).withStyle(s -> s.withColor(userColor).withBold(true)));
                    msgComp.append(Component.literal(": "));
                    msgComp.append(formatTextToComponent(text, Style.EMPTY.withColor(0xFFFFFF)));
                    mc.gui.hud.getChat().addClientSystemMessage(msgComp);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    private void loadGlobal7tvEmotes() {
        if (globalEmotesLoaded) return;
        globalEmotesLoaded = true;
        CompletableFuture.runAsync(() -> {
            try {
                HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("https://7tv.io/v3/emote-sets/global"))
                        .timeout(Duration.ofSeconds(6))
                        .GET()
                        .build();
                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
                    if (root.has("emotes") && root.get("emotes").isJsonArray()) {
                        JsonArray arr = root.getAsJsonArray("emotes");
                        SevenTvEmoteManager.parseAndRegisterEmotes(arr);
                        for (JsonElement el : arr) {
                            if (el.isJsonObject() && el.getAsJsonObject().has("name")) {
                                emotes7tv.add(el.getAsJsonObject().get("name").getAsString());
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
        });
    }

    private void resolveAndLoadChannel7tv(String chan) {
        if (chan.isEmpty() || loadedChannelEmotes.contains(chan)) return;
        CompletableFuture.runAsync(() -> {
            try {
                String uid = null;
                try {
                    HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create("https://decapi.me/twitch/id/" + chan))
                            .timeout(Duration.ofSeconds(4))
                            .GET()
                            .build();
                    HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() == 200) {
                        String body = resp.body().trim();
                        if (body.matches("\\d+")) {
                            uid = body;
                        }
                    }
                } catch (Throwable ignored) {}

                if (uid == null) {
                    HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create("https://api.ivr.fi/v2/twitch/user?login=" + chan))
                            .timeout(Duration.ofSeconds(4))
                            .GET()
                            .build();
                    HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() == 200) {
                        JsonArray arr = JsonParser.parseString(resp.body()).getAsJsonArray();
                        if (!arr.isEmpty() && arr.get(0).isJsonObject()) {
                            JsonObject obj = arr.get(0).getAsJsonObject();
                            if (obj.has("id")) {
                                uid = obj.get("id").getAsString();
                            }
                        }
                    }
                }

                if (uid != null && !uid.isEmpty()) {
                    fetch7tvByTwitchId(uid);
                    loadedChannelEmotes.add(chan);
                }
            } catch (Throwable ignored) {}
        });
    }

    private void fetch7tvByTwitchId(String twitchId) {
        if (twitchId == null || twitchId.isEmpty()) return;
        CompletableFuture.runAsync(() -> {
            try {
                HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("https://7tv.io/v3/users/twitch/" + twitchId))
                        .timeout(Duration.ofSeconds(6))
                        .GET()
                        .build();
                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
                    if (root.has("emote_set") && root.get("emote_set").isJsonObject()) {
                        JsonObject set = root.getAsJsonObject("emote_set");
                        if (set.has("emotes") && set.get("emotes").isJsonArray()) {
                            JsonArray arr = set.getAsJsonArray("emotes");
                            SevenTvEmoteManager.parseAndRegisterEmotes(arr);
                            for (JsonElement el : arr) {
                                if (el.isJsonObject() && el.getAsJsonObject().has("name")) {
                                    emotes7tv.add(el.getAsJsonObject().get("name").getAsString());
                                }
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
        });
    }

    @Override
    public void onRenderHud(GuiGraphicsExtractor g, float td) {
        if (!enabled || (!showHud.value && !editMode) || mc == null || mc.font == null) return;

        long now = System.currentTimeMillis();
        long fadeMs = (long) (fadeTime.value * 1000.0);
        int max = (int) Math.round(maxLines.value);

        List<ChatMessage> visible = new ArrayList<>();
        for (ChatMessage m : messages) {
            if (fadeMs == 0 || (now - m.time) < fadeMs) {
                visible.add(m);
            }
        }

        if (visible.isEmpty()) {
            if (editMode) {
                visible.add(new ChatMessage("Twitch", Palette.ACCENT, "Warte auf Chat-Nachrichten...", now, true, List.of()));
            } else {
                return;
            }
        }

        if (visible.size() > max) {
            visible = visible.subList(visible.size() - max, visible.size());
        }

        int pad = 8;
        int lineH = 13;
        int maxW = 160;

        String header = "Twitch #" + (activeChannel.isEmpty() ? channel.value : activeChannel);
        maxW = Math.max(maxW, mc.font.width(header) + 20);

        for (ChatMessage m : visible) {
            int lineW = SevenTvEmoteManager.getTextureByKey(TwitchBadges.LOGO_KEY) != null ? 12 : 0;
            for (TwitchBadges.Badge badge : m.badges) {
                lineW += TwitchBadges.texture(badge) != null ? 10 : mc.font.width(badge.shortLabel()) + 7;
            }
            lineW += mc.font.width(m.user + ": ");
            String[] words = m.text.split(" ");
            for (String word : words) {
                if (show7tv.value && isEmote(word)) {
                    SevenTvEmoteManager.EmoteData data = SevenTvEmoteManager.getEmote(word);
                    int eh = 12;
                    int ew = (data != null && data.height > 0) ? Math.max(9, Math.min(32, Math.round((float) eh * (float) data.width / (float) data.height))) : 12;
                    lineW += ew + 3;
                } else {
                    lineW += mc.font.width(word) + mc.font.width(" ");
                }
            }
            maxW = Math.max(maxW, lineW + 16);
        }

        int w = Math.min(380, maxW + pad * 2);
        int h = pad * 2 + 14 + visible.size() * lineH;

        float s = (float) this.scale.value;
        int sw = Math.round(w * s);
        int sh = Math.round(h * s);
        int[] a = this.anchor(this.mc.getWindow().getGuiScaledWidth(), this.mc.getWindow().getGuiScaledHeight(), sw, sh);
        this.markBounds(a[0], a[1], sw, sh);

        Matrix3x2fStack p = g.pose();
        p.pushMatrix();
        p.translate((float) a[0], (float) a[1]);
        p.scale(s, s);

        if (this.background.value) {
            this.card(g, 0.0f, 0.0f, w, h);
        }

        Glass.text(g, mc.font, header, pad, pad, 0xFF9146FF, true);
        if (webSocket != null) {
            Glass.rr(g, w - pad - 6, pad + 2, 6, 6, 3, 0xFF00FF7F);
        } else {
            Glass.rr(g, w - pad - 6, pad + 2, 6, 6, 3, 0xFF888888);
        }

        int yy = pad + 15;
        for (ChatMessage m : visible) {
            int bx = pad;
            Identifier logoTex = SevenTvEmoteManager.getTextureByKey(TwitchBadges.LOGO_KEY);
            if (logoTex != null) {
                g.blit(logoTex, bx, yy - 2, bx + 10, yy + 8, 0.0f, 1.0f, 0.0f, 1.0f);
                bx += 12;
            }
            for (TwitchBadges.Badge badge : m.badges) {
                Identifier badgeTex = TwitchBadges.texture(badge);
                if (badgeTex != null) {
                    g.blit(badgeTex, bx, yy - 1, bx + 9, yy + 8, 0.0f, 1.0f, 0.0f, 1.0f);
                    bx += 10;
                } else {
                    String badgeLabel = badge.shortLabel();
                    int badgeTextW = mc.font.width(badgeLabel);
                    int badgeW = badgeTextW + 5;
                    Glass.fillRound(g, bx, yy - 1, badgeW, 10, 2, 0x40000000);
                    Glass.text(g, mc.font, badgeLabel, bx + 2, yy, badge.color(), true);
                    bx += badgeW + 2;
                }
            }
            int nameW = mc.font.width(m.user + ": ");
            Glass.text(g, mc.font, m.user + ":", bx, yy, m.color, true);

            int cx = bx + nameW;
            String[] words = m.text.split(" ");
            for (int i = 0; i < words.length; i++) {
                String word = words[i];
                if (show7tv.value && isEmote(word)) {
                    Identifier texId = SevenTvEmoteManager.getTexture(word);
                    SevenTvEmoteManager.EmoteData data = SevenTvEmoteManager.getEmote(word);
                    int eh = 12;
                    int ew = (data != null && data.height > 0) ? Math.max(9, Math.min(32, Math.round((float) eh * (float) data.width / (float) data.height))) : 12;
                    if (texId != null) {
                        g.blit(texId, cx, yy - 2, cx + ew, yy - 2 + eh, 0.0f, 1.0f, 0.0f, 1.0f);
                        cx += ew + 3;
                    } else {
                        SevenTvEmoteManager.ensureLoaded(word);
                        int ewText = mc.font.width(word);
                        Glass.fillRound(g, cx, yy - 1, ewText + 6, 11, 3, 0x409146FF);
                        Glass.text(g, mc.font, word, cx + 3, yy, 0xFF00FFFF, true);
                        cx += ewText + 9;
                    }
                } else {
                    Glass.text(g, mc.font, word, cx, yy, 0xFFFFFFFF, true);
                    cx += mc.font.width(word) + mc.font.width(" ");
                }
            }
            yy += lineH;
        }

        p.popMatrix();
    }
}
