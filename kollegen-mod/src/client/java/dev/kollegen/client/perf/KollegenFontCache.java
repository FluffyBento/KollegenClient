package dev.kollegen.client.perf;

public final class KollegenFontCache {
    public static final int MASK = 1023;

    public static final class WidthEntry {
        public final String text;
        public final int width;

        public WidthEntry(String text, int width) {
            this.text = text;
            this.width = width;
        }
    }

    private static final ThreadLocal<WidthEntry[]> CACHE = ThreadLocal.withInitial(() -> new WidthEntry[1024]);

    private KollegenFontCache() {}

    public static WidthEntry[] table() {
        return CACHE.get();
    }
}
