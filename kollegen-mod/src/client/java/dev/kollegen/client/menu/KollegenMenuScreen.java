package dev.kollegen.client.menu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.kollegen.client.Version;
import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.BooleanSetting;
import dev.kollegen.client.mods.HudModule;
import dev.kollegen.client.mods.KeybindSetting;
import dev.kollegen.client.mods.Module;
import dev.kollegen.client.mods.ModuleManager;
import dev.kollegen.client.mods.Palette;
import dev.kollegen.client.mods.Setting;
import dev.kollegen.client.mods.SliderSetting;
import dev.kollegen.client.mods.StringSetting;
import dev.kollegen.client.presence.KollegenPresence;
import dev.kollegen.client.presence.MythicPets;
import dev.kollegen.client.presence.StoreClient;
import dev.kollegen.client.ui.Glass;
import dev.kollegen.client.ui.GlassToggle;
import dev.kollegen.client.ui.Icons;
import dev.kollegen.client.ui.LogoDraw;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class KollegenMenuScreen extends Screen {
    private static final int K_MOD = 0;
    private static final int K_INFO = 1;
    private static final int K_HEAD = 2;
    private static final int K_STORE = 4;
    private static final int K_COS = 5;
    private static final int K_PET_ACTIVE = 6;
    private static final int K_PET = 7;
    private static final int K_TITLE = 8;
    private static final int K_DETAIL = 9;
    private static final int K_SETTING = 10;
    private static final int K_TEXT = 11;
    private static final int K_GAME = 12;
    private static final int K_WARN = 13;
    private static final int GAP = 6;
    private static final int PET_ACTIVE_H = 92;

    private static final Map<String, String> SLOT_LABELS = new HashMap<String, String>();
    private static final String[] SLOT_ORDER = new String[]{"cape", "shield", "mace", "back_accessory", "head_accessory", "face_accessory", "aura", "shoulder_accessory", "torso_accessory", "legs_accessory", "gloves", "banner", "avatar_frame", "avatar_theme", "profile_frame", "profile_bg", "profil_stil", "name_color", "badge", "sticker", "font", "title"};
    private static final String[] PET_COLORS = new String[]{"#ffffff", "#ffd700", "#ff5f56", "#39d7ff", "#c77dff", "#7ee787", "#ff6b9d"};

    static {
        SLOT_LABELS.put("cape", "Umhang");
        SLOT_LABELS.put("shield", "Schild");
        SLOT_LABELS.put("back_accessory", "Rücken");
        SLOT_LABELS.put("head_accessory", "Kopf");
        SLOT_LABELS.put("face_accessory", "Gesicht");
        SLOT_LABELS.put("aura", "Aura");
        SLOT_LABELS.put("shoulder_accessory", "Schulter");
        SLOT_LABELS.put("torso_accessory", "Oberkörper");
        SLOT_LABELS.put("legs_accessory", "Unterkörper");
        SLOT_LABELS.put("mace", "Kriegshammer");
        SLOT_LABELS.put("gloves", "Handschuhe");
        SLOT_LABELS.put("banner", "Banner");
        SLOT_LABELS.put("avatar_frame", "Avatar-Rahmen");
        SLOT_LABELS.put("avatar_theme", "Avatar-Design");
        SLOT_LABELS.put("profile_frame", "Profil-Rahmen");
        SLOT_LABELS.put("profile_bg", "Profil-Hintergrund");
        SLOT_LABELS.put("profil_stil", "Profil-Stil");
        SLOT_LABELS.put("name_color", "Namensfarbe");
        SLOT_LABELS.put("badge", "Abzeichen");
        SLOT_LABELS.put("sticker", "Aufkleber");
        SLOT_LABELS.put("font", "Schriftart");
        SLOT_LABELS.put("title", "Titel");
        SLOT_LABELS.put("pet", "Haustier");
    }

    private static final class Cell {
        int kind;
        Module module;
        Setting setting;
        JsonObject data;
        String text;
        float x;
        float y;
        float w;
        float h;
    }

    private final Screen parent;
    private final Category[] cats = Category.values();
    private int category = 0;
    private Module detail = null;
    private String query = "";
    private EditBox search;
    private EditBox petNameBox;
    private String petNamePetId = "";
    private String petNameValue = "";

    private int px;
    private int py;
    private int pw;
    private int ph;
    private int sbX;
    private int sbY;
    private int sbH;
    private int sideW;
    private int headH;
    private int cx;
    private int cy;
    private int cw;
    private int ch;
    private boolean compact;

    private float scrollT = 0.0f;
    private float scrollV = 0.0f;
    private int builtScroll = Integer.MIN_VALUE;
    private float maxScroll = 0.0f;
    private float contentH = 0.0f;
    private boolean draggingBar = false;
    private float openAnim = 0.0f;
    private float pageAnim = 1.0f;
    // PERF-DEBUG: Frame-Zeitmessung (avg/max über je 300 Frames im MC-Log).
    private long perfAcc = 0L;
    private int perfN = 0;
    private long perfMax = 0L;
    private final Map<String, Float> anims = new HashMap<String, Float>();
    private final List<Cell> cells = new ArrayList<Cell>();

    private float gy;
    private int gcol;
    private float growH;

    private JsonObject storeCache;
    private long storeAt;
    private volatile boolean storeLoading;
    private volatile String storeMsg = "";
    private volatile boolean pendingRebuild;
    // Ausfallsicherung: Cache anzeigen, Fehlertext, Retry-Backoff
    private boolean storeFromCache = false;
    private volatile long storeFailUntil = 0L;
    private volatile int storeFailCount = 0;

    public KollegenMenuScreen(Screen parent) {
        super((Component) Component.literal("Kollegen Client"));
        this.parent = parent;
    }

    protected void init() {
        this.geometry();
        this.createSearch();
        this.relayout();
    }

    private void geometry() {
        this.pw = Math.min(this.width - 12, 640);
        this.ph = Math.min(this.height - 12, 420);
        this.px = (this.width - this.pw) / 2;
        this.py = (this.height - this.ph) / 2;
        this.compact = this.pw < 430;
        this.sideW = this.compact ? 30 : 108;
        this.headH = 34;
        this.sbX = this.px + 9;
        this.sbY = this.py + this.headH + 4;
        this.sbH = this.py + this.ph - 9 - this.sbY;
        this.cx = this.sbX + this.sideW + 8;
        this.cy = this.py + this.headH + 4;
        this.cw = this.px + this.pw - 10 - this.cx;
        this.ch = this.py + this.ph - 9 - this.cy;
    }

    private int searchX() {
        return this.px + 10 + 70;
    }

    private int rightButtonsX() {
        int x = this.px + this.pw - 10 - 22;
        x -= 4 + 22;
        x -= 4 + (this.compact ? 22 : 24 + this.font.width("HUD"));
        return x;
    }

    private void createSearch() {
        int sx = this.searchX();
        int sw = Math.max(50, this.rightButtonsX() - 8 - sx);
        this.search = new EditBox(this.font, sx + 20, this.py + 11, sw - 26, 14, (Component) Component.literal(""));
        this.search.setBordered(false);
        this.search.setMaxLength(40);
        this.search.setHint((Component) Component.literal("Modul suchen"));
        this.search.setTextColor(Palette.TEXT);
        this.search.setValue(this.query);
        this.search.setResponder(o -> {
            String t = (String) o;
            if (!t.equals(this.query)) {
                this.query = t;
                this.detail = null;
                this.scrollT = 0.0f;
                this.scrollV = 0.0f;
                this.relayout();
            }
        });
    }

    private float hv(String key, boolean on, float rate) {
        Float cur = this.anims.get(key);
        float c = cur == null ? (on ? 1.0f : 0.0f) : cur;
        c = Glass.step(c, on ? 1.0f : 0.0f, rate);
        this.anims.put(key, c);
        return c;
    }

    private static String str(JsonObject o, String key, String fallback) {
        try {
            if (o != null && o.has(key) && !o.get(key).isJsonNull()) {
                return o.get(key).getAsString();
            }
        } catch (Throwable t) {
        }
        return fallback;
    }

    private static int num(JsonObject o, String key, int fallback) {
        try {
            if (o != null && o.has(key) && !o.get(key).isJsonNull()) {
                return o.get(key).getAsInt();
            }
        } catch (Throwable t) {
        }
        return fallback;
    }

    private static boolean bool(JsonObject o, String key) {
        try {
            if (o != null && o.has(key) && !o.get(key).isJsonNull()) {
                return o.get(key).getAsBoolean();
            }
        } catch (Throwable t) {
        }
        return false;
    }

    private static int rarityColor(String rarity) {
        if (rarity == null) {
            return Palette.MUTED;
        }
        switch (rarity.toLowerCase()) {
            case "common":
                return 0xFF9AA5B8;
            case "rare":
                return 0xFF54A8FF;
            case "epic":
                return 0xFFB36BFF;
            case "legendary":
                return 0xFFFFB02E;
            default:
                return Palette.MUTED;
        }
    }

    private static String[] petModesFor(String id) {
        if (id == null) {
            return new String[]{"follow"};
        }
        switch (id) {
            case "pet_allay":
                return new String[]{"follow", "hover", "shoulder", "head"};
            case "pet_parrot":
            case "pet_bee":
            case "pet_bat":
            case "pet_squid":
            case "pet_nautilus":
            case "pet_ember_drake":
            case "pet_void_wyrm":
            case "pet_shadow_reaper":
            case "pet_rune_sentinel":
            case "pet_flame_skull":
            case "pet_storm_cloud":
            case "pet_spectre":
            case "pet_lantern_spirit":
            case "pet_bolt_bot":
            case "pet_mini_ufo":
            case "pet_grimoire":
                return new String[]{"follow", "hover"};
            default:
                return new String[]{"follow"};
        }
    }

    private static String modeLabel(String m) {
        if (m == null) {
            return "Folgen";
        }
        switch (m) {
            case "hover":
                return "Schweben";
            case "shoulder":
                return "Schulter";
            case "head":
                return "Kopf";
            default:
                return "Folgen";
        }
    }

    private static int parseHex(String s, int fb) {
        try {
            String h = s.startsWith("#") ? s.substring(1) : s;
            return 0xFF000000 | (int) Long.parseLong(h, 16);
        } catch (Throwable t) {
            return fb;
        }
    }

    private static boolean isStorePage(Category c) {
        return c == Category.STORE || c == Category.COSMETICS || c == Category.PETS;
    }

    private void ensureStore() {
        long now = System.currentTimeMillis();
        if (this.storeCache != null && now - this.storeAt < 20000L) {
            return;
        }
        if (this.storeLoading) {
            return;
        }
        // Backoff nach Fehlschlag: nicht alle 20s erneut versuchen (Server evtl.
        // länger offline). Nach Erfolg wieder normales Intervall.
        if (this.storeFailUntil > now) {
            return;
        }
        if (this.storeCache == null) {
            // Letzte bekannte Daten von Platte laden, damit bei Backend-Ausfall
            // die Cosmetics trotzdem sichtbar bleiben.
            JsonObject cached = StoreClient.cachedCatalog();
            if (cached != null) {
                this.storeCache = cached;
                this.storeFromCache = true;
            }
        }
        this.storeLoading = true;
        Thread t = new Thread(() -> {
            boolean ok = false;
            try {
                JsonObject data = StoreClient.catalog();
                if (data != null) {
                    this.storeCache = data;
                    this.storeAt = System.currentTimeMillis();
                    this.storeFromCache = false;
                    this.storeFailUntil = 0L;
                    ok = true;
                }
            } catch (Throwable ignored) {
            }
            if (!ok) {
                this.storeMsg = StoreClient.lastError() == null ? "Store nicht erreichbar" : StoreClient.lastError();
                // 30s / 60s / 120s (max. 5 min) ausgesetztes Wiederholen
                this.storeFailCount = Math.min(this.storeFailCount + 1, 5);
                this.storeFailUntil = now + (30L << (this.storeFailCount - 1));
            }
            this.storeLoading = false;
            this.pendingRebuild = true;
        }, "kollegen-store-fetch");
        t.setDaemon(true);
        t.start();
    }

    private void runStoreAction(String msg, Supplier<JsonObject> op) {
        this.storeMsg = "Einen Moment ...";
        Thread t = new Thread(() -> {
            try {
                JsonObject r = (JsonObject) op.get();
                this.storeMsg = r == null ? "Fehler: keine Verbindung oder nicht angemeldet (Launcher-Login nötig)." : (r.has("error") && !r.get("error").isJsonNull() ? "Fehler: " + r.get("error").getAsString() : msg);
            } catch (Throwable e) {
                this.storeMsg = "Fehler: " + String.valueOf(e.getMessage());
            }
            try {
                JsonObject data = StoreClient.catalog();
                if (data != null) {
                    this.storeCache = data;
                    this.storeAt = System.currentTimeMillis();
                }
            } catch (Throwable ignored) {
            }
            try {
                KollegenPresence.refreshAsync();
            } catch (Throwable ignored) {
            }
            this.pendingRebuild = true;
        }, "kollegen-store-action");
        t.setDaemon(true);
        t.start();
    }

    private String equippedId(String category) {
        try {
            if (this.storeCache != null && this.storeCache.has("equipped") && this.storeCache.get("equipped").isJsonObject()) {
                JsonObject eq = this.storeCache.getAsJsonObject("equipped");
                if (eq.has(category) && !eq.get(category).isJsonNull()) {
                    return eq.get(category).getAsString();
                }
            }
        } catch (Throwable t) {
        }
        return "";
    }

    private JsonObject petCfg(String petId) {
        try {
            if (this.storeCache != null && this.storeCache.has("petConfigs") && this.storeCache.get("petConfigs").isJsonObject()) {
                JsonObject cfgs = this.storeCache.getAsJsonObject("petConfigs");
                if (cfgs.has(petId) && cfgs.get(petId).isJsonObject()) {
                    return cfgs.getAsJsonObject(petId);
                }
            }
        } catch (Throwable t) {
        }
        return null;
    }

    private String petModeOf(String petId) {
        JsonObject c = this.petCfg(petId);
        return c == null ? "follow" : str(c, "mode", "follow");
    }

    private String petNameOf(String petId) {
        JsonObject c = this.petCfg(petId);
        return c == null ? "" : str(c, "name", "");
    }

    private String petColorOf(String petId) {
        JsonObject c = this.petCfg(petId);
        if (c == null) {
            return "#ffffff";
        }
        String col = str(c, "nameColor", "#ffffff");
        return col.isEmpty() ? "#ffffff" : col;
    }

    private String petVariantOf(String petId) {
        JsonObject c = this.petCfg(petId);
        return c == null ? "" : str(c, "variant", "");
    }

    private List<String[]> petSkinsOf(String petId) {
        ArrayList<String[]> out = new ArrayList<String[]>();
        try {
            if (this.storeCache != null && this.storeCache.has("catalog") && this.storeCache.get("catalog").isJsonArray()) {
                for (JsonElement el : this.storeCache.getAsJsonArray("catalog")) {
                    if (el == null || !el.isJsonObject()) {
                        continue;
                    }
                    JsonObject it = el.getAsJsonObject();
                    if (!"pet_skin".equals(str(it, "category", ""))) {
                        continue;
                    }
                    JsonObject data = it.has("data") && it.get("data").isJsonObject() ? it.getAsJsonObject("data") : null;
                    if (data == null || !petId.equals(str(data, "variantOf", "")) || !bool(it, "owned")) {
                        continue;
                    }
                    out.add(new String[]{str(data, "variant", ""), str(data, "label", str(it, "name", ""))});
                }
            }
        } catch (Throwable t) {
        }
        return out;
    }

    private String petVariantLabel(String petId) {
        String v = this.petVariantOf(petId);
        if (v.isEmpty()) {
            return "Normal";
        }
        for (String[] kv : this.petSkinsOf(petId)) {
            if (kv[0].equals(v)) {
                return kv[1];
            }
        }
        return v;
    }

    private void savePetName(String petId) {
        String name = this.petNamePetId.equals(petId) ? this.petNameValue.trim() : this.petNameOf(petId);
        this.runStoreAction("Name gespeichert", () -> StoreClient.petConfig(petId, null, name.isEmpty() ? null : name, null, null));
    }

    private void cyclePetColor(String petId) {
        String cur = this.petColorOf(petId);
        int idx = 0;
        for (int i = 0; i < PET_COLORS.length; ++i) {
            if (PET_COLORS[i].equalsIgnoreCase(cur)) {
                idx = i;
            }
        }
        String next = PET_COLORS[(idx + 1) % PET_COLORS.length];
        this.runStoreAction("Farbe geändert", () -> StoreClient.petConfig(petId, null, null, next, null));
    }

    private void cyclePetSkin(String petId) {
        List<String[]> skins = this.petSkinsOf(petId);
        String cur = this.petVariantOf(petId);
        int idx = -1;
        for (int i = 0; i < skins.size(); ++i) {
            if (skins.get(i)[0].equals(cur)) {
                idx = i;
            }
        }
        int next = (idx + 1) % (skins.size() + 1);
        String variant = next == 0 ? null : skins.get(next - 1)[0];
        this.runStoreAction("Skin geändert", () -> StoreClient.petConfig(petId, null, null, null, variant));
    }

    private void cyclePetMode(String petId) {
        String[] modes = petModesFor(petId);
        String cur = this.petModeOf(petId);
        int idx = 0;
        for (int i = 0; i < modes.length; ++i) {
            if (modes[i].equals(cur)) {
                idx = i;
            }
        }
        String next = modes[(idx + 1) % modes.length];
        this.runStoreAction("Modus: " + modeLabel(next), () -> StoreClient.petConfig(petId, next, null, null, null));
    }

    private static int slotOrder(String slot) {
        for (int i = 0; i < SLOT_ORDER.length; ++i) {
            if (SLOT_ORDER[i].equals(slot)) {
                return i;
            }
        }
        return 99;
    }

    private static boolean isCosmeticCat(String c) {
        for (String s : SLOT_ORDER) {
            if (s.equals(c)) {
                return true;
            }
        }
        // Sicherheitsnetz: Kategorien, die der Server neu hinzugefügt hat und die
        // noch nicht in SLOT_ORDER stehen, dürfen NICHT stillschweigend aus dem
        // Cosmetics-Tab verschwinden. Nur Pet-Zubehör (eigener Tab) aussortieren.
        return c != null && !c.isEmpty() && !c.startsWith("pet");
    }

    private static boolean matchQuery(JsonObject it, String q) {
        if (q == null || q.isEmpty()) {
            return true;
        }
        String hay = (str(it, "name", "") + " " + str(it, "desc", "") + " " + str(it, "category", "")).toLowerCase();
        return hay.contains(q);
    }

    private void gridFlush() {
        if (this.gcol != 0) {
            this.gy += this.growH + GAP;
            this.gcol = 0;
        }
    }

    private Cell addFull(int kind, float h) {
        this.gridFlush();
        Cell c = new Cell();
        c.kind = kind;
        c.x = 0.0f;
        c.y = this.gy;
        c.w = this.cw - 6;
        c.h = h;
        this.cells.add(c);
        this.gy += h + GAP;
        return c;
    }

    private Cell addGrid(int kind, int cols, float h) {
        if (this.gcol >= cols) {
            this.gy += this.growH + GAP;
            this.gcol = 0;
        }
        float w = (this.cw - 6 - (cols - 1) * GAP) / (float) cols;
        Cell c = new Cell();
        c.kind = kind;
        c.x = this.gcol * (w + GAP);
        c.y = this.gy;
        c.w = w;
        c.h = h;
        this.growH = h;
        this.gcol++;
        this.cells.add(c);
        return c;
    }

    private int cols(int minW) {
        return Math.max(1, Math.min(3, (this.cw - 6 + GAP) / (minW + GAP)));
    }

    private void relayout() {
        this.cells.clear();
        this.gy = 0.0f;
        this.gcol = 0;
        this.growH = 0.0f;
        Category cat = this.cats[this.category];
        String q = this.query.trim().toLowerCase();
        if (this.detail != null) {
            this.layoutDetail();
        } else if (cat == Category.EXTRAS && q.isEmpty()) {
            this.layoutExtras();
        } else if (!q.isEmpty() || !isStorePage(cat)) {
            this.layoutModules(cat, q);
        } else {
            this.layoutStore(cat, q);
        }
        this.gridFlush();
        this.contentH = Math.max(0.0f, this.gy - GAP);
        this.maxScroll = Math.max(0.0f, this.contentH - this.ch + 2);
        this.scrollT = Math.max(0.0f, Math.min(this.scrollT, this.maxScroll));
        this.scrollV = Math.max(0.0f, Math.min(this.scrollV, this.maxScroll));
        this.buildWidgets();
    }

    private void layoutModules(Category cat, String q) {
        List<Module> list = new ArrayList<Module>();
        for (Module m : ModuleManager.modules()) {
            if (!q.isEmpty()) {
                if (m.name.toLowerCase().contains(q) || m.description.toLowerCase().contains(q) || m.category.display.toLowerCase().contains(q)) {
                    list.add(m);
                }
            } else if (m.category == cat) {
                list.add(m);
            }
        }
        list.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        int on = 0;
        for (Module m : list) {
            if (m.enabled) {
                on++;
            }
        }
        Cell t = this.addFull(K_TITLE, 20.0f);
        t.text = q.isEmpty() ? cat.display : "Suche";
        t.data = new JsonObject();
        t.data.addProperty("sub", list.size() + " Module · " + on + " aktiv");
        if (list.isEmpty()) {
            Cell e = this.addFull(K_TEXT, 30.0f);
            e.text = "Keine Module gefunden.";
            return;
        }
        int cols = this.cols(150);
        for (Module m : list) {
            Cell c = this.addGrid(K_MOD, cols, 42.0f);
            c.module = m;
        }
    }

    private void layoutDetail() {
        Module m = this.detail;
        Cell h = this.addFull(K_DETAIL, m.risk != null ? 54.0f : 42.0f);
        h.module = m;
        List<Setting> sorted = new ArrayList<Setting>(m.settings());
        sorted.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        if (sorted.isEmpty()) {
            Cell e = this.addFull(K_TEXT, 30.0f);
            e.text = "Dieses Modul hat keine Einstellungen.";
            return;
        }
        for (Setting s : sorted) {
            Cell c = this.addFull(K_SETTING, 30.0f);
            c.setting = s;
            c.module = m;
        }
    }

    private void layoutStore(Category cat, String q) {
        this.ensureStore();
        boolean failed = this.storeMsg != null && !this.storeMsg.isEmpty();
        String info;
        if (failed) {
            // Bei Ausfall: Grund nennen und kennzeichnen, dass evtl. alte Daten shown.
            info = this.storeFromCache
                    ? this.storeMsg + " - zeige letzte bekannte Daten"
                    : this.storeMsg;
        } else if (this.storeCache == null) {
            info = this.storeLoading ? "Lade Store ..." : "Store nicht erreichbar. Im Launcher anmelden, dann Tab wechseln.";
        } else {
            int pts = num(this.storeCache, "points", -1);
            info = pts >= 0 ? "Guthaben: " + pts + " Punkte" + (StoreClient.token() == null ? " (nur Katalog, Login im Launcher zum Kaufen)" : "") : "Katalog";
            if (this.storeFromCache) {
                info = "Offline-Daten - " + info;
            }
        }
        Cell i = this.addFull(K_INFO, 22.0f);
        i.text = info;
        i.data = new JsonObject();
        i.data.addProperty("points", failed ? -1 : num(this.storeCache, "points", -1));
        if (failed) {
            // Deutlich sichtbarer Hinweis statt eines scheinbar leeren Tabs.
            Cell w = this.addFull(K_WARN, 34.0f);
            w.text = (this.storeMsg == null ? "Store nicht erreichbar" : this.storeMsg)
                    + (this.storeFromCache ? " - zeige letzte bekannte Daten" : "")
                    + ". Automatischer Versuch folgt.";
        }
        JsonArray items = null;
        try {
            if (this.storeCache != null && this.storeCache.has("catalog") && this.storeCache.get("catalog").isJsonArray()) {
                items = this.storeCache.getAsJsonArray("catalog");
            }
        } catch (Throwable t) {
        }
        List<JsonObject> all = new ArrayList<JsonObject>();
        if (items != null) {
            for (JsonElement el : items) {
                if (el != null && el.isJsonObject()) {
                    all.add(el.getAsJsonObject());
                }
            }
        }
        int cols = this.cols(150);
        if (cat == Category.STORE) {
            this.addHead("Verfügbar");
            for (JsonObject it : all) {
                if (bool(it, "owned") || !matchQuery(it, q)) {
                    continue;
                }
                Cell c = this.addGrid(K_STORE, cols, 54.0f);
                c.data = it;
            }
            return;
        }
        if (cat == Category.COSMETICS) {
            List<JsonObject> active = new ArrayList<JsonObject>();
            List<JsonObject> rest = new ArrayList<JsonObject>();
            for (JsonObject it : all) {
                if (!bool(it, "owned") || !isCosmeticCat(str(it, "category", "")) || !matchQuery(it, q)) {
                    continue;
                }
                if (str(it, "id", "").equals(this.equippedId(str(it, "category", "")))) {
                    active.add(it);
                } else {
                    rest.add(it);
                }
            }
            rest.sort((a, b) -> {
                int c = Integer.compare(slotOrder(str(a, "category", "")), slotOrder(str(b, "category", "")));
                return c != 0 ? c : str(a, "name", "").compareToIgnoreCase(str(b, "name", ""));
            });
            if (!active.isEmpty()) {
                this.addHead("Aktiv");
                for (JsonObject it : active) {
                    Cell c = this.addGrid(K_COS, cols, 46.0f);
                    c.data = it;
                }
            }
            String last = "";
            for (JsonObject it : rest) {
                String slot = str(it, "category", "");
                if (!slot.equals(last)) {
                    last = slot;
                    this.addHead(SLOT_LABELS.getOrDefault(slot, slot));
                }
                Cell c = this.addGrid(K_COS, cols, 46.0f);
                c.data = it;
            }
            if (active.isEmpty() && rest.isEmpty() && this.storeCache != null) {
                Cell e = this.addFull(K_TEXT, 30.0f);
                e.text = "Noch keine Cosmetics im Besitz.";
            }
            return;
        }
        String activeId = this.equippedId("pet");
        JsonObject activePet = null;
        List<JsonObject> rest = new ArrayList<JsonObject>();
        for (JsonObject it : all) {
            if (!str(it, "category", "").equals("pet") || !bool(it, "owned") || !matchQuery(it, q)) {
                continue;
            }
            if (!activeId.isEmpty() && str(it, "id", "").equals(activeId)) {
                activePet = it;
            } else {
                rest.add(it);
            }
        }
        rest.sort((a, b) -> str(a, "name", "").compareToIgnoreCase(str(b, "name", "")));
        if (activePet != null) {
            this.addHead("Aktiv");
            Cell c = this.addFull(K_PET_ACTIVE, PET_ACTIVE_H);
            c.data = activePet;
            String pid = str(activePet, "id", "");
            if (!pid.equals(this.petNamePetId)) {
                this.petNamePetId = pid;
                this.petNameValue = this.petNameOf(pid);
            }
        } else {
            this.petNamePetId = "";
        }
        if (!rest.isEmpty()) {
            this.addHead(activePet != null ? "Verfügbar" : "Haustiere");
            for (JsonObject it : rest) {
                Cell c = this.addGrid(K_PET, cols, 46.0f);
                c.data = it;
            }
        }
        if (activePet == null && rest.isEmpty() && this.storeCache != null) {
            Cell e = this.addFull(K_TEXT, 30.0f);
            e.text = "Noch keine Haustiere im Besitz.";
        }
    }

    private void layoutExtras() {
        Cell t = this.addFull(K_TITLE, 20.0f);
        t.text = Category.EXTRAS.display;
        t.data = new JsonObject();
        t.data.addProperty("sub", "Minispiele - KI oder gegen Kollegen im Spiel");
        this.addHead("Tetris");
        Cell c = this.addFull(K_GAME, 64.0f);
        c.data = new JsonObject();
        c.data.addProperty("game", "tetris");
        this.addHead("Snake");
        Cell cs = this.addFull(K_GAME, 64.0f);
        cs.data = new JsonObject();
        cs.data.addProperty("game", "snake");
        this.addHead("Brett- & Kartenspiele");
        Cell c4 = this.addFull(K_GAME, 64.0f);
        c4.data = new JsonObject();
        c4.data.addProperty("game", "c4");
        Cell ch = this.addFull(K_GAME, 64.0f);
        ch.data = new JsonObject();
        ch.data.addProperty("game", "chess");
        Cell lu = this.addFull(K_GAME, 64.0f);
        lu.data = new JsonObject();
        lu.data.addProperty("game", dev.kollegen.client.game.MenschAergereDichNichtScreen.ID);
    }

    private void addHead(String text) {
        Cell h = this.addFull(K_HEAD, 14.0f);
        h.text = text;
    }

    private void buildWidgets() {
        this.clearWidgets();
        this.petNameBox = null;
        this.addRenderableWidget(this.search);
        int sy = Math.round(this.scrollV);
        this.builtScroll = sy;
        for (Cell c : this.cells) {
            int top = Math.round(this.cy + c.y - sy);
            if (top < this.cy || top + c.h > this.cy + this.ch) {
                continue;
            }
            if (c.kind == K_SETTING) {
                int[] wr = this.widgetRect(c);
                AbstractWidget w = c.setting.buildWidget(wr[0], top, wr[1], 30, this);
                this.addRenderableWidget(w);
            } else if (c.kind == K_PET_ACTIVE) {
                int bx = this.cx + 8;
                int bw = this.cw - 6 - 16 - 54;
                this.petNameBox = new EditBox(this.font, bx + 4, top + 41, bw - 8, 12, (Component) Component.literal(""));
                this.petNameBox.setBordered(false);
                this.petNameBox.setMaxLength(16);
                this.petNameBox.setHint((Component) Component.literal("Pet-Name"));
                this.petNameBox.setTextColor(Palette.TEXT);
                this.petNameBox.setValue(this.petNameValue);
                this.petNameBox.setResponder(o -> {
                    this.petNameValue = (String) o;
                });
                this.addRenderableWidget(this.petNameBox);
            }
        }
    }

    private int[] widgetRect(Cell c) {
        int lw = (int) (c.w * 0.36f);
        if (c.setting instanceof SliderSetting || c.setting instanceof StringSetting) {
            return new int[]{this.cx + lw, (int) c.w - lw - 2};
        }
        if (c.setting instanceof BooleanSetting) {
            return new int[]{this.cx, (int) c.w - 10};
        }
        return new int[]{this.cx, (int) c.w - 2};
    }

    private void switchCategory(int idx) {
        if (idx < 0 || idx >= this.cats.length) {
            return;
        }
        this.category = idx;
        this.detail = null;
        this.query = "";
        this.search.setValue("");
        this.scrollT = 0.0f;
        this.scrollV = 0.0f;
        this.pageAnim = 0.0f;
        this.relayout();
    }

    private void openDetail(Module m) {
        this.detail = m;
        this.scrollT = 0.0f;
        this.scrollV = 0.0f;
        this.pageAnim = 0.0f;
        this.relayout();
    }

    private void closeDetail() {
        this.detail = null;
        this.scrollT = 0.0f;
        this.scrollV = 0.0f;
        this.pageAnim = 0.0f;
        this.relayout();
    }

    private void toggleModule(Module m) {
        if (m.locked) {
            return;
        }
        m.enabled = !m.enabled;
        if (m.enabled) {
            m.onEnable();
        } else {
            m.onDisable();
        }
        ModuleManager.save();
    }

    private void close() {
        Minecraft.getInstance().setScreenAndShow(this.parent);
    }

    public boolean isPauseScreen() {
        return false;
    }

    private float cellScreenY(Cell c) {
        return this.cy + c.y - this.scrollV;
    }

    private static boolean hit(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private boolean inContent(double mx, double my) {
        return hit(mx, my, this.cx - 2, this.cy, this.cw + 4, this.ch);
    }

    private int sideItemH() {
        int n = this.cats.length;
        int h = (this.sbH - 6 - (n - 1) * 2) / n;
        return Math.max(15, Math.min(24, h));
    }

    private float sideItemY(int i) {
        int ih = this.sideItemH();
        float y = this.sbY + 3 + i * (ih + 2);
        if (i >= 7) {
            y += 4;
        }
        return y;
    }

    private float hudBtnW() {
        return this.compact ? 22.0f : 24 + this.font.width("HUD");
    }

    private float closeX() {
        return this.px + this.pw - 10 - 22;
    }

    private float themeX() {
        return this.closeX() - 4 - 22;
    }

    private float hudX() {
        return this.themeX() - 4 - this.hudBtnW();
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        double mx = event.x();
        double my = event.y();
        if (event.button() == 0) {
            float by = this.py + 6;
            if (hit(mx, my, this.closeX(), by, 22, 22)) {
                this.close();
                return true;
            }
            if (hit(mx, my, this.themeX(), by, 22, 22)) {
                Minecraft.getInstance().setScreenAndShow((Screen) new ThemeSelectorScreen(this));
                return true;
            }
            if (hit(mx, my, this.hudX(), by, this.hudBtnW(), 22)) {
                HudModule.editMode = true;
                Minecraft.getInstance().setScreenAndShow((Screen) new HudEditScreen(this));
                return true;
            }
            for (int i = 0; i < this.cats.length; i++) {
                if (hit(mx, my, this.sbX, this.sideItemY(i), this.sideW, this.sideItemH())) {
                    if (i != this.category || this.detail != null || !this.query.isEmpty()) {
                        this.switchCategory(i);
                    }
                    return true;
                }
            }
            if (this.maxScroll > 0 && hit(mx, my, this.cx + this.cw - 8, this.cy, 10, this.ch)) {
                this.draggingBar = true;
                this.dragTo(my);
                return true;
            }
            if (this.inContent(mx, my) && this.clickCells(mx, my)) {
                return true;
            }
        }
        return super.mouseClicked(event, bl);
    }

    private void dragTo(double my) {
        float trackH = this.ch;
        float thumbH = Math.max(18.0f, trackH * trackH / (trackH + this.maxScroll));
        float r = (float) ((my - this.cy - thumbH / 2.0) / Math.max(1.0f, trackH - thumbH));
        r = Math.max(0.0f, Math.min(1.0f, r));
        this.scrollT = r * this.maxScroll;
        this.scrollV = this.scrollT;
    }

    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (this.draggingBar && event.button() == 0) {
            this.dragTo(event.y());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0) {
            this.draggingBar = false;
        }
        return super.mouseReleased(event);
    }

    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (this.inContent(mx, my) && this.maxScroll > 0) {
            this.scrollT = Math.max(0.0f, Math.min(this.maxScroll, this.scrollT - (float) (vertical * 28.0)));
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    private boolean clickCells(double mx, double my) {
        for (Cell c : this.cells) {
            float X = this.cx + c.x;
            float Y = this.cellScreenY(c);
            if (!hit(mx, my, X, Y, c.w, c.h)) {
                continue;
            }
            switch (c.kind) {
                case K_MOD: {
                    Module m = c.module;
                    boolean onSwitch = hit(mx, my, X + c.w - 40, Y, 40, 24);
                    if (onSwitch && !m.locked) {
                        this.toggleModule(m);
                    } else if (m.settings().isEmpty() && !m.locked) {
                        this.toggleModule(m);
                    } else {
                        this.openDetail(m);
                    }
                    return true;
                }
                case K_DETAIL: {
                    if (hit(mx, my, X, Y + 2, 22, 22)) {
                        this.closeDetail();
                        return true;
                    }
                    if (hit(mx, my, X + c.w - 42, Y + 2, 42, 22)) {
                        this.toggleModule(c.module);
                        return true;
                    }
                    return false;
                }
                case K_STORE:
                case K_COS: {
                    String id = str(c.data, "id", "");
                    String itemCat = str(c.data, "category", "");
                    if (id.isEmpty()) {
                        return false;
                    }
                    if (!bool(c.data, "owned")) {
                        this.runStoreAction("Gekauft", () -> StoreClient.buy(id));
                    } else if (id.equals(this.equippedId(itemCat))) {
                        this.runStoreAction("Abgelegt", () -> StoreClient.unequipCategory(itemCat));
                    } else {
                        this.runStoreAction("Ausgerüstet", () -> StoreClient.equipItem(id));
                    }
                    return true;
                }
                case K_PET: {
                    String id = str(c.data, "id", "");
                    if (id.isEmpty()) {
                        return false;
                    }
                    this.runStoreAction("Pet aktiv", () -> StoreClient.equipItem(id));
                    return true;
                }
                case K_PET_ACTIVE: {
                    String id = str(c.data, "id", "");
                    if (id.isEmpty()) {
                        return false;
                    }
                    float bx = X + 8;
                    float saveX = X + c.w - 8 - 46;
                    if (hit(mx, my, saveX, Y + 38, 46, 18)) {
                        this.savePetName(id);
                        return true;
                    }
                    float chipW = (c.w - 16 - 3 * 4) / 4.0f;
                    float chipY = Y + 64;
                    for (int i = 0; i < 4; i++) {
                        if (hit(mx, my, bx + i * (chipW + 4), chipY, chipW, 20)) {
                            if (i == 0) {
                                this.cyclePetColor(id);
                            } else if (i == 1) {
                                this.cyclePetSkin(id);
                            } else if (i == 2) {
                                this.cyclePetMode(id);
                            } else {
                                this.runStoreAction("Pet weggeschickt", () -> StoreClient.unequipCategory("pet"));
                            }
                            return true;
                        }
                    }
                    return false;
                }
                case K_GAME: {
                    String game = str(c.data, "game", "");
                    try {
                        if ("tetris".equals(game)) {
                            Minecraft.getInstance().setScreenAndShow(new TetrisScreen(this));
                            return true;
                        }
                        if ("snake".equals(game)) {
                            Minecraft.getInstance().setScreenAndShow(new dev.kollegen.client.game.SnakeScreen(this));
                            return true;
                        }
                        if (dev.kollegen.client.game.Connect4Screen.ID.equals(game)) {
                            Minecraft.getInstance().setScreenAndShow(new dev.kollegen.client.game.Connect4Screen(this));
                            return true;
                        }
                        if (dev.kollegen.client.game.ChessScreen.ID.equals(game)) {
                            Minecraft.getInstance().setScreenAndShow(new dev.kollegen.client.game.ChessScreen(this));
                            return true;
                        }
                        if (dev.kollegen.client.game.MenschAergereDichNichtScreen.ID.equals(game)) {
                            Minecraft.getInstance().setScreenAndShow(new dev.kollegen.client.game.MenschAergereDichNichtScreen(this));
                            return true;
                        }
                    } catch (Throwable ignored) {
                    }
                    return false;
                }
                default:
                    break;
            }
        }
        return false;
    }

    public boolean keyPressed(KeyEvent ki) {
        int key = ki.key();
        if (this.petNameBox != null && this.petNameBox.isFocused() && (key == 257 || key == 335)) {
            if (!this.petNamePetId.isEmpty()) {
                this.savePetName(this.petNamePetId);
            }
            return true;
        }
        if (KeybindSetting.capturing != null) {
            KeybindSetting cap = KeybindSetting.capturing;
            cap.value = key == 256 ? -1 : key;
            KeybindSetting.capturing = null;
            this.buildWidgets();
            return true;
        }
        if (key == 256) {
            if (this.detail != null) {
                this.closeDetail();
            } else if (!this.query.isEmpty()) {
                this.search.setValue("");
            } else {
                this.close();
            }
            return true;
        }
        boolean typing = this.search.isFocused() && !this.search.getValue().isEmpty();
        if (!typing && this.detail == null && key == 263 && this.category > 0) {
            this.switchCategory(this.category - 1);
            return true;
        }
        if (!typing && this.detail == null && key == 262 && this.category < this.cats.length - 1) {
            this.switchCategory(this.category + 1);
            return true;
        }
        return super.keyPressed(ki);
    }

    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        long perfT0 = System.nanoTime();
        Glass.fillCount = 0;
        Glass.primCount = 0;
        Glass.frame();
        if (this.pendingRebuild) {
            this.pendingRebuild = false;
            this.relayout();
        }
        this.openAnim = Math.min(1.0f, this.openAnim + Glass.dt * 4.5f);
        this.pageAnim = Math.min(1.0f, this.pageAnim + Glass.dt * 6.0f);
        this.scrollV = Glass.step(this.scrollV, this.scrollT, 20.0f);
        if (Math.round(this.scrollV) != this.builtScroll && (this.detail != null || this.cells.stream().anyMatch(c -> c.kind == K_PET_ACTIVE))) {
            this.buildWidgets();
        }
        float open = Glass.ease(this.openAnim);
        this.drawBackdrop(g, open);
        Glass.alpha = open;
        float lift = (1.0f - open) * 10.0f;
        g.pose().pushMatrix();
        g.pose().translate(0.0f, lift);
        this.drawPanel(g, mx, my);
        this.drawSidebar(g, mx, my);
        this.drawHeader(g, mx, my);
        Glass.alpha = open * Glass.ease(this.pageAnim);
        this.drawContent(g, mx, my);
        Glass.alpha = open * Glass.ease(this.pageAnim);
        super.extractRenderState(g, mx, my, pt);
        g.pose().popMatrix();
        Glass.alpha = 1.0f;
        long perfDt = System.nanoTime() - perfT0;
        this.perfAcc += perfDt;
        if (perfDt > this.perfMax) {
            this.perfMax = perfDt;
        }
        if (++this.perfN >= 300) {
            System.out.println("[Kollegen-UI] Menü-Frame avg=" + (this.perfAcc / this.perfN / 1000) + "µs max=" + (this.perfMax / 1000) + "µs fills=" + (Glass.fillCount) + " prims=" + (Glass.primCount));
            this.perfAcc = 0L;
            this.perfN = 0;
            this.perfMax = 0L;
        }
    }

    private void drawBackdrop(GuiGraphicsExtractor g, float open) {
        int base = (int) (140 * open);
        g.fill(0, 0, this.width, this.height, Glass.tint(Palette.BG, base));
        int bands = 10;
        for (int i = 0; i < bands; i++) {
            int y0 = this.height * i / bands;
            int y1 = this.height * (i + 1) / bands;
            int a = (int) (open * 70 * (i / (float) (bands - 1)));
            g.fill(0, y0, this.width, y1, Glass.tint(0x000000, a));
        }
    }

    private void drawPanel(GuiGraphicsExtractor g, int mx, int my) {
        Glass.glassCard(g, this.px, this.py, this.pw, this.ph, 14, Palette.BG, Math.min(0.92f, Glass.opacity + 0.12f), 1.0f);
        Glass.rrGrad(g, this.px + 1, this.py + 1, this.pw - 2, 120, 13, Glass.a(Palette.ACCENT, 0.10f), 0x00000000);
        Glass.separator(g, this.px + 10, this.py + this.headH, this.pw - 20, 0x22FFFFFF);
    }

    private void drawHeader(GuiGraphicsExtractor g, int mx, int my) {
        LogoDraw.draw(g, this.px + 10, this.py + 4, 64);
        Glass.textScaled(g, this.font, "v" + Version.get(), this.px + 12, this.py + 26, 0.7f, 0x66FFFFFF, false);
        int sx = this.searchX();
        int sw = Math.max(50, (int) this.rightButtonsX() - 8 - sx);
        float focus = this.hv("search", this.search.isFocused(), 14.0f);
        Glass.rr(g, sx, this.py + 8, sw, 20, 10, Glass.a(0x000000, 0.28f));
        Glass.ring(g, sx, this.py + 8, sw, 20, 10, 0.5f, Glass.mixA(0x28FFFFFF, Glass.a(Palette.ACCENT, 0.9f), focus));
        Icons.draw(g, Icons.SEARCH, sx + 6, this.py + 12, 12, Palette.MUTED);
        float by = this.py + 6;
        this.headerButton(g, mx, my, this.hudX(), by, this.hudBtnW(), Icons.MOVE, this.compact ? null : "HUD", "hud");
        this.headerButton(g, mx, my, this.themeX(), by, 22, Icons.PALETTE, null, "theme");
        this.headerButton(g, mx, my, this.closeX(), by, 22, Icons.CLOSE, null, "close");
    }

    private void headerButton(GuiGraphicsExtractor g, int mx, int my, float x, float y, float w, int icon, String label, String key) {
        boolean hov = hit(mx, my, x, y, w, 22);
        float h = this.hv("hb:" + key, hov, 14.0f);
        Glass.pill(g, x, y, w, 22, h, false, false);
        int col = Glass.mix(Palette.TEXT, 0xFFFFFFFF, h * 0.4f);
        if (label == null) {
            Icons.draw(g, icon, x + (w - 12) / 2, y + 5, 12, col);
        } else {
            Icons.draw(g, icon, x + 6, y + 5, 12, col);
            Glass.text(g, this.font, label, Math.round(x + 21), Math.round(y + 7), col, false);
        }
    }

    private void drawSidebar(GuiGraphicsExtractor g, int mx, int my) {
        Glass.rr(g, this.sbX - 3, this.sbY - 1, this.sideW + 6, this.sbH + 2, 10, 0x1E000000);
        int ih = this.sideItemH();
        for (int i = 0; i < this.cats.length; i++) {
            Category c = this.cats[i];
            float y = this.sideItemY(i);
            boolean sel = i == this.category && this.query.isEmpty();
            boolean hov = hit(mx, my, this.sbX, y, this.sideW, ih);
            float hs = this.hv("sb:" + i, hov, 14.0f);
            float ss = this.hv("ss:" + i, sel, 12.0f);
            if (ss > 0.01f) {
                Glass.rrGrad(g, this.sbX, y, this.sideW, ih, 7, Glass.a(Palette.ACCENT, 0.85f * ss), Glass.a(Palette.ACCENT2, 0.85f * ss));
                Glass.ring(g, this.sbX, y, this.sideW, ih, 7, 0.5f, Glass.a(0xFFFFFFFF, 0.3f * ss));
            } else if (hs > 0.01f) {
                Glass.rr(g, this.sbX, y, this.sideW, ih, 7, Glass.a(0xFFFFFFFF, 0.10f * hs));
            }
            int col = Glass.mix(Glass.mix(Palette.MUTED, Palette.TEXT, hs), 0xFFFFFFFF, ss);
            float isz = Math.min(14.0f, ih - 6.0f);
            float ix = this.compact ? this.sbX + (this.sideW - isz) / 2.0f : this.sbX + 7;
            Icons.draw(g, Icons.forCategory(c.name()), ix, y + (ih - isz) / 2.0f, isz, col);
            if (!this.compact) {
                Glass.text(g, this.font, c.display, Math.round(this.sbX + 7 + isz + 6), Math.round(y + (ih - 8) / 2.0f), col, false);
            }
        }
    }

    private void drawContent(GuiGraphicsExtractor g, int mx, int my) {
        g.enableScissor(this.cx - 2, this.cy, this.cx + this.cw + 2, this.cy + this.ch);
        for (Cell c : this.cells) {
            float X = this.cx + c.x;
            float Y = this.cellScreenY(c);
            if (Y + c.h < this.cy - 2 || Y > this.cy + this.ch + 2) {
                continue;
            }
            boolean hov = this.inContent(mx, my) && hit(mx, my, X, Y, c.w, c.h);
            switch (c.kind) {
                case K_TITLE:
                    this.drawTitle(g, c, X, Y);
                    break;
                case K_HEAD:
                    Glass.text(g, this.font, c.text, Math.round(X + 2), Math.round(Y + 3), Palette.ACCENT, false);
                    Glass.rr(g, X + 4 + this.font.width(c.text), Y + 7, Math.max(0, c.w - this.font.width(c.text) - 8), 0.6f, 0.3f, 0x22FFFFFF);
                    break;
                case K_TEXT:
                    Glass.text(g, this.font, c.text, Math.round(X + 2), Math.round(Y + 8), Palette.MUTED, false);
                    break;
                case K_INFO:
                    this.drawInfo(g, c, X, Y);
                    break;
                case K_MOD:
                    this.drawModCard(g, c, X, Y, hov);
                    break;
                case K_DETAIL:
                    this.drawDetailHead(g, c, X, Y, mx, my);
                    break;
                case K_SETTING:
                    this.drawSettingRow(g, c, X, Y);
                    break;
                case K_STORE:
                case K_COS:
                    this.drawItemCard(g, c, X, Y, hov);
                    break;
                case K_PET:
                    this.drawPetCard(g, c, X, Y, hov);
                    break;
                case K_PET_ACTIVE:
                    this.drawActivePet(g, c, X, Y, mx, my);
                    break;
                case K_GAME:
                    this.drawGameCard(g, c, X, Y, hov);
                    break;
                case K_WARN:
                    this.drawWarn(g, c, X, Y);
                    break;
                default:
                    break;
            }
        }
        g.disableScissor();
        if (this.maxScroll > 0) {
            Glass.vanillaScrollbar(g, this.cx + this.cw - 3, this.cy, 3, this.ch, this.scrollV, this.maxScroll, false, this.draggingBar);
        }
    }

    private void drawTitle(GuiGraphicsExtractor g, Cell c, float X, float Y) {
        Glass.textScaled(g, this.font, c.text, X + 2, Y + 2, 1.2f, Palette.TEXT, true);
        String sub = c.data == null ? "" : str(c.data, "sub", "");
        Glass.textR(g, this.font, sub, Math.round(X + c.w - 2), Math.round(Y + 6), Palette.MUTED, false);
    }

    private void drawInfo(GuiGraphicsExtractor g, Cell c, float X, float Y) {
        Glass.rr(g, X, Y, c.w, c.h, 8, Glass.a(0x000000, 0.22f));
        Glass.ring(g, X, Y, c.w, c.h, 8, 0.5f, 0x22FFFFFF);
        int pts = c.data == null ? -1 : num(c.data, "points", -1);
        float tx = X + 9;
        if (pts >= 0 && this.storeMsg.isEmpty()) {
            Icons.draw(g, Icons.COIN, X + 7, Y + 5, 12, Palette.ACCENT);
            tx = X + 23;
        }
        Glass.text(g, this.font, Glass.trunc(this.font, c.text, (int) (c.w - (tx - X) - 8)), Math.round(tx), Math.round(Y + 7), Palette.TEXT, false);
    }

    private void drawModCard(GuiGraphicsExtractor g, Cell c, float X, float Y, boolean hov) {
        Module m = c.module;
        float h = this.hv("m:" + m.id, hov, 14.0f);
        float en = this.hv("e:" + m.id, m.enabled, 12.0f);
        int tint = Glass.mix(Palette.PANEL, Palette.ACCENT, 0.10f * en + 0.05f * h);
        Glass.glassCard(g, X, Y, c.w, c.h, 9, tint, Glass.opacity * (0.85f + 0.3f * h), 0.0f);
        if (en > 0.01f) {
            Glass.rrGrad(g, X + 1, Y + 1, c.w - 2, c.h - 2, 8, Glass.a(Palette.ACCENT, 0.16f * en), 0x00000000);
            Glass.ring(g, X, Y, c.w, c.h, 9, 0.5f, Glass.a(Palette.ACCENT, 0.7f * en));
        } else if (h > 0.01f) {
            Glass.ring(g, X, Y, c.w, c.h, 9, 0.5f, Glass.a(0xFFFFFFFF, 0.35f * h));
        }
        int tx = Math.round(X + 10);
        int maxW = (int) (c.w - 20 - 34);
        Glass.textBold(g, this.font, Glass.trunc(this.font, m.name, maxW), tx, Math.round(Y + 9), Palette.TEXT, true);
        Glass.text(g, this.font, Glass.trunc(this.font, m.description, (int) (c.w - 38)), tx, Math.round(Y + 23), Palette.MUTED, false);
        if (m.locked) {
            Icons.draw(g, Icons.LOCK, X + c.w - 20, Y + 8, 11, Palette.MUTED);
        } else {
            GlassToggle.drawSwitch(g, X + c.w - 10 - 24, Y + 8, 24, 13, en, h * 0.0f);
        }
        if (!m.settings().isEmpty()) {
            Icons.draw(g, Icons.SLIDERS, X + c.w - 20, Y + c.h - 16, 10, Glass.a(Palette.MUTED, 0.5f + 0.5f * h));
        }
        if (m.risk != null) {
            Icons.draw(g, Icons.WARN, X + c.w - 34, Y + c.h - 16, 10, Palette.DANGER);
        }
    }

    private void drawDetailHead(GuiGraphicsExtractor g, Cell c, float X, float Y, int mx, int my) {
        Module m = c.module;
        boolean hb = hit(mx, my, X, Y + 2, 22, 22);
        float h = this.hv("back", hb, 14.0f);
        Glass.pill(g, X, Y + 2, 22, 22, h, false, false);
        Icons.draw(g, Icons.BACK, X + 5, Y + 7, 12, Palette.TEXT);
        Glass.textScaled(g, this.font, m.name, X + 30, Y + 4, 1.3f, Palette.TEXT, true);
        Glass.text(g, this.font, Glass.trunc(this.font, m.description, (int) (c.w - 80)), Math.round(X + 30), Math.round(Y + 20), Palette.MUTED, false);
        if (m.risk != null) {
            Icons.draw(g, Icons.WARN, X + 30, Y + 32, 10, Palette.DANGER);
            Glass.text(g, this.font, Glass.trunc(this.font, m.risk, (int) (c.w - 60)), Math.round(X + 43), Math.round(Y + 33), Palette.DANGER, false);
        }
        float en = this.hv("e:" + m.id, m.enabled, 12.0f);
        if (m.locked) {
            Icons.draw(g, Icons.LOCK, X + c.w - 18, Y + 6, 12, Palette.MUTED);
        } else {
            GlassToggle.drawSwitch(g, X + c.w - 34, Y + 5, 30, 16, en, 0.0f);
        }
    }

    private void drawSettingRow(GuiGraphicsExtractor g, Cell c, float X, float Y) {
        Glass.rr(g, X, Y, c.w, c.h - 3, 7, Glass.a(0x000000, 0.20f));
        Glass.ring(g, X, Y, c.w, c.h - 3, 7, 0.5f, 0x16FFFFFF);
        Setting s = c.setting;
        Glass.text(g, this.font, Glass.trunc(this.font, s.name, (int) (c.w * 0.36f) - 10), Math.round(X + 10), Math.round(Y + (c.h - 3 - 8) / 2.0f), Palette.TEXT, false);
        if (s instanceof SliderSetting) {
            Glass.textR(g, this.font, s.valueText(), Math.round(X + c.w - 8), Math.round(Y + (c.h - 3 - 8) / 2.0f), Palette.MUTED, false);
        }
    }

    private void drawPill(GuiGraphicsExtractor g, float x, float y, float w, float h, String label, boolean primary, boolean hov) {
        float hh = this.hv("pill:" + (int) x + ":" + (int) y, hov, 14.0f);
        Glass.pill(g, x, y, w, h, hh, primary, false);
        Glass.textC(g, this.font, Glass.trunc(this.font, label, (int) w - 6), Math.round(x + w / 2), Math.round(y + (h - 8) / 2.0f), primary ? 0xFFFFFFFF : Palette.TEXT, false);
    }

    private void drawItemCard(GuiGraphicsExtractor g, Cell c, float X, float Y, boolean hov) {
        JsonObject d = c.data;
        String id = str(d, "id", "");
        String cat = str(d, "category", "");
        boolean owned = bool(d, "owned");
        boolean eq = owned && id.equals(this.equippedId(cat));
        int rar = rarityColor(str(d, "rarity", ""));
        float h = this.hv("i:" + id, hov, 14.0f);
        Glass.glassCard(g, X, Y, c.w, c.h, 9, Glass.mix(Palette.PANEL, rar, 0.08f + 0.06f * h), Glass.opacity * (0.85f + 0.3f * h), 0.0f);
        Glass.rrGrad(g, X + 5, Y + 7, 2.5f, c.h - 14, 1.2f, rar, Glass.a(rar, 0.35f));
        if (eq) {
            Glass.ring(g, X, Y, c.w, c.h, 9, 0.5f, Glass.a(Palette.ACCENT, 0.9f));
        } else if (h > 0.01f) {
            Glass.ring(g, X, Y, c.w, c.h, 9, 0.5f, Glass.a(0xFFFFFFFF, 0.35f * h));
        }
        int tx = Math.round(X + 14);
        Glass.textBold(g, this.font, Glass.trunc(this.font, str(d, "name", id), (int) (c.w - 24)), tx, Math.round(Y + 8), Palette.TEXT, true);
        Glass.text(g, this.font, SLOT_LABELS.getOrDefault(cat, cat), tx, Math.round(Y + 20), Palette.MUTED, false);
        float by = Y + c.h - 18;
        if (c.kind == K_STORE) {
            int price = num(d, "price", 0);
            int pts = this.storeCache == null ? -1 : num(this.storeCache, "points", -1);
            boolean poor = pts >= 0 && price > pts;
            Icons.draw(g, Icons.COIN, X + 13, by + 3, 11, rar);
            Glass.text(g, this.font, String.valueOf(price), Math.round(X + 27), Math.round(by + 5), poor ? Palette.DANGER : rar, false);
            this.drawPill(g, X + c.w - 8 - 46, by, 46, 15, "Kaufen", true, hov);
        } else {
            this.drawPill(g, X + c.w - 8 - 58, by, 58, 15, eq ? "Ablegen" : "Ausrüsten", !eq, hov);
            if (eq) {
                Glass.badge(g, Math.round(X + 14), Math.round(by), 36, 15, 7, Palette.GREEN, 0xFFFFFFFF, this.font, "Aktiv");
            }
        }
    }

    private void drawWarn(GuiGraphicsExtractor g, Cell c, float X, float Y) {
        Glass.rr(g, X, Y, c.w, c.h, 8, Glass.a(Palette.DANGER, 0.16f));
        Glass.ring(g, X, Y, c.w, c.h, 8, 0.5f, Glass.a(Palette.DANGER, 0.55f));
        Icons.draw(g, Icons.WARN, X + 8, Y + (c.h - 14) / 2.0f, 14, Palette.DANGER);
        Glass.text(g, this.font, Glass.trunc(this.font, c.text, (int) (c.w - 40)), Math.round(X + 28), Math.round(Y + (c.h - 3 - 8) / 2.0f), Palette.TEXT, false);
    }

    private void drawGameCard(GuiGraphicsExtractor g, Cell c, float X, float Y, boolean hov) {
        String game = str(c.data, "game", "tetris");
        String title;
        String desc;
        int icon;
        if (dev.kollegen.client.game.Connect4Screen.ID.equals(game)) {
            title = "Connect 4";
            desc = "Vier gewinnt - KI oder gegen Kollegen";
            icon = Icons.GRID;
        } else if (dev.kollegen.client.game.ChessScreen.ID.equals(game)) {
            title = "Schach";
            desc = "Komplette Regeln - KI oder gegen Kollegen";
            icon = Icons.SPARKLE;
        } else if (dev.kollegen.client.game.MenschAergereDichNichtScreen.ID.equals(game)) {
            title = "Mensch aergere dich nicht";
            desc = "Wuerfeln und raus - KI oder gegen Kollegen";
            icon = Icons.GRID;
        } else if ("snake".equals(game)) {
            title = "Snake";
            desc = "Fressen und wachsen - nicht an die Wand laufen";
            icon = Icons.SNAKE;
        } else {
            title = "Tetris";
            desc = "Spielen und Kollegen-Punkte verdienen (100:1)";
            icon = Icons.SPARKLE;
        }
        float h = this.hv("g:" + game, hov, 14.0f);
        Glass.glassCard(g, X, Y, c.w, c.h, 9, Glass.mix(Palette.PANEL, Palette.ACCENT, 0.04f * h), Glass.opacity * (0.85f + 0.3f * h), 0.0f);
        if (h > 0.01f) {
            Glass.ring(g, X, Y, c.w, c.h, 9, 0.5f, Glass.a(0xFFFFFFFF, 0.35f * h));
        }
        Glass.rr(g, X + 7, Y + (c.h - 32) / 2.0f, 32, 32, 9, Glass.a(Palette.ACCENT, 0.18f));
        Icons.draw(g, icon, X + 7 + 32 * 0.22f, Y + (c.h - 32) / 2.0f + 32 * 0.22f, 32 * 0.56f, Palette.ACCENT);
        Glass.textBold(g, this.font, Glass.trunc(this.font, title, (int) (c.w - 60)), Math.round(X + 48), Math.round(Y + 10), Palette.TEXT, true);
        Glass.text(g, this.font, Glass.trunc(this.font, desc, (int) (c.w - 48 - 130)), Math.round(X + 48), Math.round(Y + 30), Palette.MUTED, false);
        this.drawPill(g, X + c.w - 8 - 128, Y + (c.h - 28) / 2.0f, 128, 28, "Spielen", true, hov);
    }

    private void drawPetPreview(GuiGraphicsExtractor g, String id, float x, float y, int size) {
        String kind = id.startsWith("pet_") ? id.substring(4) : id;
        boolean drawn = false;
        try {
            if (MythicPets.isMythicKind(kind)) {
                Identifier pid = Identifier.tryParse("kollegen:pets/preview/" + kind + ".png");
                if (LogoDraw.exists(pid)) {
                    LogoDraw.drawId(g, pid, Math.round(x), Math.round(y), size);
                    drawn = true;
                }
            }
        } catch (Throwable t) {
        }
        if (!drawn) {
            Glass.rr(g, x, y, size, size, size / 2.0f, Glass.a(Palette.ACCENT, 0.18f));
            Icons.draw(g, Icons.PAW, x + size * 0.22f, y + size * 0.22f, size * 0.56f, Palette.ACCENT);
        }
    }

    private void drawPetCard(GuiGraphicsExtractor g, Cell c, float X, float Y, boolean hov) {
        JsonObject d = c.data;
        String id = str(d, "id", "");
        float h = this.hv("p:" + id, hov, 14.0f);
        Glass.glassCard(g, X, Y, c.w, c.h, 9, Glass.mix(Palette.PANEL, Palette.ACCENT, 0.04f * h), Glass.opacity * (0.85f + 0.3f * h), 0.0f);
        if (h > 0.01f) {
            Glass.ring(g, X, Y, c.w, c.h, 9, 0.5f, Glass.a(0xFFFFFFFF, 0.35f * h));
        }
        this.drawPetPreview(g, id, X + 7, Y + 7, 32);
        Glass.textBold(g, this.font, Glass.trunc(this.font, str(d, "name", id), (int) (c.w - 56)), Math.round(X + 46), Math.round(Y + 9), Palette.TEXT, true);
        this.drawPill(g, X + 46, Y + c.h - 19, 50, 15, "Rufen", true, hov);
    }

    private void drawActivePet(GuiGraphicsExtractor g, Cell c, float X, float Y, int mx, int my) {
        JsonObject d = c.data;
        String id = str(d, "id", "");
        Glass.glassCard(g, X, Y, c.w, c.h, 10, Glass.mix(Palette.PANEL, Palette.ACCENT, 0.10f), Glass.opacity, 0.0f);
        Glass.ring(g, X, Y, c.w, c.h, 10, 0.5f, Glass.a(Palette.ACCENT, 0.8f));
        this.drawPetPreview(g, id, X + 8, Y + 6, 28);
        Glass.textScaled(g, this.font, str(d, "name", id), X + 44, Y + 7, 1.2f, Palette.TEXT, true);
        String sub = "Modus: " + modeLabel(this.petModeOf(id)) + "  ·  Skin: " + this.petVariantLabel(id);
        Glass.text(g, this.font, Glass.trunc(this.font, sub, (int) (c.w - 54)), Math.round(X + 44), Math.round(Y + 21), Palette.MUTED, false);
        float bx = X + 8;
        float bw = c.w - 16 - 54;
        Glass.rr(g, bx, Y + 37, bw, 20, 10, Glass.a(0x000000, 0.28f));
        Glass.ring(g, bx, Y + 37, bw, 20, 10, 0.5f, 0x28FFFFFF);
        if (this.petNameBox == null) {
            Glass.text(g, this.font, this.petNameValue.isEmpty() ? "Pet-Name" : this.petNameValue, Math.round(bx + 8), Math.round(Y + 43), Palette.MUTED, false);
        }
        this.drawPill(g, X + c.w - 8 - 46, Y + 38, 46, 18, "Sichern", true, hit(mx, my, X + c.w - 54, Y + 38, 46, 18));
        float chipW = (c.w - 16 - 3 * 4) / 4.0f;
        float chipY = Y + 64;
        String[] labels = new String[]{"Farbe", this.petVariantLabel(id), modeLabel(this.petModeOf(id)), "Wegschicken"};
        for (int i = 0; i < 4; i++) {
            float chipX = bx + i * (chipW + 4);
            boolean hv = hit(mx, my, chipX, chipY, chipW, 20);
            float hh = this.hv("chip:" + i, hv, 14.0f);
            Glass.pill(g, chipX, chipY, chipW, 20, hh, false, false);
            if (i == 0) {
                int pc = parseHex(this.petColorOf(id), 0xFFFFFFFF);
                Glass.disc(g, chipX + 10, chipY + 10, 5, pc);
                Glass.ring(g, chipX + 5, chipY + 5, 10, 10, 5, 0.5f, 0x66FFFFFF);
                Glass.text(g, this.font, "Farbe", Math.round(chipX + 20), Math.round(chipY + 6), Palette.TEXT, false);
            } else {
                Glass.textC(g, this.font, Glass.trunc(this.font, labels[i], (int) chipW - 8), Math.round(chipX + chipW / 2), Math.round(chipY + 6), i == 3 ? Palette.DANGER : Palette.TEXT, false);
            }
        }
    }
}
