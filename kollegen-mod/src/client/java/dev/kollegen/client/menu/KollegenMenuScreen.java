package dev.kollegen.client.menu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
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
import dev.kollegen.client.mods.NumberSetting;
import dev.kollegen.client.presence.KollegenPresence;
import dev.kollegen.client.presence.MythicPets;
import dev.kollegen.client.presence.StoreClient;
import dev.kollegen.client.ui.Glass;
import dev.kollegen.client.ui.GlassToggle;
import dev.kollegen.client.ui.Icons;
import dev.kollegen.client.ui.LogoDraw;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

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
    private static final int K_SKIN_ITEM = 14;
    private static final int K_CAPE_ITEM = 15;
    private static final int K_CAPE_TAB = 16;
    private static final int K_COS_TAB = 17;
    private static final int K_COS_PREVIEW = 18;
    private static final int K_SKIN_CAPE_TOP_TAB = 19;
    private static final int K_ARMOR_TAB = 20;
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
        float hoverAnim = 0.0f;
        float extraAnim = 0.0f;
        String truncTitle = null;
        String truncDesc = null;
        int rarity = 0;
        boolean owned = false;
        String itemId = "";
        String itemCat = "";
        boolean hasSettings = false;
        int price = 0;
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
    private boolean hasPetActive = false;
    private String skinCapeMode = "skins";
    private String capeCatFilter = "community";
    private String cosSubTab = "name";
    private String inGameActiveCape = null;
    private String inGameActiveSkin = null;

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
        this.search = new EditBox(this.font, sx + 22, this.py + 13, sw - 28, 12, (Component) Component.literal(""));
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
        return c == Category.STORE || c == Category.COSMETICS || c == Category.PETS || c == Category.SKIN_CAPE;
    }

    private void ensureStore() {
        long now = System.currentTimeMillis();
        if (this.storeCache != null && now - this.storeAt < 20000L) {
            return;
        }
        if (this.storeLoading) {
            return;
        }
        if (this.storeFailUntil > now) {
            return;
        }
        if (this.storeCache == null) {
            JsonObject cached = StoreClient.cachedCatalog();
            if (cached != null) {
                this.storeCache = cached;
                this.updateEquippedCache();
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
                    this.updateEquippedCache();
                    this.storeAt = System.currentTimeMillis();
                    this.storeFromCache = false;
                    this.storeFailUntil = 0L;
                    ok = true;
                }
            } catch (Throwable ignored) {
            }
            if (!ok) {
                this.storeMsg = StoreClient.lastError() == null ? "Store nicht erreichbar" : StoreClient.lastError();
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
                    this.updateEquippedCache();
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

    private final Map<String, String> equippedCache = new HashMap<String, String>();

    private void updateEquippedCache() {
        this.equippedCache.clear();
        try {
            if (this.storeCache != null && this.storeCache.has("equipped") && this.storeCache.get("equipped").isJsonObject()) {
                JsonObject eq = this.storeCache.getAsJsonObject("equipped");
                for (Map.Entry<String, JsonElement> e : eq.entrySet()) {
                    if (e.getValue() != null && !e.getValue().isJsonNull()) {
                        this.equippedCache.put(e.getKey(), e.getValue().getAsString());
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private String equippedId(String category) {
        return this.equippedCache.getOrDefault(category, "");
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

    private static boolean isNameCosmetic(String c) {
        if (c == null) return false;
        String lc = c.toLowerCase(java.util.Locale.ROOT);
        return lc.equals("name_color") || lc.equals("title") || lc.equals("badge") || lc.equals("font") || lc.equals("sticker")
                || lc.equals("banner") || lc.equals("avatar_frame") || lc.equals("avatar_theme") || lc.equals("profile_frame")
                || lc.equals("profile_bg") || lc.equals("profil_stil");
    }

    private static boolean isSkinCosmetic(String c) {
        if (c == null) return false;
        String lc = c.toLowerCase(java.util.Locale.ROOT);
        return lc.equals("head_accessory") || lc.equals("face_accessory") || lc.equals("back_accessory")
                || lc.equals("aura") || lc.equals("shield") || lc.equals("shoulder_accessory")
                || lc.equals("torso_accessory") || lc.equals("legs_accessory") || lc.equals("gloves") || lc.equals("mace");
    }

    private static boolean isCosmeticCat(String c) {
        for (String s : SLOT_ORDER) {
            if (s.equals(c)) {
                return true;
            }
        }
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
        } else if (cat == Category.SKIN_CAPE) {
            this.layoutSkinAndCape(q);
        } else if (!q.isEmpty() || !isStorePage(cat)) {
            this.layoutModules(cat, q);
        } else {
            this.layoutStore(cat, q);
        }
        this.gridFlush();
        this.contentH = Math.max(0.0f, this.gy - GAP);
        if (cat == Category.COSMETICS) {
            float stickyH = ("name".equalsIgnoreCase(this.cosSubTab) ? 68.0f : 108.0f) + 22.0f + GAP;
            float scrollableListH = Math.max(0.0f, this.contentH - stickyH);
            float viewH = Math.max(1.0f, this.ch - stickyH);
            this.maxScroll = Math.max(0.0f, scrollableListH - viewH + 20.0f);
        } else {
            this.maxScroll = Math.max(0.0f, this.contentH - this.ch + 16.0f);
        }
        this.scrollT = Math.max(0.0f, Math.min(this.scrollT, this.maxScroll));
        this.scrollV = Math.max(0.0f, Math.min(this.scrollV, this.maxScroll));
        this.hasPetActive = false;
        for (Cell c : this.cells) {
            if (c.kind == K_PET_ACTIVE) {
                this.hasPetActive = true;
                break;
            }
        }
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
            c.hasSettings = !m.settings().isEmpty();
        }
    }

    private void layoutDetail() {
        Module m = this.detail;
        Cell h = this.addFull(K_DETAIL, m.risk != null ? 54.0f : 42.0f);
        h.module = m;
        if (m instanceof dev.kollegen.client.mods.modules.ArmorTransparency at) {
            this.addFull(K_ARMOR_TAB, 24.0f);
            for (Setting s : at.getActiveSettings()) {
                Cell c = this.addFull(K_SETTING, 30.0f);
                c.setting = s;
                c.module = m;
            }
            return;
        }
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
                c.itemId = str(it, "id", "");
                c.itemCat = str(it, "category", "");
                c.owned = false;
                c.rarity = rarityColor(str(it, "rarity", ""));
                c.price = num(it, "price", 0);
            }
            return;
        }
        if (cat == Category.COSMETICS) {
            this.addFull(K_COS_TAB, 22.0f);

            this.addFull(K_COS_PREVIEW, "name".equalsIgnoreCase(this.cosSubTab) ? 68.0f : 138.0f);

            Set<String> pkgOwned = new HashSet<String>();
            for (JsonObject it : all) {
                if ("package".equals(str(it, "category", "")) && (bool(it, "owned") || bool(it, "is_owned")) && it.has("data") && it.getAsJsonObject("data").has("items")) {
                    for (JsonElement itemEl : it.getAsJsonObject("data").getAsJsonArray("items")) {
                        pkgOwned.add(itemEl.getAsString());
                    }
                }
            }

            List<JsonObject> active = new ArrayList<JsonObject>();
            List<JsonObject> rest = new ArrayList<JsonObject>();
            for (JsonObject it : all) {
                String icat = str(it, "category", "");
                if ("cape".equalsIgnoreCase(icat) || "pet".equalsIgnoreCase(icat) || "package".equalsIgnoreCase(icat)) {
                    continue;
                }
                boolean owned = bool(it, "owned") || bool(it, "is_owned") || pkgOwned.contains(str(it, "id", ""));
                if (!owned || !isCosmeticCat(icat) || !matchQuery(it, q)) {
                    continue;
                }
                boolean isName = isNameCosmetic(icat);
                boolean isSkin = isSkinCosmetic(icat);
                if ("name".equalsIgnoreCase(this.cosSubTab) && !isName) {
                    continue;
                }
                if ("skin".equalsIgnoreCase(this.cosSubTab) && !isSkin) {
                    continue;
                }

                if (str(it, "id", "").equals(this.equippedId(icat))) {
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
                    c.itemId = str(it, "id", "");
                    c.itemCat = str(it, "category", "");
                    c.owned = true;
                    c.rarity = rarityColor(str(it, "rarity", ""));
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
                c.itemId = str(it, "id", "");
                c.itemCat = slot;
                c.owned = true;
                c.rarity = rarityColor(str(it, "rarity", ""));
            }
            if (active.isEmpty() && rest.isEmpty() && this.storeCache != null) {
                Cell e = this.addFull(K_TEXT, 30.0f);
                e.text = "name".equalsIgnoreCase(this.cosSubTab) ? "Noch keine Name-Cosmetics im Besitz." : "Noch keine Skin-Cosmetics im Besitz.";
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
        Cell tt = this.addFull(K_GAME, 64.0f);
        tt.data = new JsonObject();
        tt.data.addProperty("game", dev.kollegen.client.game.TicTacToeScreen.ID);
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
        if (c.setting instanceof SliderSetting || c.setting instanceof StringSetting || c.setting instanceof NumberSetting) {
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
        if (m.isLocked()) {
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
        if (c.kind == K_COS_TAB) {
            return this.cy;
        }
        if (c.kind == K_COS_PREVIEW) {
            return this.cy + 22.0f + GAP;
        }
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
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
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
            int sx = this.searchX();
            int sw = Math.max(50, this.rightButtonsX() - 8 - sx);
            if (hit(mx, my, sx, this.py + 8, sw, 20)) {
                this.search.setFocused(true);
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
            Category cat = this.cats[this.category];
            float stickyH = (cat == Category.COSMETICS) ? (("name".equalsIgnoreCase(this.cosSubTab) ? 68.0f : 108.0f) + 22.0f + GAP) : 0.0f;
            if (this.maxScroll > 0 && hit(mx, my, this.cx + this.cw - 8, this.cy + stickyH, 10, this.ch - stickyH)) {
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
        Category cat = this.cats[this.category];
        float stickyH = (cat == Category.COSMETICS) ? (("name".equalsIgnoreCase(this.cosSubTab) ? 68.0f : 108.0f) + 22.0f + GAP) : 0.0f;
        float trackY = this.cy + stickyH;
        float trackH = Math.max(1.0f, this.ch - stickyH);
        float thumbH = Math.max(18.0f, trackH * trackH / (trackH + this.maxScroll));
        float r = (float) ((my - trackY - thumbH / 2.0) / Math.max(1.0f, trackH - thumbH));
        r = Math.max(0.0f, Math.min(1.0f, r));
        this.scrollT = r * this.maxScroll;
        this.scrollV = this.scrollT;
    }

    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (this.draggingBar && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            this.dragTo(event.y());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            this.draggingBar = false;
        }
        return super.mouseReleased(event);
    }

    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (this.maxScroll > 0) {
            this.scrollT = Math.max(0.0f, Math.min(this.maxScroll, this.scrollT - (float) (vertical * 32.0)));
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    private boolean clickCells(double mx, double my) {
        Category cat = this.cats[this.category];
        float stickyH = (cat == Category.COSMETICS) ? (("name".equalsIgnoreCase(this.cosSubTab) ? 68.0f : 108.0f) + 22.0f + GAP) : 0.0f;
        for (Cell c : this.cells) {
            float X = this.cx + c.x;
            float Y = this.cellScreenY(c);
            if (c.kind != K_COS_TAB && c.kind != K_COS_PREVIEW) {
                if (cat == Category.COSMETICS && Y < this.cy + stickyH) {
                    continue;
                }
            }
            if (Y + c.h < this.cy || Y > this.cy + this.ch) {
                continue;
            }
            if (!hit(mx, my, X, Y, c.w, c.h)) {
                continue;
            }
            switch (c.kind) {
                case K_MOD: {
                    Module m = c.module;
                    boolean onSwitch = hit(mx, my, X + c.w - 40, Y, 40, 24);
                    if (onSwitch && !m.isLocked()) {
                        this.toggleModule(m);
                    } else if (m.settings().isEmpty() && !m.isLocked()) {
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
                        if (dev.kollegen.client.game.TicTacToeScreen.ID.equals(game)) {
                            Minecraft.getInstance().setScreenAndShow(new dev.kollegen.client.game.TicTacToeScreen(this));
                            return true;
                        }
                    } catch (Throwable ignored) {
                    }
                    return false;
                }
                case K_SKIN_ITEM: {
                    String skinName = str(c.data, "name", "");
                    if (!skinName.isEmpty()) {
                        this.activateSkinInGame(skinName);
                    }
                    return true;
                }
                case K_CAPE_ITEM: {
                    if (c.data != null) {
                        this.equipCapeInGame(c.data);
                    }
                    return true;
                }
                case K_SKIN_CAPE_TOP_TAB: {
                    float tw = c.w / 2.0f;
                    if (hit(mx, my, X, Y, tw, c.h)) {
                        this.skinCapeMode = "skins";
                        this.relayout();
                        return true;
                    } else if (hit(mx, my, X + tw, Y, tw, c.h)) {
                        this.skinCapeMode = "capes";
                        this.relayout();
                        return true;
                    }
                    return false;
                }
                case K_CAPE_TAB: {
                    String[] tabs = new String[]{"community", "all", "my_capes", "gekauft", "vanilla", "favorites"};
                    float tw = c.w / tabs.length;
                    for (int i = 0; i < tabs.length; i++) {
                        if (hit(mx, my, X + i * tw, Y, tw, c.h)) {
                            this.capeCatFilter = tabs[i];
                            this.relayout();
                            return true;
                        }
                    }
                    return false;
                }
                case K_COS_TAB: {
                    float tw = c.w / 2.0f;
                    if (hit(mx, my, X, Y, tw, c.h)) {
                        this.cosSubTab = "name";
                        this.relayout();
                        return true;
                    } else if (hit(mx, my, X + tw, Y, tw, c.h)) {
                        this.cosSubTab = "skin";
                        this.relayout();
                        return true;
                    }
                    return false;
                }
                case K_ARMOR_TAB: {
                    float tw = c.w / 2.0f;
                    if (hit(mx, my, X, Y, tw, c.h)) {
                        dev.kollegen.client.mods.modules.ArmorTransparency.activeSubTab = 0;
                        this.relayout();
                        return true;
                    } else if (hit(mx, my, X + tw, Y, tw, c.h)) {
                        dev.kollegen.client.mods.modules.ArmorTransparency.activeSubTab = 1;
                        this.relayout();
                        return true;
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
        if (this.petNameBox != null && this.petNameBox.isFocused() && (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER || ki.isConfirmation())) {
            if (!this.petNamePetId.isEmpty()) {
                this.savePetName(this.petNamePetId);
            }
            return true;
        }
        if (KeybindSetting.capturing != null) {
            KeybindSetting cap = KeybindSetting.capturing;
            cap.value = (key == InputConstants.KEY_ESCAPE || ki.isEscape()) ? -1 : key;
            KeybindSetting.capturing = null;
            this.buildWidgets();
            return true;
        }
        if (key == InputConstants.KEY_ESCAPE || ki.isEscape()) {
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
        if (!typing && this.detail == null && (key == InputConstants.KEY_LEFT || ki.isLeft()) && this.category > 0) {
            this.switchCategory(this.category - 1);
            return true;
        }
        if (!typing && this.detail == null && (key == InputConstants.KEY_RIGHT || ki.isRight()) && this.category < this.cats.length - 1) {
            this.switchCategory(this.category + 1);
            return true;
        }
        if (!typing && (key == InputConstants.KEY_UP || ki.isUp() || key == InputConstants.KEY_PAGEUP) && this.maxScroll > 0) {
            float delta = (key == InputConstants.KEY_PAGEUP) ? (this.ch * 0.8f) : 32.0f;
            this.scrollT = Math.max(0.0f, Math.min(this.maxScroll, this.scrollT - delta));
            return true;
        }
        if (!typing && (key == InputConstants.KEY_DOWN || ki.isDown() || key == InputConstants.KEY_PAGEDOWN) && this.maxScroll > 0) {
            float delta = (key == InputConstants.KEY_PAGEDOWN) ? (this.ch * 0.8f) : 32.0f;
            this.scrollT = Math.max(0.0f, Math.min(this.maxScroll, this.scrollT + delta));
            return true;
        }
        if (!typing && this.detail == null && (key == InputConstants.KEY_TAB || ki.isCycleFocus())) {
            boolean shift = ki.hasShiftDown();
            int next = shift ? (this.category - 1 + this.cats.length) % this.cats.length : (this.category + 1) % this.cats.length;
            this.switchCategory(next);
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
        if (Math.round(this.scrollV) != this.builtScroll && (this.detail != null || this.hasPetActive)) {
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
        Category cat = this.cats[this.category];
        boolean inCont = this.inContent(mx, my);
        float stickyH = (cat == Category.COSMETICS) ? (("name".equalsIgnoreCase(this.cosSubTab) ? 68.0f : 108.0f) + 22.0f + GAP) : 0.0f;

        if (cat == Category.COSMETICS) {
            g.enableScissor(this.cx - 2, Math.round(this.cy + stickyH), this.cx + this.cw + 2, this.cy + this.ch);
            for (Cell c : this.cells) {
                if (c.kind == K_COS_TAB || c.kind == K_COS_PREVIEW) {
                    continue;
                }
                float X = this.cx + c.x;
                float Y = this.cellScreenY(c);
                if (Y + c.h < this.cy + stickyH || Y > this.cy + this.ch + 2) {
                    continue;
                }
                boolean hov = inCont && hit(mx, my, X, Y, c.w, c.h);
                this.renderCellContent(g, c, X, Y, mx, my, inCont, hov);
            }
            g.disableScissor();

            for (Cell c : this.cells) {
                if (c.kind == K_COS_TAB || c.kind == K_COS_PREVIEW) {
                    float X = this.cx + c.x;
                    float Y = this.cellScreenY(c);
                    boolean hov = inCont && hit(mx, my, X, Y, c.w, c.h);
                    this.renderCellContent(g, c, X, Y, mx, my, inCont, hov);
                }
            }

            if (this.maxScroll > 0) {
                Glass.vanillaScrollbar(g, this.cx + this.cw - 3, Math.round(this.cy + stickyH), 3, Math.round(this.ch - stickyH), this.scrollV, this.maxScroll, false, this.draggingBar);
            }
        } else {
            g.enableScissor(this.cx - 2, this.cy, this.cx + this.cw + 2, this.cy + this.ch);
            for (Cell c : this.cells) {
                float X = this.cx + c.x;
                float Y = this.cellScreenY(c);
                if (Y + c.h < this.cy - 2 || Y > this.cy + this.ch + 2) {
                    continue;
                }
                boolean hov = inCont && hit(mx, my, X, Y, c.w, c.h);
                this.renderCellContent(g, c, X, Y, mx, my, inCont, hov);
            }
            g.disableScissor();

            if (this.maxScroll > 0) {
                Glass.vanillaScrollbar(g, this.cx + this.cw - 3, this.cy, 3, this.ch, this.scrollV, this.maxScroll, false, this.draggingBar);
            }
        }
    }

    private void renderCellContent(GuiGraphicsExtractor g, Cell c, float X, float Y, int mx, int my, boolean inCont, boolean hov) {
        switch (c.kind) {
            case K_TITLE:
                this.drawTitle(g, c, X, Y);
                break;
            case K_HEAD:
                if (c.truncTitle == null) {
                    c.truncTitle = c.text;
                    c.extraAnim = this.font.width(c.text);
                }
                Glass.text(g, this.font, c.text, Math.round(X + 2), Math.round(Y + 3), Palette.ACCENT, false);
                float tw = c.extraAnim;
                Glass.rr(g, X + 4 + tw, Y + 7, Math.max(0, c.w - tw - 8), 0.6f, 0.3f, 0x22FFFFFF);
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
            case K_SKIN_ITEM:
                this.drawSkinCard(g, c, X, Y, hov);
                break;
            case K_CAPE_ITEM:
                this.drawCapeCard(g, c, X, Y, hov);
                break;
            case K_CAPE_TAB:
                this.drawCapeTabs(g, c, X, Y, mx, my);
                break;
            case K_SKIN_CAPE_TOP_TAB:
                this.drawSkinCapeTopTabs(g, c, X, Y, mx, my);
                break;
            case K_COS_TAB:
                this.drawCosTabs(g, c, X, Y, mx, my);
                break;
            case K_COS_PREVIEW:
                this.drawCosPreview(g, c, X, Y, mx, my);
                break;
            case K_ARMOR_TAB:
                this.drawArmorTabs(g, c, X, Y, mx, my);
                break;
            default:
                break;
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
        c.hoverAnim = Glass.step(c.hoverAnim, hov ? 1.0f : 0.0f, 14.0f);
        c.extraAnim = Glass.step(c.extraAnim, m.enabled ? 1.0f : 0.0f, 12.0f);
        float h = c.hoverAnim;
        float en = c.extraAnim;
        int tint = Glass.mix(Palette.PANEL, Palette.ACCENT, 0.10f * en + 0.05f * h);
        int border = en > 0.01f ? Glass.a(Palette.ACCENT, 0.7f * en) : (h > 0.01f ? Glass.a(0xFFFFFFFF, 0.35f * h) : 0x38FFFFFF);
        Glass.glassCard(g, X, Y, c.w, c.h, 9, tint, Glass.opacity * (0.85f + 0.3f * h), 0.0f, border);
        if (en > 0.01f) {
            Glass.rrGrad(g, X + 1, Y + 1, c.w - 2, c.h - 2, 8, Glass.a(Palette.ACCENT, 0.16f * en), 0x00000000);
        }
        int tx = Math.round(X + 10);
        if (c.truncTitle == null) {
            int maxW = (int) (c.w - 20 - 34);
            c.truncTitle = Glass.trunc(this.font, m.name, maxW);
            c.truncDesc = Glass.trunc(this.font, m.description, (int) (c.w - 38));
        }
        Glass.textBold(g, this.font, c.truncTitle, tx, Math.round(Y + 9), Palette.TEXT, true);
        Glass.text(g, this.font, c.truncDesc, tx, Math.round(Y + 23), Palette.MUTED, false);
        if (m.isLocked()) {
            Icons.draw(g, Icons.LOCK, X + c.w - 28, Y + 8, 12, Palette.MUTED);
        } else {
            GlassToggle.drawSwitch(g, X + c.w - 10 - 24, Y + 8, 24, 13, en, 0.0f);
        }
        if (c.hasSettings) {
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
        if (m.isLocked()) {
            Icons.draw(g, Icons.LOCK, X + c.w - 26, Y + 6, 14, Palette.MUTED);
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
        Glass.pill(g, x, y, w, h, hov ? 1.0f : 0.0f, primary, false);
        Glass.textC(g, this.font, Glass.trunc(this.font, label, (int) w - 6), Math.round(x + w / 2), Math.round(y + (h - 8) / 2.0f), primary ? 0xFFFFFFFF : Palette.TEXT, false);
    }

    private void drawPill(GuiGraphicsExtractor g, Cell c, float x, float y, float w, float h, String label, boolean primary, boolean hov) {
        c.extraAnim = Glass.step(c.extraAnim, hov ? 1.0f : 0.0f, 14.0f);
        Glass.pill(g, x, y, w, h, c.extraAnim, primary, false);
        Glass.textC(g, this.font, label, Math.round(x + w / 2), Math.round(y + (h - 8) / 2.0f), primary ? 0xFFFFFFFF : Palette.TEXT, false);
    }

    private void drawItemCard(GuiGraphicsExtractor g, Cell c, float X, float Y, boolean hov) {
        String id = c.itemId;
        String cat = c.itemCat;
        boolean eq = c.owned && id.equals(this.equippedId(cat));
        int rar = c.rarity;
        c.hoverAnim = Glass.step(c.hoverAnim, hov ? 1.0f : 0.0f, 14.0f);
        float h = c.hoverAnim;
        int border = eq ? Glass.a(Palette.ACCENT, 0.9f) : (h > 0.01f ? Glass.a(0xFFFFFFFF, 0.35f * h) : 0x38FFFFFF);
        Glass.glassCard(g, X, Y, c.w, c.h, 9, Glass.mix(Palette.PANEL, rar, 0.08f + 0.06f * h), Glass.opacity * (0.85f + 0.3f * h), 0.0f, border);
        Glass.rrGrad(g, X + 5, Y + 7, 2.5f, c.h - 14, 1.2f, rar, Glass.a(rar, 0.35f));
        int tx = Math.round(X + 14);
        if (c.truncTitle == null) {
            c.truncTitle = Glass.trunc(this.font, str(c.data, "name", id), (int) (c.w - 24));
            c.truncDesc = cat == null || cat.isEmpty() ? "" : SLOT_LABELS.getOrDefault(cat, cat);
        }
        Glass.textBold(g, this.font, c.truncTitle, tx, Math.round(Y + 8), Palette.TEXT, true);
        Glass.text(g, this.font, c.truncDesc, tx, Math.round(Y + 20), Palette.MUTED, false);
        float by = Y + c.h - 18;
        if (c.kind == K_STORE) {
            int price = c.price;
            int pts = this.storeCache == null ? -1 : num(this.storeCache, "points", -1);
            boolean poor = pts >= 0 && price > pts;
            Icons.draw(g, Icons.COIN, X + 13, by + 3, 11, rar);
            Glass.text(g, this.font, String.valueOf(price), Math.round(X + 27), Math.round(by + 5), poor ? Palette.DANGER : rar, false);
            this.drawPill(g, c, X + c.w - 8 - 46, by, 46, 15, "Kaufen", true, hov);
        } else {
            this.drawPill(g, c, X + c.w - 8 - 58, by, 58, 15, eq ? "Ablegen" : "Ausrüsten", !eq, hov);
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
        c.hoverAnim = Glass.step(c.hoverAnim, hov ? 1.0f : 0.0f, 14.0f);
        float h = c.hoverAnim;
        int border = h > 0.01f ? Glass.a(0xFFFFFFFF, 0.35f * h) : 0x38FFFFFF;
        Glass.glassCard(g, X, Y, c.w, c.h, 9, Glass.mix(Palette.PANEL, Palette.ACCENT, 0.04f * h), Glass.opacity * (0.85f + 0.3f * h), 0.0f, border);
        int icon = Icons.GRID;
        if (c.truncTitle == null) {
            String title;
            String desc;
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
            } else if (dev.kollegen.client.game.TicTacToeScreen.ID.equals(game)) {
                title = "Tic-Tac-Toe";
                desc = "3 in einer Reihe - gegen smarte KI oder zu zweit";
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
            c.truncTitle = Glass.trunc(this.font, title, (int) (c.w - 60));
            c.truncDesc = Glass.trunc(this.font, desc, (int) (c.w - 48 - 130));
            c.extraAnim = icon;
        } else {
            icon = (int) c.extraAnim;
        }
        Glass.rr(g, X + 7, Y + (c.h - 32) / 2.0f, 32, 32, 9, Glass.a(Palette.ACCENT, 0.18f));
        Icons.draw(g, icon, X + 7 + 32 * 0.22f, Y + (c.h - 32) / 2.0f + 32 * 0.22f, 32 * 0.56f, Palette.ACCENT);
        Glass.textBold(g, this.font, c.truncTitle, Math.round(X + 48), Math.round(Y + 10), Palette.TEXT, true);
        Glass.text(g, this.font, c.truncDesc, Math.round(X + 48), Math.round(Y + 30), Palette.MUTED, false);
        this.drawPill(g, c, X + c.w - 8 - 128, Y + (c.h - 28) / 2.0f, 128, 28, "Spielen", true, hov);
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
        c.hoverAnim = Glass.step(c.hoverAnim, hov ? 1.0f : 0.0f, 14.0f);
        float h = c.hoverAnim;
        int border = h > 0.01f ? Glass.a(0xFFFFFFFF, 0.35f * h) : 0x38FFFFFF;
        Glass.glassCard(g, X, Y, c.w, c.h, 9, Glass.mix(Palette.PANEL, Palette.ACCENT, 0.04f * h), Glass.opacity * (0.85f + 0.3f * h), 0.0f, border);
        this.drawPetPreview(g, id, X + 7, Y + 7, 32);
        if (c.truncTitle == null) {
            c.truncTitle = Glass.trunc(this.font, str(d, "name", id), (int) (c.w - 56));
        }
        Glass.textBold(g, this.font, c.truncTitle, Math.round(X + 46), Math.round(Y + 9), Palette.TEXT, true);
        this.drawPill(g, c, X + 46, Y + c.h - 19, 50, 15, "Rufen", true, hov);
    }

    private void drawActivePet(GuiGraphicsExtractor g, Cell c, float X, float Y, int mx, int my) {
        JsonObject d = c.data;
        String id = str(d, "id", "");
        Glass.glassCard(g, X, Y, c.w, c.h, 10, Glass.mix(Palette.PANEL, Palette.ACCENT, 0.10f), Glass.opacity, 0.0f, Glass.a(Palette.ACCENT, 0.8f));
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

    private void layoutSkinAndCape(String q) {
        this.ensureStore();
        Cell t = this.addFull(K_TITLE, 20.0f);
        t.text = "Skin & Cape wechseln";
        t.data = new JsonObject();
        t.data.addProperty("sub", "Skins & Capes direkt im Spiel auswählen");

        this.addFull(K_SKIN_CAPE_TOP_TAB, 24.0f);
        int cols = this.compact ? 1 : 2;

        if ("skins".equalsIgnoreCase(this.skinCapeMode)) {
            Cell inf = this.addFull(K_INFO, 18.0f);
            inf.text = "Skin-Bibliothek";

            List<JsonObject> skins = this.loadSkinsFromConfig();
            for (JsonObject skin : skins) {
                String name = str(skin, "name", "");
                if (!q.isEmpty() && !name.toLowerCase().contains(q.toLowerCase())) {
                    continue;
                }
                Cell sc = this.addGrid(K_SKIN_ITEM, cols, 48.0f);
                sc.data = skin;
            }
        } else {
            this.addFull(K_CAPE_TAB, 22.0f);
            Cell inf = this.addFull(K_INFO, 18.0f);
            if ("my_capes".equalsIgnoreCase(this.capeCatFilter)) {
                inf.text = "Meine Capes";
            } else if ("community".equalsIgnoreCase(this.capeCatFilter)) {
                inf.text = "Community Capes";
            } else if ("favorites".equalsIgnoreCase(this.capeCatFilter)) {
                inf.text = "Favoriten";
            } else if ("vanilla".equalsIgnoreCase(this.capeCatFilter)) {
                inf.text = "Vanilla Minecraft Capes";
            } else if ("gekauft".equalsIgnoreCase(this.capeCatFilter)) {
                inf.text = "Gekaufte Store Capes";
            } else {
                inf.text = "Alle Capes";
            }

            if ("community".equalsIgnoreCase(this.capeCatFilter)) {
                Cell eqCell = this.addGrid(K_CAPE_ITEM, cols, 50.0f);
                JsonObject eqObj = new JsonObject();
                if (this.inGameActiveCape != null && !this.inGameActiveCape.isEmpty() && !"none".equalsIgnoreCase(this.inGameActiveCape)) {
                    eqObj.addProperty("id", this.inGameActiveCape);
                    eqObj.addProperty("name", "Aktuell ausgerüstet");
                    eqObj.addProperty("author", this.inGameActiveCape);
                    eqObj.addProperty("category", "community");
                } else {
                    eqObj.addProperty("id", "none");
                    eqObj.addProperty("name", "Kein Cape");
                    eqObj.addProperty("author", "Umhang ablegen");
                    eqObj.addProperty("category", "community");
                }
                eqCell.data = eqObj;
            }

            List<JsonObject> capes = this.loadCapesFromConfig();
            for (JsonObject cape : capes) {
                String id = str(cape, "id", "");
                String name = str(cape, "name", id);
                String author = str(cape, "author", "");
                String cat = str(cape, "category", "community");
                boolean fav = cape.has("favorite") && cape.get("favorite").getAsBoolean();

                if ("my_capes".equalsIgnoreCase(this.capeCatFilter) && !"my_capes".equalsIgnoreCase(cat) && !"custom".equalsIgnoreCase(cat) && !"my".equalsIgnoreCase(cat)) {
                    continue;
                }
                if ("community".equalsIgnoreCase(this.capeCatFilter) && !"community".equalsIgnoreCase(cat)) {
                    continue;
                }
                if ("favorites".equalsIgnoreCase(this.capeCatFilter) && !fav) {
                    continue;
                }
                if ("vanilla".equalsIgnoreCase(this.capeCatFilter) && !"vanilla".equalsIgnoreCase(cat)) {
                    continue;
                }
                if ("gekauft".equalsIgnoreCase(this.capeCatFilter) && !"gekauft".equalsIgnoreCase(cat) && !"store".equalsIgnoreCase(cat)) {
                    continue;
                }

                if (!q.isEmpty()) {
                    String matchStr = (name + " " + author + " " + cat).toLowerCase();
                    if (!matchStr.contains(q.toLowerCase())) {
                        continue;
                    }
                }

                Cell cc = this.addGrid(K_CAPE_ITEM, cols, 50.0f);
                cc.data = cape;
            }
        }
    }

    private void drawSkinCapeTopTabs(GuiGraphicsExtractor g, Cell c, float X, float Y, int mx, int my) {
        String[] tabs = new String[]{"skins", "capes"};
        String[] labels = new String[]{"Skins", "Capes"};
        float tw = c.w / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            float tx = X + i * tw;
            boolean active = tabs[i].equalsIgnoreCase(this.skinCapeMode);
            boolean hov = hit(mx, my, tx, Y, tw - 2, c.h);
            int bg = active ? Glass.a(Palette.ACCENT, 0.50f) : (hov ? Glass.a(0xFFFFFF, 0.12f) : Glass.a(0x000000, 0.30f));
            int border = active ? Glass.a(Palette.ACCENT, 0.9f) : (hov ? 0x66FFFFFF : 0x22FFFFFF);
            Glass.rr(g, tx, Y, tw - 2, c.h, 6, bg);
            Glass.ring(g, tx, Y, tw - 2, c.h, 6, 0.5f, border);
            int textColor = active ? 0xFFFFFFFF : (hov ? Palette.TEXT : Palette.MUTED);
            Glass.textC(g, this.font, labels[i], Math.round(tx + (tw - 2) / 2.0f), Math.round(Y + (c.h - 8) / 2.0f), textColor, active);
        }
    }

    private void drawArmorTabs(GuiGraphicsExtractor g, Cell c, float X, float Y, int mx, int my) {
        String[] labels = new String[]{"Eigene", "Andere"};
        float tw = c.w / 2.0f;
        for (int i = 0; i < 2; i++) {
            float tx = X + i * tw;
            boolean active = i == dev.kollegen.client.mods.modules.ArmorTransparency.activeSubTab;
            boolean hov = hit(mx, my, tx, Y, tw - 2, c.h);
            int bg = active ? Glass.a(Palette.ACCENT, 0.50f) : (hov ? Glass.a(0xFFFFFF, 0.12f) : Glass.a(0x000000, 0.30f));
            int border = active ? Glass.a(Palette.ACCENT, 0.9f) : (hov ? 0x66FFFFFF : 0x22FFFFFF);
            Glass.rr(g, tx, Y, tw - 2, c.h, 6, bg);
            Glass.ring(g, tx, Y, tw - 2, c.h, 6, 0.5f, border);
            int textColor = active ? 0xFFFFFFFF : (hov ? Palette.TEXT : Palette.MUTED);
            Glass.textC(g, this.font, labels[i], Math.round(tx + (tw - 2) / 2.0f), Math.round(Y + (c.h - 8) / 2.0f), textColor, active);
        }
    }

    private void drawCapeTabs(GuiGraphicsExtractor g, Cell c, float X, float Y, int mx, int my) {
        String[] tabs = new String[]{"community", "all", "my_capes", "gekauft", "vanilla", "favorites"};
        String[] labels = new String[]{"Community", "Alle", "Meine", "Gekauft", "Vanilla", "Favoriten"};
        float tw = c.w / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            float tx = X + i * tw;
            boolean active = tabs[i].equalsIgnoreCase(this.capeCatFilter);
            boolean hov = hit(mx, my, tx, Y, tw - 2, c.h);
            int bg = active ? Glass.a(Palette.ACCENT, 0.45f) : (hov ? Glass.a(0xFFFFFF, 0.12f) : Glass.a(0x000000, 0.25f));
            int border = active ? Glass.a(Palette.ACCENT, 0.9f) : (hov ? 0x66FFFFFF : 0x22FFFFFF);
            Glass.rr(g, tx, Y, tw - 2, c.h, 6, bg);
            Glass.ring(g, tx, Y, tw - 2, c.h, 6, 0.5f, border);
            int textColor = active ? 0xFFFFFFFF : (hov ? Palette.TEXT : Palette.MUTED);
            Glass.textC(g, this.font, labels[i], Math.round(tx + (tw - 2) / 2.0f), Math.round(Y + (c.h - 8) / 2.0f), textColor, active);
        }
    }

    private void drawCosTabs(GuiGraphicsExtractor g, Cell c, float X, float Y, int mx, int my) {
        String[] tabs = new String[]{"name", "skin"};
        String[] labels = new String[]{"Name Cosmetics", "Skin Cosmetics"};
        float tw = c.w / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            float tx = X + i * tw;
            boolean active = tabs[i].equalsIgnoreCase(this.cosSubTab);
            boolean hov = hit(mx, my, tx, Y, tw - 2, c.h);
            int bg = active ? Glass.a(Palette.ACCENT, 0.45f) : (hov ? Glass.a(0xFFFFFF, 0.12f) : Glass.a(0x000000, 0.25f));
            int border = active ? Glass.a(Palette.ACCENT, 0.9f) : (hov ? 0x66FFFFFF : 0x22FFFFFF);
            Glass.rr(g, tx, Y, tw - 2, c.h, 6, bg);
            Glass.ring(g, tx, Y, tw - 2, c.h, 6, 0.5f, border);
            int textColor = active ? 0xFFFFFFFF : (hov ? Palette.TEXT : Palette.MUTED);
            Glass.textC(g, this.font, labels[i], Math.round(tx + (tw - 2) / 2.0f), Math.round(Y + (c.h - 8) / 2.0f), textColor, active);
        }
    }

    private void drawCosPreview(GuiGraphicsExtractor g, Cell c, float X, float Y, int mx, int my) {
        Glass.glassCard(g, X, Y, c.w, c.h, 8, Glass.a(0x000000, 0.35f), Glass.opacity * 0.9f, 0.0f, 0x33FFFFFF);
        if ("name".equalsIgnoreCase(this.cosSubTab)) {
            Glass.text(g, this.font, "VORSCHAU · NAMETAG", Math.round(X + 10), Math.round(Y + 5), Palette.MUTED, false);

            Minecraft mc = Minecraft.getInstance();
            String pName = mc.getUser() != null ? mc.getUser().getName() : "Spieler";
            String title = "";
            int titleColor = Palette.TEXT;
            int nameColor = Palette.TEXT;
            String badge = "";
            int badgeColor = Palette.ACCENT;
            String sticker = "";

            if (this.storeCache != null && this.storeCache.has("catalog")) {
                String eqTitle = this.equippedId("title");
                String eqColor = this.equippedId("name_color");
                String eqBadge = this.equippedId("badge");
                String eqSticker = this.equippedId("sticker");

                for (JsonElement el : this.storeCache.getAsJsonArray("catalog")) {
                    if (!el.isJsonObject()) continue;
                    JsonObject it = el.getAsJsonObject();
                    String id = str(it, "id", "");
                    if (id.equals(eqTitle)) {
                        title = str(it, "name", "");
                        if (it.has("data") && it.getAsJsonObject("data").has("color")) {
                            titleColor = parseHex(str(it.getAsJsonObject("data"), "color", "#ffffff"), Palette.TEXT);
                        }
                    } else if (id.equals(eqColor)) {
                        if (it.has("data") && it.getAsJsonObject("data").has("color")) {
                            nameColor = parseHex(str(it.getAsJsonObject("data"), "color", "#ffffff"), Palette.TEXT);
                        }
                    } else if (id.equals(eqBadge)) {
                        if (it.has("data") && it.getAsJsonObject("data").has("icon")) {
                            badge = str(it.getAsJsonObject("data"), "icon", "");
                        }
                        if (it.has("data") && it.getAsJsonObject("data").has("color")) {
                            badgeColor = parseHex(str(it.getAsJsonObject("data"), "color", "#ffc400"), Palette.ACCENT);
                        }
                    } else if (id.equals(eqSticker)) {
                        if (it.has("data") && it.getAsJsonObject("data").has("icon")) {
                            sticker = str(it.getAsJsonObject("data"), "icon", "");
                        }
                    }
                }
            }

            if (!title.isEmpty()) {
                int tw = this.font.width(title);
                Glass.textBold(g, this.font, title, Math.round(X + (c.w - tw) / 2.0f), Math.round(Y + 17), titleColor, false);
            }

            int nw = this.font.width(pName);
            int badgeW = badge.isEmpty() ? 0 : this.font.width(badge + " ");
            int stickerW = sticker.isEmpty() ? 0 : this.font.width(" " + sticker);
            int midContentW = 12 + badgeW + nw + stickerW;
            float midX = X + (c.w - midContentW) / 2.0f;
            float midY = Y + (title.isEmpty() ? 24.0f : 31.0f);
            Glass.rr(g, midX - 6, midY - 2, midContentW + 12, 16, 4, Glass.a(0x000000, 0.55f));

            Icons.draw(g, Icons.SPARKLE, midX, midY + 3, 9, Palette.ACCENT);
            float curX = midX + 12;
            if (!badge.isEmpty()) {
                Glass.text(g, this.font, badge + " ", Math.round(curX), Math.round(midY + 2), badgeColor, false);
                curX += badgeW;
            }
            Glass.textBold(g, this.font, pName, Math.round(curX), Math.round(midY + 2), nameColor, false);
            curX += nw;
            if (!sticker.isEmpty()) {
                Glass.text(g, this.font, " " + sticker, Math.round(curX), Math.round(midY + 2), Palette.TEXT, false);
            }

            int lvl = this.storeCache != null ? num(this.storeCache, "level", 1) : 1;
            String lvlText = "[lv " + lvl + "]";
            int lw = this.font.width(lvlText);
            float botY = title.isEmpty() ? (midY + 20.0f) : (midY + 18.0f);
            Glass.text(g, this.font, lvlText, Math.round(X + (c.w - lw) / 2.0f), Math.round(botY), 0xFF7EE787, false);
        } else {
            Glass.text(g, this.font, "VORSCHAU · SKIN COSMETICS", Math.round(X + 10), Math.round(Y + 6), Palette.MUTED, false);

            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                try {
                    InventoryScreen.extractEntityInInventoryFollowsMouse(g, Math.round(X + 10), Math.round(Y + 12), Math.round(X + 96), Math.round(Y + 132), 40, 0.0f, (float) mx, (float) my, mc.player);
                } catch (Throwable ignored) {
                }
            } else {
                Identifier skinTex = SkinCapeTextureManager.getOrLoadSkin(mc.getUser() != null ? mc.getUser().getName() : "Player", null);
                if (skinTex != null) {
                    try {
                        PlayerFaceExtractor.extractRenderState(g, skinTex, Math.round(X + 22), Math.round(Y + 30), 56, true, false, -1);
                    } catch (Throwable ignored) {
                    }
                }
            }

            float infoX = X + 104;
            float infoY = Y + 16;
            Glass.text(g, this.font, "Ausrüstung im Spiel:", Math.round(infoX), Math.round(infoY), Palette.TEXT, true);

            String[] slots1 = new String[]{"head_accessory", "face_accessory", "shoulder_accessory", "torso_accessory", "back_accessory"};
            String[] slotNames1 = new String[]{"Kopf", "Gesicht", "Schulter", "Oberkörper", "Rücken"};
            String[] slots2 = new String[]{"legs_accessory", "gloves", "shield", "aura"};
            String[] slotNames2 = new String[]{"Unterkörper", "Handschuhe", "Schild", "Aura"};

            float col2X = infoX + Math.max(100.0f, (c.w - 114.0f) / 2.0f);
            float rowY = infoY + 14;
            for (int i = 0; i < 5; i++) {
                String eqId1 = this.equippedId(slots1[i]);
                String eqName1 = this.resolveCosmeticName(eqId1);
                String txt1 = Glass.trunc(this.font, slotNames1[i] + ": " + eqName1, (int) Math.max(70, col2X - infoX - 6));
                Glass.text(g, this.font, txt1, Math.round(infoX), Math.round(rowY), eqId1.isEmpty() ? Palette.MUTED : Palette.ACCENT, false);

                if (i < slots2.length) {
                    String eqId2 = this.equippedId(slots2[i]);
                    String eqName2 = this.resolveCosmeticName(eqId2);
                    String txt2 = Glass.trunc(this.font, slotNames2[i] + ": " + eqName2, (int) Math.max(70, X + c.w - col2X - 8));
                    Glass.text(g, this.font, txt2, Math.round(col2X), Math.round(rowY), eqId2.isEmpty() ? Palette.MUTED : Palette.ACCENT, false);
                }

                rowY += 12;
            }
        }
    }

    private String resolveCosmeticName(String eqId) {
        if (eqId == null || eqId.isEmpty()) return "Keine";
        if (this.storeCache != null && this.storeCache.has("catalog")) {
            for (JsonElement el : this.storeCache.getAsJsonArray("catalog")) {
                if (el.isJsonObject() && str(el.getAsJsonObject(), "id", "").equals(eqId)) {
                    return str(el.getAsJsonObject(), "name", eqId);
                }
            }
        }
        return eqId;
    }

    private void drawSkinCard(GuiGraphicsExtractor g, Cell c, float X, float Y, boolean hov) {
        JsonObject d = c.data;
        String name = str(d, "name", "Skin");
        String model = str(d, "model", "classic");
        String curActive = this.inGameActiveSkin != null ? this.inGameActiveSkin : (Minecraft.getInstance().getUser() != null ? Minecraft.getInstance().getUser().getName() : "");
        boolean eq = name.equalsIgnoreCase(curActive);
        c.hoverAnim = Glass.step(c.hoverAnim, hov ? 1.0f : 0.0f, 14.0f);
        float h = c.hoverAnim;
        int border = eq ? Glass.a(Palette.ACCENT, 0.9f) : (h > 0.01f ? Glass.a(0xFFFFFFFF, 0.35f * h) : 0x38FFFFFF);
        Glass.glassCard(g, X, Y, c.w, c.h, 9, Glass.mix(Palette.PANEL, Palette.ACCENT, 0.05f * h), Glass.opacity * (0.85f + 0.3f * h), 0.0f, border);

        Identifier skinTex = SkinCapeTextureManager.getOrLoadSkin(name, str(d, "file", null));
        if (skinTex != null) {
            try {
                PlayerFaceExtractor.extractRenderState(g, skinTex, Math.round(X + 7), Math.round(Y + 6), 36, true, false, -1);
                Glass.ring(g, X + 7, Y + 6, 36, 36, 4, 0.5f, 0x33FFFFFF);
            } catch (Throwable t) {
                Glass.rr(g, X + 7, Y + 6, 36, 36, 8, Glass.a(Palette.ACCENT, 0.18f));
                Icons.draw(g, Icons.USER, X + 7 + 36 * 0.22f, Y + 6 + 36 * 0.22f, 36 * 0.56f, Palette.ACCENT);
            }
        } else {
            Glass.rr(g, X + 7, Y + 6, 36, 36, 8, Glass.a(Palette.ACCENT, 0.18f));
            Icons.draw(g, Icons.USER, X + 7 + 36 * 0.22f, Y + 6 + 36 * 0.22f, 36 * 0.56f, Palette.ACCENT);
        }

        if (c.truncTitle == null) {
            c.truncTitle = Glass.trunc(this.font, name, (int) (c.w - 120));
            c.truncDesc = "slim".equalsIgnoreCase(model) ? "Slim" : "Classic";
        }
        Glass.textBold(g, this.font, c.truncTitle, Math.round(X + 48), Math.round(Y + 10), Palette.TEXT, true);
        Glass.text(g, this.font, c.truncDesc, Math.round(X + 48), Math.round(Y + 26), Palette.MUTED, false);

        if (eq) {
            Glass.badge(g, Math.round(X + c.w - 8 - 56), Math.round(Y + (c.h - 18) / 2.0f), 56, 18, 7, Palette.GREEN, 0xFFFFFFFF, this.font, "Aktiv");
        } else {
            this.drawPill(g, c, X + c.w - 8 - 66, Y + (c.h - 18) / 2.0f, 66, 18, "Aktivieren", true, hov);
        }
    }

    private void drawCapeCard(GuiGraphicsExtractor g, Cell c, float X, float Y, boolean hov) {
        JsonObject d = c.data;
        String id = str(d, "id", "");
        String name = str(d, "name", id);
        String author = str(d, "author", "Community");
        String cat = str(d, "category", "community");
        String texRaw = str(d, "texture", "");
        if (texRaw.isEmpty() && d.has("preview") && !d.get("preview").isJsonNull()) {
            texRaw = d.get("preview").getAsString();
        } else if (texRaw.isEmpty() && d.has("frames") && d.get("frames").isJsonArray() && d.getAsJsonArray("frames").size() > 0) {
            texRaw = d.getAsJsonArray("frames").get(0).getAsString();
        }

        int uses = num(d, "uses", 0);
        boolean isNone = "none".equalsIgnoreCase(id);
        boolean isUpload = "upload_action".equalsIgnoreCase(id);
        boolean eq = isNone ? (this.inGameActiveCape == null || this.inGameActiveCape.isEmpty()) : (!isUpload && id.equalsIgnoreCase(this.inGameActiveCape));
        c.hoverAnim = Glass.step(c.hoverAnim, hov ? 1.0f : 0.0f, 14.0f);
        float h = c.hoverAnim;
        int border = eq ? Glass.a(Palette.ACCENT, 0.9f) : (h > 0.01f ? Glass.a(0xFFFFFFFF, 0.35f * h) : 0x38FFFFFF);
        Glass.glassCard(g, X, Y, c.w, c.h, 9, Glass.mix(Palette.PANEL, Palette.ACCENT, 0.05f * h), Glass.opacity * (0.85f + 0.3f * h), 0.0f, border);

        float cw = 24.0f;
        float ch = 36.0f;
        float cx = X + 8;
        float cy = Y + (c.h - ch) / 2.0f;
        if (isNone) {
            Glass.rr(g, cx, cy, cw, ch, 4, Glass.a(0x000000, 0.35f));
            Glass.ring(g, cx, cy, cw, ch, 4, 0.5f, 0x44FFFFFF);
            Icons.draw(g, Icons.CLOSE, cx + 5, cy + 11, 14, Palette.MUTED);
        } else if (isUpload) {
            Glass.rr(g, cx, cy, cw, ch, 4, Glass.a(Palette.ACCENT, 0.18f));
            Glass.ring(g, cx, cy, cw, ch, 4, 0.5f, Glass.a(Palette.ACCENT, 0.75f));
            Icons.draw(g, Icons.PLUS, cx + 5, cy + 11, 14, Palette.ACCENT);
        } else {
            Identifier capeTex = SkinCapeTextureManager.getOrLoadCape(id, texRaw);
            boolean isStore = "gekauft".equalsIgnoreCase(cat) || "store".equalsIgnoreCase(cat) || (d.has("model") && !str(d, "model", "").isEmpty());
            if (capeTex != null) {
                try {
                    int[] dims = SkinCapeTextureManager.getDimensions(id);
                    int tw = (dims != null && dims[0] > 0) ? dims[0] : 64;
                    int th = (dims != null && dims[1] > 0) ? dims[1] : 64;
                    Matrix3x2fStack pose = g.pose();
                    pose.pushMatrix();
                    pose.translate(cx, cy);

                    float aspect = (float) tw / (float) th;
                    boolean isVanillaLayout = !isStore && Math.abs(aspect - 2.0f) < 0.25f;

                    if (isVanillaLayout) {
                        float u = 1.0f * ((float) tw / 64.0f);
                        float v = 1.0f * ((float) th / 32.0f);
                        float uw = 10.0f * ((float) tw / 64.0f);
                        float vh = 16.0f * ((float) th / 32.0f);
                        pose.scale(cw / uw, ch / vh);
                        g.blit(RenderPipelines.GUI_TEXTURED, capeTex, 0, 0, u, v, Math.round(uw), Math.round(vh), tw, th);
                    } else {
                        pose.scale(cw / (float) tw, ch / (float) th);
                        g.blit(RenderPipelines.GUI_TEXTURED, capeTex, 0, 0, 0.0f, 0.0f, tw, th, tw, th);
                    }
                    pose.popMatrix();
                    Glass.ring(g, cx, cy, cw, ch, 4, 0.5f, 0x44FFFFFF);
                } catch (Throwable t) {
                    Glass.rr(g, cx, cy, cw, ch, 4, Glass.a(Palette.ACCENT, 0.28f));
                    Glass.ring(g, cx, cy, cw, ch, 4, 0.5f, Glass.a(Palette.ACCENT, 0.75f));
                    Icons.draw(g, Icons.SPARKLE, cx + 5, cy + 11, 14, Palette.ACCENT);
                }
            } else {
                Glass.rr(g, cx, cy, cw, ch, 4, Glass.a(Palette.ACCENT, 0.28f));
                Glass.ring(g, cx, cy, cw, ch, 4, 0.5f, Glass.a(Palette.ACCENT, 0.75f));
                Icons.draw(g, Icons.SPARKLE, cx + 5, cy + 11, 14, Palette.ACCENT);
            }
        }

        if (c.truncTitle == null) {
            c.truncTitle = Glass.trunc(this.font, name, (int) (c.w - 110));
            if (isNone) {
                c.truncDesc = "Kein Umhang";
            } else if (isUpload) {
                c.truncDesc = "Im Launcher hochladen";
            } else if ("community".equalsIgnoreCase(cat) && uses > 0) {
                c.truncDesc = author + " · " + uses + " Downloads";
            } else {
                c.truncDesc = author;
            }
            c.truncDesc = Glass.trunc(this.font, c.truncDesc, (int) (c.w - 110));
        }
        Glass.textBold(g, this.font, c.truncTitle, Math.round(X + 38), Math.round(Y + 11), Palette.TEXT, true);
        Glass.text(g, this.font, c.truncDesc, Math.round(X + 38), Math.round(Y + 27), Palette.MUTED, false);

        if (eq) {
            Glass.badge(g, Math.round(X + c.w - 8 - 56), Math.round(Y + (c.h - 18) / 2.0f), 56, 18, 7, Palette.GREEN, 0xFFFFFFFF, this.font, "Aktiv");
        } else if (isUpload) {
            this.drawPill(g, c, X + c.w - 8 - 66, Y + (c.h - 18) / 2.0f, 66, 18, "Upload", true, hov);
        } else {
            this.drawPill(g, c, X + c.w - 8 - 66, Y + (c.h - 18) / 2.0f, 66, 18, isNone ? "Ablegen" : "Auswählen", true, hov);
        }
    }

    private void activateSkinInGame(String skinName) {
        this.inGameActiveSkin = skinName;
        try {
            Path p = FabricLoader.getInstance().getConfigDir().resolve("kollegen-skins.json");
            JsonObject o = Files.exists(p) ? JsonParser.parseString(Files.readString(p)).getAsJsonObject() : new JsonObject();
            o.addProperty("active", skinName);
            Files.writeString(p, o.toString());
        } catch (Throwable ignored) {
        }
    }

    private void equipCapeInGame(JsonObject d) {
        String capeId = str(d, "id", "");
        String capeTex = str(d, "texture", "");
        String capeModel = str(d, "model", "");
        String cat = str(d, "category", "community");
        if (capeTex.isEmpty() && d.has("preview") && !d.get("preview").isJsonNull()) {
            capeTex = d.get("preview").getAsString();
        } else if (capeTex.isEmpty() && d.has("frames") && d.get("frames").isJsonArray() && d.getAsJsonArray("frames").size() > 0) {
            capeTex = d.getAsJsonArray("frames").get(0).getAsString();
        }

        if ("upload_action".equalsIgnoreCase(capeId)) {
            this.storeMsg = "Eigene Capes können im Launcher hochgeladen werden";
            return;
        }

        if ("none".equalsIgnoreCase(capeId) || capeId.isEmpty()) {
            this.inGameActiveCape = null;
            KollegenPresence.setLocalCape(null, null);
            this.runStoreAction("Cape abgelegt", () -> StoreClient.unequipCategory("cape"));
        } else {
            this.inGameActiveCape = capeId;
            KollegenPresence.setLocalCape(capeTex != null && !capeTex.isEmpty() ? capeTex : capeId, capeModel);
            if ("gekauft".equalsIgnoreCase(cat) || "store".equalsIgnoreCase(cat)) {
                this.runStoreAction("Cape ausgewählt", () -> StoreClient.equipItem(capeId));
            } else {
                this.runStoreAction("Cape ausgewählt", () -> StoreClient.unequipCategory("cape"));
            }
        }
        try {
            Path p = FabricLoader.getInstance().getConfigDir().resolve("kollegen-cape.json");
            JsonObject o = Files.exists(p) ? JsonParser.parseString(Files.readString(p)).getAsJsonObject() : new JsonObject();
            if (this.inGameActiveCape == null) {
                o.add("active", com.google.gson.JsonNull.INSTANCE);
                o.add("cape", com.google.gson.JsonNull.INSTANCE);
            } else {
                o.addProperty("active", this.inGameActiveCape);
                o.add("cape", d);
            }
            Files.writeString(p, o.toString());
        } catch (Throwable ignored) {
        }
    }

    private List<JsonObject> loadSkinsFromConfig() {
        List<JsonObject> list = new ArrayList<JsonObject>();
        try {
            Path p = FabricLoader.getInstance().getConfigDir().resolve("kollegen-skins.json");
            if (!Files.exists(p)) {
                String home = System.getProperty("user.home", "");
                Path lp = Path.of(home, ".local", "share", "kollegenclient", "skins", "index.json");
                if (!Files.exists(lp)) {
                    lp = Path.of(home, ".local", "share", "kollegen-launcher", "skins", "index.json");
                }
                if (Files.exists(lp)) p = lp;
            }
            if (Files.exists(p)) {
                String s = Files.readString(p);
                JsonObject o = JsonParser.parseString(s).getAsJsonObject();
                if (o.has("active") && !o.get("active").isJsonNull()) {
                    this.inGameActiveSkin = o.get("active").getAsString();
                }
                if (o.has("skins") && o.get("skins").isJsonArray()) {
                    for (JsonElement el : o.getAsJsonArray("skins")) {
                        if (el.isJsonObject()) list.add(el.getAsJsonObject());
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        if (list.isEmpty()) {
            JsonObject def = new JsonObject();
            String name = Minecraft.getInstance().getUser() != null ? Minecraft.getInstance().getUser().getName() : "Player";
            def.addProperty("name", name);
            def.addProperty("model", "classic");
            list.add(def);
        }
        return list;
    }

    private List<JsonObject> loadCapesFromConfig() {
        List<JsonObject> list = new ArrayList<JsonObject>();
        try {
            Path p = FabricLoader.getInstance().getConfigDir().resolve("kollegen-cape.json");
            if (!Files.exists(p)) {
                String home = System.getProperty("user.home", "");
                Path lp = Path.of(home, ".local", "share", "kollegenclient", "capes", "kollegen-cape.json");
                if (!Files.exists(lp)) {
                    lp = Path.of(home, ".local", "share", "kollegenclient", "capes", "capes.json");
                }
                if (!Files.exists(lp)) {
                    lp = Path.of(home, ".local", "share", "kollegen-launcher", "capes", "kollegen-cape.json");
                }
                if (!Files.exists(lp)) {
                    lp = Path.of(home, ".local", "share", "kollegen-launcher", "capes", "capes.json");
                }
                if (Files.exists(lp)) p = lp;
            }
            if (Files.exists(p)) {
                String s = Files.readString(p);
                JsonObject o = JsonParser.parseString(s).getAsJsonObject();
                if (o.has("active") && !o.get("active").isJsonNull()) {
                    this.inGameActiveCape = o.get("active").getAsString();
                }
                if (o.has("all_capes") && o.get("all_capes").isJsonArray()) {
                    for (JsonElement el : o.getAsJsonArray("all_capes")) {
                        if (el.isJsonObject()) list.add(el.getAsJsonObject());
                    }
                } else if (o.has("capes") && o.get("capes").isJsonArray()) {
                    for (JsonElement el : o.getAsJsonArray("capes")) {
                        if (el.isJsonObject()) list.add(el.getAsJsonObject());
                    }
                }
                if (o.has("vanilla_capes") && o.get("vanilla_capes").isJsonArray()) {
                    for (JsonElement el : o.getAsJsonArray("vanilla_capes")) {
                        if (el.isJsonObject()) {
                            JsonObject vc = el.getAsJsonObject();
                            String vcId = str(vc, "id", "");
                            boolean exists = false;
                            for (JsonObject ex : list) {
                                if (vcId.equals(str(ex, "id", ""))) {
                                    exists = true;
                                    break;
                                }
                            }
                            if (!exists) list.add(vc);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        JsonObject sc = this.storeCache != null ? this.storeCache : StoreClient.cachedCatalog();
        if (sc != null && sc.has("catalog") && sc.get("catalog").isJsonArray()) {
            Set<String> pkgOwned = new HashSet<String>();
            for (JsonElement el : sc.getAsJsonArray("catalog")) {
                if (!el.isJsonObject()) continue;
                JsonObject it = el.getAsJsonObject();
                if ("package".equals(str(it, "category", "")) && (bool(it, "owned") || bool(it, "is_owned")) && it.has("data") && it.getAsJsonObject("data").has("items")) {
                    for (JsonElement itemEl : it.getAsJsonObject("data").getAsJsonArray("items")) {
                        pkgOwned.add(itemEl.getAsString());
                    }
                }
            }

            for (JsonElement el : sc.getAsJsonArray("catalog")) {
                if (!el.isJsonObject()) continue;
                JsonObject it = el.getAsJsonObject();
                String id = str(it, "id", "");
                boolean owned = bool(it, "owned") || bool(it, "is_owned") || pkgOwned.contains(id);
                if ("cape".equals(str(it, "category", "")) && owned) {
                    boolean exists = false;
                    for (JsonObject ex : list) {
                        if (id.equals(str(ex, "id", ""))) {
                            exists = true;
                            break;
                        }
                    }
                    if (!exists) {
                        JsonObject c = new JsonObject();
                        c.addProperty("id", id);
                        c.addProperty("name", str(it, "name", "Store Cape"));
                        c.addProperty("author", "Kollegen Store");
                        c.addProperty("category", "gekauft");
                        String tex = "";
                        String model = "";
                        if (it.has("data") && it.getAsJsonObject("data").isJsonObject()) {
                            JsonObject itData = it.getAsJsonObject("data");
                            tex = str(itData, "texture", "");
                            model = str(itData, "model", "");
                        }
                        if (tex.isEmpty()) tex = str(it, "texture", "");
                        if (model.isEmpty()) model = str(it, "model", "");
                        c.addProperty("texture", tex);
                        c.addProperty("model", model);
                        list.add(c);
                    }
                }
            }
        }
        if (list.isEmpty()) {
            list.addAll(this.createDefaultCapesList());
        }
        return list;
    }

    private List<JsonObject> createDefaultCapesList() {
        List<JsonObject> defs = new ArrayList<JsonObject>();
        String[][] data = new String[][]{
            {"migrator", "Migrator Cape", "Mojang Studios", "https://textures.minecraft.net/texture/2340c0e03dd66da11e164f6b042da7735e72dc8485f5841b7b0c840f6b9a8a71"},
            {"vanilla", "Vanilla Cape", "Mojang Studios", "https://textures.minecraft.net/texture/1a84ec02161a00c6d71b846e4990c885542b262862d512a839e9f6888c75d4"},
            {"15th_anni", "15th Anniversary", "Mojang Studios", "https://textures.minecraft.net/texture/a35f1377884ff10471b879ec3298ec697b0a7900b1a03058fc3ee422bfcc3e24"},
            {"cherry", "Cherry Blossom Cape", "Mojang Studios", "https://textures.minecraft.net/texture/8f12031959797831345328e3009405a12ae1d1355f30b9614a09382f799969c4"},
            {"tiktok", "TikTok Cape", "TikTok / Mojang", "https://textures.minecraft.net/texture/5e0030514194098eb330c6a5a73b22a0a2df3ba6fa7f0a82b937e2cbfe48e244"},
            {"twitch", "Twitch Cape", "Twitch / Mojang", "https://textures.minecraft.net/texture/41434c441bbcb9472e39cd1332a76f28ac7eb6fc238c9c0250df5fd82cfcf6e6"}
        };
        for (String[] row : data) {
            JsonObject o = new JsonObject();
            o.addProperty("id", "vanilla_" + row[0]);
            o.addProperty("name", row[1]);
            o.addProperty("author", row[2]);
            o.addProperty("category", "vanilla");
            o.addProperty("texture", row[3]);
            o.addProperty("preview", row[3]);
            o.addProperty("uses", 1);
            o.addProperty("favorite", false);
            defs.add(o);
        }
        return defs;
    }
}
