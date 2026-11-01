package dev.kollegen.client.mods.modules;

import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.Module;
import dev.kollegen.client.mods.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

public class AutoReconnect extends Module {
    public static AutoReconnect INSTANCE;

    public final SliderSetting delay = new SliderSetting(
            "Wartezeit",
            "Sekunden bis zum automatischen Wiederverbinden.",
            5.0, 1.0, 30.0, 1.0,
            v -> Math.round(v) + "s", null
    );

    public final SliderSetting maxAttempts = new SliderSetting(
            "Versuche",
            "Maximale Versuche (0 = unbegrenzt).",
            5.0, 0.0, 20.0, 1.0,
            v -> Math.round(v) == 0 ? "Unbegrenzt" : String.valueOf(Math.round(v)), null
    );

    private static ServerAddress lastAddress;
    private static ServerData lastData;
    private static Screen lastParent;

    private static boolean active = false;
    private static int ticksLeft = 0;
    private static int attemptCount = 0;
    private static Button reconnectButton;
    private static Button cancelButton;

    public AutoReconnect() {
        super("autoreconnect", "Auto-Reconnect", "Verbindet sich bei Verbindungsabbruch nach kurzer Zeit erneut.", Category.GAMEPLAY);
        INSTANCE = this;
        this.enabled = true;
        add(delay);
        add(maxAttempts);
    }

    public static void setLastServer(ServerAddress address, ServerData data, Screen parent) {
        if (address != null) lastAddress = address;
        if (data != null) lastData = data;
        if (parent != null && !(parent instanceof DisconnectedScreen)) {
            lastParent = parent;
        }
    }

    public static boolean isReady() {
        return INSTANCE != null && INSTANCE.enabled && (lastData != null || lastAddress != null);
    }

    public static void onScreenInit(DisconnectedScreen screen, Screen parent) {
        if (parent != null && !(parent instanceof DisconnectedScreen)) {
            lastParent = parent;
        }
        if (!isReady()) {
            active = false;
            return;
        }
        int max = (int) Math.round(INSTANCE.maxAttempts.value);
        if (max > 0 && attemptCount >= max) {
            active = false;
            return;
        }
        active = true;
        ticksLeft = Math.max(20, (int) Math.round(INSTANCE.delay.value * 20.0));
    }

    public static void attachButtons(Button reconnectBtn, Button cancelBtn) {
        reconnectButton = reconnectBtn;
        cancelButton = cancelBtn;
        updateButtonText();
    }

    public static void cancel() {
        active = false;
        ticksLeft = 0;
        attemptCount = 0;
        updateButtonText();
    }

    public static void resetAttempts() {
        attemptCount = 0;
    }

    public static void reconnectNow(Minecraft mc) {
        active = false;
        ticksLeft = 0;
        attemptCount++;
        doConnect(mc);
    }

    public static void onScreenTick(Minecraft mc, DisconnectedScreen screen) {
        if (!active || !isReady()) return;
        if (ticksLeft > 0) {
            ticksLeft--;
            if (ticksLeft % 20 == 0 || ticksLeft < 20) {
                updateButtonText();
            }
            if (ticksLeft <= 0) {
                active = false;
                attemptCount++;
                doConnect(mc);
            }
        }
    }

    private static void updateButtonText() {
        if (reconnectButton == null) return;
        if (active && ticksLeft > 0) {
            int sec = (ticksLeft + 19) / 20;
            reconnectButton.setMessage(Component.literal("Jetzt verbinden (" + sec + "s)"));
            if (cancelButton != null) cancelButton.active = true;
        } else {
            reconnectButton.setMessage(Component.literal("Jetzt verbinden"));
            if (cancelButton != null) cancelButton.active = false;
        }
    }

    private static void doConnect(Minecraft mc) {
        if (mc == null) return;
        ServerData data = lastData;
        ServerAddress address = lastAddress;
        if (data == null && address != null) {
            data = new ServerData("Server", address.getHost() + ":" + address.getPort(), ServerData.Type.OTHER);
        } else if (address == null && data != null && data.ip != null) {
            address = ServerAddress.parseString(data.ip);
        }
        if (data == null || address == null) return;
        Screen parent = lastParent != null ? lastParent : new TitleScreen();
        ConnectScreen.startConnecting(parent, mc, address, data, false, null);
    }

    public static boolean isActive() {
        return active;
    }

    public static int getSecondsLeft() {
        return Math.max(0, (ticksLeft + 19) / 20);
    }

    public static int getAttemptCount() {
        return attemptCount;
    }
}
