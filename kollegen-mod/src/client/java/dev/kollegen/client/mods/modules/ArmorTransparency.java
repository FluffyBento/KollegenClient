package dev.kollegen.client.mods.modules;

import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.Module;
import dev.kollegen.client.mods.SliderSetting;
import dev.kollegen.client.presence.RenderStateIds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

import java.util.UUID;

public final class ArmorTransparency {
    public static Own OWN;
    public static Others OTHERS;

    private ArmorTransparency() {
    }

    public static class Own extends Module {
        public final SliderSetting helmet = new SliderSetting("Helm", "Deckkraft des eigenen Helms (0% = unsichtbar).", 0.5, 0.0, 1.0, 0.05, v -> v <= 0.05 ? "0% (Unsichtbar)" : (v >= 0.98 ? "100% (Voll)" : Math.round(v * 100.0) + "%"), null);
        public final SliderSetting chest = new SliderSetting("Brustplatte", "Deckkraft der eigenen Brustplatte (0% = unsichtbar).", 0.5, 0.0, 1.0, 0.05, v -> v <= 0.05 ? "0% (Unsichtbar)" : (v >= 0.98 ? "100% (Voll)" : Math.round(v * 100.0) + "%"), null);
        public final SliderSetting legs = new SliderSetting("Hose", "Deckkraft der eigenen Hose (0% = unsichtbar).", 0.5, 0.0, 1.0, 0.05, v -> v <= 0.05 ? "0% (Unsichtbar)" : (v >= 0.98 ? "100% (Voll)" : Math.round(v * 100.0) + "%"), null);
        public final SliderSetting boots = new SliderSetting("Schuhe", "Deckkraft der eigenen Schuhe (0% = unsichtbar).", 0.5, 0.0, 1.0, 0.05, v -> v <= 0.05 ? "0% (Unsichtbar)" : (v >= 0.98 ? "100% (Voll)" : Math.round(v * 100.0) + "%"), null);

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
        public final SliderSetting helmet = new SliderSetting("Helm", "Deckkraft von Helmen anderer Spieler (0% = unsichtbar).", 0.5, 0.0, 1.0, 0.05, v -> v <= 0.05 ? "0% (Unsichtbar)" : (v >= 0.98 ? "100% (Voll)" : Math.round(v * 100.0) + "%"), null);
        public final SliderSetting chest = new SliderSetting("Brustplatte", "Deckkraft von Brustplatten anderer Spieler (0% = unsichtbar).", 0.5, 0.0, 1.0, 0.05, v -> v <= 0.05 ? "0% (Unsichtbar)" : (v >= 0.98 ? "100% (Voll)" : Math.round(v * 100.0) + "%"), null);
        public final SliderSetting legs = new SliderSetting("Hose", "Deckkraft von Hosen anderer Spieler (0% = unsichtbar).", 0.5, 0.0, 1.0, 0.05, v -> v <= 0.05 ? "0% (Unsichtbar)" : (v >= 0.98 ? "100% (Voll)" : Math.round(v * 100.0) + "%"), null);
        public final SliderSetting boots = new SliderSetting("Schuhe", "Deckkraft von Schuhen anderer Spieler (0% = unsichtbar).", 0.5, 0.0, 1.0, 0.05, v -> v <= 0.05 ? "0% (Unsichtbar)" : (v >= 0.98 ? "100% (Voll)" : Math.round(v * 100.0) + "%"), null);

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

    public static float getOpacity(Object state, ItemStack stack) {
        if (!(state instanceof AvatarRenderState ars)) {
            return 1.0f;
        }
        if (stack == null || stack.isEmpty()) {
            return 1.0f;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return 1.0f;

        boolean isSelf = false;
        if (mc.player != null) {
            if (ars.id != 0 && ars.id == mc.player.getId()) {
                isSelf = true;
            } else {
                UUID id = RenderStateIds.get(ars);
                if (id != null && id.equals(mc.player.getUUID())) {
                    isSelf = true;
                }
            }
        }

        EquipmentSlot slot = getSlot(ars, stack);
        if (slot == null) return 1.0f;

        if (isSelf) {
            if (OWN == null || !OWN.enabled) return 1.0f;
            return (float) OWN.getOpacity(slot);
        } else {
            if (OTHERS == null || !OTHERS.enabled) return 1.0f;
            return (float) OTHERS.getOpacity(slot);
        }
    }

    public static EquipmentSlot getSlot(AvatarRenderState ars, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        if (ars != null) {
            if (stack == ars.headEquipment) return EquipmentSlot.HEAD;
            if (stack == ars.chestEquipment) return EquipmentSlot.CHEST;
            if (stack == ars.legsEquipment) return EquipmentSlot.LEGS;
            if (stack == ars.feetEquipment) return EquipmentSlot.FEET;
        }
        try {
            Equippable eq = stack.get(DataComponents.EQUIPPABLE);
            if (eq != null) {
                return eq.slot();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static boolean shouldHide(Object state, ItemStack stack) {
        return getOpacity(state, stack) <= 0.05f;
    }
}
