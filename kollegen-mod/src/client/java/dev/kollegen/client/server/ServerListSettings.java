package dev.kollegen.client.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class ServerListSettings {

    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("kollegen-serverlist.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static int columns = 2;
    public static int cardHeight = 56;
    public static int menuWidth = 720;
    public static boolean expanded = true;
    private static boolean loaded = false;

    public static final int[] COLUMN_OPTIONS = { 1, 2, 3 };
    public static final int[] HEIGHT_OPTIONS = { 36, 46, 56, 68, 80 };
    public static final int[] WIDTH_OPTIONS = { 560, 640, 720, 820, 920 };

    private ServerListSettings() {
    }

    public static void load() {
        if (loaded) return;
        loaded = true;
        if (!Files.exists(CONFIG_PATH)) {
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
            if (obj.has("columns")) columns = Math.max(1, Math.min(3, obj.get("columns").getAsInt()));
            if (obj.has("cardHeight")) cardHeight = Math.max(36, Math.min(120, obj.get("cardHeight").getAsInt()));
            if (obj.has("menuWidth")) menuWidth = Math.max(400, Math.min(1200, obj.get("menuWidth").getAsInt()));
            if (obj.has("expanded")) expanded = obj.get("expanded").getAsBoolean();
        } catch (Throwable ignored) {
        }
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            JsonObject obj = new JsonObject();
            obj.addProperty("columns", columns);
            obj.addProperty("cardHeight", cardHeight);
            obj.addProperty("menuWidth", menuWidth);
            obj.addProperty("expanded", expanded);
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(obj, writer);
            }
        } catch (Throwable ignored) {
        }
    }

    public static int getColumns() {
        load();
        return columns;
    }

    public static int getCardHeight() {
        load();
        return cardHeight;
    }

    public static int getActualWidth(int screenOrWidgetWidth) {
        load();
        int maxAllowed = Math.max(320, screenOrWidgetWidth - 28);
        return Math.min(menuWidth, maxAllowed);
    }

    public static void cycleColumns() {
        load();
        columns = (columns % 3) + 1;
        save();
    }

    public static void cycleHeight() {
        load();
        int idx = 0;
        for (int i = 0; i < HEIGHT_OPTIONS.length; i++) {
            if (HEIGHT_OPTIONS[i] == cardHeight) {
                idx = i;
                break;
            }
        }
        cardHeight = HEIGHT_OPTIONS[(idx + 1) % HEIGHT_OPTIONS.length];
        save();
    }

    public static void cycleWidth() {
        load();
        int idx = 0;
        for (int i = 0; i < WIDTH_OPTIONS.length; i++) {
            if (WIDTH_OPTIONS[i] == menuWidth) {
                idx = i;
                break;
            }
        }
        menuWidth = WIDTH_OPTIONS[(idx + 1) % WIDTH_OPTIONS.length];
        save();
    }

    public static String getColumnsLabel() {
        load();
        if (columns == 1) return "Reihen / Spalten: 1";
        if (columns == 2) return "Reihen / Spalten: 2 (Standard)";
        return "Reihen / Spalten: 3";
    }

    public static String getHeightLabel() {
        load();
        String name = switch (cardHeight) {
            case 36 -> "Kompakt (36px)";
            case 46 -> "Normal (46px)";
            case 56 -> "Mittel (56px, Standard)";
            case 68 -> "Groß (68px)";
            case 80 -> "Vollbild (80px)";
            default -> cardHeight + "px";
        };
        return "Karten-Größe: " + name;
    }

    public static String getWidthLabel() {
        load();
        String name = switch (menuWidth) {
            case 560 -> "Kompakt (560px)";
            case 640 -> "Normal (640px)";
            case 720 -> "Standard (720px)";
            case 820 -> "Breit (820px)";
            case 920 -> "Sehr Breit (920px)";
            default -> menuWidth + "px";
        };
        return "Menü-Breite: " + name;
    }

    public static int calculateContentHeight(ServerSelectionList ssl) {
        load();
        List<?> children = ssl.children();
        if (children.isEmpty()) return 0;

        int cols = columns;
        int cardH = cardHeight;
        int gap = 6;
        int onlineCount = 0;
        int netCount = 0;
        boolean hasLan = false;

        for (Object obj : children) {
            if (obj instanceof ServerSelectionList.OnlineServerEntry) {
                onlineCount++;
            } else if (obj instanceof ServerSelectionList.LANHeader) {
                hasLan = true;
            } else if (obj instanceof ServerSelectionList.NetworkServerEntry) {
                netCount++;
            }
        }

        int onlineRows = (onlineCount + cols - 1) / cols;
        int netRows = (netCount + cols - 1) / cols;

        int totalH = 8;
        if (onlineRows > 0) {
            totalH += onlineRows * cardH + (onlineRows - 1) * gap;
        }
        if (hasLan) {
            totalH += 24 + gap;
        }
        if (netRows > 0) {
            totalH += netRows * cardH + (netRows - 1) * gap;
        }
        totalH += 12;
        return totalH;
    }

    public static void applyLayout(ServerSelectionList ssl) {
        load();
        List<?> children = ssl.children();
        if (children.isEmpty()) return;

        int cols = columns;
        int cardH = cardHeight;
        int totalW = getActualWidth(ssl.getWidth());
        int gap = 6;
        int cardW = cols > 1 ? (totalW - (cols - 1) * gap) / cols : totalW;
        int startLeft = ssl.getX() + (ssl.getWidth() - totalW) / 2;
        int startY = ssl.getY() + 4 - (int) ssl.scrollAmount();

        int onlineCount = 0;
        for (Object obj : children) {
            if (obj instanceof ServerSelectionList.OnlineServerEntry) {
                onlineCount++;
            }
        }
        int onlineRows = (onlineCount + cols - 1) / cols;
        int yAfterOnline = startY + (onlineRows > 0 ? (onlineRows * (cardH + gap)) : 0);

        int curOnline = 0;
        int curNet = 0;
        for (int i = 0; i < children.size(); i++) {
            Object obj = children.get(i);
            if (obj instanceof ServerSelectionList.OnlineServerEntry entry) {
                int col = curOnline % cols;
                int row = curOnline / cols;
                entry.setX(startLeft + col * (cardW + gap));
                entry.setY(startY + row * (cardH + gap));
                entry.setWidth(cardW);
                entry.setHeight(cardH);
                curOnline++;
            } else if (obj instanceof ServerSelectionList.LANHeader header) {
                header.setX(startLeft);
                header.setY(yAfterOnline + 2);
                header.setWidth(totalW);
                header.setHeight(20);
            } else if (obj instanceof ServerSelectionList.NetworkServerEntry netEntry) {
                int col = curNet % cols;
                int row = curNet / cols;
                int netStartY = yAfterOnline + 26;
                netEntry.setX(startLeft + col * (cardW + gap));
                netEntry.setY(netStartY + row * (cardH + gap));
                netEntry.setWidth(cardW);
                netEntry.setHeight(cardH);
                curNet++;
            }
        }
    }
}
