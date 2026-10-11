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
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PetRenderer {
    private static final Map<UUID, PetInstance> PETS = new ConcurrentHashMap<>();
    private static final Map<UUID, Vec3> LASTPOS = new ConcurrentHashMap<>();
    private static final Map<UUID, Float> LAST_YAW = new ConcurrentHashMap<>();
    private static final Map<UUID, Level> LEVELS = new ConcurrentHashMap<>();

    private static final Logger PETLOG = LoggerFactory.getLogger("kollegen-pet");
    private static boolean loggedOnTick = false;
    private static boolean loggedTickOk = false;
    private static boolean loggedTickFail = false;
    private static boolean loggedExtract = false;
    private static boolean loggedSubmit = false;
    private static final Set<UUID> loggedRender = ConcurrentHashMap.newKeySet();

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
        if (!loggedOnTick) {
            loggedOnTick = true;
            PETLOG.info("pet onTick active pets={}", PETS.size());
        }

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
            float yaw = LAST_YAW.getOrDefault(ownerId, 0f);
            if (mc.player != null && mc.player.getUUID().equals(ownerId)) {
                base = mc.player.position();
                yaw = mc.player.getYRot();
            }
            if (base == null) continue;

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
        if (!loggedExtract) {
            loggedExtract = true;
            int dragons = 0;
            for (PetInstance p : PETS.values()) {
                if (p != null && p.entity instanceof EnderDragon) dragons++;
            }
            PETLOG.info("pet extract active total={} dragonsSkipped={}", PETS.size(), dragons);
        }
        if (pet.entity instanceof EnderDragon) continue;

            try {
                EntityRenderState renderState = dispatcher.extractEntity(pet.entity, partial);
                if (renderState != null) {
                    double scale = d.petScale > 0.0 ? d.petScale : 0.2;
                    String kind = d.petKind != null ? d.petKind.toLowerCase() : "";
                    if (kind.equals("ender_dragon") || kind.equals("dragon") || kind.equals("enderdragon")) {
                        if (scale < 0.5) scale = 0.5;
                    }
                    if (renderState instanceof LivingEntityRenderState living) {
                        living.scale = (float) scale;
                    }
                    state.entityRenderStates.add(renderState);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    public static void submitDragons(Minecraft mc, PoseStack poseStack, LevelRenderState state, SubmitNodeCollector collector) {
        if (mc.level == null || state == null) return;
        if (!loggedSubmit) {
            loggedSubmit = true;
            PETLOG.info("pet submitHook active");
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
            if (!(pet.entity instanceof EnderDragon)) continue;
            CosmeticData d = KollegenPresence.getCosmetics(ownerId);
            if (d == null || d.petKind == null) continue;
            double scale = d.petScale > 0.0 ? d.petScale : 0.2;
            if (scale > 1.0) scale = 1.0;
            Vec3 pos = pet.entity.position();
            poseStack.pushPose();
            try {
                poseStack.translate(pos.x, pos.y, pos.z);
                poseStack.scale((float) scale, (float) scale, (float) scale);
                EntityRenderState renderState = dispatcher.extractEntity(pet.entity, 0.0F);
                if (renderState != null) {
                    dispatcher.submit(renderState, state.cameraRenderState, 0.0, 0.0, 0.0, poseStack, collector);
                }
            } catch (Throwable ignored) {
            } finally {
                poseStack.popPose();
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
        if (loggedRender.add(ownerId)) {
            PETLOG.info("pet renderFor {} kind={} scale={}", ownerId, d.petKind, d.petScale);
        }
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
        if (!(pet.entity instanceof EnderDragon)) return;
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
        private boolean placed = false;

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

            double orbitX = 0.0;
            double orbitZ = 0.0;
            double anchorX = 0.0;
            double anchorZ = 0.0;
            if ("follow".equals(mode)) {
                long now = System.currentTimeMillis();
                bob = Math.sin(now / 1000.0) * 0.12;
                double orbit = now / 2800.0;
                orbitX = Math.cos(orbit) * 0.7;
                orbitZ = Math.sin(orbit) * 0.7;
                double anchorA = now / 6000.0;
                anchorX = Math.cos(anchorA) * 1.6;
                anchorZ = Math.sin(anchorA) * 1.6;
                ox = 0.0;
            }

            double rad = Math.toRadians(ownerYaw + 180);
            double x = ownerPos.x - Math.sin(rad) * ox + orbitX + anchorX;
            double y = ownerPos.y + oy + bob;
            double z = ownerPos.z + Math.cos(rad) * ox + orbitZ + anchorZ;

            double scale = d.petScale > 0.0 ? d.petScale : 0.2;
            if (scale > 1.0) scale = 1.0;
            try {
                AttributeInstance attr = entity.getAttribute(Attributes.SCALE);
                if (attr != null && Math.abs(attr.getBaseValue() - scale) > 0.001) {
                    attr.setBaseValue(scale);
                }
            } catch (Throwable ignored) {
            }
            boolean snap;
            try {
                snap = !placed || entity.position().distanceToSqr(x, y, z) > 144.0;
            } catch (Throwable ignored) {
                snap = true;
            }
            if (snap) {
                entity.setPos(x, y, z);
                try {
                    entity.setOldPosAndRot();
                } catch (Throwable ignored) {
                }
                placed = true;
                entity.setDeltaMovement(0, 0, 0);
            } else {
                try {
                    entity.setOldPosAndRot();
                } catch (Throwable ignored) {
                }
                Vec3 cur = entity.position();
                double dx = x - cur.x;
                double dy = y - cur.y;
                double dz = z - cur.z;
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (dist > 0.001) {
                    double step = Math.min(dist, 0.25);
                    entity.setPos(cur.x + dx / dist * step, cur.y + dy / dist * step, cur.z + dz / dist * step);
                } else {
                    entity.setPos(x, y, z);
                }
            }
            long lookNow = System.currentTimeMillis();
            float lookYaw = ownerYaw + (float) (Math.sin(lookNow / 1700.0) * 50.0);
            float headYaw = lookYaw + (float) (Math.sin(lookNow / 900.0) * 15.0);
            entity.setYRot(lookYaw);
            entity.setXRot(0);
            entity.setDeltaMovement(-Math.sin(rad) * 0.05, 0, Math.cos(rad) * 0.05);
            try {
                entity.yRotO = lookYaw;
                entity.setYBodyRot(lookYaw);
                entity.setYHeadRot(headYaw);
            } catch (Throwable ignored) {
            }
            try {
                entity.tick();
                tickCount = entity.tickCount;
                if (!loggedTickOk) {
                    loggedTickOk = true;
                    PETLOG.info("pet tick ok {}", entity.getClass().getSimpleName());
                }
            } catch (Throwable ignored) {
                tickCount++;
                entity.tickCount = tickCount;
                if (!loggedTickFail) {
                    loggedTickFail = true;
                    PETLOG.info("pet tick failed {}", entity.getClass().getSimpleName());
                }
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
        if (petKind == null) return EntityType.ALLAY;

        return switch (petKind.toLowerCase()) {
            case "ender_dragon", "dragon", "enderdragon" -> EntityType.ALLAY;
            case "wolf", "dog" -> EntityType.WOLF;
            case "cat", "ocelot" -> EntityType.CAT;
            case "parrot" -> EntityType.PARROT;
            case "fox" -> EntityType.FOX;
            case "rabbit", "bunny" -> EntityType.RABBIT;
            case "shulker" -> EntityType.SHULKER;
            default -> EntityType.ALLAY;
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
