package dev.kollegen.client.menu;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kollegen.client.Version;
import dev.kollegen.client.KollegenMod;
import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.HudModule;
import dev.kollegen.client.mods.Module;
import dev.kollegen.client.mods.ModuleManager;
import dev.kollegen.client.mods.Palette;
import dev.kollegen.client.mods.Setting;
import dev.kollegen.client.presence.KollegenPresence;
import dev.kollegen.client.presence.StoreClient;
import dev.kollegen.client.ui.Glass;
import dev.kollegen.client.ui.GlassButton;
import dev.kollegen.client.ui.GlassToggle;
import dev.kollegen.client.ui.LogoDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class KollegenMenuScreen extends Screen {
    private final Screen parent;

    private final Category[] cats = Category.values();
    private int category = 0;
    private String query = "";
    private final Set<String> expanded = new HashSet<>();

    private static final int PANEL_W = 920;
    private static final int PANEL_H = 620;
    private static final int TAB_H = 36;
    private static final int HEADER_H = 72;
    private static final int CARD_H = 52;
    private static final int SETTING_H = 36;
    private static final int GAP = 8;
    private static final int PADDING = 16;
    private static final int RADIUS = 8;
    private static final int SCROLLBAR_W = 6;
    private static final int SCROLLBAR_AREA_W = 16;
    private static final int TOGGLE_W = 24;
    private static final int TOGGLE_H = 24;
    private static final int GEAR_W = 28;
    private static final int GEAR_H = 28;

    private EditBox search;
    private GlassButton closeBtn;

    private int px, py, pw, ph;
    private int contentY, contentH, maxScroll, scroll = 0;
    private int tabScroll = 0, maxTabScroll = 0;
    private boolean draggingScroll = false, draggingTabScroll = false;
    private int dragStartY, dragStartScroll;
    private long lastRebuild = 0;

    private static final int TAB_W = 110;
    private static final int TAB_GAP = 8;
    private static final int TAB_ARROW_W = 26;

    private float tabAnimProgress = 0f;
    private int prevCategory = 0;

    private boolean tabOverflow() {
        return cats.length * (TAB_W + TAB_GAP) > pw - PADDING * 2;
    }

    private int tabAreaX() {
        return px + PADDING + (tabOverflow() ? TAB_ARROW_W + 6 : 0);
    }

    private int tabAreaW() {
        return pw - PADDING * 2 - (tabOverflow() ? (TAB_ARROW_W + 6) * 2 : 0);
    }

    private int tabMaxScroll() {
        return Math.max(0, cats.length * (TAB_W + TAB_GAP) - tabAreaW());
    }

    private void clampTabScroll() {
        maxTabScroll = tabMaxScroll();
        tabScroll = Math.max(0, Math.min(tabScroll, maxTabScroll));
    }

    private com.google.gson.JsonObject storeCache;
    private long storeAt;
    private boolean storeLoading;
    private String storeMsg = "";
    private volatile boolean pendingRebuild;

    private static final class Entry {
        final boolean isModule;
        final Module module;
        final Setting setting;
        final int y;
        final int h;
        final AbstractWidget widget;
        final int cardIndex;
        final int custom;
        final com.google.gson.JsonObject data;

        Entry(boolean isModule, Module module, Setting setting, int y, int h, AbstractWidget widget, int cardIndex) {
            this(isModule, module, setting, y, h, widget, cardIndex, 0, null);
        }

        Entry(boolean isModule, Module module, Setting setting, int y, int h, AbstractWidget widget, int cardIndex, int custom, com.google.gson.JsonObject data) {
            this.isModule = isModule;
            this.module = module;
            this.setting = setting;
            this.y = y;
            this.h = h;
            this.widget = widget;
            this.cardIndex = cardIndex;
            this.custom = custom;
            this.data = data;
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private final List<Module> visibleModules = new ArrayList<>();

    public KollegenMenuScreen(Screen parent) {
        super(Component.literal("Kollegen Client"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        rebuild();
        search.setFocused(true);
    }

    private int[] panel() {
        int w = Math.min(this.width - 32, Math.max(560, this.width * 3 / 4));
        int h = Math.min(this.height - 32, Math.max(400, this.height * 4 / 5));
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;
        return new int[]{x, y, w, h};
    }

    private void rebuild() {
        long now = System.currentTimeMillis();
        if (now - lastRebuild < 50) return;
        lastRebuild = now;

        clearWidgets();
        entries.clear();
        visibleModules.clear();

        int[] p = panel();
        px = p[0]; py = p[1]; pw = p[2]; ph = p[3];

        int tabBarY = py + HEADER_H;
        int contentTop = tabBarY + TAB_H + GAP;
        int contentBottom = py + ph - PADDING;

        closeBtn = new GlassButton(px + pw - 40, py + 34, 32, 32, Component.literal("✕"), btn -> close());
        addRenderableWidget(closeBtn);

        GlassButton hudBtn = new GlassButton(px + pw - 148, py + 34, 104, 32, Component.literal("HUD bearbeiten"), btn -> {
            HudModule.editMode = true;
            Minecraft.getInstance().setScreenAndShow(new HudEditScreen(this));
        });
        addRenderableWidget(hudBtn);

        search = new EditBox(this.font, px + PADDING + 10, py + 34, pw - PADDING * 2 - 158, 24, Component.literal(""));
        search.setMaxLength(40);
        search.setHint(Component.literal("Modul suchen…"));
        search.setValue(query);
        search.setResponder(t -> {
            query = t;
            scroll = 0;
            rebuild();
        });
        addRenderableWidget(search);
        search.setFocused(true);

        if (cats[category] == Category.STORE || cats[category] == Category.COSMETICS || cats[category] == Category.PETS) {
            buildCustom(contentTop, contentBottom);
            return;
        }

        String q = query.trim().toLowerCase();
        for (Module m : ModuleManager.modules()) {
            if (!q.isEmpty()) {
                if (m.name.toLowerCase().contains(q) || m.description.toLowerCase().contains(q)
                        || m.category.display.toLowerCase().contains(q)) {
                    visibleModules.add(m);
                }
            } else if (m.category == cats[category]) {
                visibleModules.add(m);
            }
        }
        visibleModules.sort((a, b) -> a.name.compareToIgnoreCase(b.name));

        int y = contentTop - scroll;
        int cardIdx = 0;
        for (Module m : visibleModules) {
            boolean vis = y + CARD_H > contentTop && y < contentBottom;
            int contentW = pw - PADDING * 2 - SCROLLBAR_AREA_W;

            int toggleX = px + PADDING + 4;

            int gearX = px + pw - PADDING - SCROLLBAR_AREA_W - GEAR_W - 8;

            if (!m.locked && vis) {
                GlassToggle t = new GlassToggle(toggleX, y + (CARD_H - TOGGLE_H) / 2, TOGGLE_W, TOGGLE_H, m.enabled, on -> {
                    m.enabled = on;
                    if (on) m.onEnable();
                    else m.onDisable();
                    ModuleManager.save();
                });
                addRenderableWidget(t);
            }
            if (vis) {
                GlassButton gear = new GlassButton(gearX, y + (CARD_H - GEAR_H) / 2, GEAR_W, GEAR_H, Component.literal("⚙"), btn -> {
                    if (expanded.contains(m.id)) expanded.remove(m.id);
                    else expanded.add(m.id);
                    rebuild();
                });
                addRenderableWidget(gear);
            }
            entries.add(new Entry(true, m, null, y, CARD_H, null, cardIdx++));
            y += CARD_H + GAP;

            if (expanded.contains(m.id)) {
                List<Setting> sorted = new ArrayList<>(m.settings());
                sorted.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
                for (Setting s : sorted) {
                    boolean sv = y + SETTING_H > contentTop && y < contentBottom;
                    AbstractWidget w = null;
                    if (sv) {
                        int settingW = pw - PADDING * 2 - SCROLLBAR_AREA_W - 20;
                        w = s.buildWidget(px + PADDING + 10, y, pw - PADDING * 2 - SCROLLBAR_AREA_W - 20, SETTING_H, this);
                        addRenderableWidget(w);
                    }
                    entries.add(new Entry(false, m, s, y, SETTING_H, w, cardIdx));
                    y += SETTING_H + GAP / 2;
                }
            }
        }

        contentH = y - contentTop + scroll;
        int visibleH = contentBottom - contentTop;
        maxScroll = Math.max(0, contentH - visibleH);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
    }

    private static final java.util.Map<String, String> SLOT_LABELS = new java.util.HashMap<>();
    private static final java.util.Map<String, String> PET_ICONS = new java.util.HashMap<>();
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
        SLOT_LABELS.put("name_color", "Namensfarbe");
        SLOT_LABELS.put("badge", "Abzeichen");
        SLOT_LABELS.put("sticker", "Aufkleber");
        SLOT_LABELS.put("font", "Schriftart");
        SLOT_LABELS.put("title", "Titel");
        SLOT_LABELS.put("pet", "Haustier");
        String[][] icons = {
                {"pet_allay", "🧚"}, {"pet_blub", "🫧"}, {"pet_cat", "🐱"}, {"pet_dog", "🐶"},
                {"pet_chicken", "🐔"}, {"pet_rabbit", "🐰"}, {"pet_axolotl", "🦎"}, {"pet_bat", "🦇"},
                {"pet_fox", "🦊"}, {"pet_parrot", "🦜"}, {"pet_bee", "🐝"}, {"pet_squid", "🦑"},
                {"pet_nautilus", "🐚"}, {"pet_copper_golem", "🤖"},
        };
        for (String[] kv : icons) PET_ICONS.put(kv[0], kv[1]);
    }

    private static int rarityColor(String rarity) {
        if (rarity == null) return Palette.MUTED;
        switch (rarity.toLowerCase()) {
            case "common": return 0x9AA0A6;
            case "rare": return 0x54AEFF;
            case "epic": return 0xAA55FF;
            case "legendary": return 0xFFAA00;
            default: return Palette.MUTED;
        }
    }

    private static String[] petModesFor(String id) {
        if (id == null) return new String[]{"follow"};
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
        if (m == null) return "Folgen";
        switch (m) {
            case "hover": return "Schweben";
            case "shoulder": return "Schulter";
            case "head": return "Kopf";
            default: return "Folgen";
        }
    }

    private static String str(com.google.gson.JsonObject o, String key, String fallback) {
        try {
            if (o != null && o.has(key) && !o.get(key).isJsonNull()) return o.get(key).getAsString();
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    private static int num(com.google.gson.JsonObject o, String key, int fallback) {
        try {
            if (o != null && o.has(key) && !o.get(key).isJsonNull()) return o.get(key).getAsInt();
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    private static boolean bool(com.google.gson.JsonObject o, String key) {
        try {
            if (o != null && o.has(key) && !o.get(key).isJsonNull()) return o.get(key).getAsBoolean();
        } catch (Throwable ignored) {
        }
        return false;
    }

    private void ensureStore() {
        long now = System.currentTimeMillis();
        if (storeCache != null && now - storeAt < 20000) return;
        if (storeLoading) return;
        storeLoading = true;
        Thread t = new Thread(() -> {
            try {
                com.google.gson.JsonObject data = StoreClient.catalog();
                if (data != null) {
                    storeCache = data;
                    storeAt = System.currentTimeMillis();
                }
            } catch (Throwable ignored) {
            }
            storeLoading = false;
            pendingRebuild = true;
        }, "kollegen-store-fetch");
        t.setDaemon(true);
        t.start();
    }

    private void runStoreAction(String msg, java.util.function.Supplier<com.google.gson.JsonObject> op) {
        storeMsg = "Einen Moment …";
        Thread t = new Thread(() -> {
            try {
                com.google.gson.JsonObject r = op.get();
                if (r == null) {
                    storeMsg = "Fehler: keine Verbindung oder nicht angemeldet (Launcher-Login nötig).";
                } else if (r.has("error") && !r.get("error").isJsonNull()) {
                    storeMsg = "Fehler: " + r.get("error").getAsString();
                } else {
                    storeMsg = msg;
                }
            } catch (Throwable e) {
                storeMsg = "Fehler: " + String.valueOf(e.getMessage());
            }
            try {
                com.google.gson.JsonObject data = StoreClient.catalog();
                if (data != null) {
                    storeCache = data;
                    storeAt = System.currentTimeMillis();
                }
            } catch (Throwable ignored) {
            }
            try {
                KollegenPresence.refreshAsync();
            } catch (Throwable ignored) {
            }
            pendingRebuild = true;
        }, "kollegen-store-action");
        t.setDaemon(true);
        t.start();
        rebuild();
    }

    private static final String[] SLOT_ORDER = {"cape", "shield", "back_accessory", "head_accessory", "face_accessory",
            "aura", "shoulder_accessory", "torso_accessory", "legs_accessory",
            "name_color", "badge", "sticker", "font", "title"};
    private static final String[] PET_COLORS = {"#ffffff", "#ffd700", "#ff5f56", "#39d7ff", "#c77dff", "#7ee787", "#ff6b9d"};

    private EditBox petNameBox;
    private String petNamePetId = "";
    private String petNameValue = "";

    private void buildCustom(int contentTop, int contentBottom) {
        ensureStore();
        int y = contentTop - scroll;
        String info = storeMsg != null && !storeMsg.isEmpty() ? storeMsg : null;
        if (storeCache == null) {
            info = storeLoading ? "Lade Store \u2026" : "Store nicht erreichbar. Im Launcher anmelden, dann Tab wechseln.";
        } else if (info == null) {
            int pts = num(storeCache, "points", -1);
            info = pts >= 0 ? ("Guthaben: " + pts + " Punkte" + (StoreClient.token() == null ? " (nur Katalog, Login im Launcher zum Kaufen)" : "")) : "Katalog";
        }
        com.google.gson.JsonObject infoData = new com.google.gson.JsonObject();
        infoData.addProperty("text", info);
        entries.add(new Entry(false, null, null, y, 26, null, 0, 5, infoData));
        y += 26 + GAP / 2;

        Category cat = cats[category];
        String q = query.trim().toLowerCase();
        com.google.gson.JsonArray items = null;
        try {
            if (storeCache != null && storeCache.has("catalog") && storeCache.get("catalog").isJsonArray()) {
                items = storeCache.getAsJsonArray("catalog");
            }
        } catch (Throwable ignored) {
        }
        java.util.List<com.google.gson.JsonObject> all = new java.util.ArrayList<>();
        if (items != null) {
            for (com.google.gson.JsonElement el : items) {
                if (el == null || !el.isJsonObject()) continue;
                all.add(el.getAsJsonObject());
            }
        }
        if (cat == Category.COSMETICS && storeCache != null) {
            com.google.gson.JsonObject banner = new com.google.gson.JsonObject();
            entries.add(new Entry(false, null, null, y, 150, null, 0, 4, banner));
            y += 150 + GAP;
        }
        if (cat == Category.STORE) {
            for (com.google.gson.JsonObject it : all) {
                if (bool(it, "owned")) continue;
                if (!matchQuery(it, q)) continue;
                entries.add(new Entry(false, null, null, y, 36, null, 0, 1, it));
                y += 36 + GAP / 2;
            }
        } else if (cat == Category.COSMETICS) {
            java.util.List<com.google.gson.JsonObject> owned = new java.util.ArrayList<>();
            for (com.google.gson.JsonObject it : all) {
                if (!bool(it, "owned")) continue;
                if (!isCosmeticCat(str(it, "category", ""))) continue;
                if (!matchQuery(it, q)) continue;
                owned.add(it);
            }
            java.util.List<com.google.gson.JsonObject> active = new java.util.ArrayList<>();
            java.util.List<com.google.gson.JsonObject> rest = new java.util.ArrayList<>();
            for (com.google.gson.JsonObject it : owned) {
                if (str(it, "id", "").equals(equippedId(str(it, "category", "")))) active.add(it);
                else rest.add(it);
            }
            rest.sort((a, b) -> {
                int c = Integer.compare(slotOrder(str(a, "category", "")), slotOrder(str(b, "category", "")));
                if (c != 0) return c;
                return str(a, "name", "").compareToIgnoreCase(str(b, "name", ""));
            });
            if (!active.isEmpty()) {
                y = addHeader(y, "Aktiv");
                for (com.google.gson.JsonObject it : active) {
                    entries.add(new Entry(false, null, null, y, 40, null, 0, 2, it));
                    y += 40 + GAP / 2;
                }
            }
            String lastSlot = "";
            for (com.google.gson.JsonObject it : rest) {
                String slot = str(it, "category", "");
                if (!slot.equals(lastSlot)) {
                    lastSlot = slot;
                    y = addHeader(y, SLOT_LABELS.getOrDefault(slot, slot));
                }
                entries.add(new Entry(false, null, null, y, 40, null, 0, 2, it));
                y += 40 + GAP / 2;
            }
        } else if (cat == Category.PETS) {
            String activeId = equippedId("pet");
            com.google.gson.JsonObject activeItem = null;
            java.util.List<com.google.gson.JsonObject> rest = new java.util.ArrayList<>();
            for (com.google.gson.JsonObject it : all) {
                if (!str(it, "category", "").equals("pet") || !bool(it, "owned")) continue;
                if (!matchQuery(it, q)) continue;
                if (str(it, "id", "").equals(activeId) && !activeId.isEmpty()) activeItem = it;
                else rest.add(it);
            }
            rest.sort((a, b) -> str(a, "name", "").compareToIgnoreCase(str(b, "name", "")));
            if (activeItem != null) {
                y = addHeader(y, "Aktiv");
                entries.add(new Entry(false, null, null, y, 150, null, 0, 8, activeItem));
                y += 150 + GAP / 2;
                String pid = str(activeItem, "id", "");
                if (!pid.equals(petNamePetId)) {
                    petNamePetId = pid;
                    petNameValue = petNameOf(pid);
                }
                int cardX = px + PADDING;
                int cardW = pw - PADDING * 2 - SCROLLBAR_AREA_W;
                petNameBox = new EditBox(this.font, cardX + 10, y - 150 - GAP / 2 + 76, cardW - 120, 24, Component.literal(""));
                petNameBox.setMaxLength(16);
                petNameBox.setHint(Component.literal("Pet-Name"));
                petNameBox.setValue(petNameValue);
                petNameBox.setResponder(t -> petNameValue = t);
                addRenderableWidget(petNameBox);
            } else {
                petNameBox = null;
                petNamePetId = "";
            }
            if (!rest.isEmpty()) {
                y = addHeader(y, activeItem != null ? "Verfügbar" : "Haustiere");
                for (com.google.gson.JsonObject it : rest) {
                    entries.add(new Entry(false, null, null, y, 56, null, 0, 3, it));
                    y += 56 + GAP / 2;
                }
            }
        }
        contentH = y - contentTop + scroll;
        int visibleH = contentBottom - contentTop;
        maxScroll = Math.max(0, contentH - visibleH);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
    }

    private int addHeader(int y, String title) {
        com.google.gson.JsonObject d = new com.google.gson.JsonObject();
        d.addProperty("text", title);
        entries.add(new Entry(false, null, null, y, 24, null, 0, 7, d));
        return y + 24 + GAP / 2;
    }

    private static int slotOrder(String slot) {
        for (int i = 0; i < SLOT_ORDER.length; i++) {
            if (SLOT_ORDER[i].equals(slot)) return i;
        }
        return 99;
    }

    private static boolean isCosmeticCat(String c) {
        for (String s : SLOT_ORDER) {
            if (s.equals(c)) return true;
        }
        return false;
    }

    private static boolean matchQuery(com.google.gson.JsonObject it, String q) {
        if (q == null || q.isEmpty()) return true;
        String hay = (str(it, "name", "") + " " + str(it, "desc", "") + " " + str(it, "category", "")).toLowerCase();
        return hay.contains(q);
    }

    private String equippedId(String category) {
        try {
            if (storeCache != null && storeCache.has("equipped") && storeCache.get("equipped").isJsonObject()) {
                com.google.gson.JsonObject eq = storeCache.getAsJsonObject("equipped");
                if (eq.has(category) && !eq.get(category).isJsonNull()) return eq.get(category).getAsString();
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    private String petModeOf(String petId) {
        try {
            if (storeCache != null && storeCache.has("petConfigs") && storeCache.get("petConfigs").isJsonObject()) {
                com.google.gson.JsonObject cfgs = storeCache.getAsJsonObject("petConfigs");
                if (cfgs.has(petId) && cfgs.get(petId).isJsonObject()) {
                    com.google.gson.JsonObject c = cfgs.getAsJsonObject(petId);
                    if (c.has("mode") && !c.get("mode").isJsonNull()) return c.get("mode").getAsString();
                }
            }
        } catch (Throwable ignored) {
        }
        return "follow";
    }

    private boolean handleCustomClick(Entry e, double mx, double my, int cardX, int cardW) {
        try {
            if (e.custom == 1 || e.custom == 2) {
                String id = str(e.data, "id", "");
                String itemCat = str(e.data, "category", "");
                if (id.isEmpty()) return false;
                boolean owned = bool(e.data, "owned");
                if (!owned) {
                    runStoreAction("Gekauft \u2713", () -> StoreClient.buy(id));
                    rebuild();
                    return true;
                }
                boolean isEq = id.equals(equippedId(itemCat));
                if (isEq) {
                    runStoreAction("Abgelegt \u2713", () -> StoreClient.unequipCategory(itemCat));
                } else {
                    runStoreAction("Ausger\u00fcstet \u2713", () -> StoreClient.equipItem(id));
                }
                rebuild();
                return true;
            }
            if (e.custom == 3) {
                String id = str(e.data, "id", "");
                if (id.isEmpty()) return false;
                boolean isEq = id.equals(equippedId("pet"));
                if (isEq) {
                    runStoreAction("Pet weggeschickt \u2713", () -> StoreClient.unequipCategory("pet"));
                } else {
                    runStoreAction("Pet aktiv \u2713", () -> StoreClient.equipItem(id));
                }
                rebuild();
                return true;
            }
            if (e.custom == 8) {
                String id = str(e.data, "id", "");
                if (id.isEmpty()) return false;
                int nameY = e.y + 76;
                if (mx >= cardX + 10 && mx < cardX + cardW - 110 && my >= nameY && my < nameY + 24) {
                    return false;
                }
                int btnY = e.y + 112;
                if (mx >= cardX + cardW - 100 && my >= btnY && my < btnY + 28) {
                    savePetName(id);
                    return true;
                }
                if (mx >= cardX + 10 && mx < cardX + 160 && my >= btnY && my < btnY + 28) {
                    cyclePetColor(id);
                    return true;
                }
                if (mx >= cardX + 168 && mx < cardX + 338 && my >= btnY && my < btnY + 28) {
                    cyclePetSkin(id);
                    return true;
                }
                if (mx >= cardX + 346 && mx < cardX + 496 && my >= btnY && my < btnY + 28) {
                    cyclePetMode(id);
                    return true;
                }
                boolean isEq = id.equals(equippedId("pet"));
                if (isEq) {
                    runStoreAction("Pet weggeschickt \u2713", () -> StoreClient.unequipCategory("pet"));
                } else {
                    runStoreAction("Pet aktiv \u2713", () -> StoreClient.equipItem(id));
                }
                rebuild();
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private void savePetName(String petId) {
        try {
            String name = petNamePetId.equals(petId) ? petNameValue.trim() : petNameOf(petId);
            runStoreAction("Name gespeichert \u2713", () -> StoreClient.petConfig(petId, null, name.isEmpty() ? null : name, null, null));
            rebuild();
        } catch (Throwable ignored) {
        }
    }

    private void cyclePetColor(String petId) {
        try {
            String cur = petColorOf(petId);
            int idx = 0;
            for (int i = 0; i < PET_COLORS.length; i++) {
                if (PET_COLORS[i].equalsIgnoreCase(cur)) idx = i;
            }
            String next = PET_COLORS[(idx + 1) % PET_COLORS.length];
            runStoreAction("Farbe: " + next, () -> StoreClient.petConfig(petId, null, null, next, null));
            rebuild();
        } catch (Throwable ignored) {
        }
    }

    private void cyclePetSkin(String petId) {
        try {
            java.util.List<String[]> skins = petSkinsOf(petId);
            String cur = petVariantOf(petId);
            int idx = -1;
            for (int i = 0; i < skins.size(); i++) {
                if (skins.get(i)[0].equals(cur)) idx = i;
            }
            int next = (idx + 1) % (skins.size() + 1);
            String variant = next == 0 ? null : skins.get(next - 1)[0];
            String label = next == 0 ? "Normal" : skins.get(next - 1)[1];
            runStoreAction("Skin: " + label, () -> StoreClient.petConfig(petId, null, null, null, variant));
            rebuild();
        } catch (Throwable ignored) {
        }
    }

    private void cyclePetMode(String petId) {
        try {
            String[] modes = petModesFor(petId);
            String cur = petModeOf(petId);
            int idx = 0;
            for (int i = 0; i < modes.length; i++) {
                if (modes[i].equals(cur)) idx = i;
            }
            String next = modes[(idx + 1) % modes.length];
            runStoreAction("Modus: " + modeLabel(next), () -> StoreClient.petConfig(petId, next, null, null, null));
            rebuild();
        } catch (Throwable ignored) {
        }
    }

    private String petNameOf(String petId) {
        com.google.gson.JsonObject c = petCfg(petId);
        if (c == null) return "";
        return str(c, "name", "");
    }

    private String petColorOf(String petId) {
        com.google.gson.JsonObject c = petCfg(petId);
        if (c == null) return "#ffffff";
        String col = str(c, "nameColor", "#ffffff");
        return col.isEmpty() ? "#ffffff" : col;
    }

    private String petVariantOf(String petId) {
        com.google.gson.JsonObject c = petCfg(petId);
        if (c == null) return "";
        return str(c, "variant", "");
    }

    private String petVariantLabel(String petId) {
        String v = petVariantOf(petId);
        if (v.isEmpty()) return "Normal";
        for (String[] kv : petSkinsOf(petId)) {
            if (kv[0].equals(v)) return kv[1];
        }
        return v;
    }

    private com.google.gson.JsonObject petCfg(String petId) {
        try {
            if (storeCache != null && storeCache.has("petConfigs") && storeCache.get("petConfigs").isJsonObject()) {
                com.google.gson.JsonObject cfgs = storeCache.getAsJsonObject("petConfigs");
                if (cfgs.has(petId) && cfgs.get(petId).isJsonObject()) return cfgs.getAsJsonObject(petId);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private java.util.List<String[]> petSkinsOf(String petId) {
        java.util.List<String[]> out = new java.util.ArrayList<>();
        try {
            if (storeCache != null && storeCache.has("catalog") && storeCache.get("catalog").isJsonArray()) {
                for (com.google.gson.JsonElement el : storeCache.getAsJsonArray("catalog")) {
                    if (el == null || !el.isJsonObject()) continue;
                    com.google.gson.JsonObject it = el.getAsJsonObject();
                    if (!"pet_skin".equals(str(it, "category", ""))) continue;
                    com.google.gson.JsonObject data = it.has("data") && it.get("data").isJsonObject() ? it.getAsJsonObject("data") : null;
                    if (data == null) continue;
                    if (!petId.equals(str(data, "variantOf", ""))) continue;
                    if (!bool(it, "owned")) continue;
                    out.add(new String[]{str(data, "variant", ""), str(data, "label", str(it, "name", ""))});
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private void close() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
        if (draggingScroll && event.button() == 0) {
            int contentTop = py + HEADER_H + TAB_H + GAP;
            int contentBottom = py + ph - PADDING;
            int trackH = contentBottom - contentTop;
            int thumbH = Math.max(36, (int) ((double) trackH * trackH / (trackH + maxScroll)));
            int maxThumbY = trackH - thumbH;
            if (maxThumbY > 0 && maxScroll > 0) {
                double ratio = (event.y() - contentTop - thumbH / 2.0) / maxThumbY;
                ratio = Math.max(0, Math.min(1, ratio));
                scroll = (int) Math.round(ratio * maxScroll);
                rebuild();
            }
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        if (event.button() == 0) {
            draggingScroll = false;
            draggingTabScroll = false;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean bl) {
        double mx = event.x();
        double my = event.y();
        int button = event.button();

        int tabBarY = py + HEADER_H;
        int tabBarX = px + PADDING;
        int contentTop = tabBarY + TAB_H + GAP;
        int contentBottom = py + ph - PADDING;

        if (button == 0) {
            clampTabScroll();
            if (tabOverflow()) {
                int leftX = px + PADDING;
                int rightX = px + pw - PADDING - TAB_ARROW_W;
                if (mx >= leftX && mx <= leftX + TAB_ARROW_W && my >= tabBarY && my <= tabBarY + TAB_H) {
                    tabScroll = Math.max(0, tabScroll - 240);
                    return true;
                }
                if (mx >= rightX && mx <= rightX + TAB_ARROW_W && my >= tabBarY && my <= tabBarY + TAB_H) {
                    tabScroll = Math.min(maxTabScroll, tabScroll + 240);
                    return true;
                }
            }
            int tabX = tabAreaX() - tabScroll;
            for (int i = 0; i < cats.length; i++) {
                if (mx >= tabX && mx <= tabX + TAB_W && my >= tabBarY && my <= tabBarY + TAB_H) {
                    if (i != category) {
                        prevCategory = category;
                        category = i;
                        tabAnimProgress = 0f;
                        scroll = 0;
                        query = "";
                        search.setValue("");
                        rebuild();
                    }
                    return true;
                }
                tabX += TAB_W + TAB_GAP;
            }

            if (cats[category] == Category.STORE || cats[category] == Category.COSMETICS || cats[category] == Category.PETS) {
                int cardX = px + PADDING;
                int cardW = pw - PADDING * 2 - SCROLLBAR_AREA_W;
                for (Entry e : entries) {
                    if (e.custom <= 0 || e.custom == 4 || e.custom == 5 || e.custom == 7) continue;
                    if (my < e.y || my >= e.y + e.h) continue;
                    if (mx < cardX || mx > cardX + cardW) continue;
                    if (handleCustomClick(e, mx, my, cardX, cardW)) return true;
                }
            }

            if (maxScroll > 0 && mx >= px + pw - 20 && my >= contentTop && my <= contentBottom) {
                draggingScroll = true;
                dragStartY = (int) my;
                dragStartScroll = scroll;
                return true;
            }
        }

        return super.mouseClicked(event, bl);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        int tabBarY = py + HEADER_H;
        clampTabScroll();
        int ax = tabAreaX();
        int aw = tabAreaW();

        if (my >= tabBarY && my <= tabBarY + TAB_H && mx >= ax - TAB_ARROW_W - 6 && mx <= ax + aw + TAB_ARROW_W + 6 && maxTabScroll > 0) {
            tabScroll = Math.max(0, Math.min(maxTabScroll, tabScroll - (int) ((horizontal + vertical) * 30)));
            return true;
        }

        int contentTop = tabBarY + TAB_H + GAP;
        int contentBottom = py + ph - PADDING;
        if (mx >= px + PADDING && mx <= px + pw - PADDING && my >= contentTop && my <= contentBottom) {
            scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (vertical * 30)));
            rebuild();
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent ki) {
        if (petNameBox != null && petNameBox.isFocused() && (ki.key() == InputConstants.KEY_RETURN || ki.key() == InputConstants.KEY_NUMPADENTER)) {
            String pid = petNamePetId;
            if (pid != null && !pid.isEmpty()) {
                String name = petNameValue != null ? petNameValue.trim() : "";
                runStoreAction("Name gespeichert ✓", () -> StoreClient.petConfig(pid, null, name.isEmpty() ? null : name, null, null));
                rebuild();
            }
            return true;
        }
        if (dev.kollegen.client.mods.KeybindSetting.capturing != null) {
            dev.kollegen.client.mods.KeybindSetting cap = dev.kollegen.client.mods.KeybindSetting.capturing;
            cap.value = (ki.key() == InputConstants.KEY_ESCAPE) ? -1 : ki.key();
            dev.kollegen.client.mods.KeybindSetting.capturing = null;
            rebuild();
            return true;
        }
        if (ki.key() == InputConstants.KEY_LEFT && category > 0) {
            prevCategory = category;
            category--;
            tabAnimProgress = 0f;
            scroll = 0;
            query = "";
            search.setValue("");
            rebuild();
            return true;
        }
        if (ki.key() == InputConstants.KEY_RIGHT && category < cats.length - 1) {
            prevCategory = category;
            category++;
            tabAnimProgress = 0f;
            scroll = 0;
            query = "";
            search.setValue("");
            rebuild();
            return true;
        }
        return super.keyPressed(ki);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        if (pendingRebuild) {
            pendingRebuild = false;
            rebuild();
        }
        tabAnimProgress = Math.min(1f, tabAnimProgress + pt * 8f);

        g.fill(0, 0, this.width, this.height, Palette.tint(Palette.BG, 0xCC));

        Glass.vanillaPanel(g, px, py, pw, ph);

        int tabBarY = py + HEADER_H;
        int tabBarX = px + PADDING;
        int tabBarW = pw - PADDING * 2;

        Glass.fillRound(g, tabBarX - 4, tabBarY - 4, tabBarW + 8, TAB_H + 8, 8, Palette.tint(Palette.PANEL2, 0x80));

        clampTabScroll();
        boolean overflow = tabOverflow();
        int ax = tabAreaX();
        int aw = tabAreaW();

        g.enableScissor(ax, tabBarY, ax + aw, tabBarY + TAB_H);
        int tabX = ax - tabScroll;
        for (int i = 0; i < cats.length; i++) {
            boolean sel = i == category;
            boolean hov = mx >= tabX && mx <= tabX + TAB_W && my >= tabBarY && my <= tabBarY + TAB_H;

            Glass.vanillaTab(g, tabX, tabBarY + 2, TAB_W, TAB_H - 4, this.font,
                    cats[i].icon + " " + cats[i].display, sel, hov);
            tabX += TAB_W + TAB_GAP;
        }
        g.disableScissor();

        if (overflow) {
            int leftX = px + PADDING;
            int rightX = px + pw - PADDING - TAB_ARROW_W;
            boolean leftHov = mx >= leftX && mx <= leftX + TAB_ARROW_W && my >= tabBarY && my <= tabBarY + TAB_H;
            boolean rightHov = mx >= rightX && mx <= rightX + TAB_ARROW_W && my >= tabBarY && my <= tabBarY + TAB_H;
            Glass.fillRound(g, leftX, tabBarY + 2, TAB_ARROW_W, TAB_H - 4, 6, Palette.tint(leftHov ? Palette.ACCENT : Palette.PANEL2, leftHov ? 0xFF : 0xC0));
            Glass.fillRound(g, rightX, tabBarY + 2, TAB_ARROW_W, TAB_H - 4, 6, Palette.tint(rightHov ? Palette.ACCENT : Palette.PANEL2, rightHov ? 0xFF : 0xC0));
            g.text(this.font, "◀", leftX + (TAB_ARROW_W - this.font.width("◀")) / 2, tabBarY + (TAB_H - this.font.lineHeight) / 2, Palette.TEXT, false);
            g.text(this.font, "▶", rightX + (TAB_ARROW_W - this.font.width("▶")) / 2, tabBarY + (TAB_H - this.font.lineHeight) / 2, Palette.TEXT, false);
        }

        int contentTop = tabBarY + TAB_H + GAP;
        int contentBottom = py + ph - PADDING;
        int contentX = px + PADDING;
        int contentW = pw - PADDING * 2 - SCROLLBAR_AREA_W;

        g.enableScissor(contentX, contentTop, contentX + contentW, contentBottom);

        for (Entry e : entries) {
            int ey = e.y;
            if (ey + e.h < contentTop || ey > contentBottom) continue;

            if (e.custom > 0) {
                drawCustomRow(g, e, mx, my, px, pw, contentTop, contentBottom);
                continue;
            }

            boolean hov = mx >= px + PADDING && mx <= px + pw - PADDING - SCROLLBAR_AREA_W && my >= ey && my < ey + e.h;

            if (e.isModule) {
                Module m = e.module;
                int cardW = pw - PADDING * 2 - SCROLLBAR_AREA_W;
                int cardX = px + PADDING;
                int cardY = ey;
                int cardH = e.h;

                Glass.panelDark(g, cardX, cardY, cardW, cardH, 8);

                if (hov) {
                    Glass.fillRound(g, cardX, cardY, cardW, cardH, RADIUS, 0x50FFFFFF);
                }

                if (m.locked) {
                    Glass.fillRound(g, cardX + 4, cardY + (cardH - 20) / 2, 20, 20, 4, Palette.tint(Palette.MUTED, 0x80));
                    g.text(this.font, "🔒", cardX + 8, cardY + (cardH - this.font.lineHeight) / 2, Palette.MUTED, false);
                }

                int titleX = cardX + 16 + (m.locked ? 24 : 0);
                g.text(this.font, m.name, titleX, cardY + 10, Palette.TEXT, false);
                g.text(this.font, trunc(m.description, cardW - 200), titleX, cardY + 28, Palette.MUTED, false);
                if (m.risk != null) {
                    g.text(this.font, "⚠ " + trunc(m.risk, cardW - 80), titleX, cardY + 44, Palette.DANGER, false);
                }

                int badgeX = px + pw - PADDING - SCROLLBAR_AREA_W - 96;
                if (m.enabled) {
                    Glass.badge(g, badgeX, cardY + (cardH - 18) / 2, 88, 18, 9,
                            Palette.GREEN, 0xFFFFFFFF, Minecraft.getInstance().font, "Aktiv");
                }
            } else {
                int cardW = pw - PADDING * 2 - SCROLLBAR_AREA_W;
                int cardX = px + PADDING;
                int cardY = ey;
                int cardH = e.h;

                Glass.fillRound(g, cardX, cardY, cardW, cardH, 6, Palette.tint(Palette.BORDER, 0x80));
                Glass.fillRound(g, cardX + 1, cardY + 1, cardW - 2, cardH - 2, 5,
                        Palette.tint(Palette.PANEL2, 0x70));

                g.text(this.font, e.setting.name, cardX + 16, cardY + (cardH - this.font.lineHeight) / 2, Palette.TEXT, false);

                String vt = e.setting.valueText();
                if (!vt.isEmpty() && e.widget != null) {
                    int vx = e.widget.getX() - 12 - this.font.width(vt);
                    if (vx < cardX + cardW / 2) vx = cardX + cardW / 2;
                    g.text(this.font, vt, vx, cardY + (cardH - this.font.lineHeight) / 2, Palette.MUTED, false);
                }
            }
        }
        g.disableScissor();

        if (maxScroll > 0) {
            int scrollbarX = px + pw - SCROLLBAR_W - 8;
            int trackH = contentBottom - contentTop;
            float scrollRatio = maxScroll > 0 ? (float) scroll / maxScroll : 0;
            int thumbH = Math.max(36, (int) ((double) trackH * trackH / (trackH + maxScroll)));
            int thumbY = contentTop + (int) (scrollRatio * (trackH - thumbH));
            Glass.vanillaScrollbar(g, px + pw - SCROLLBAR_W - 8, contentTop, SCROLLBAR_W, trackH, 
                    scroll, maxScroll, false, draggingScroll);
        }

        g.text(this.font, "KOLLEGEN", px + PADDING + 12, py + 8, Palette.ACCENT, false);
        g.text(this.font, "Client", px + PADDING + 12 + this.font.width("KOLLEGEN") + 6, py + 8, Palette.MUTED, false);
        g.text(this.font, "v" + Version.get(), px + pw - PADDING - this.font.width("v" + Version.get()) - 12, py + 8, Palette.MUTED, false);

        super.extractRenderState(g, mx, my, pt);
    }

    private void drawCustomRow(GuiGraphicsExtractor g, Entry e, int mx, int my, int px, int pw, int contentTop, int contentBottom) {
        try {
            int cardX = px + PADDING;
            int cardW = pw - PADDING * 2 - SCROLLBAR_AREA_W;
            int ey = e.y;
            if (e.custom == 5 || e.custom == 7) {
                String text = e.data != null ? str(e.data, "text", "") : "";
                int col = e.custom == 7 ? Palette.ACCENT : Palette.MUTED;
                g.text(this.font, trunc(text, cardW / 6), cardX + 8, ey + (e.h - this.font.lineHeight) / 2, col, false);
                return;
            }
            if (e.custom == 4) {
                Glass.panelDark(g, cardX, ey, cardW, e.h, 8);
                try {
                    net.minecraft.resources.Identifier vid = net.minecraft.resources.Identifier.tryParse("kollegen:cosmetics/fluffybento/vorschau_FluffyBento.png");
                    int targetW = 240;
                    LogoDraw.drawId(g, vid, cardX + (cardW - targetW) / 2, ey + 4, targetW);
                } catch (Throwable ignored) {
                }
                g.text(this.font, "Set-Vorschau", cardX + 12, ey + e.h - 20, Palette.MUTED, false);
                return;
            }
            boolean hov = mx >= cardX && mx <= cardX + cardW && my >= ey && my < ey + e.h;
            Glass.panelDark(g, cardX, ey, cardW, e.h, 8);
            if (hov) {
                Glass.fillRound(g, cardX, ey, cardW, e.h, RADIUS, 0x50FFFFFF);
            }
            if (e.custom == 8) {
                String id = str(e.data, "id", "");
                String kind = id.startsWith("pet_") ? id.substring(4) : id;
                try {
                    if (dev.kollegen.client.presence.MythicPets.isMythicKind(kind)) {
                        net.minecraft.resources.Identifier pid = net.minecraft.resources.Identifier.tryParse("kollegen:pets/preview/" + kind + ".png");
                        LogoDraw.drawId(g, pid, cardX + 10, ey + 8, 56);
                    } else {
                        g.text(this.font, PET_ICONS.getOrDefault(id, "🐾"), cardX + 24, ey + 22, Palette.TEXT, false);
                    }
                } catch (Throwable ignored) {
                }
                String mode = petModeOf(id);
                g.text(this.font, trunc(str(e.data, "name", id), 30), cardX + 76, ey + 8, Palette.TEXT, false);
                g.text(this.font, "Modus: " + modeLabel(mode) + " · Skin: " + petVariantLabel(id), cardX + 76, ey + 24, Palette.MUTED, false);
                g.text(this.font, "Farbe: " + petColorOf(id), cardX + 76, ey + 40, Palette.MUTED, false);
                int btnY = ey + 112;
                drawMiniButton(g, cardX + cardW - 100, btnY, 92, 28, "💾", mx, my);
                drawMiniButton(g, cardX + 10, btnY, 150, 28, petColorOf(id), mx, my);
                drawMiniButton(g, cardX + 168, btnY, 170, 28, petVariantLabel(id), mx, my);
                drawMiniButton(g, cardX + 346, btnY, 150, 28, modeLabel(mode), mx, my);
                return;
            }
            if (e.custom == 3) {
                String id = str(e.data, "id", "");
                String kind = id.startsWith("pet_") ? id.substring(4) : id;
                try {
                    if (dev.kollegen.client.presence.MythicPets.isMythicKind(kind)) {
                        net.minecraft.resources.Identifier pid = net.minecraft.resources.Identifier.tryParse("kollegen:pets/preview/" + kind + ".png");
                        LogoDraw.drawId(g, pid, cardX + 8, ey + (e.h - 44) / 2, 44);
                    } else {
                        g.text(this.font, PET_ICONS.getOrDefault(id, "🐾"), cardX + 20, ey + (e.h - this.font.lineHeight) / 2, Palette.TEXT, false);
                    }
                } catch (Throwable ignored) {
                }
                g.text(this.font, trunc(str(e.data, "name", id), 30), cardX + 62, ey + (e.h - this.font.lineHeight) / 2, Palette.TEXT, false);
                return;
            }
            String name = str(e.data, "name", str(e.data, "id", "?"));
            String itemCat = str(e.data, "category", "");
            String right;
            if (e.custom == 1) {
                int price = num(e.data, "price", 0);
                right = price + " \u2B21";
            } else {
                right = SLOT_LABELS.getOrDefault(itemCat, itemCat);
            }
            g.text(this.font, trunc(name, (cardW - 160) / 6), cardX + 14, ey + (e.h - this.font.lineHeight) / 2, Palette.TEXT, false);
            g.text(this.font, trunc(right, 24), cardX + cardW - 12 - this.font.width(trunc(right, 24)), ey + (e.h - this.font.lineHeight) / 2, e.custom == 1 ? rarityColor(str(e.data, "rarity", "")) : Palette.MUTED, false);
        } catch (Throwable ignored) {
        }
    }

    private void drawMiniButton(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, int mx, int my) {
        try {
            boolean hov = mx >= x && mx <= x + w && my >= y && my < y + h;
            Glass.fillRound(g, x, y, w, h, 6, Palette.tint(hov ? Palette.ACCENT : Palette.PANEL2, hov ? 0xFF : 0x80));
            String t = trunc(label, w / 7);
            g.text(this.font, t, x + (w - this.font.width(t)) / 2, y + (h - this.font.lineHeight) / 2, 0xFFFFFFFF, false);
        } catch (Throwable ignored) {
        }
    }

    private static String trunc(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, Math.max(1, max - 1)) + "…" : s;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}