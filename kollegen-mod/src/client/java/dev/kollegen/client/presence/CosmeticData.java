package dev.kollegen.client.presence;

import com.google.gson.JsonObject;

public final class CosmeticData {
    public final int level;
    public final String titleText;
    public final String titleColor;
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
    public final String petVariant;
    public final String capeTexture;
    public final String capeModel;
    public final String backAccessoryTexture;
    public final String backAccessoryModel;
    public final String headAccessoryTexture;
    public final String headAccessoryModel;
    public final String shieldTexture;
    public final String shieldModel;
    public final String faceAccessoryTexture;
    public final String faceAccessoryModel;
    public final String auraTexture;
    public final String auraModel;
    public final String shoulderAccessoryTexture;
    public final String shoulderAccessoryModel;
    public final String torsoAccessoryTexture;
    public final String torsoAccessoryModel;
    public final String legsAccessoryTexture;
    public final String legsAccessoryModel;
    public final String glovesTexture;
    public final String glovesModel;

    public CosmeticData(int level, String titleText, String titleColor, String nameColor, String badgeIcon,
                        String badgeColor, String stickerIcon, String stickerColor, String font,
                        String petKind, double petScale, String petMode, String petName, String petNameColor, String petVariant,
                        String capeTexture, String capeModel, String backAccessoryTexture, String backAccessoryModel,
                        String headAccessoryTexture, String headAccessoryModel, String shieldTexture, String shieldModel,
                        String faceAccessoryTexture, String faceAccessoryModel, String auraTexture, String auraModel,
                        String shoulderAccessoryTexture, String shoulderAccessoryModel, String torsoAccessoryTexture, String torsoAccessoryModel,
                        String legsAccessoryTexture, String legsAccessoryModel, String glovesTexture, String glovesModel) {
        this.level = level;
        this.titleText = titleText;
        this.titleColor = titleColor;
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
        this.petVariant = petVariant;
        this.capeTexture = capeTexture;
        this.capeModel = capeModel;
        this.backAccessoryTexture = backAccessoryTexture;
        this.backAccessoryModel = backAccessoryModel;
        this.headAccessoryTexture = headAccessoryTexture;
        this.headAccessoryModel = headAccessoryModel;
        this.shieldTexture = shieldTexture;
        this.shieldModel = shieldModel;
        this.faceAccessoryTexture = faceAccessoryTexture;
        this.faceAccessoryModel = faceAccessoryModel;
        this.auraTexture = auraTexture;
        this.auraModel = auraModel;
        this.shoulderAccessoryTexture = shoulderAccessoryTexture;
        this.shoulderAccessoryModel = shoulderAccessoryModel;
        this.torsoAccessoryTexture = torsoAccessoryTexture;
        this.torsoAccessoryModel = torsoAccessoryModel;
        this.legsAccessoryTexture = legsAccessoryTexture;
        this.legsAccessoryModel = legsAccessoryModel;
        this.glovesTexture = glovesTexture;
        this.glovesModel = glovesModel;
    }

    public boolean isEmpty() {
        return level <= 0 && titleText == null && nameColor == null
                && badgeIcon == null && stickerIcon == null
                && capeTexture == null && backAccessoryTexture == null
                && headAccessoryTexture == null && shieldTexture == null
                && faceAccessoryTexture == null && auraTexture == null
                && shoulderAccessoryTexture == null && torsoAccessoryTexture == null
                && legsAccessoryTexture == null && glovesTexture == null;
    }

    public static CosmeticData fromJson(JsonObject root) {
        if (root == null) return null;
        int level = root.has("level") && !root.get("level").isJsonNull()
                ? safeInt(root.get("level").getAsInt()) : 0;
        JsonObject c = root.has("cosmetics") && root.get("cosmetics").isJsonObject()
                ? root.getAsJsonObject("cosmetics") : null;
        if (c == null) return level > 0 ? new CosmeticData(level, null, null, null, null, null, null, null, null, null, 0.2, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null) : null;
        String titleText = textOf(c, "title", "text");
        String titleColor = textOf(c, "title", "color");
        String nameColor = strOf(c, "nameColor");
        String badgeIcon = textOf(c, "badge", "icon");
        String badgeColor = textOf(c, "badge", "color");
        String stickerIcon = textOf(c, "sticker", "icon");
        String stickerColor = textOf(c, "sticker", "color");
        String font = strOf(c, "font");
        String capeTexture = textureOf(c, "cape");
        String capeModel = modelOf(c, "cape", capeTexture);
        String backAccessoryTexture = textureOf(c, "backAccessory");
        String backAccessoryModel = modelOf(c, "backAccessory", backAccessoryTexture);
        String headAccessoryTexture = textureOf(c, "headAccessory");
        String headAccessoryModel = modelOf(c, "headAccessory", headAccessoryTexture);
        String shieldTexture = textureOf(c, "shield");
        String shieldModel = modelOf(c, "shield", shieldTexture);
        String faceAccessoryTexture = textureOf(c, "face_accessory");
        String faceAccessoryModel = modelOf(c, "face_accessory", faceAccessoryTexture);
        String auraTexture = textureOf(c, "aura");
        String auraModel = modelOf(c, "aura", auraTexture);
        String shoulderAccessoryTexture = textureOf(c, "shoulder_accessory");
        String shoulderAccessoryModel = modelOf(c, "shoulder_accessory", shoulderAccessoryTexture);
        String torsoAccessoryTexture = textureOf(c, "torso_accessory");
        String torsoAccessoryModel = modelOf(c, "torso_accessory", torsoAccessoryTexture);
        String legsAccessoryTexture = textureOf(c, "legs_accessory");
        String legsAccessoryModel = modelOf(c, "legs_accessory", legsAccessoryTexture);
        String glovesTexture = textureOf(c, "gloves");
        String glovesModel = modelOf(c, "gloves", glovesTexture);
        String petKind = null;
        double petScale = 0.2;
        String petMode = null;
        String petName = null;
        String petNameColor = null;
        String petVariant = null;
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
                if (petScale <= 0.0 || petScale > 5.0) petScale = 1.0;
                petMode = strOf(p, "mode");
                petName = strOf(p, "name");
                petNameColor = strOf(p, "nameColor");
                petVariant = strOf(p, "variant");
            } catch (Throwable ignored) {
            }
        }
        CosmeticData d = new CosmeticData(level, titleText, titleColor, nameColor, badgeIcon, badgeColor, stickerIcon, stickerColor, font, petKind, petScale, petMode, petName, petNameColor, petVariant, capeTexture, capeModel, backAccessoryTexture, backAccessoryModel, headAccessoryTexture, headAccessoryModel, shieldTexture, shieldModel, faceAccessoryTexture, faceAccessoryModel, auraTexture, auraModel, shoulderAccessoryTexture, shoulderAccessoryModel, torsoAccessoryTexture, torsoAccessoryModel, legsAccessoryTexture, legsAccessoryModel, glovesTexture, glovesModel);
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

    private static String textureOf(JsonObject o, String key) {
        if (!o.has(key) || o.get(key).isJsonNull()) return null;
        try {
            if (o.get(key).isJsonObject()) {
                return strOf(o.getAsJsonObject(key), "texture");
            }
            String s = o.get(key).getAsString();
            return s == null || s.isEmpty() ? null : s;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String modelOf(JsonObject o, String key, String texture) {
        if (o.has(key) && !o.get(key).isJsonNull()) {
            try {
                if (o.get(key).isJsonObject()) {
                    String m = strOf(o.getAsJsonObject(key), "model");
                    if (m != null) return m;
                }
            } catch (Throwable ignored) {
            }
        }
        String direct = strOf(o, key + "Model");
        if (direct != null) return direct;
        if (texture != null && texture.endsWith(".png")) {
            return texture.substring(0, texture.length() - 4) + ".bbmodel";
        }
        return null;
    }

    private static String textOf(JsonObject o, String obj, String key) {
        if (!o.has(obj) || o.get(obj).isJsonNull() || !o.get(obj).isJsonObject()) return null;
        return strOf(o.getAsJsonObject(obj), key);
    }
}