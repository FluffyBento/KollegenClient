package dev.kollegen.client.mods.modules;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kollegen.client.mods.BooleanSetting;
import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.Module;
import dev.kollegen.client.mods.SliderSetting;
import dev.kollegen.client.presence.RenderStateIds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.UUID;

public class ShieldSettings extends Module {
    public static ShieldSettings INSTANCE;

    public final BooleanSetting lowerShield = new BooleanSetting(
            "PvP-Position (Tief & Seitlich)",
            "Versetzt das gehaltene Schild tiefer und zur Seite für freies Sichtfeld (wie im Texture Pack).",
            true
    );

    public final SliderSetting scale = new SliderSetting(
            "Schild-Größe",
            "Größe des Schilds in der eigenen Hand.",
            0.75, 0.3, 1.2, 0.05,
            v -> Math.round(v * 100.0) + "%", null
    );

    public final SliderSetting offsetX = new SliderSetting(
            "Seitlicher Versatz (X)",
            "Verschiebt das Schild nach außen zur Seite.",
            0.15, -0.6, 0.6, 0.02,
            v -> String.format("%.2f", v), null
    );

    public final SliderSetting offsetY = new SliderSetting(
            "Höhen-Versatz (Y)",
            "Senkt das Schild nach unten ab.",
            -0.20, -0.8, 0.4, 0.02,
            v -> String.format("%.2f", v), null
    );

    public final SliderSetting offsetZ = new SliderSetting(
            "Tiefen-Versatz (Z)",
            "Verschiebt das Schild nach vorne oder hinten.",
            0.0, -0.6, 0.6, 0.02,
            v -> String.format("%.2f", v), null
    );

    public final SliderSetting rotX = new SliderSetting(
            "Neigung (X-Achse)",
            "Kippt das Schild nach vorne oder hinten für freie Sicht.",
            0.0, -180.0, 180.0, 5.0,
            v -> Math.round(v) + "°", null
    );

    public final SliderSetting rotY = new SliderSetting(
            "Drehung (Y-Achse)",
            "Dreht das Schild seitlich weg (wie im Texture Pack für FOV).",
            0.0, -180.0, 180.0, 5.0,
            v -> Math.round(v) + "°", null
    );

    public final SliderSetting rotZ = new SliderSetting(
            "Kippung (Z-Achse)",
            "Neigt das Schild seitlich.",
            0.0, -180.0, 180.0, 5.0,
            v -> Math.round(v) + "°", null
    );

    public final BooleanSetting statusOutline = new BooleanSetting(
            "Status-Umrandung am Schild",
            "Schild erhält eine farbige Umrandung: Grün = nutzbar, Rot = auf Cooldown (im First-Person-Sichtfeld & bei anderen Spielern).",
            true
    );

    public final BooleanSetting tintShield = new BooleanSetting(
            "Schild komplett einfärben",
            "Färbt das Schild-Material komplett in der Statusfarbe ein.",
            false
    );

    public final SliderSetting colorPreset = new SliderSetting(
            "Farb-Schema",
            "Wähle deine Statusfarben: 0 = Grün/Rot, 1 = Cyan/Orange, 2 = Gold/Lila, 3 = Weiß/Rot.",
            0.0, 0.0, 3.0, 1.0,
            v -> {
                int i = (int) Math.round(v);
                return switch (i) {
                    case 1 -> "Cyan / Orange";
                    case 2 -> "Gold / Lila";
                    case 3 -> "Weiß / Rot";
                    default -> "Klassisch (Grün / Rot)";
                };
            }, null
    );

    public ShieldSettings() {
        super("shieldsettings", "Schild-Einstellungen", "Schildgröße, Position und Drehung für freies Sichtfeld (FOV) und Status-Umrandung (grün = nutzbar, rot = Cooldown).", Category.VISUAL);
        INSTANCE = this;
        add(lowerShield);
        add(scale);
        add(offsetX);
        add(offsetY);
        add(offsetZ);
        add(rotX);
        add(rotY);
        add(rotZ);
        add(statusOutline);
        add(tintShield);
        add(colorPreset);
    }

    public static boolean isCustomTransformEnabled() {
        return INSTANCE != null && INSTANCE.enabled && INSTANCE.lowerShield.value;
    }

    public static void applyFirstPersonTransform(PoseStack poseStack, InteractionHand hand) {
        if (INSTANCE == null) return;
        float xOff = (float) INSTANCE.offsetX.value;
        float yOff = (float) INSTANCE.offsetY.value;
        float zOff = (float) INSTANCE.offsetZ.value;
        float rx = (float) INSTANCE.rotX.value;
        float ry = (float) INSTANCE.rotY.value;
        float rz = (float) INSTANCE.rotZ.value;

        if (hand == InteractionHand.OFF_HAND) {
            xOff = -xOff;
            ry = -ry;
            rz = -rz;
        }

        poseStack.translate(xOff, yOff, zOff);
        if (rx != 0.0f) poseStack.rotateDegrees(com.mojang.math.Axis.XP, rx);
        if (ry != 0.0f) poseStack.rotateDegrees(com.mojang.math.Axis.YP, ry);
        if (rz != 0.0f) poseStack.rotateDegrees(com.mojang.math.Axis.ZP, rz);
        float sc = (float) INSTANCE.scale.value;
        poseStack.scale(sc, sc, sc);
    }

    public static boolean isStatusOutlineEnabled() {
        return INSTANCE != null && INSTANCE.enabled && INSTANCE.statusOutline.value;
    }

    public static boolean isStatusTintEnabled() {
        return INSTANCE != null && INSTANCE.enabled && INSTANCE.tintShield.value;
    }

    public static int getReadyColor() {
        if (INSTANCE == null) return 0xFF33FF44;
        int p = (int) Math.round(INSTANCE.colorPreset.value);
        return switch (p) {
            case 1 -> 0xFF22E5FF;
            case 2 -> 0xFFFFD700;
            case 3 -> 0xFFFFFFFF;
            default -> 0xFF33FF44;
        };
    }

    public static int getCooldownColor() {
        if (INSTANCE == null) return 0xFFFF2222;
        int p = (int) Math.round(INSTANCE.colorPreset.value);
        return switch (p) {
            case 1 -> 0xFFFF7700;
            case 2 -> 0xFFD022FF;
            default -> 0xFFFF2222;
        };
    }

    public static int getSelfStatusColor() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return 0;
        ItemStack shield = mc.player.getOffhandItem();
        if (!shield.is(Items.SHIELD)) {
            shield = mc.player.getMainHandItem();
        }
        if (!shield.is(Items.SHIELD)) {
            shield = new ItemStack(Items.SHIELD);
        }
        boolean cd = mc.player.getCooldowns().isOnCooldown(shield);
        return cd ? getCooldownColor() : getReadyColor();
    }

    public static int getStatusColor(Player player) {
        if (player == null) return 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null && player.getUUID().equals(mc.player.getUUID())) {
            return getSelfStatusColor();
        }
        boolean cd = ShieldCooldownTracker.isOnCooldown(player.getUUID());
        return cd ? getCooldownColor() : getReadyColor();
    }

    public static int getStatusColor(AvatarRenderState state) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return 0;
        if (mc.player != null && state.id == mc.player.getId()) {
            return getSelfStatusColor();
        }
        UUID id = RenderStateIds.get(state);
        if (id != null && mc.player != null && id.equals(mc.player.getUUID())) {
            return getSelfStatusColor();
        }
        boolean cd = id != null && ShieldCooldownTracker.isOnCooldown(id);
        return cd ? getCooldownColor() : getReadyColor();
    }
}
