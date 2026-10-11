package dev.kollegen.client.presence;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PetRenderer {
    private static final Map<UUID, PetInstance> PETS = new ConcurrentHashMap<>();
    private static final Map<UUID, Vec3> LASTPOS = new ConcurrentHashMap<>();
    private static final Map<UUID, Float> LAST_YAW = new ConcurrentHashMap<>();
    private static final Map<UUID, Level> LEVELS = new ConcurrentHashMap<>();

    private PetRenderer() {}

    public static void clear() {
        PETS.clear();
        LASTPOS.clear();
        LAST_YAW.clear();
        LEVELS.clear();
    }

    public static void notePosition(UUID id, Vec3 pos, float yaw) {
        if (id == null || pos == null) return;
        LASTPOS.put(id, pos);
        LAST_YAW.put(id, yaw);
    }

    public static void onTick(Minecraft mc) {
        if (mc.level == null) return;

        for (Map.Entry<UUID, PetInstance> entry : PETS.entrySet()) {
            UUID ownerId = entry.getKey();
            PetInstance pet = entry.getValue();

            if (!mc.level.equals(pet.level)) {
                PETS.remove(ownerId);
                LASTPOS.remove(ownerId);
                LAST_YAW.remove(ownerId);
                LEVELS.remove(ownerId);
                continue;
            }

            CosmeticData d = KollegenPresence.getCosmetics(ownerId);
            if (d == null || d.petKind == null) {
                PETS.remove(ownerId);
                LASTPOS.remove(ownerId);
                LAST_YAW.remove(ownerId);
                LEVELS.remove(ownerId);
                continue;
            }

            Vec3 base = LASTPOS.get(ownerId);
            if (base == null) {
                if (mc.player != null && mc.player.getUUID().equals(ownerId)) {
                    base = mc.player.position();
                }
            }
            if (base == null) continue;

            float yaw = LAST_YAW.getOrDefault(ownerId, 0f);
            pet.updatePosition(base, yaw, d);
        }
    }

    public static void extractPets(Minecraft mc, DeltaTracker tracker, LevelRenderState state) {
        if (mc.level == null || state == null) return;

        float partial = 0.0F;
        try {
            partial = tracker.getGameTimeDeltaPartialTick(true);
        } catch (Throwable ignored) {
        }

        EntityRenderDispatcher dispatcher;
        try {
            dispatcher = mc.getEntityRenderDispatcher();
        } catch (Throwable ignored) {
            return;
        }
        if (dispatcher == null) return;

        for (Map.Entry<UUID, PetInstance> entry : PETS.entrySet()) {
            UUID ownerId = entry.getKey();
            PetInstance pet = entry.getValue();
            if (pet == null || pet.entity == null) continue;

            CosmeticData d = KollegenPresence.getCosmetics(ownerId);
            if (d == null || d.petKind == null) continue;

            try {
                EntityRenderState renderState = dispatcher.extractEntity(pet.entity, partial);
                if (renderState != null) {
                    double scale = d.petScale > 0.0 ? d.petScale : 0.2;
                    if (renderState instanceof LivingEntityRenderState living) {
                        living.scale = (float) scale;
                    }
                    state.entityRenderStates.add(renderState);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    public static void renderFor(UUID ownerId, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (ownerId == null) return;
        CosmeticData d = KollegenPresence.getCosmetics(ownerId);
        if (d == null || d.petKind == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return;
        Vec3 base = LASTPOS.get(ownerId);
        if (base == null) {
            if (mc.player != null && mc.player.getUUID().equals(ownerId)) {
                base = mc.player.position();
            }
        }
        if (base == null) return;
        float yaw = LAST_YAW.getOrDefault(ownerId, 0f);
        PetInstance pet = PETS.get(ownerId);
        if (pet == null || pet.level != mc.level) {
            try {
                pet = new PetInstance(getPetType(d.petKind), mc.level);
                PETS.put(ownerId, pet);
                LEVELS.put(ownerId, mc.level);
            } catch (Throwable ignored) {
                return;
            }
        }
        pet.updatePosition(base, yaw, d);
        if (pet.entity == null) return;
        Vec3 pos = pet.entity.position();
        double scale = d.petScale > 0.0 ? d.petScale : 0.2;
        poseStack.pushPose();
        try {
            poseStack.translate(pos.x, pos.y, pos.z);
            poseStack.scale((float) scale, (float) scale, (float) scale);
            EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
            EntityRenderState renderState = dispatcher.extractEntity(pet.entity, 0.0F);
            if (renderState != null) {
                dispatcher.submit(renderState, camera, 0.0, 0.0, 0.0, poseStack, collector);
            }
        } catch (Throwable ignored) {
        } finally {
            poseStack.popPose();
        }
    }

    private static class PetInstance {
        private final Mob entity;
        private final Level level;
        private int tickCount = 0;

        PetInstance(EntityType<? extends Mob> type, Level level) {
            this.level = level;
            try {
                this.entity = type.create(level, EntitySpawnReason.LOAD);
            } catch (Throwable t) {
                throw new RuntimeException("Failed to create pet entity", t);
            }
            if (this.entity != null) {
                this.entity.setNoAi(true);
                this.entity.setInvulnerable(true);
                this.entity.setSilent(true);
                this.entity.setNoGravity(true);
            }
        }

        void updatePosition(Vec3 ownerPos, float ownerYaw, CosmeticData d) {
            if (entity == null) return;

            entity.setNoAi(true);
            entity.setInvulnerable(true);
            entity.setSilent(true);
            entity.setNoGravity(true);

            String mode = d.petMode != null ? d.petMode : "follow";
            double bob = 0.0;
            double ox = 1.2;
            double oy = 0.5;

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

            double rad = Math.toRadians(ownerYaw + 180);
            double x = ownerPos.x - Math.sin(rad) * ox;
            double y = ownerPos.y + oy + bob;
            double z = ownerPos.z + Math.cos(rad) * ox;

            entity.setPos(x, y, z);
            entity.setYRot(ownerYaw);
            entity.setXRot(0);
            entity.setDeltaMovement(0, 0, 0);
            try {
                entity.yRotO = ownerYaw;
                entity.setYBodyRot(ownerYaw);
                entity.setYHeadRot(ownerYaw);
            } catch (Throwable ignored) {
            }
            try {
                entity.tick();
                tickCount = entity.tickCount;
            } catch (Throwable ignored) {
                tickCount++;
                entity.tickCount = tickCount;
            }
            try {
                entity.setOldPosAndRot();
            } catch (Throwable ignored) {
            }

            if (d.petName != null && !d.petName.isEmpty()) {
                int nameColor = CosmeticText.parseColor(d.petNameColor, 0xFFFFFF);
                try {
                    entity.setCustomName(Component.literal(d.petName)
                            .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(nameColor))));
                    entity.setCustomNameVisible(true);
                } catch (Throwable ignored) {
                }
            } else {
                try {
                    entity.setCustomNameVisible(false);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static EntityType<? extends Mob> getPetType(String petKind) {
        if (petKind == null) return EntityType.ENDER_DRAGON;

        return switch (petKind.toLowerCase()) {
            case "ender_dragon", "dragon", "enderdragon" -> EntityType.ENDER_DRAGON;
            case "wolf", "dog" -> EntityType.WOLF;
            case "cat", "ocelot" -> EntityType.CAT;
            case "parrot" -> EntityType.PARROT;
            case "fox" -> EntityType.FOX;
            case "rabbit", "bunny" -> EntityType.RABBIT;
            case "shulker" -> EntityType.SHULKER;
            default -> EntityType.ENDER_DRAGON;
        };
    }

    public static void ensurePetExists(UUID ownerId, Level level, String petKind) {
        if (ownerId == null || level == null || petKind == null) return;

        PetInstance existing = PETS.get(ownerId);
        if (existing != null && existing.level == level) return;

        EntityType<? extends Mob> type = getPetType(petKind);
        PETS.put(ownerId, new PetInstance(type, level));
        LEVELS.put(ownerId, level);
    }
}
