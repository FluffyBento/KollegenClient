package dev.kollegen.client.ui;

public enum UiStyle {
    LIQUID_GLASS("Liquid Glass", "Abgerundet mit weichem Glas-Look"),
    MINECRAFT_VANILLA("Minecraft Vanilla", "Eckig im klassischen Vanilla-Container-Look"),
    MODERN_FLAT("Modern Flat", "Minimalistisch, flach mit feinem 1px-Rand"),
    NEON_CYBER("Neon Cyber", "Dunkles Schwarz mit leuchtendem Akzent-Glow");

    public final String title;
    public final String desc;

    UiStyle(String title, String desc) {
        this.title = title;
        this.desc = desc;
    }
}
