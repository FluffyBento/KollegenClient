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

    public static Component logoComponent() {
        return Component.literal(LOGO_CHAR)
                .withStyle(Style.EMPTY.withFont(LOGO_FONT).withColor(TextColor.fromRgb(0xFFFFFF)));
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
            out.append(logoComponent());
            out.append(Component.literal(" "));
        }
        if (hasData) {
            String nm = (plain == null || plain.isEmpty()) ? "?" : plain;
            out.append(decorateName(nm, d));
        } else {
            out.append(base);
        }
        return out;
    }

    public static int levelTierColor(int level) {
        if (level >= 80) return 0xFFAA00;
        if (level >= 60) return 0xAA55FF;
        if (level >= 30) return 0x5555FF;
        if (level >= 10) return 0x55FF55;
        return 0x9AA0A6;
    }

    public static Component decorateNameLine(Component base, UUID id) {
        if (base == null || id == null) return base;
        CosmeticData d = KollegenPresence.getCosmetics(id);
        if (d == null || d.isEmpty()) return base;
        String plain;
        try {
            plain = base.getString();
        } catch (Throwable ignored) {
            return base;
        }
        if (plain == null || plain.isEmpty()) return base;
        int nameColor = parseColor(d.nameColor, FALLBACK_NAME);
        MutableComponent out = Component.empty();
        if (d.badgeIcon != null) {
            out.append(Component.literal(d.badgeIcon).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(parseColor(d.badgeColor, FALLBACK_BADGE)))));
        }
        out.append(Component.literal(plain).withStyle(nameStyle(nameColor, d.font)));
        if (d.stickerIcon != null) {
            out.append(Component.literal(d.stickerIcon).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(parseColor(d.stickerColor, FALLBACK_NAME)))));
        }
        return out;
    }

    public static Component titleComponent(UUID id) {
        CosmeticData d = id == null ? null : KollegenPresence.getCosmetics(id);
        if (d == null || d.titleText == null) return null;
        int nameColor = parseColor(d.nameColor, FALLBACK_NAME);
        return Component.literal(d.titleText)
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(nameColor)).withBold(true));
    }

    public static Component levelComponent(UUID id) {
        CosmeticData d = id == null ? null : KollegenPresence.getCosmetics(id);
        if (d == null || d.level <= 0) return null;
        MutableComponent out = Component.empty();
        out.append(Component.literal("[").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x555555))));
        out.append(Component.literal("lv ").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(LEVEL_GRAY))));
        out.append(Component.literal(String.valueOf(d.level)).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x7DD3FC))));
        out.append(Component.literal("]").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x555555))));
        return out;
    }

    public static Component decorateNametag(Component base, UUID id) {
        if (base == null || id == null) return base;
        CosmeticData d = KollegenPresence.getCosmetics(id);
        if (d == null || d.isEmpty()) return base;
        String plain;
        try {
            plain = base.getString();
        } catch (Throwable ignored) {
            return base;
        }
        if (plain == null || plain.isEmpty() || plain.contains("· Lv ")) return base;
        int nameColor = parseColor(d.nameColor, FALLBACK_NAME);
        MutableComponent out = Component.empty();
        if (d.titleText != null) {
            out.append(Component.literal(d.titleText).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(nameColor)).withBold(true)));
            out.append(Component.literal("\n"));
        }
        out.append(Component.literal(LOGO_CHAR)
                .withStyle(Style.EMPTY.withFont(LOGO_FONT).withColor(TextColor.fromRgb(0xFFFFFF))));
        out.append(Component.literal(" "));
        if (d.badgeIcon != null) {
            out.append(Component.literal(d.badgeIcon).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(parseColor(d.badgeColor, FALLBACK_BADGE)))));
        }
        out.append(Component.literal(plain).withStyle(nameStyle(nameColor, d.font)));
        if (d.stickerIcon != null) {
            out.append(Component.literal(d.stickerIcon).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(parseColor(d.stickerColor, FALLBACK_NAME)))));
        }
        if (d.level > 0) {
            out.append(Component.literal("\n"));
            out.append(Component.literal("· Lv " + d.level + " ·").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(LEVEL_GRAY))));
        }
        return out;
    }

    public static Component decorateTab(Component base, UUID id) {
        if (base == null || id == null) return base;
        boolean kollege = KollegenPresence.isKollegen(id);
        CosmeticData d = KollegenPresence.getCosmetics(id);
        boolean hasData = d != null && !d.isEmpty();
        boolean useful = d != null && (d.badgeIcon != null || d.nameColor != null || d.stickerIcon != null);
        if (!kollege && !useful) return base;
        String plain = null;
        try {
            plain = base.getString();
        } catch (Throwable ignored) {
        }
        if (plain != null && plain.contains(LOGO_CHAR)) return base;
        MutableComponent out = Component.empty();
        if (kollege) {
            out.append(logoComponent());
            out.append(Component.literal(" "));
        }
        if (hasData) {
            if (d.badgeIcon != null) {
                out.append(Component.literal(d.badgeIcon).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(parseColor(d.badgeColor, FALLBACK_BADGE)))));
                out.append(Component.literal(" "));
            }
            int nameColor = parseColor(d.nameColor, FALLBACK_NAME);
            if (d.nameColor != null) {
                String nm = (plain == null || plain.isEmpty()) ? "?" : plain;
                out.append(Component.literal(nm).withStyle(nameStyle(nameColor, d.font)));
            } else if (plain != null && !plain.isEmpty()) {
                out.append(Component.literal(plain));
            } else {
                out.append(base);
            }
            if (d.stickerIcon != null) {
                out.append(Component.literal(" "));
                out.append(Component.literal(d.stickerIcon).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(parseColor(d.stickerColor, FALLBACK_NAME)))));
            }
        } else if (plain != null && !plain.isEmpty()) {
            out.append(Component.literal(plain));
        } else {
            out.append(base);
        }
        return out;
    }

    private static Style nameStyle(int nameColor, String font) {
        Style s = Style.EMPTY.withColor(TextColor.fromRgb(nameColor));
        if (font != null && (font.toLowerCase().contains("bold") || font.toLowerCase().contains("fett"))) s = s.withBold(true);
        if (font != null && (font.toLowerCase().contains("italic") || font.toLowerCase().contains("kursiv"))) s = s.withItalic(true);
        return s;
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
