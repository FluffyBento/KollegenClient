package dev.kollegen.client.waypoints;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class WaypointManager {
    private static final List<Waypoint> WAYPOINTS = new CopyOnWriteArrayList<>();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("kollegen-waypoints.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private WaypointManager() {
    }

    public static List<Waypoint> getWaypoints() {
        return Collections.unmodifiableList(WAYPOINTS);
    }

    public static void add(Waypoint wp) {
        if (wp == null) return;
        WAYPOINTS.add(wp);
        save();
    }

    public static void remove(String id) {
        if (id == null) return;
        WAYPOINTS.removeIf(w -> w.id.equals(id));
        save();
    }

    public static void update(Waypoint wp) {
        if (wp == null) return;
        for (int i = 0; i < WAYPOINTS.size(); i++) {
            if (WAYPOINTS.get(i).id.equals(wp.id)) {
                WAYPOINTS.set(i, wp);
                save();
                return;
            }
        }
        WAYPOINTS.add(wp);
        save();
    }

    public static Waypoint getById(String id) {
        if (id == null) return null;
        for (Waypoint wp : WAYPOINTS) {
            if (wp.id.equals(id)) return wp;
        }
        return null;
    }

    public static String getCurrentDimension() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return "minecraft:overworld";
        Identifier dim = mc.level.dimension().identifier();
        return dim.toString();
    }

    public static List<Waypoint> getForCurrentDimension() {
        String currentDim = getCurrentDimension();
        String pathOnly = currentDim.contains(":") ? currentDim.substring(currentDim.indexOf(':') + 1) : currentDim;
        List<Waypoint> list = new ArrayList<>();
        for (Waypoint wp : WAYPOINTS) {
            String wpDim = wp.dimension;
            String wpPath = wpDim.contains(":") ? wpDim.substring(wpDim.indexOf(':') + 1) : wpDim;
            if (wpDim.equalsIgnoreCase(currentDim) || wpPath.equalsIgnoreCase(pathOnly)) {
                list.add(wp);
            }
        }
        return list;
    }

    public static List<Waypoint> getVisibleForCurrentDimension() {
        String currentDim = getCurrentDimension();
        String pathOnly = currentDim.contains(":") ? currentDim.substring(currentDim.indexOf(':') + 1) : currentDim;
        List<Waypoint> list = new ArrayList<>();
        for (Waypoint wp : WAYPOINTS) {
            if (!wp.visible) continue;
            String wpDim = wp.dimension;
            String wpPath = wpDim.contains(":") ? wpDim.substring(wpDim.indexOf(':') + 1) : wpDim;
            if (wpDim.equalsIgnoreCase(currentDim) || wpPath.equalsIgnoreCase(pathOnly)) {
                list.add(wp);
            }
        }
        return list;
    }

    public static void createQuickWaypoint(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        double x = Math.round(mc.player.getX() * 10.0) / 10.0;
        double y = Math.round(mc.player.getY() * 10.0) / 10.0;
        double z = Math.round(mc.player.getZ() * 10.0) / 10.0;
        String dim = getCurrentDimension();
        String wpName = (name != null && !name.isBlank()) ? name.trim() : ("Wegpunkt " + (WAYPOINTS.size() + 1));
        Waypoint wp = new Waypoint(null, wpName, x, y, z, dim, 0, false, true);
        add(wp);
    }

    public static synchronized void load() {
        try {
            if (!Files.exists(PATH)) return;
            String json = Files.readString(PATH);
            JsonElement el = JsonParser.parseString(json);
            if (!el.isJsonArray()) return;
            JsonArray arr = el.getAsJsonArray();
            WAYPOINTS.clear();
            for (JsonElement item : arr) {
                if (item.isJsonObject()) {
                    Waypoint wp = Waypoint.fromJson(item.getAsJsonObject());
                    if (wp != null) {
                        WAYPOINTS.add(wp);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public static synchronized void save() {
        try {
            JsonArray arr = new JsonArray();
            for (Waypoint wp : WAYPOINTS) {
                arr.add(wp.toJson());
            }
            if (PATH.getParent() != null) {
                Files.createDirectories(PATH.getParent());
            }
            Files.writeString(PATH, GSON.toJson(arr));
        } catch (Throwable ignored) {
        }
    }
}
