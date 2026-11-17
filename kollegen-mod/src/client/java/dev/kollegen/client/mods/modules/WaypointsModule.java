package dev.kollegen.client.mods.modules;

import dev.kollegen.client.map.WorldMapScreen;
import dev.kollegen.client.mods.BooleanSetting;
import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.KeybindSetting;
import dev.kollegen.client.mods.Module;
import dev.kollegen.client.waypoints.AddEditWaypointScreen;
import dev.kollegen.client.waypoints.WaypointListScreen;
import dev.kollegen.client.waypoints.WaypointRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.lwjgl.glfw.GLFW;

public class WaypointsModule extends Module {
    public static WaypointsModule INSTANCE;

    public final KeybindSetting hotkeyMap = new KeybindSetting("Karte öffnen", "Taste zum Öffnen der großen interaktiven Weltkarte.");
    public final KeybindSetting hotkeyAdd = new KeybindSetting("Schnell-Wegpunkt", "Taste zum schnellen Setzen eines Wegpunkts am aktuellen Standort.");
    public final KeybindSetting hotkeyMenu = new KeybindSetting("Wegpunkt-Menü", "Taste zum Öffnen der Wegpunkt-Verwaltung.");
    public final BooleanSetting showDistance = new BooleanSetting("Entfernung anzeigen", "Zeigt die Entfernung zum Wegpunkt in Metern an.", true);
    public final BooleanSetting showInCompass = new BooleanSetting("Im Kompass anzeigen", "Zeigt aktive Wegpunkte in der Kompass-Leiste an.", true);
    public final BooleanSetting showInWorld = new BooleanSetting("In der Welt anzeigen", "Zeigt Wegpunkt-Marker in der 3D-Welt und auf dem Bildschirm.", true);
    public final BooleanSetting beaconBeam = new BooleanSetting("Lichtsäule", "Rendert eine sichtbare Lichtsäule bis in den Himmel.", true);
    public final BooleanSetting hudPins = new BooleanSetting("HUD-Pins", "Zeigt schwebende Wegpunkt-Pins auf dem Bildschirm an.", false);

    public WaypointsModule() {
        super("waypoints", "Wegpunkte", "Verwaltet und visualisiert Wegpunkte mit Namen, Farben und Entfernungsanzeige.", Category.WORLD);
        INSTANCE = this;
        this.hotkeyMap.value = 77;
        this.hotkeyAdd.value = 78;
        this.hotkeyMenu.value = -1;
        this.add(this.hotkeyMap);
        this.add(this.hotkeyAdd);
        this.add(this.hotkeyMenu);
        this.add(this.showDistance);
        this.add(this.showInCompass);
        this.add(this.showInWorld);
        this.add(this.beaconBeam);
        this.add(this.hudPins);
        this.enabled = true;
    }

    @Override
    public void onKey() {
        if (!this.enabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.getWindow() == null) return;
        long handle = mc.getWindow().handle();

        if (this.hotkeyMap.value >= 0 && GLFW.glfwGetKey(handle, this.hotkeyMap.value) == GLFW.GLFW_PRESS) {
            if (mc.gui.screen() instanceof WorldMapScreen) {
                mc.setScreenAndShow(null);
                return;
            }
            if (mc.gui.screen() == null) {
                mc.setScreenAndShow(new WorldMapScreen(null));
                return;
            }
        }

        if (this.hotkeyAdd.value >= 0 && GLFW.glfwGetKey(handle, this.hotkeyAdd.value) == GLFW.GLFW_PRESS) {
            if (mc.gui.screen() == null) {
                mc.setScreenAndShow(new AddEditWaypointScreen(null, null));
                return;
            }
        }

        if (this.hotkeyMenu.value >= 0 && GLFW.glfwGetKey(handle, this.hotkeyMenu.value) == GLFW.GLFW_PRESS) {
            if (mc.gui.screen() instanceof WaypointListScreen) {
                mc.setScreenAndShow(null);
                return;
            }
            if (mc.gui.screen() == null) {
                mc.setScreenAndShow(new WaypointListScreen(null));
            }
        }
    }

    @Override
    public void onRenderHud(GuiGraphicsExtractor g, float td) {
        if (!this.enabled) return;
        WaypointRenderer.renderHudMarkers(g, td);
    }
}
