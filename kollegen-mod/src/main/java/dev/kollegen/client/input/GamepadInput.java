package dev.kollegen.client.input;

import dev.kollegen.client.KollegenMod;
import dev.kollegen.client.mixin.ClientInputAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;

import java.util.ArrayList;
import java.util.List;

/**
 * SteamDeck-Gameplay-Controller (in-game Steuerung wie Controlify).
 * <p>
 * Solange der Launcher den SteamDeck-Modus aktiviert hat (State-File
 * {@code mods/.kollegen-controller} = "on", geprüft in {@link ControllerMode})
 * und man sich im normalen Spiel befindet (kein Screen offen), übernimmt dieses
 * Modul die komplette Spielsteuerung:
 * <ul>
 *   <li>Linker Stick → laufen/springen-Achse (Input-Record + moveVector analog, nicht digital)</li>
 *   <li>Rechter Stick → Kamera (Entity.turn über die Maus-Sensitivity)</li>
 *   <li>D-Pad Links/Rechts → Hotbar-Slot</li>
 *   <li>D-Pad Hoch/Runter → Hotbar auch</li>
 *   <li>LB/RB → Hotbar vor/zurück</li>
 *   <li>LS-Druck → Sprinten (quasi Doppel-W)</li>
 *   <li>RS-Druck → Kameraperspektive wechseln (F5)</li>
 *   <li>A → Springen</li>
 *   <li>B → Schleichen (halten)</li>
 *   <li>X → Inventar öffnen</li>
 *   <li>Y → Benutzen/Platzieren (Rechtsklick, halten)</li>
 *   <li>LT → Benutzen/Platzieren (halten)</li>
 *   <li>RT → Angreifen/Abbauen (halten)</li>
 *   <li>Select/Zurück → Chat öffnen</li>
 *   <li>Start/Menü → Pause-Menü</li>
 * </ul>
 * Die eigentliche Überschreibung des Vanilla-KeyboardInput passiert im Mixin
 * {@code KeyboardInputMixin} (HEAD-Cancel), das pro Tick
 * {@link #takeOverMovement()} / {@link #applyMovement()} aufruft. Kamera trotz
 * Netz-Tick-Hook glatt: wird von der render-Frame-Hook auf globaler Tick-Loop
 * ({@code Minecraft.runTick}) gelesen.
 * <p>
 * Bewusst im Stil des Mods gehalten (GLFW direkt, ohne fabric-api): analog zu
 * {@link ControllerMode} und {@link KollegenKeybind}.
 */
public final class GamepadInput {

    private static final GLFWGamepadState GAMEPAD = GLFWGamepadState.create();

    // Kamera-Sensitivity basiert auf den Maus-Options (0.0 .. 1.0).
    private static double sensScale = 0.6;

    // Gedrückt-Halte-Zustände der Actions (für edge-getriggerte Sachen)
    private static boolean previousAttackHeld = false;
    private static boolean previousUseHeld = false;
    private static boolean previousHotbarLeftHeld = false;
    private static boolean previousHotbarRightHeld = false;
    private static boolean previousPerspectiveHeld = false;
    private static boolean previousInventoryHeld = false;
    private static boolean previousChatHeld = false;
    private static boolean previousPauseHeld = false;

    private static int selectedSlot = -1;
    private static boolean slotDirty = false;

    // Analoger Bewegungsvektor des linken Sticks (nicht normalisiert-belassen,
    // damit Helm-/Analog-Werte wie bei Controlify erhalten bleiben).
    private static float moveX = 0f, moveY = 0f;

    // Wie oben, für den Input-Record (Schwellwert, ab wann als "gedrückt" gilt)
    private static boolean fwd, back, left, right;
    private static boolean jump, sneak, sprint;

    private static final float DEADZONE = 0.18f;
    private static final float BUTTON_THRESHOLD = 0.4f;

    private GamepadInput() {
    }

    /** True, sobald ein Gamepad verbunden ist und der SteamDeck-Modus aktiv ist. */
    public static boolean isActive(Minecraft mc) {
        if (!ControllerMode.isActive()) return false;
        return isAnyGamepadPresent();
    }

    /** Es ist ein Gamepad (GLFW) mit einem State verbunden. */
    public static boolean isAnyGamepadPresent() {
        return GamepadDetect.scan(GAMEPAD) != -1;
    }

    /**
     * Soll der KeyboardInput-Tick von uns übernommen werden?
     * NUR im freien Spiel: active (SteamDeck-Modus) + Gamepad da + kein Screen offen.
     */
    public static boolean takeOverMovement() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) return false;
        if (mc.screen != null) return false;
        if (!ControllerMode.isActive()) return false;
        return isAnyGamepadPresent();
    }

    /**
     * Liest den linken Stick und die Bewegungs-Buttons und schreibt den
     * {@code Input}-Record + analoges {@code Vec2} moveVector in den ClientInput.
     */
    public static void applyMovement() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        LocalPlayer player = mc.player;
        if (!(player.input instanceof ClientInputAccessor)) return;

        readStick(DEADZONE);

        fwd = moveY < -BUTTON_THRESHOLD;
        back = moveY > BUTTON_THRESHOLD;
        left = moveX < -BUTTON_THRESHOLD;
        right = moveX > BUTTON_THRESHOLD;

        // A = Sprung (Beliebig: auch X ohne Inventar-Kontext). D-Pad-Up = wie beim
        // Cursor-Modus zusätzlich laufen.
        jump = GAMEPAD.buttons(GLFW.GLFW_GAMEPAD_BUTTON_A) == GLFW.GLFW_PRESS;
        sneak = GAMEPAD.buttons(GLFW.GLFW_GAMEPAD_BUTTON_B) == GLFW.GLFW_PRESS;
        sprint = isSprintPressed();

        Input keyPresses = new Input(fwd, back, left, right, jump, sneak, sprint);
        ((ClientInputAccessor) player.input).kollegen$setKeyPresses(keyPresses);

        // moveVector analog: x = links/rechts (+ = vorwärts-Y beim Vec2 ist y),
        // y = vor/zurück. Vanilla normalisiert nur auf Achsen-Zoom; analog lassen.
        float forwardImpulse = -moveY; // Stick nach oben (GLFW -1) = vorwärts (+)
        float strafeImpulse = moveX;
        ((ClientInputAccessor) player.input)
                .kollegen$setMoveVector(new Vec2(strafeImpulse, forwardImpulse));
    }

    private static boolean isSprintPressed() {
        // LS-Druck (GLFW_GAMEPAD_BUTTON_LEFT_THUMB) → Sprinten (Toggle wie Doppel-W)
        return GAMEPAD.buttons(GLFW.GLFW_GAMEPAD_BUTTON_LEFT_THUMB) == GLFW.GLFW_PRESS;
    }

    /** Liest die Stick-Achsen mit Deadzone in moveX/moveY. */
    private static void readStick(float dead) {
        float lx = GAMEPAD.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_X);
        float ly = GAMEPAD.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_Y);
        moveX = (Math.abs(lx) < dead) ? 0 : (lx > 0 ? 1 : -1) * (Math.abs(lx));
        moveY = (Math.abs(ly) < dead) ? 0 : (ly > 0 ? 1 : -1) * (Math.abs(ly));
        // D-Pad als Zusatz (springen/schleichen/links-rechts)
        float dx = 0, dy = 0;
        if (GAMEPAD.buttons(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_LEFT) == GLFW.GLFW_PRESS) dx = -1;
        else if (GAMEPAD.buttons(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_RIGHT) == GLFW.GLFW_PRESS) dx = 1;
        if (GAMEPAD.buttons(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_UP) == GLFW.GLFW_PRESS) dy = -1;
        else if (GAMEPAD.buttons(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_DOWN) == GLFW.GLFW_PRESS) dy = 1;
        if (dx != 0) moveX = dx;
        if (dy != 0) moveY = dy;
    }

    /**
     * Pro Render-Frame aufgerufen (aus {@code KollegenMod.onFrame()} / dem
     * {@code Minecraft.runTick}-Hook): glatte Kamera-Drehung mit dem rechten Stick.
     * Die Aktionen laufen weiterhin im Client-Tick, nur die Drehung ist pro Frame.
     */
    public static void onFrame(Minecraft mc) {
        if (mc == null || mc.player == null || mc.level == null) return;
        if (mc.screen != null) return;
        if (!ControllerMode.isActive()) return;
        if (!isAnyGamepadPresent()) return;
        turnCamera(mc);
    }

    /**
     * Pro Client-Tick aufgerufen (onTick): Kamera-Achsen und Aktionen.
     */
    public static void tick(Minecraft mc) {
        if (mc == null || mc.player == null || mc.level == null) return;
        if (mc.screen != null) return; // Cursor-Modus läuft in ControllerMode
        if (!ControllerMode.isActive()) return;
        if (!isAnyGamepadPresent()) return;

        // ── Rechter Stick → Kamera ──
        turnCamera(mc);

        // ── Aktionen (edge-getriggert) ──
        // Trigger sind GLFW-Achsen (4/5), kein Button-Index.
        boolean attackHeld = GAMEPAD.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER) > 0.5f;
        boolean useHeld = GAMEPAD.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_TRIGGER) > 0.5f;
        boolean hotbarLeft = GAMEPAD.buttons(GLFW.GLFW_GAMEPAD_BUTTON_LEFT_BUMPER) == GLFW.GLFW_PRESS;
        boolean hotbarRight = GAMEPAD.buttons(GLFW.GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER) == GLFW.GLFW_PRESS;
        boolean perspective = GAMEPAD.buttons(GLFW.GLFW_GAMEPAD_BUTTON_RIGHT_THUMB) == GLFW.GLFW_PRESS;
        boolean inventory = GAMEPAD.buttons(GLFW.GLFW_GAMEPAD_BUTTON_X) == GLFW.GLFW_PRESS;
        boolean chat = GAMEPAD.buttons(GLFW.GLFW_GAMEPAD_BUTTON_BACK) == GLFW.GLFW_PRESS;
        boolean pause = GAMEPAD.buttons(GLFW.GLFW_GAMEPAD_BUTTON_START) == GLFW.GLFW_PRESS;

        handleAttack(mc, attackHeld);
        handleUse(mc, useHeld);
        handleHotbar(mc, hotbarLeft, hotbarRight);
        handlePerspective(mc, perspective);
        handleInventory(mc, inventory);
        handleChat(mc, chat);
        handlePause(mc, pause);

        previousAttackHeld = attackHeld;
        previousUseHeld = useHeld;
        previousHotbarLeftHeld = hotbarLeft;
        previousHotbarRightHeld = hotbarRight;
        previousPerspectiveHeld = perspective;
        previousInventoryHeld = inventory;
        previousChatHeld = chat;
        previousPauseHeld = pause;
    }

    private static void turnCamera(Minecraft mc) {
        try {
            float rx = GAMEPAD.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_X);
            float ry = GAMEPAD.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_Y);
            if (Math.abs(rx) < DEADZONE) rx = 0;
            if (Math.abs(ry) < DEADZONE) ry = 0;
            if (rx == 0 && ry == 0) return;

            double sens = mc.options.sensitivity().get();
            sensScale = sens;
            // Entity.turn skaliert intern mit 0.15 (wie die Vanilla-Maus).
            // Produkt: rx * sens * factor * 0.15 ≈ 2.4°/Frame bei vollem Stick
            // und Standard-Sens 0.6 → bei 60 fps ~144°/s. Etwas schneller als die
            // Maus, fühlt sich analog angenehm an.
            double factor = 2.5;
            mc.player.turn(rx * sensScale * factor, -ry * sensScale * factor);
        } catch (Throwable ignored) {
        }
    }

    private static void handleAttack(Minecraft mc, boolean held) {
        // RT gehalten = Angriff/Abbau. startAttack() hat den Vanilla-missTime-Guard
        // und triggert für Blöcke startDestroyBlock (dann läuft continueDestroyBlock
        // im Tick weiter); zusätzliche Aufrufe im Getragener-Zustand sind harmlos.
        try {
            if (held) ((dev.kollegen.client.mixin.MinecraftAccessor) mc).kollegen$startAttack();
        } catch (Throwable ignored) {
        }
    }

    private static void handleUse(Minecraft mc, boolean held) {
        // LT gehalten = Benutzen/Platzieren. startUseItem() hat den internen
        // isUsingItem-Guard, damit das Item nicht jeden Tick neu gestartet wird.
        boolean released = !held && previousUseHeld;
        try {
            if (held) ((dev.kollegen.client.mixin.MinecraftAccessor) mc).kollegen$startUseItem();
            else if (released) mc.player.stopUsingItem();
        } catch (Throwable ignored) {
        }
    }

    private static void handleHotbar(Minecraft mc, boolean left, boolean right) {
        boolean l = left && !previousHotbarLeftHeld;
        boolean r = right && !previousHotbarRightHeld;
        if (!l && !r) return;
        try {
            int slot = mc.player.getInventory().getSelectedSlot();
            if (l) slot = (slot - 1 + 9) % 9;
            if (r) slot = (slot + 1) % 9;
            mc.player.getInventory().setSelectedSlot(slot);
        } catch (Throwable ignored) {
        }
    }

    private static void handlePerspective(Minecraft mc, boolean held) {
        if (!(held && !previousPerspectiveHeld)) return;
        try {
            mc.options.setCameraType(mc.options.getCameraType().cycle());
        } catch (Throwable ignored) {
        }
    }

    private static void handleInventory(Minecraft mc, boolean held) {
        if (!(held && !previousInventoryHeld)) return;
        try {
            mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
        } catch (Throwable ignored) {
        }
    }

    private static void handleChat(Minecraft mc, boolean held) {
        if (!(held && !previousChatHeld)) return;
        try {
            mc.setScreen(new net.minecraft.client.gui.screens.ChatScreen("", false));
        } catch (Throwable ignored) {
        }
    }

    private static void handlePause(Minecraft mc, boolean held) {
        if (!(held && !previousPauseHeld)) return;
        try {
            mc.setScreen(new net.minecraft.client.gui.screens.PauseScreen(true));
        } catch (Throwable ignored) {
        }
    }
}