package dev.kollegen.client.mods.modules;

import dev.kollegen.client.mods.BooleanSetting;
import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.ModeSetting;
import dev.kollegen.client.mods.Module;
import dev.kollegen.client.mods.SliderSetting;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class HitboxSettings extends Module {
    public static HitboxSettings INSTANCE;

    public final ModeSetting colorPreset = new ModeSetting(
            "Hitbox-Farbe",
            "Farbe der Hitbox-Umrandung.",
            new String[]{"Weiß", "Cyan", "Rot", "Grün", "Blau", "Gelb", "Magenta", "Orange"},
            1, null
    );

    public final BooleanSetting alwaysOn = new BooleanSetting(
            "Immer aktiv",
            "Hitboxen dauerhaft ohne F3+B anzeigen.",
            true
    );

    public final BooleanSetting fill = new BooleanSetting(
            "Fläche füllen",
            "Füllt die Hitbox transparent aus.",
            true
    );

    public final SliderSetting fillOpacity = new SliderSetting(
            "Füllung-Deckkraft",
            "Deckkraft der gefüllten Hitbox.",
            0.20, 0.05, 0.90, 0.05,
            v -> Math.round(v * 100.0) + "%", null
    );

    public final SliderSetting lineWidth = new SliderSetting(
            "Linienstärke",
            "Dicke der Hitbox-Linien.",
            2.0, 1.0, 6.0, 0.5,
            v -> String.format("%.1f px", v), null
    );

    public final BooleanSetting showEyeHeight = new BooleanSetting(
            "Augenhöhe",
            "Zeigt die rote Linie auf Augenhöhe an.",
            true
    );

    public final BooleanSetting showLookVector = new BooleanSetting(
            "Blickrichtung",
            "Zeigt die Blickrichtungslinie an.",
            true
    );

    public final BooleanSetting onlyPlayers = new BooleanSetting(
            "Nur Spieler",
            "Zeigt Hitboxen ausschließlich für Spieler an.",
            false
    );

    public HitboxSettings() {
        super("hitboxes", "Hitboxen", "Umfangreiche Anpassung und permanente Anzeige von Entity-Hitboxen.", Category.VISUAL);
        INSTANCE = this;
        add(colorPreset);
        add(alwaysOn);
        add(fill);
        add(fillOpacity);
        add(lineWidth);
        add(showEyeHeight);
        add(showLookVector);
        add(onlyPlayers);
    }

    public static boolean isCustom() {
        return INSTANCE != null && INSTANCE.enabled;
    }

    public static boolean shouldAlwaysRender() {
        return INSTANCE != null && INSTANCE.enabled && INSTANCE.alwaysOn.value;
    }

    public static int getStrokeColor() {
        if (INSTANCE == null) return 0xFFFFFFFF;
        return switch (INSTANCE.colorPreset.index) {
            case 1 -> 0xFF00E5FF;
            case 2 -> 0xFFFF3333;
            case 3 -> 0xFF55FF55;
            case 4 -> 0xFF3388FF;
            case 5 -> 0xFFFFEE33;
            case 6 -> 0xFFFF33DD;
            case 7 -> 0xFFFF8800;
            default -> 0xFFFFFFFF;
        };
    }

    public static void renderCustomHitbox(Entity entity, float partialTick, boolean isServer) {
        if (!isCustom()) return;
        if (INSTANCE.onlyPlayers.value && !(entity instanceof Player)) return;

        Vec3 pos = entity.position();
        Vec3 interpolated = entity.getPosition(partialTick);
        Vec3 offset = interpolated.subtract(pos);

        int strokeCol = isServer ? 0xFF00FF00 : getStrokeColor();
        float width = (float) INSTANCE.lineWidth.value;

        AABB box = entity.getBoundingBox().move(offset);

        GizmoStyle style;
        if (INSTANCE.fill.value) {
            int alpha = (int) Math.round(INSTANCE.fillOpacity.value * 255.0);
            int fillCol = (alpha << 24) | (strokeCol & 0x00FFFFFF);
            style = GizmoStyle.strokeAndFill(strokeCol, width, fillCol);
        } else {
            style = GizmoStyle.stroke(strokeCol, width);
        }

        Gizmos.cuboid(box, style);
        Gizmos.point(interpolated, strokeCol, width + 1.0f);

        if (entity instanceof LivingEntity && INSTANCE.showEyeHeight.value) {
            double eyeY = box.minY + entity.getEyeHeight() - 0.01;
            AABB eyeBox = new AABB(box.minX, eyeY, box.minZ, box.maxX, eyeY + 0.02, box.maxZ);
            Gizmos.cuboid(eyeBox, GizmoStyle.stroke(0xFFFF2222, width));
        }

        if (INSTANCE.showLookVector.value) {
            Vec3 eyePos = entity.getEyePosition(partialTick);
            Vec3 lookVec = entity.getViewVector(partialTick);
            Gizmos.line(eyePos, eyePos.add(lookVec.scale(2.0)), 0xFF3366FF, width);
        }
    }
}
