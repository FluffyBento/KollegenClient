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
import net.minecraft.world.entity.Entity;
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
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PetRenderer {
    private static final Map<UUID, PetInstance> PETS = new ConcurrentHashMap<>();
    private static final AtomicInteger NEXT_FAKE_ID = new AtomicInteger(-1000000);
    private static final Map<UUID, Vec3> LASTPOS = new ConcurrentHashMap<>();
    private static final Map<UUID, Float> LAST_YAW = new ConcurrentHashMap<>();
    private static final Map<UUID, Level> LEVELS = new ConcurrentHashMap<>();

    private static final Logger PETLOG = LoggerFactory.getLogger("kollegen-pet");
    private static boolean loggedOnTick = false;
    private static boolean loggedTickOk = false;
    private static boolean loggedTickFail = false;
    private static boolean loggedExtract = false;
    private static boolean loggedSubmit = false;
    private static boolean loggedExtractFail = false;
    private static boolean loggedExtractAdded = false;
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
                    if (!loggedExtractAdded) {
                        loggedExtractAdded = true;
                        PETLOG.info("pet extract added {}", pet.entity.getClass().getSimpleName());
                    }
                } else if (!loggedExtractFail) {
                    loggedExtractFail = true;
                    PETLOG.warn("pet extract null {}", pet.entity.getClass().getSimpleName());
                }
            } catch (Throwable t) {
                if (!loggedExtractFail) {
                    loggedExtractFail = true;
                    PETLOG.warn("pet extract failed {}", pet.entity.getClass().getSimpleName(), t);
                }
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
        private final Entity entity;
        private final Level level;
        private int tickCount = 0;
        private boolean placed = false;
        private double wx = 0.0;
        private double wy = 0.0;
        private double wz = 0.0;
        private long wanderUntil = 0;
        private boolean resting = false;
        private int hopT = 0;
        private int hopDur = 15;
        private int hopCd = 0;
        private double hopX0 = 0.0;
        private double hopY0 = 0.0;
        private double hopZ0 = 0.0;
        private double hopX1 = 0.0;
        private double hopZ1 = 0.0;
        private double hopY1 = 0.0;
        private float hopYaw = 0.0f;
        private float yawCur = 0.0f;
        private double hopPeak = 0.3;
        private int flapT = 0;
        private int flapCd = 200;
        private int happyT = 0;
        private int happyCd = 600;

        PetInstance(EntityType<?> type, Level level) {
            this.level = level;
            try {
                this.entity = type.create(level, EntitySpawnReason.LOAD);
            } catch (Throwable t) {
                throw new RuntimeException("Failed to create pet entity", t);
            }
            if (this.entity != null) {
                this.entity.setId(NEXT_FAKE_ID.getAndDecrement());
                if (this.entity instanceof Mob mob) mob.setNoAi(true);
                this.entity.setInvulnerable(true);
                this.entity.setSilent(true);
                this.entity.setNoGravity(true);
                BlubPets.setupDisplay(this.entity);
            }
        }

        void updatePosition(Vec3 ownerPos, float ownerYaw, CosmeticData d) {
            if (entity == null) return;

            if (entity instanceof Mob mob) mob.setNoAi(true);
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

            if ("follow".equals(mode)) {
                long now = System.currentTimeMillis();
                bob = Math.sin(now / 1000.0) * 0.12;
                Vec3 ep = entity.position();
                double dxo = ep.x - ownerPos.x;
                double dzo = ep.z - ownerPos.z;
                double ownerDistSq = dxo * dxo + dzo * dzo;
                boolean rethink = now >= wanderUntil || ownerDistSq > 36.0;
                if (!rethink && !resting) {
                    double ddx = wx - ep.x;
                    double ddy = wy - ep.y;
                    double ddz = wz - ep.z;
                    rethink = ddx * ddx + ddy * ddy + ddz * ddz < 0.64;
                }
                if (rethink) {
                    if (!resting && ownerDistSq <= 36.0 && Math.random() < 0.6) {
                        wx = ep.x;
                        wy = ep.y;
                        wz = ep.z;
                        wanderUntil = now + 3000 + (long) (Math.random() * 4000);
                        resting = true;
                    } else {
                        double wa = Math.random() * Math.PI * 2.0;
                        double wr = ownerDistSq > 25.0 ? 1.2 : (1.0 + Math.random() * 2.5);
                        wx = ownerPos.x + Math.cos(wa) * wr;
                        wy = Math.max(ownerPos.y - 0.2, ownerPos.y + 0.6 + Math.random() * 1.2);
                        wz = ownerPos.z + Math.sin(wa) * wr;
                        wanderUntil = now + 4000 + (long) (Math.random() * 5000);
                        resting = false;
                    }
                }
            }

            double rad = Math.toRadians(ownerYaw + 180);
            double x;
            double y;
            double z;
            if ("follow".equals(mode)) {
                x = wx;
                y = wy + bob;
                z = wz;
            } else {
                x = ownerPos.x - Math.sin(rad) * ox;
                y = ownerPos.y + oy + bob;
                z = ownerPos.z + Math.cos(rad) * ox;
            }

            double scale = d.petScale > 0.0 ? d.petScale : 0.2;
            if (scale > 1.0) scale = 1.0;
            if (entity instanceof Mob mob) {
                try {
                    AttributeInstance attr = mob.getAttribute(Attributes.SCALE);
                    if (attr != null && Math.abs(attr.getBaseValue() - scale) > 0.001) {
                        attr.setBaseValue(scale);
                    }
                } catch (Throwable ignored) {
                }
            }
            double faceDx = 0.0;
            double faceDz = 0.0;
            boolean blub = BlubPets.isBlubDisplay(entity);
            if (blub) {
                blubHopMove(x, y, z, (float) scale);
            }
            boolean snap;
            try {
                snap = !placed || entity.position().distanceToSqr(x, y, z) > 144.0;
            } catch (Throwable ignored) {
                snap = true;
            }
            if (snap && !blub) {
                entity.setPos(x, y, z);
                try {
                    entity.setOldPosAndRot();
                } catch (Throwable ignored) {
                }
                placed = true;
                entity.setDeltaMovement(0, 0, 0);
            } else if (!blub) {
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
                    double oxo = ownerPos.x - cur.x;
                    double ozo = ownerPos.z - cur.z;
                    double cap = (oxo * oxo + ozo * ozo) > 9.0 ? 0.3 : 0.05;
                    double step = Math.min(dist, cap);
                    faceDx = dx / dist * step;
                    faceDz = dz / dist * step;
                    entity.setPos(cur.x + faceDx, cur.y + dy / dist * step, cur.z + faceDz);
                } else {
                    entity.setPos(x, y, z);
                }
            }
            double faceSpeed = Math.sqrt(faceDx * faceDx + faceDz * faceDz);
            float lookYaw;
            if (faceSpeed > 0.03) {
                lookYaw = (float) (Math.toDegrees(Math.atan2(faceDz, faceDx)) - 90.0);
            } else {
                lookYaw = entity.getYRot();
            }
            entity.setYRot(lookYaw);
            entity.setXRot(0);
            entity.setDeltaMovement(-Math.sin(rad) * 0.02, 0, Math.cos(rad) * 0.02);
            if (entity instanceof Mob mob) {
                try {
                    mob.yRotO = lookYaw;
                    mob.setYBodyRot(lookYaw);
                    mob.setYHeadRot(lookYaw);
                } catch (Throwable ignored) {
                }
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

        void blubHopMove(double xin, double yin, double zin, float baseScale) {
            double x = xin;
            double y = yin - 0.4;
            double z = zin;
            Vec3 cur = null;
            try {
                cur = entity.position();
            } catch (Throwable ignored) {
            }
            if (cur == null) return;
            double dx0 = x - cur.x;
            double dy0 = y - cur.y;
            double dz0 = z - cur.z;
            double distSq = dx0 * dx0 + dy0 * dy0 + dz0 * dz0;
            boolean settled = resting || distSq < 0.04;
            if (!placed || distSq > 144.0) {
                entity.setPos(x, y, z);
                try {
                    entity.setOldPosAndRot();
                } catch (Throwable ignored) {
                }
                placed = true;
                entity.setDeltaMovement(0, 0, 0);
                hopT = 0;
                hopCd = 0;
                if (happyT <= 0) {
                    happyT = 15;
                    happyCd = 900 + (int) (Math.random() * 900);
                }
            } else if (!settled) {
                if (hopT <= 0 && hopCd > 0) {
                    hopCd--;
                } else {
                    if (hopT <= 0) {
                        double hdx = x - cur.x;
                        double hdz = z - cur.z;
                        double hdist = Math.sqrt(hdx * hdx + hdz * hdz);
                        double hopLen = Math.min(hdist, 1.2);
                        hopDur = Math.max(6, Math.min(15, 6 + (int) (hdist * 7.0)));
                        hopT = hopDur;
                        hopX0 = cur.x;
                        hopY0 = cur.y;
                        hopZ0 = cur.z;
                        hopY1 = y;
                        if (hdist > 0.001) {
                            hopX1 = cur.x + hdx / hdist * hopLen;
                            hopZ1 = cur.z + hdz / hdist * hopLen;
                            hopYaw = (float) (Math.toDegrees(Math.atan2(hopZ1 - hopZ0, hopX1 - hopX0)) + 90.0);
                        } else {
                            hopX1 = x;
                            hopZ1 = z;
                        }
                        hopCd = hdist > 2.0 ? 3 : 6;
                        hopPeak = Math.min(0.25, 0.10 + hopLen * 0.10);
                    }
                    hopT--;
                    double p = 1.0 - (double) Math.max(hopT, 0) / (double) hopDur;
                    double q = p * p * (3.0 - 2.0 * p);
                    entity.setPos(hopX0 + (hopX1 - hopX0) * q, hopY0 + (hopY1 - hopY0) * q + 4.0 * hopPeak * q * (1.0 - q), hopZ0 + (hopZ1 - hopZ0) * q);
                    try {
                        entity.setOldPosAndRot();
                    } catch (Throwable ignored) {
                    }
                }
            } else {
                hopT = 0;
                hopCd = 0;
                entity.setPos(x, y, z);
            }
            if (flapT > 0) {
                flapT--;
            } else if (settled) {
                if (--flapCd <= 0) {
                    flapT = 20;
                    flapCd = 140 + (int) (Math.random() * 401);
                }
            } else {
                flapCd = Math.max(flapCd, 40);
            }
            if (happyT <= 0 && --happyCd <= 0) {
                happyT = 15;
                happyCd = 900 + (int) (Math.random() * 900);
            } else if (happyT > 0) {
                happyT--;
            }
            float targetYawDeg = yawCur;
            if (!settled) {
                float dy = hopYaw - yawCur;
                while (dy > 180.0f) dy -= 360.0f;
                while (dy < -180.0f) dy += 360.0f;
                float turn = Math.max(-12.0f, Math.min(12.0f, dy));
                yawCur += turn;
                targetYawDeg = yawCur;
            }
            float yaw = targetYawDeg * ((float) Math.PI / 180.0f);
            float sy = baseScale;
            if (settled) sy = baseScale * 0.94f;
            if (hopT > 0) {
                double pp = 1.0 - (double) Math.max(hopT, 0) / (double) hopDur;
                sy = baseScale * (float) (1.0 + 0.10 * Math.sin(pp * Math.PI));
            }
            if (flapT > 0) {
                double fp = 1.0 - (double) flapT / 20.0;
                sy = baseScale * (float) (1.0 - 0.22 * Math.sin(fp * Math.PI));
            }
            if (happyT > 0) {
                double hp = 1.0 - (double) happyT / 15.0;
                yaw = targetYawDeg * ((float) Math.PI / 180.0f) + (float) (hp * Math.PI * 2.0);
                sy = baseScale * (float) (1.0 + 0.12 * Math.sin(hp * Math.PI));
            }
            BlubPets.applyDisplayPose(entity, baseScale, sy, sy, yaw);
        }
    }

    private static EntityType<?> getPetType(String petKind) {
        if (petKind == null) return EntityType.ALLAY;

        return switch (petKind.toLowerCase()) {
            case "blub" -> EntityType.ITEM_DISPLAY;
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

        EntityType<?> type = getPetType(petKind);
        PETS.put(ownerId, new PetInstance(type, level));
        LEVELS.put(ownerId, level);
    }
}
