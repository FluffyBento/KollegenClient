package dev.kollegen.client.mods;


public enum Category {
    VISUAL("Visual", "🎨"),
    HUD("HUD", "📊"),
    GAMEPLAY("Gameplay", "🎮"),
    WORLD("Welt", "🌍"),
    CHAT("Chat", "💬"),
    PERFORMANCE("Performance", "⚡"),
    MISC("Verschiedenes", "⚙"),
    STORE("Store", "🛍"),
    COSMETICS("Cosmetics", "✨"),
    PETS("Pets", "🐾"),
    EXTRAS("Extras", "🕹");

    public final String display;
    public final String icon;

    Category(String display, String icon) {
        this.display = display;
        this.icon = icon;
    }
}
