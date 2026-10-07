package dev.kollegen.client.mods;


public enum Category {
    VISUAL("Visual", "🎨"),
    HUD("HUD", "📊"),
    GAMEPLAY("Gameplay", "🎮"),
    WORLD("Welt", "🌍"),
    CHAT("Chat", "💬"),
    PERFORMANCE("Performance", "⚡"),
    MISC("Verschiedenes", "⚙");

    public final String display;
    public final String icon;

    Category(String display, String icon) {
        this.display = display;
        this.icon = icon;
    }
}
