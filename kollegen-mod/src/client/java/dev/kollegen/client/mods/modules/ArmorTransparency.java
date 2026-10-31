package dev.kollegen.client.mods.modules;

import com.google.gson.JsonObject;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ArmorTransparency extends Module {
    public static ArmorTransparency INSTANCE;
    public static int activeSubTab = 0;

    public final SliderSetting ownHelmet = new SliderSetting("Helm", "Deckkraft des eigenen Helms (0% = unsichtbar).", 0.0, 0.0, 1.0, 0.05, v -> v <= 0.05 ? "0% (Unsichtbar)" : (v >= 0.98 ? "100% (Voll)" : Math.round(v * 100.0) + "%"), null);
    public final SliderSetting ownChest = new SliderSetting("Brustplatte", "Deckkraft der eigenen Brustplatte (0% = unsichtbar).", 0.0, 0.0, 1.0, 0.05, v -> v <= 0.05 ? "0% (Unsichtbar)" : (v >= 0.98 ? "100% (Voll)" : Math.round(v * 100.0) + "%"), null);
    public final SliderSetting ownLegs = new SliderSetting("Hose", "Deckkraft der eigenen Hose (0% = unsichtbar).", 0.0, 0.0, 1.0, 0.05, v -> v <= 0.05 ? "0% (Unsichtbar)" : (v >= 0.98 ? "100% (Voll)" : Math.round(v * 100.0) + "%"), null);
    public final SliderSetting ownBoots = new SliderSetting("Schuhe", "Deckkraft der eigenen Schuhe (0% = unsichtbar).", 0.0, 0.0, 1.0, 0.05, v -> v <= 0.05 ? "0% (Unsichtbar)" : (v >= 0.98 ? "100% (Voll)" : Math.round(v * 100.0) + "%"), null);

    public final SliderSetting othersHelmet = new SliderSetting("Helm", "Deckkraft von Helmen anderer Spieler (0% = unsichtbar).", 0.0, 0.0, 1.0, 0.05, v -> v <= 0.05 ? "0% (Unsichtbar)" : (v >= 0.98 ? "100% (Voll)" : Math.round(v * 100.0) + "%"), null);
    public final SliderSetting othersChest = new SliderSetting("Brustplatte", "Deckkraft von Brustplatten anderer Spieler (0% = unsichtbar).", 0.0, 0.0, 1.0, 0.05, v -> v <= 0.05 ? "0% (Unsichtbar)" : (v >= 0.98 ? "100% (Voll)" : Math.round(v * 100.0) + "%"), null);
    public final SliderSetting othersLegs = new SliderSetting("Hose", "Deckkraft von Hosen anderer Spieler (0% = unsichtbar).", 0.0, 0.0, 1.0, 0.05, v -> v <= 0.05 ? "0% (Unsichtbar)" : (v >= 0.98 ? "100% (Voll)" : Math.round(v * 100.0) + "%"), null);
    public final SliderSetting othersBoots = new SliderSetting("Schuhe", "Deckkraft von Schuhen anderer Spieler (0% = unsichtbar).", 0.0, 0.0, 1.0, 0.05, v -> v <= 0.05 ? "0% (Unsichtbar)" : (v >= 0.98 ? "100% (Voll)" : Math.round(v * 100.0) + "%"), null);

    private final List<Setting> ownSettingsList = new ArrayList<>();
    private final List<Setting> othersSettingsList = new ArrayList<>();

    public ArmorTransparency() {
        super("armortransparency", "Transparente Rüstung", "Deckkraft von eigener Rüstung und Rüstung anderer Spieler anpassen.", Category.VISUAL);
        INSTANCE = this;
        ownSettingsList.add(ownHelmet);
        ownSettingsList.add(ownChest);
        ownSettingsList.add(ownLegs);
        ownSettingsList.add(ownBoots);
        othersSettingsList.add(othersHelmet);
        othersSettingsList.add(othersChest);
        othersSettingsList.add(othersLegs);
        othersSettingsList.add(othersBoots);
        settings.addAll(ownSettingsList);
        settings.addAll(othersSettingsList);
    }

    public List<Setting> getActiveSettings() {
        return activeSubTab == 0 ? ownSettingsList : othersSettingsList;
    }

    @Override
    public void save(JsonObject o) {
        super.save(o);
        JsonObject own = new JsonObject();
        ownHelmet.save(own);
        ownChest.save(own);
        ownLegs.save(own);
        ownBoots.save(own);
        o.add("own", own);

        JsonObject others = new JsonObject();
        othersHelmet.save(others);
        othersChest.save(others);
        othersLegs.save(others);
        othersBoots.save(others);
        o.add("others", others);
    }

    @Override
    public void load(JsonObject o) {
        super.load(o);
        if (o.has("own") && o.get("own").isJsonObject()) {
            JsonObject own = o.getAsJsonObject("own");
            if (own.has("Helm")) ownHelmet.load(own.getAsJsonObject("Helm"));
            if (own.has("Brustplatte")) ownChest.load(own.getAsJsonObject("Brustplatte"));
            if (own.has("Hose")) ownLegs.load(own.getAsJsonObject("Hose"));
            if (own.has("Schuhe")) ownBoots.load(own.getAsJsonObject("Schuhe"));
        }
        if (o.has("others") && o.get("others").isJsonObject()) {
            JsonObject others = o.getAsJsonObject("others");
            if (others.has("Helm")) othersHelmet.load(others.getAsJsonObject("Helm"));
            if (others.has("Brustplatte")) othersChest.load(others.getAsJsonObject("Brustplatte"));
            if (others.has("Hose")) othersLegs.load(others.getAsJsonObject("Hose"));
            if (others.has("Schuhe")) othersBoots.load(others.getAsJsonObject("Schuhe"));
        }
    }

    public double getOwnOpacity(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> ownHelmet.value;
            case CHEST -> ownChest.value;
            case LEGS -> ownLegs.value;
            case FEET -> ownBoots.value;
            default -> 1.0;
        };
    }

    public double getOthersOpacity(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> othersHelmet.value;
            case CHEST -> othersChest.value;
            case LEGS -> othersLegs.value;
            case FEET -> othersBoots.value;
            default -> 1.0;
        };
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
        return (isSelf ? INSTANCE.getOwnOpacity(slot) : INSTANCE.getOthersOpacity(slot)) <= 0.05;
    }

    public static float getOpacityForSlot(boolean isSelf, EquipmentSlot slot) {
        if (INSTANCE == null || !INSTANCE.enabled) return 1.0f;
        return (float) (isSelf ? INSTANCE.getOwnOpacity(slot) : INSTANCE.getOthersOpacity(slot));
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
