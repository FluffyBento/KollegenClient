package dev.kollegen.client.mods.modules;

import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.Module;
import dev.kollegen.client.mods.Setting;
import dev.kollegen.client.mods.SliderSetting;
import dev.kollegen.client.presence.RenderStateIds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

import java.util.List;
import java.util.UUID;

public class ArmorTransparency extends Module {
    public static ArmorTransparency INSTANCE;

    public final SliderSetting ownHelmet = new SliderSetting("Helm", "Deckkraft des eigenen Helms (0% = unsichtbar).", 0.0, 0.0, 1.0, 0.05, v -> v <= 0.05 ? "0% (Unsichtbar)" : (v >= 0.98 ? "100% (Voll)" : Math.round(v * 100.0) + "%"), null);
    public final SliderSetting ownChest = new SliderSetting("Brustplatte", "Deckkraft der eigenen Brustplatte (0% = unsichtbar).", 0.0, 0.0, 1.0, 0.05, v -> v <= 0.05 ? "0% (Unsichtbar)" : (v >= 0.98 ? "100% (Voll)" : Math.round(v * 100.0) + "%"), null);
    public final SliderSetting ownLegs = new SliderSetting("Hose", "Deckkraft der eigenen Hose (0% = unsichtbar).", 0.0, 0.0, 1.0, 0.05, v -> v <= 0.05 ? "0% (Unsichtbar)" : (v >= 0.98 ? "100% (Voll)" : Math.round(v * 100.0) + "%"), null);
    public final SliderSetting ownBoots = new SliderSetting("Schuhe", "Deckkraft der eigenen Schuhe (0% = unsichtbar).", 0.0, 0.0, 1.0, 0.05, v -> v <= 0.05 ? "0% (Unsichtbar)" : (v >= 0.98 ? "100% (Voll)" : Math.round(v * 100.0) + "%"), null);

    public final SliderSetting othersHelmet = new SliderSetting("Helm (Andere)", "Deckkraft von Helmen anderer Spieler (0% = unsichtbar).", 0.0, 0.0, 1.0, 0.05, v -> v <= 0.05 ? "0% (Unsichtbar)" : (v >= 0.98 ? "100% (Voll)" : Math.round(v * 100.0) + "%"), null);
    public final SliderSetting othersChest = new SliderSetting("Brustplatte (Andere)", "Deckkraft von Brustplatten anderer Spieler (0% = unsichtbar).", 0.0, 0.0, 1.0, 0.05, v -> v <= 0.05 ? "0% (Unsichtbar)" : (v >= 0.98 ? "100% (Voll)" : Math.round(v * 100.0) + "%"), null);
    public final SliderSetting othersLegs = new SliderSetting("Hose (Andere)", "Deckkraft von Hosen anderer Spieler (0% = unsichtbar).", 0.0, 0.0, 1.0, 0.05, v -> v <= 0.05 ? "0% (Unsichtbar)" : (v >= 0.98 ? "100% (Voll)" : Math.round(v * 100.0) + "%"), null);
    public final SliderSetting othersBoots = new SliderSetting("Schuhe (Andere)", "Deckkraft von Schuhen anderer Spieler (0% = unsichtbar).", 0.0, 0.0, 1.0, 0.05, v -> v <= 0.05 ? "0% (Unsichtbar)" : (v >= 0.98 ? "100% (Voll)" : Math.round(v * 100.0) + "%"), null);

    public ArmorTransparency() {
        super("armortransparency", "Transparente Rüstung", "Deckkraft der Rüstungsteile für sich selbst und andere Spieler einzeln anpassen.", Category.VISUAL);
        INSTANCE = this;
        add(ownHelmet);
        add(ownChest);
        add(ownLegs);
        add(ownBoots);
        add(othersHelmet);
        add(othersChest);
        add(othersLegs);
        add(othersBoots);
    }

    @Override
    public String[] tabs() {
        return new String[]{"Eigene Rüstung", "Andere Spieler"};
    }

    @Override
    public List<Setting> settingsForTab(String tab) {
        if ("Andere Spieler".equalsIgnoreCase(tab)) {
            return List.of(othersHelmet, othersChest, othersLegs, othersBoots);
        }
        return List.of(ownHelmet, ownChest, ownLegs, ownBoots);
    }

    public double getOpacity(boolean isSelf, EquipmentSlot slot) {
        if (isSelf) {
            return switch (slot) {
                case HEAD -> ownHelmet.value;
                case CHEST -> ownChest.value;
                case LEGS -> ownLegs.value;
                case FEET -> ownBoots.value;
                default -> 1.0;
            };
        } else {
            return switch (slot) {
                case HEAD -> othersHelmet.value;
                case CHEST -> othersChest.value;
                case LEGS -> othersLegs.value;
                case FEET -> othersBoots.value;
                default -> 1.0;
            };
        }
    }

    public static boolean isSelf(AvatarRenderState ars) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return false;
        if (ars.id != 0 && ars.id == mc.player.getId()) {
            return true;
        }
        UUID id = RenderStateIds.get(ars);
        if (id != null && id.equals(mc.player.getUUID())) {
            return true;
        }
        if (mc.gui != null && mc.gui.screen() instanceof InventoryScreen && ars.id == 0) {
            return true;
        }
        return false;
    }

    public static boolean shouldHideSlot(boolean isSelf, EquipmentSlot slot) {
        if (INSTANCE == null || !INSTANCE.enabled) return false;
        return INSTANCE.getOpacity(isSelf, slot) <= 0.05;
    }

    public static float getOpacityForSlot(boolean isSelf, EquipmentSlot slot) {
        if (INSTANCE == null || !INSTANCE.enabled) return 1.0f;
        return (float) INSTANCE.getOpacity(isSelf, slot);
    }

    public static float getOpacity(Object state, ItemStack stack) {
        if (!(state instanceof AvatarRenderState ars)) {
            return 1.0f;
        }
        boolean isSelf = isSelf(ars);
        EquipmentSlot slot = getSlot(ars, stack);
        if (slot == null) return 1.0f;
        return getOpacityForSlot(isSelf, slot);
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
