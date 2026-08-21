package dev.kollegen.client.mods;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Zählt Maus-Klicks für die CPS-Anzeige und merkt sich den Tasten-Druck-Zustand
 * der Maustasten (für Keystrokes). Wird aus {@code MouseHandlerMixin} gefüllt.
 */
public final class ClickTracker {
    public static final Deque<Long> LEFT = new ArrayDeque<>();
    public static final Deque<Long> RIGHT = new ArrayDeque<>();
    public static boolean leftDown = false;
    public static boolean rightDown = false;

    private ClickTracker() {
    }

    public static void pressLeft() {
        LEFT.add(System.currentTimeMillis());
        leftDown = true;
    }

    public static void releaseLeft() {
        leftDown = false;
    }

    public static void pressRight() {
        RIGHT.add(System.currentTimeMillis());
        rightDown = true;
    }

    public static void releaseRight() {
        rightDown = false;
    }

    public static int cps(Deque<Long> d) {
        long now = System.currentTimeMillis();
        while (!d.isEmpty() && now - d.peek() > 1000) d.poll();
        return d.size();
    }
}
