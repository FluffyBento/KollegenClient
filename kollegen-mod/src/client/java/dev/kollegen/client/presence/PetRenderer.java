package dev.kollegen.client.presence;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PetRenderer {
    private static final Map<UUID, EnderDragon> PETS = new ConcurrentHashMap<>();
    private static final Map<UUID, Vec3> LASTPOS = new ConcurrentHashMap<>();
    private static final Map<UUID, Level> LEVELS = new ConcurrentHashMap<>();

    private PetRenderer() {
    }

    public static void clear() {
        PETS.clear();
        LASTPOS.clear();
        LEVELS.clear();
    }

    public static void notePosition(UUID id, Vec3 pos) {
        if (id == null || pos == null) return;
        LASTPOS.put(id, pos);
    }

    private static void mark(String stage) {
        try {
            String base = String.valueOf(KollegenPresence.dbgRender);
            if (base == null || base.isEmpty() || "init".equals(base)) base = "render";
            if (base.length() > 30) base = base.substring(0, 30);
            KollegenPresence.dbgRender = base + "|" + stage;
        } catch (Throwable ignored) {
        }
    }

    public static void renderFor(UUID ownerId, AvatarRenderState ownerState, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        try {
            if (ownerId == null) return;
            CosmeticData d = KollegenPresence.getCosmetics(ownerId);
            if (d == null || d.petKind == null) {
                mark("pet-nodata");
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.level == null) return;
            Vec3 base = LASTPOS.get(ownerId);
            if (base == null) {
                try {
                    if (mc.player != null && mc.player.getUUID().equals(ownerId)) {
                        base = mc.player.position();
                    }
                } catch (Throwable ignored) {
                }
            }
            if (base == null) {
                mark("pet-nopos");
                return;
            }
            String mode = d.petMode != null ? d.petMode : "follow";
            double bob = 0.0;
            double ox = 1.2;
            double oy = 0.5;
            double oz = 0.0;
            if ("shoulder".equals(mode)) {
                ox = 0.45;
                oy = 1.55;
            } else if ("head".equals(mode)) {
                ox = 0.0;
                oy = 2.35;
            } else if ("hover".equals(mode)) {
                ox = 0.0;
                oy = 3.1;
                bob = Math.sin(System.currentTimeMillis() / 450.0) * 0.25;
            }
            double x = base.x + ox;
            double y = base.y + oy + bob;
            double z = base.z + oz;
            Level lvl = LEVELS.get(ownerId);
            EnderDragon dragon = PETS.get(ownerId);
            if (dragon == null || lvl != mc.level) {
                dragon = new EnderDragon(EntityTypes.ENDER_DRAGON, mc.level);
                PETS.put(ownerId, dragon);
                LEVELS.put(ownerId, mc.level);
            }
            dragon.setPos(x, y, z);
            dragon.tickCount++;
            double scale = d.petScale > 0.0 ? d.petScale : 0.2;
            poseStack.pushPose();
            try {
                poseStack.translate(x, y, z);
                poseStack.scale((float) scale, (float) scale, (float) scale);
                poseStack.translate(-x, -y, -z);
                Object st = mc.getEntityRenderDispatcher().extractEntity(dragon, 0.0F);
                if (st instanceof net.minecraft.client.renderer.entity.state.EntityRenderState renderState) {
                    mc.getEntityRenderDispatcher().submit(renderState, camera, 0.0, 0.0, 0.0, poseStack, collector);
                }
            } finally {
                poseStack.popPose();
            }
            if (d.petName != null && !d.petName.isEmpty()) {
                int nameColor = CosmeticText.parseColor(d.petNameColor, 0xFFFFFF);
                Component nameComp = Component.literal(d.petName)
                        .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(nameColor)));
                collector.submitNameTag(poseStack, new Vec3(x, y + 2.2, z), 0, nameComp, true, 15728880, camera);
            }
            mark("pet-ok");
        } catch (Throwable t) {
            try {
                mark("pet-err-" + t.getClass().getSimpleName());
            } catch (Throwable ignored) {
            }
        }
    }
}
