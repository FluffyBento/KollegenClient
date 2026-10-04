package dev.kollegen.client.presence;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public final class CosmeticText {
    public static final String LOGO_CHAR = "";
    private static final FontDescription LOGO_FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath("kollegen", "logo"));
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

    public static String safeIcon(String icon) {
        if (icon == null || icon.isEmpty()) return null;
        for (int i = 0; i < icon.length(); i++) {
            char c = icon.charAt(i);
            if (Character.isHighSurrogate(c) || Character.isLowSurrogate(c)) return "◆";
        }
        return icon;
    }

    public static Component decoratePlayer(Component base, UUID id) {
        if (base == null || id == null) return base;
        boolean kollege = KollegenPresence.isKollegen(id);
        CosmeticData d = KollegenPresence.getCosmetics(id);
        boolean hasData = d != null && !d.isEmpty();
        if (!kollege && !hasData) return base;
        String plain = null;
        try {
            plain = base.getString();
        } catch (Throwable ignored) {
        }
        if (plain != null && plain.contains(LOGO_CHAR)) return base;
        MutableComponent out = Component.empty();
        if (kollege) {
            out.append(Component.literal(LOGO_CHAR + " ")
                    .withStyle(Style.EMPTY.withFont(LOGO_FONT).withColor(TextColor.fromRgb(0xFFFFFF))));
        }
        if (hasData) {
            String nm = (plain == null || plain.isEmpty()) ? "?" : plain;
            out.append(decorateName(nm, d));
        } else {
            out.append(base);
        }
        return out;
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
        return decorateName(name, d);
    }

    private static Component decorateName(String name, CosmeticData d) {
        int nameColor = parseColor(d.nameColor, FALLBACK_NAME);
        boolean bold = d.font != null && (d.font.toLowerCase().contains("bold") || d.font.toLowerCase().contains("fett"));
        boolean italic = d.font != null && (d.font.toLowerCase().contains("italic") || d.font.toLowerCase().contains("kursiv"));
        String badge = safeIcon(d.badgeIcon);
        MutableComponent out = Component.empty();
        if (badge != null) {
            out.append(Component.literal(badge).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(parseColor(d.badgeColor, FALLBACK_BADGE)))));
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
        String sticker = safeIcon(d.stickerIcon);
        if (sticker != null) {
            out.append(Component.literal(" "));
            out.append(Component.literal(sticker).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(parseColor(d.stickerColor, FALLBACK_NAME)))));
        }
        if (d.level > 0) {
            out.append(Component.literal(" · Lv " + d.level).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(LEVEL_GRAY))));
        }
        return out;
    }
}
