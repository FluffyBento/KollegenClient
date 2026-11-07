package dev.kollegen.client.mods;

public enum Category {
    VISUAL("Grafik", "🎨"),
    HUD("HUD", "📊"),
    GAMEPLAY("Gameplay", "🎮"),
    WORLD("Welt", "🌍"),
    CHAT("Chat", "💬"),
    PERFORMANCE("Leistung", "⚡"),
    MISC("Verschiedenes", "⚙"),
    STORE("Shop", "🛍"),
    COSMETICS("Kosmetika", "✨"),
    EMOTES("Emotes", "💃"),
    SKIN_CAPE("Skin & Cape", "👤"),
    PETS("Haustiere", "🐾"),
    EXTRAS("Extras", "🕹");

    public final String display;
    public final String icon;

    Category(String display, String icon) {
        this.display = display;
        this.icon = icon;
    }
}
