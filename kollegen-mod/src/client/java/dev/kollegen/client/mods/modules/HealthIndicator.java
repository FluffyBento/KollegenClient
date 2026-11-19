package dev.kollegen.client.mods.modules;

import dev.kollegen.client.mods.BooleanSetting;
import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.ModeSetting;
import dev.kollegen.client.mods.Module;
import dev.kollegen.client.server.ServerRestrictions;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;

import java.util.Locale;

public class HealthIndicator extends Module {
    public static volatile HealthIndicator INSTANCE;

    public final BooleanSetting showSelf = new BooleanSetting(
            "Eigenes Leben",
            "Zeigt die Lebensanzeige auch über dem eigenen Kopf in Drittperson.",
            false
    );

    public final BooleanSetting onlyDamaged = new BooleanSetting(
            "Nur verletzte Entities",
            "Versteckt die Anzeige bei vollen Lebenspunkten.",
            false
    );

    public final BooleanSetting onlyTargeted = new BooleanSetting(
            "Nur anvisierte Entities",
            "Zeigt Leben nur für das Entity im Fadenkreuz an.",
            false
    );

    public final ModeSetting displayStyle = new ModeSetting(
            "Anzeige-Stil",
            "Art der Lebensanzeige.",
            new String[]{"Herzen & Zahl", "Nur Herzen", "Nur Zahl", "Balken & Zahl"},
            0
    );

    public final BooleanSetting serverProtection = new BooleanSetting(
            "Server-Schutz",
            "Deaktiviert die Anzeige automatisch auf gesperrten Servern (Anti-Cheat / Bann-Schutz).",
            true
    );

    public HealthIndicator() {
        super("healthindicator", "Health Indicator", "Zeigt verbleibende Lebenspunkte und Herzen über Kreaturen und Spielern an.", Category.GAMEPLAY);
        INSTANCE = this;
        add(showSelf);
        add(onlyDamaged);
        add(onlyTargeted);
        add(displayStyle);
        add(serverProtection);
    }

    public static boolean isAllowed() {
        if (INSTANCE == null || !INSTANCE.enabled) return false;
        if (INSTANCE.serverProtection.value && ServerRestrictions.isRestrictedCurrent()) {
            return false;
        }
        return true;
    }

    public static boolean shouldShow(LivingEntity entity) {
        if (!isAllowed()) return false;
        if (entity == null || entity.isDeadOrDying() || entity.isInvisible()) return false;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return false;

        boolean isSelf = (entity == mc.player);
        if (isSelf) {
            return INSTANCE.showSelf.value && !mc.options.getCameraType().isFirstPerson();
        }

        if (INSTANCE.onlyTargeted.value) {
            boolean targeted = false;
            if (mc.hitResult instanceof EntityHitResult ehr && ehr.getEntity() == entity) {
                targeted = true;
            }
            if (!targeted) return false;
        }

        if (INSTANCE.onlyDamaged.value) {
            if (entity.getHealth() >= entity.getMaxHealth() - 0.05f) {
                return false;
            }
        }

        return true;
    }

    public static Component formatHealth(LivingEntity entity) {
        if (entity == null) return Component.empty();
        float hp = Math.max(0.0f, entity.getHealth());
        float maxHp = Math.max(1.0f, entity.getMaxHealth());
        float abs = entity.getAbsorptionAmount();

        float ratio = hp / maxHp;
        String color = ratio > 0.65f ? "§a" : (ratio > 0.30f ? "§e" : "§c");

        int style = INSTANCE != null ? INSTANCE.displayStyle.index : 0;
        switch (style) {
            case 1 -> {
                int totalHearts = Math.max(1, Math.min(10, (int) Math.ceil(ratio * 10.0f)));
                StringBuilder sb = new StringBuilder();
                sb.append("§c");
                for (int i = 0; i < totalHearts; i++) {
                    sb.append("❤");
                }
                sb.append("§8");
                for (int i = totalHearts; i < 10; i++) {
                    sb.append("❤");
                }
                if (abs > 0.05f) {
                    sb.append(" §6❤");
                }
                return Component.literal(sb.toString());
            }
            case 2 -> {
                String str = color + String.format(Locale.ROOT, "%.1f", hp) + "§7/§f" + String.format(Locale.ROOT, "%.1f", maxHp) + " HP";
                if (abs > 0.05f) {
                    str += " §6(+" + String.format(Locale.ROOT, "%.1f", abs) + ")";
                }
                return Component.literal(str);
            }
            case 3 -> {
                int bars = Math.max(0, Math.min(10, Math.round(ratio * 10.0f)));
                StringBuilder sb = new StringBuilder();
                sb.append(color);
                for (int i = 0; i < bars; i++) {
                    sb.append("■");
                }
                sb.append("§8");
                for (int i = bars; i < 10; i++) {
                    sb.append("■");
                }
                sb.append(" ").append(color).append(String.format(Locale.ROOT, "%.1f", hp));
                return Component.literal(sb.toString());
            }
            default -> {
                String str = color + String.format(Locale.ROOT, "%.1f", hp) + "§c❤";
                if (abs > 0.05f) {
                    str += " §6+" + String.format(Locale.ROOT, "%.1f", abs) + "❤";
                }
                return Component.literal(str);
            }
        }
    }

    public static Component attachHealth(LivingEntity entity, Component existing) {
        Component hpComp = formatHealth(entity);
        if (existing == null || existing.getString().trim().isEmpty()) {
            return hpComp;
        }
        MutableComponent combined = Component.empty();
        combined.append(existing);
        combined.append(Component.literal(" "));
        combined.append(hpComp);
        return combined;
    }
}
