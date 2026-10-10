package dev.kollegen.client.presence;

import com.google.gson.JsonObject;

public final class CosmeticData {
    public final int level;
    public final String titleText;
    public final String nameColor;
    public final String badgeIcon;
    public final String badgeColor;
    public final String stickerIcon;
    public final String stickerColor;
    public final String font;
    public final String petKind;
    public final double petScale;
    public final String petMode;
    public final String petName;
    public final String petNameColor;

    public CosmeticData(int level, String titleText, String nameColor, String badgeIcon,
                        String badgeColor, String stickerIcon, String stickerColor, String font,
                        String petKind, double petScale, String petMode, String petName, String petNameColor) {
        this.level = level;
        this.titleText = titleText;
        this.nameColor = nameColor;
        this.badgeIcon = badgeIcon;
        this.badgeColor = badgeColor;
        this.stickerIcon = stickerIcon;
        this.stickerColor = stickerColor;
        this.font = font;
        this.petKind = petKind;
        this.petScale = petScale;
        this.petMode = petMode;
        this.petName = petName;
        this.petNameColor = petNameColor;
    }

    public boolean isEmpty() {
        return level <= 0 && titleText == null && nameColor == null
                && badgeIcon == null && stickerIcon == null;
    }

    public static CosmeticData fromJson(JsonObject root) {
        if (root == null) return null;
        int level = root.has("level") && !root.get("level").isJsonNull()
                ? safeInt(root.get("level").getAsInt()) : 0;
        JsonObject c = root.has("cosmetics") && root.get("cosmetics").isJsonObject()
                ? root.getAsJsonObject("cosmetics") : null;
        if (c == null) return level > 0 ? new CosmeticData(level, null, null, null, null, null, null, null, null, 0.2, null, null, null) : null;
        String titleText = textOf(c, "title", "text");
        String nameColor = strOf(c, "nameColor");
        String badgeIcon = textOf(c, "badge", "icon");
        String badgeColor = textOf(c, "badge", "color");
        String stickerIcon = textOf(c, "sticker", "icon");
        String stickerColor = textOf(c, "sticker", "color");
        String font = strOf(c, "font");
        String petKind = null;
        double petScale = 0.2;
        String petMode = null;
        String petName = null;
        String petNameColor = null;
        if (c.has("pet") && !c.get("pet").isJsonNull() && c.get("pet").isJsonObject()) {
            try {
                JsonObject p = c.getAsJsonObject("pet");
                petKind = strOf(p, "kind");
                if (p.has("scale") && !p.get("scale").isJsonNull()) {
                    try {
                        petScale = p.get("scale").getAsDouble();
                    } catch (Throwable ignored) {
                    }
                }
                if (petScale <= 0.0 || petScale > 1.0) petScale = 0.2;
                petMode = strOf(p, "mode");
                petName = strOf(p, "name");
                petNameColor = strOf(p, "nameColor");
            } catch (Throwable ignored) {
            }
        }
        CosmeticData d = new CosmeticData(level, titleText, nameColor, badgeIcon, badgeColor, stickerIcon, stickerColor, font, petKind, petScale, petMode, petName, petNameColor);
        if (d.isEmpty() && petKind == null) return null;
        return d;
    }

    private static int safeInt(int v) {
        return Math.max(0, Math.min(9999, v));
    }

    private static String strOf(JsonObject o, String key) {
        if (!o.has(key) || o.get(key).isJsonNull()) return null;
        try {
            String s = o.get(key).getAsString();
            return s == null || s.isEmpty() ? null : s;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String textOf(JsonObject o, String obj, String key) {
        if (!o.has(obj) || o.get(obj).isJsonNull() || !o.get(obj).isJsonObject()) return null;
        return strOf(o.getAsJsonObject(obj), key);
    }
}
