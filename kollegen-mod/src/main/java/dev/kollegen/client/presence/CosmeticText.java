package dev.kollegen.client.presence;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

public final class CosmeticText {
    private static final int FALLBACK_NAME = 0xFFFFFF;
    private static final int FALLBACK_BADGE = 0xFFD700;
    private static final int LEVEL_GRAY = 0xAAAAAA;

    private CosmeticText() {
    }

    public static int parseColor(String hex, int fallback) {
        if (hex == null) return fallback;
        String s = hex.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.length() == 3) {
            char r = s.charAt(0), g = s.charAt(1), b = s.charAt(2);
            s = "" + r + r + g + g + b + b;
        }
        if (s.length() != 6) return fallback;
        try {
            return Integer.parseInt(s, 16);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    public static Component decorate(Component base, CosmeticData d) {
        if (base == null) return base;
        if (d == null || d.isEmpty()) return base;
        String name;
        try {
            name = base.getString();
        } catch (Throwable ignored) {
            return base;
        }
        if (name == null || name.isEmpty()) return base;
        int nameColor = parseColor(d.nameColor, FALLBACK_NAME);
        boolean bold = d.font != null && (d.font.toLowerCase().contains("bold") || d.font.toLowerCase().contains("fett"));
        boolean italic = d.font != null && (d.font.toLowerCase().contains("italic") || d.font.toLowerCase().contains("kursiv"));
        MutableComponent out = Component.empty();
        if (d.badgeIcon != null) {
            out.append(Component.literal(d.badgeIcon).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(parseColor(d.badgeColor, FALLBACK_BADGE)))));
            out.append(Component.literal(" "));
        }
        if (d.titleText != null) {
            out.append(Component.literal(d.titleText).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(nameColor)).withBold(true)));
            out.append(Component.literal(" "));
        }
        Style nameStyle = Style.EMPTY.withColor(TextColor.fromRgb(nameColor));
        if (bold) nameStyle = nameStyle.withBold(true);
        if (italic) nameStyle = nameStyle.withItalic(true);
        out.append(Component.literal(name).withStyle(nameStyle));
        if (d.stickerIcon != null) {
            out.append(Component.literal(" "));
            out.append(Component.literal(d.stickerIcon).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(parseColor(d.stickerColor, FALLBACK_NAME)))));
        }
        if (d.level > 0) {
            out.append(Component.literal(" · Lv " + d.level).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(LEVEL_GRAY))));
        }
        return out;
    }
}
