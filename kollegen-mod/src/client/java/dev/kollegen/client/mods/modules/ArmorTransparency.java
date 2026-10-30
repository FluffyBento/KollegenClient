package dev.kollegen.client.mods.modules;

import dev.kollegen.client.mods.BooleanSetting;
import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.Module;
import dev.kollegen.client.mods.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.EquipmentSlot;

public class ArmorTransparency extends Module {
    public static ArmorTransparency INSTANCE;

    // Eigene Rüstung
    public final BooleanSetting ownEnabled = new BooleanSetting("Eigene Rüstung anpassen", "Aktiviert die Transparenz für die eigene Rüstung.", true);
    public final SliderSetting ownOpacity = new SliderSetting("Eigene Deckkraft", "Deckkraft der eigenen Rüstung (0% = unsichtbar).", 0.0, 0.0, 1.0, 0.05, v -> Math.round(v * 100.0) + "%", null);
    public final BooleanSetting ownHelmet = new BooleanSetting("Eigener Helm", "Transparenz auf den Helm anwenden.", true);
    public final BooleanSetting ownChest = new BooleanSetting("Eigene Brustplatte", "Transparenz auf die Brustplatte anwenden.", true);
    public final BooleanSetting ownLegs = new BooleanSetting("Eigene Hose", "Transparenz auf die Hose anwenden.", true);
    public final BooleanSetting ownBoots = new BooleanSetting("Eigene Schuhe", "Transparenz auf die Schuhe anwenden.", true);

    // Andere Spieler
    public final BooleanSetting othersEnabled = new BooleanSetting("Andere Spieler anpassen", "Aktiviert die Transparenz für Rüstung anderer Spieler.", false);
    public final SliderSetting othersOpacity = new SliderSetting("Deckkraft anderer", "Deckkraft der Rüstung anderer Spieler (0% = unsichtbar).", 0.0, 0.0, 1.0, 0.05, v -> Math.round(v * 100.0) + "%", null);
    public final BooleanSetting othersHelmet = new BooleanSetting("Helm anderer", "Transparenz auf Helme anderer Spieler anwenden.", true);
    public final BooleanSetting othersChest = new BooleanSetting("Brustplatte anderer", "Transparenz auf Brustplatten anderer Spieler anwenden.", true);
    public final BooleanSetting othersLegs = new BooleanSetting("Hose anderer", "Transparenz auf Hosen anderer Spieler anwenden.", true);
    public final BooleanSetting othersBoots = new BooleanSetting("Schuhe anderer", "Transparenz auf Schuhe anderer Spieler anwenden.", true);

    public ArmorTransparency() {
        super("armortransparency", "Transparente Rüstung", "Rüstungsteile von sich und anderen Spielern einzeln transparent machen oder ausblenden.", Category.VISUAL);
        INSTANCE = this;
        add(ownEnabled);
        add(ownOpacity);
        add(ownHelmet);
        add(ownChest);
        add(ownLegs);
        add(ownBoots);

        add(othersEnabled);
        add(othersOpacity);
        add(othersHelmet);
        add(othersChest);
        add(othersLegs);
        add(othersBoots);
    }

    public static boolean shouldHide(HumanoidRenderState state, EquipmentSlot slot) {
        if (INSTANCE == null || !INSTANCE.enabled) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return false;

        boolean isSelf = false;
        if (!(state instanceof net.minecraft.client.renderer.entity.state.AvatarRenderState ars)) {
            return false;
        }
        if (mc.player != null) {
            if (ars.id == mc.player.getId()) {
                isSelf = true;
            } else {
                java.util.UUID id = dev.kollegen.client.presence.RenderStateIds.get(ars);
                if (id != null && id.equals(mc.player.getUUID())) {
                    isSelf = true;
                }
            }
        }

        if (isSelf) {
            if (!INSTANCE.ownEnabled.value) return false;
            if (!isSlotActive(slot, INSTANCE.ownHelmet.value, INSTANCE.ownChest.value, INSTANCE.ownLegs.value, INSTANCE.ownBoots.value)) return false;
            return INSTANCE.ownOpacity.value <= 0.05;
        } else {
            if (!INSTANCE.othersEnabled.value) return false;
            if (!isSlotActive(slot, INSTANCE.othersHelmet.value, INSTANCE.othersChest.value, INSTANCE.othersLegs.value, INSTANCE.othersBoots.value)) return false;
            return INSTANCE.othersOpacity.value <= 0.05;
        }
    }

    private static boolean isSlotActive(EquipmentSlot slot, boolean helm, boolean chest, boolean legs, boolean boots) {
        return switch (slot) {
            case HEAD -> helm;
            case CHEST -> chest;
            case LEGS -> legs;
            case FEET -> boots;
            default -> false;
        };
    }
}
