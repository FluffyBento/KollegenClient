package dev.kollegen.client.waypoints;

import com.google.gson.JsonObject;
import dev.kollegen.client.mods.Palette;
import net.minecraft.world.entity.Entity;

import java.util.Locale;
import java.util.UUID;

public class Waypoint {
    public final String id;
    public String name;
    public double x;
    public double y;
    public double z;
    public String dimension;
    public int color;
    public boolean hasColor;
    public boolean visible;
    public boolean isDeath;
    public long createdAt;

    public Waypoint(String id, String name, double x, double y, double z, String dimension, int color, boolean hasColor, boolean visible, boolean isDeath) {
        this.id = id != null && !id.isBlank() ? id : UUID.randomUUID().toString();
        this.name = name != null && !name.isBlank() ? name.trim() : "Wegpunkt";
        this.x = x;
        this.y = y;
        this.z = z;
        this.dimension = dimension != null && !dimension.isBlank() ? dimension : "minecraft:overworld";
        this.color = color;
        this.hasColor = hasColor;
        this.visible = visible;
        this.isDeath = isDeath;
        this.createdAt = System.currentTimeMillis();
    }

    public Waypoint(String id, String name, double x, double y, double z, String dimension, int color, boolean hasColor, boolean visible) {
        this(id, name, x, y, z, dimension, color, hasColor, visible, false);
    }

    public Waypoint(String name, double x, double y, double z, String dimension, int color, boolean hasColor, boolean visible, boolean isDeath) {
        this(UUID.randomUUID().toString(), name, x, y, z, dimension, color, hasColor, visible, isDeath);
    }

    public Waypoint(String name, double x, double y, double z, String dimension, int color, boolean hasColor, boolean visible) {
        this(UUID.randomUUID().toString(), name, x, y, z, dimension, color, hasColor, visible, false);
    }

    public int getEffectiveColor() {
        if (isDeath) {
            return (hasColor && color != 0) ? (0xFF000000 | color) : 0xFFE53935;
        }
        if (!hasColor) {
            return Palette.ACCENT;
        }
        return 0xFF000000 | color;
    }

    public double distanceTo(double px, double py, double pz) {
        double dx = this.x - px;
        double dy = this.y - py;
        double dz = this.z - pz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public double horizontalDistanceTo(double px, double pz) {
        double dx = this.x - px;
        double dz = this.z - pz;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public double distanceTo(Entity entity) {
        if (entity == null) return 0.0;
        return distanceTo(entity.getX(), entity.getY(), entity.getZ());
    }

    public String formatDistance(double dist) {
        if (dist >= 1000.0) {
            return String.format(Locale.ROOT, "%.1fkm", dist / 1000.0);
        }
        return Math.round(dist) + "m";
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("name", name);
        o.addProperty("x", x);
        o.addProperty("y", y);
        o.addProperty("z", z);
        o.addProperty("dimension", dimension);
        o.addProperty("color", color);
        o.addProperty("hasColor", hasColor);
        o.addProperty("visible", visible);
        o.addProperty("isDeath", isDeath);
        o.addProperty("createdAt", createdAt);
        return o;
    }

    public static Waypoint fromJson(JsonObject o) {
        if (o == null) return null;
        String id = o.has("id") ? o.get("id").getAsString() : UUID.randomUUID().toString();
        String name = o.has("name") ? o.get("name").getAsString() : "Wegpunkt";
        double x = o.has("x") ? o.get("x").getAsDouble() : 0.0;
        double y = o.has("y") ? o.get("y").getAsDouble() : 64.0;
        double z = o.has("z") ? o.get("z").getAsDouble() : 0.0;
        String dim = o.has("dimension") ? o.get("dimension").getAsString() : "minecraft:overworld";
        int color = o.has("color") ? o.get("color").getAsInt() : 0;
        boolean hasColor = o.has("hasColor") && o.get("hasColor").getAsBoolean();
        boolean visible = !o.has("visible") || o.get("visible").getAsBoolean();
        boolean isDeath = o.has("isDeath") && o.get("isDeath").getAsBoolean();
        Waypoint wp = new Waypoint(id, name, x, y, z, dim, color, hasColor, visible, isDeath);
        if (o.has("createdAt")) {
            wp.createdAt = o.get("createdAt").getAsLong();
        }
        return wp;
    }
}
