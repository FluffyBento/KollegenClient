package dev.kollegen.client.mods.modules;

import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.Module;
import dev.kollegen.client.mods.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.EquipmentSlot;

public final class ArmorTransparency {
    public static Own OWN;
    public static Others OTHERS;

    private ArmorTransparency() {
    }

    public static class Own extends Module {
        public final SliderSetting helmet = new SliderSetting("Helm", "Deckkraft des eigenen Helms (0% = unsichtbar).", 0.0, 0.0, 1.0, 0.05, v -> v <= 0.05 ? "0% (Unsichtbar)" : Math.round(v * 100.0) + "%", null);
        public final SliderSetting chest = new SliderSetting("Brustplatte", "Deckkraft der eigenen Brustplatte (0% = unsichtbar).", 0.0, 0.0, 1.0, 0.05, v -> v <= 0.05 ? "0% (Unsichtbar)" : Math.round(v * 100.0) + "%", null);
        public final SliderSetting legs = new SliderSetting("Hose", "Deckkraft der eigenen Hose (0% = unsichtbar).", 0.0, 0.0, 1.0, 0.05, v -> v <= 0.05 ? "0% (Unsichtbar)" : Math.round(v * 100.0) + "%", null);
        public final SliderSetting boots = new SliderSetting("Schuhe", "Deckkraft der eigenen Schuhe (0% = unsichtbar).", 0.0, 0.0, 1.0, 0.05, v -> v <= 0.05 ? "0% (Unsichtbar)" : Math.round(v * 100.0) + "%", null);

        public Own() {
            super("armortransparency", "Transparente Rüstung (Eigen)", "Deckkraft der eigenen Rüstungsteile einzeln einstellen (0% = unsichtbar).", Category.VISUAL);
            OWN = this;
            add(helmet);
            add(chest);
            add(legs);
            add(boots);
        }

        public double getOpacity(EquipmentSlot slot) {
            return switch (slot) {
                case HEAD -> helmet.value;
                case CHEST -> chest.value;
                case LEGS -> legs.value;
                case FEET -> boots.value;
                default -> 1.0;
            };
        }
    }

    public static class Others extends Module {
        public final SliderSetting helmet = new SliderSetting("Helm", "Deckkraft von Helmen anderer Spieler (0% = unsichtbar).", 0.0, 0.0, 1.0, 0.05, v -> v <= 0.05 ? "0% (Unsichtbar)" : Math.round(v * 100.0) + "%", null);
        public final SliderSetting chest = new SliderSetting("Brustplatte", "Deckkraft von Brustplatten anderer Spieler (0% = unsichtbar).", 0.0, 0.0, 1.0, 0.05, v -> v <= 0.05 ? "0% (Unsichtbar)" : Math.round(v * 100.0) + "%", null);
        public final SliderSetting legs = new SliderSetting("Hose", "Deckkraft von Hosen anderer Spieler (0% = unsichtbar).", 0.0, 0.0, 1.0, 0.05, v -> v <= 0.05 ? "0% (Unsichtbar)" : Math.round(v * 100.0) + "%", null);
        public final SliderSetting boots = new SliderSetting("Schuhe", "Deckkraft von Schuhen anderer Spieler (0% = unsichtbar).", 0.0, 0.0, 1.0, 0.05, v -> v <= 0.05 ? "0% (Unsichtbar)" : Math.round(v * 100.0) + "%", null);

        public Others() {
            super("armortransparency_others", "Transparente Rüstung (Andere)", "Deckkraft der Rüstungsteile anderer Spieler einzeln einstellen (0% = unsichtbar).", Category.VISUAL);
            OTHERS = this;
            add(helmet);
            add(chest);
            add(legs);
            add(boots);
        }

        public double getOpacity(EquipmentSlot slot) {
            return switch (slot) {
                case HEAD -> helmet.value;
                case CHEST -> chest.value;
                case LEGS -> legs.value;
                case FEET -> boots.value;
                default -> 1.0;
            };
        }
    }

    public static boolean shouldHide(HumanoidRenderState state, EquipmentSlot slot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return false;

        if (!(state instanceof AvatarRenderState ars)) {
            return false;
        }

        boolean isSelf = false;
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
            if (OWN == null || !OWN.enabled) return false;
            return OWN.getOpacity(slot) <= 0.05;
        } else {
            if (OTHERS == null || !OTHERS.enabled) return false;
            return OTHERS.getOpacity(slot) <= 0.05;
        }
    }
}
