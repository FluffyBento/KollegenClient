package dev.kollegen.client.mods.modules;

import dev.kollegen.client.mods.BooleanSetting;
import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.Module;
import dev.kollegen.client.mods.SliderSetting;
import dev.kollegen.client.mods.StringSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

public class UhcOverlay extends Module implements DebugRenderer.SimpleDebugRenderer {
    public static UhcOverlay INSTANCE;

    public final BooleanSetting scaleItems = new BooleanSetting(
            "Wichtige Items vergrößern",
            "Goldene Äpfel, Spinnenweben und Köpfe auf dem Boden vergrößern.",
            true
    );

    public final SliderSetting itemScale = new SliderSetting(
            "Item-Größe",
            "Skalierungsfaktor für gedroppte wichtige Items.",
            2.0, 1.2, 3.5, 0.1,
            v -> String.format(Locale.ROOT, "%.1fx", v), null
    );

    public final BooleanSetting highlightItems = new BooleanSetting(
            "Gedroppte Items hervorheben",
            "Zeigt leuchtende Boxen um wichtige gedroppte Items auf dem Boden in Sichtlinie.",
            true
    );

    public final StringSetting customItems = new StringSetting(
            "Weitere Highlight-Items",
            "Komma-getrennte Item-Namen wie diamond, ender_pearl oder totem_of_undying.",
            "diamond,ender_pearl,totem_of_undying",
            256
    );

    public final BooleanSetting colorOres = new BooleanSetting(
            "Farbige Erze (Solid Ores)",
            "Zeigt Erze im UHC-Stil in klaren, leuchtenden Farben mit Kontur-Umrandung ohne Cheats.",
            true,
            on -> setPackEnabled(on && isOverlayEnabled())
    );

    public final BooleanSetting diamondOnly = new BooleanSetting(
            "Nur wertvolle Erze",
            "Fokus auf Diamant, Netherite, Gold und Smaragd.",
            false
    );

    public UhcOverlay() {
        super("uhcoverlay", "UHC Overlay", "Vergrößert goldene Äpfel, Spinnweben und Köpfe am Boden, zeigt Erze in leuchtenden Farben und hebt gedroppte Items hervor.", Category.GAMEPLAY);
        INSTANCE = this;
        this.enabled = true;
        add(scaleItems);
        add(itemScale);
        add(highlightItems);
        add(customItems);
        add(colorOres);
        add(diamondOnly);
    }

    @Override
    public void onEnable() {
        if (this.colorOres.value) {
            setPackEnabled(true);
        }
    }

    @Override
    public void onDisable() {
        setPackEnabled(false);
    }

    public static boolean isOverlayEnabled() {
        return INSTANCE != null && INSTANCE.enabled;
    }

    public static double getItemScale() {
        return INSTANCE != null ? INSTANCE.itemScale.value : 2.0;
    }

    public static boolean shouldScale(ItemStack stack) {
        if (!isOverlayEnabled() || !INSTANCE.scaleItems.value || stack == null || stack.isEmpty()) {
            return false;
        }
        return isTargetItem(stack);
    }

    public static boolean shouldHighlight(ItemStack stack) {
        if (!isOverlayEnabled() || !INSTANCE.highlightItems.value || stack == null || stack.isEmpty()) {
            return false;
        }
        return isTargetItem(stack);
    }

    public static boolean isTargetItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE)) return true;
        if (stack.is(Items.COBWEB)) return true;
        if (stack.is(Items.PLAYER_HEAD) || stack.is(Items.ZOMBIE_HEAD) || stack.is(Items.CREEPER_HEAD)
                || stack.is(Items.SKELETON_SKULL) || stack.is(Items.WITHER_SKELETON_SKULL)
                || stack.is(Items.PIGLIN_HEAD) || stack.is(Items.DRAGON_HEAD)) return true;

        if (INSTANCE != null && INSTANCE.customItems != null && INSTANCE.customItems.value != null) {
            String custom = INSTANCE.customItems.value.trim().toLowerCase(Locale.ROOT);
            if (!custom.isEmpty()) {
                String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toLowerCase(Locale.ROOT);
                for (String part : custom.split(",")) {
                    String clean = part.trim().toLowerCase(Locale.ROOT);
                    if (!clean.isEmpty() && (path.equals(clean) || path.contains(clean))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static int getItemColor(ItemStack stack) {
        if (stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE)) return 0xFFFFD700;
        if (stack.is(Items.COBWEB)) return 0xFFFFFFFF;
        if (stack.is(Items.PLAYER_HEAD) || stack.is(Items.ZOMBIE_HEAD) || stack.is(Items.CREEPER_HEAD)
                || stack.is(Items.SKELETON_SKULL) || stack.is(Items.WITHER_SKELETON_SKULL)) return 0xFFFF4444;
        return 0xFF00FFCC;
    }

    public static void ensurePackExists() {
        try {
            Minecraft mc = Minecraft.getInstance();
            Path rpDir = mc.getResourcePackDirectory();
            if (rpDir == null) return;
            Files.createDirectories(rpDir);
            Path target = rpDir.resolve("Kollegen-UHC-Overlay.zip");
            try (InputStream in = UhcOverlay.class.getResourceAsStream("/Kollegen-UHC-Overlay.zip")) {
                if (in != null) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public static void setPackEnabled(boolean enable) {
        try {
            ensurePackExists();
            Minecraft mc = Minecraft.getInstance();
            if (mc.options == null) return;
            net.minecraft.server.packs.repository.PackRepository repo = mc.getResourcePackRepository();
            if (repo == null) return;
            repo.reload();
            String packId = "file/Kollegen-UHC-Overlay.zip";
            java.util.List<String> selected = new java.util.ArrayList<>(repo.getSelectedIds());
            boolean changed = false;
            if (enable) {
                if (!selected.contains(packId)) {
                    selected.add(packId);
                    changed = true;
                }
            } else {
                if (selected.remove(packId)) {
                    changed = true;
                }
            }
            if (changed) {
                repo.setSelected(selected);
                mc.options.resourcePacks.clear();
                mc.options.resourcePacks.addAll(selected);
                mc.options.save();
                mc.reloadResourcePacks();
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean isEntityVisible(LocalPlayer player, Entity entity, ClientLevel level) {
        if (player == null || entity == null || level == null) return false;
        Vec3 from = player.getEyePosition();
        Vec3 to = entity.position().add(0, 0.25, 0);
        ClipContext ctx = new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player);
        BlockHitResult hit = level.clip(ctx);
        return hit.getType() == HitResult.Type.MISS;
    }

    @Override
    public void emitGizmos(double cameraX, double cameraY, double cameraZ, DebugValueAccess access, Frustum frustum, float tickDelta) {
        if (!isOverlayEnabled() || !this.highlightItems.value) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) return;

        AABB searchBox = player.getBoundingBox().inflate(24.0);
        for (Entity e : level.getEntities(player, searchBox)) {
            if (e instanceof ItemEntity itemEntity) {
                ItemStack stack = itemEntity.getItem();
                if (shouldHighlight(stack)) {
                    if (!isEntityVisible(player, itemEntity, level)) continue;
                    int stroke = getItemColor(stack);
                    int fill = (0x35 << 24) | (stroke & 0x00FFFFFF);
                    AABB box = itemEntity.getBoundingBox();
                    GizmoStyle style = GizmoStyle.strokeAndFill(stroke, 2.0f, fill);
                    Gizmos.cuboid(box, style);
                }
            }
        }
    }
}
