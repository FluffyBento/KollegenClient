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
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
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
        String effKind = effectiveKind(d.petKind, d.petVariant);
        if (pet == null || pet.level != mc.level || !effKind.equals(pet.kind)
                || (d.petVariant == null ? pet.variant != null : !d.petVariant.equals(pet.variant))) {
            try {
                pet = new PetInstance(getPetType(effKind), mc.level, effKind, d.petVariant);
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
        private final String kind;
        private final String variant;
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
        private double vy = 0.0;
        private double hopPeak = 0.3;
        private int flapT = 0;
        private int flapCd = 200;
        private int happyT = 0;
        private int happyCd = 600;

        PetInstance(EntityType<?> type, Level level, String kind, String variant) {
            this.level = level;
            this.kind = kind;
            this.variant = variant;
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
            applyVariant(entity, d.petVariant);

            String mode = d.petMode != null ? d.petMode : "follow";
            if (BlubPets.isBlubDisplay(entity) && !"follow".equals(mode)) mode = "follow";
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

            boolean groundFollow = "follow".equals(mode) && isGroundKind(d.petKind);
            if ("follow".equals(mode)) {
                long now = System.currentTimeMillis();
                bob = groundFollow ? 0.0 : Math.sin(now / 1000.0) * 0.12;
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
                        wz = ownerPos.z + Math.sin(wa) * wr;
                        if (groundFollow) {
                            double g = groundTop(level, wx, ownerPos.y, wz);
                            wy = Double.isNaN(g) ? ownerPos.y : g + 0.1;
                        } else {
                            wy = Math.max(ownerPos.y - 0.2, ownerPos.y + 0.6 + Math.random() * 1.2);
                        }
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
                double sy = y;
                if ("follow".equals(mode) || "hover".equals(mode)) {
                    double gy = groundTop(level, x, y, z);
                    if (!Double.isNaN(gy) && sy < gy + 0.02) sy = gy + 0.02;
                }
                entity.setPos(x, sy, z);
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
                    double tnx = cur.x + dx / dist * step;
                    double tny = cur.y + dy / dist * step;
                    double tnz = cur.z + dz / dist * step;
                    if ("follow".equals(mode) || "hover".equals(mode)) {
                        double gy = groundTop(level, tnx, tny, tnz);
                        if (!Double.isNaN(gy) && tny < gy + 0.02) tny = gy + 0.02;
                    }
                    double[] cl = clipHorizontal(level, entity, cur, Math.max(cur.y, tny), tnx, tnz, 0.8);
                    if (Math.abs(cl[0] - cur.x) + Math.abs(cl[1] - cur.z) < 0.02) {
                        double[] cx = clipHorizontal(level, entity, cur, Math.max(cur.y, tny), tnx, cur.z, 0.8);
                        double[] cz = clipHorizontal(level, entity, cur, Math.max(cur.y, tny), cur.x, tnz, 0.8);
                        if (Math.abs(cx[0] - cur.x) > 0.02) cl = new double[]{cx[0], cur.z};
                        else if (Math.abs(cz[1] - cur.z) > 0.02) cl = new double[]{cur.x, cz[1]};
                        else cl = new double[]{cur.x, cur.z};
                    }
                    faceDx = cl[0] - cur.x;
                    faceDz = cl[1] - cur.z;
                    entity.setPos(cl[0], tny, cl[1]);
                } else {
                    entity.setPos(x, y, z);
                }
            }
            double faceSpeed = Math.sqrt(faceDx * faceDx + faceDz * faceDz);
            float lookYaw;
            if (blub) {
                lookYaw = yawCur;
            } else if (faceSpeed > 0.03) {
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
            Vec3 cur = null;
            try {
                cur = entity.position();
            } catch (Throwable ignored) {
            }
            if (cur == null) return;
            double gHere = groundTop(level, cur.x, cur.y, cur.z);
            boolean hasGround = !Double.isNaN(gHere);
            if (hasGround && cur.y < gHere - 0.02) {
                entity.setPos(cur.x, gHere, cur.z);
                try {
                    entity.setOldPosAndRot();
                } catch (Throwable ignored) {
                }
                cur = entity.position();
                vy = 0.0;
            }
            boolean grounded = hasGround && cur.y <= gHere + 0.05;
            double hdx = xin - cur.x;
            double hdz = zin - cur.z;
            double hdist = Math.sqrt(hdx * hdx + hdz * hdz);
            double dyy = yin - 0.4 - cur.y;
            double distSq = hdx * hdx + dyy * dyy + hdz * hdz;
            boolean settled = hdist < 0.3 && grounded;
            if (!placed || distSq > 144.0) {
                double gT = groundTop(level, xin, yin, zin);
                double ty = Double.isNaN(gT) ? yin - 0.4 : Math.max(yin - 0.4, gT);
                entity.setPos(xin, ty, zin);
                try {
                    entity.setOldPosAndRot();
                } catch (Throwable ignored) {
                }
                placed = true;
                entity.setDeltaMovement(0, 0, 0);
                vy = 0.0;
                hopT = 0;
                hopCd = 0;
                if (happyT <= 0) {
                    happyT = 15;
                    happyCd = 900 + (int) (Math.random() * 900);
                }
            } else if (hopT > 0) {
                hopT--;
                double p = 1.0 - (double) Math.max(hopT, 0) / (double) hopDur;
                double q = p * p * (3.0 - 2.0 * p);
                double tnx = hopX0 + (hopX1 - hopX0) * q;
                double tnz = hopZ0 + (hopZ1 - hopZ0) * q;
                vy -= 0.05;
                if (vy < -0.5) vy = -0.5;
                double tny = cur.y + vy;
                double[] cl = clipHorizontal(level, entity, cur, Math.max(cur.y, tny), tnx, tnz, 1.0);
                boolean walled = Math.abs(cl[0] - tnx) + Math.abs(cl[1] - tnz) > 0.05;
                double g = groundTop(level, cl[0], tny, cl[1]);
                if (!Double.isNaN(g) && tny <= g) {
                    entity.setPos(cl[0], g, cl[1]);
                    hopT = 0;
                    vy = 0.0;
                    hopCd = 4;
                } else if (walled && hopT > 2) {
                    entity.setPos(cl[0], tny, cl[1]);
                    hopT = 0;
                    vy = Math.min(vy, 0.0);
                    hopCd = 8;
                } else {
                    entity.setPos(cl[0], tny, cl[1]);
                }
                try {
                    entity.setOldPosAndRot();
                } catch (Throwable ignored) {
                }
            } else if (hdist > 0.3 && grounded) {
                if (hopCd > 0) {
                    hopCd--;
                } else {
                    double hopLen = Math.min(hdist, 2.5);
                    hopDur = Math.max(8, Math.min(15, 8 + (int) (hopLen * 2.0)));
                    hopT = hopDur;
                    hopX0 = cur.x;
                    hopY0 = cur.y;
                    hopZ0 = cur.z;
                    hopY1 = cur.y;
                    hopX1 = cur.x + hdx / hdist * hopLen;
                    hopZ1 = cur.z + hdz / hdist * hopLen;
                    hopYaw = (float) (Math.toDegrees(Math.atan2(hdz, hdx)) - 90.0);
                    vy = Math.min(0.22, 0.15 + hopLen * 0.03);
                    hopCd = hdist > 4.0 ? 1 : (hdist > 2.0 ? 3 : 6);
                }
            } else if (!grounded) {
                vy -= 0.05;
                if (vy < -0.5) vy = -0.5;
                double tny = cur.y + vy;
                double drift = Math.min(hdist, 0.08);
                double tnx = cur.x;
                double tnz = cur.z;
                if (hdist > 0.001) {
                    tnx = cur.x + hdx / hdist * drift;
                    tnz = cur.z + hdz / hdist * drift;
                }
                double[] cl = clipHorizontal(level, entity, cur, Math.max(cur.y, tny), tnx, tnz, 1.0);
                double g = groundTop(level, cl[0], tny, cl[1]);
                if (!Double.isNaN(g) && tny <= g) {
                    entity.setPos(cl[0], g, cl[1]);
                    vy = 0.0;
                } else {
                    entity.setPos(cl[0], tny, cl[1]);
                }
                try {
                    entity.setOldPosAndRot();
                } catch (Throwable ignored) {
                }
            } else {
                vy = 0.0;
                hopT = 0;
                if (hopCd > 0) hopCd--;
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
            float yaw = (float) Math.PI - targetYawDeg * ((float) Math.PI / 180.0f);
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
                yaw = (float) Math.PI - targetYawDeg * ((float) Math.PI / 180.0f) + (float) (hp * Math.PI * 2.0);
                sy = baseScale * (float) (1.0 + 0.12 * Math.sin(hp * Math.PI));
            }
            BlubPets.applyDisplayPose(entity, baseScale, sy, sy, yaw);
        }
    }

    private static double groundTop(Level level, double x, double yRef, double z) {
        if (level == null) return Double.NaN;
        try {
            int bx = (int) Math.floor(x);
            int bz = (int) Math.floor(z);
            int start = (int) Math.floor(yRef) + 1;
            for (int i = 0; i < 10; i++) {
                int by = start - i;
                BlockPos bp = new BlockPos(bx, by, bz);
                if (!level.getBlockState(bp).getCollisionShape(level, bp).isEmpty()) return by + 1.0;
            }
        } catch (Throwable ignored) {
        }
        return Double.NaN;
    }

    private static double[] clipHorizontal(Level level, Entity ent, Vec3 from, double yBase, double nx, double nz, double height) {
        double[] out = new double[]{nx, nz};
        if (level == null || ent == null || from == null) return out;
        try {
            double dx = nx - from.x;
            double dz = nz - from.z;
            double lenSq = dx * dx + dz * dz;
            if (lenSq < 0.00000001) return out;
            double len = Math.sqrt(lenSq);
            double best = 1.0;
            double[] offs = new double[]{0.25, Math.max(0.3, height - 0.15)};
            for (double off : offs) {
                Vec3 a = new Vec3(from.x, yBase + off, from.z);
                Vec3 b = new Vec3(nx, yBase + off, nz);
                BlockHitResult hit = level.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ent));
                if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
                    Vec3 loc = hit.getLocation();
                    double dot = (loc.x - a.x) * dx + (loc.z - a.z) * dz;
                    double t = dot / lenSq - 0.15 / len;
                    if (t < best) best = t;
                }
            }
            if (best < 1.0) {
                if (best < 0.02) return new double[]{from.x, from.z};
                out = new double[]{from.x + dx * best, from.z + dz * best};
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static boolean isGroundKind(String petKind) {
        if (petKind == null) return false;
        return switch (petKind.toLowerCase()) {
            case "cat", "ocelot", "dog", "wolf", "chicken", "hen", "rabbit", "bunny",
                 "fox", "axolotl", "copper_golem", "coppergolem", "golem", "shulker" -> true;
            default -> false;
        };
    }

    private static void applyVariant(Entity entity, String variant) {
        if (entity == null) return;
        try {
            if (entity instanceof Mob mob && mob instanceof AgeableMob) {
                try {
                    mob.setBaby("baby".equals(variant));
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            if (entity instanceof Fox fox) {
                try {
                    Fox.Variant want = "snow".equals(variant) ? Fox.Variant.SNOW : Fox.Variant.RED;
                    if (fox.getVariant() != want) ((dev.kollegen.client.mixin.FoxAccessor) fox).kollegen$setVariant(want);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            if (entity instanceof Axolotl ax) {
                try {
                    Axolotl.Variant want = Axolotl.Variant.LUCY;
                    if ("wild".equals(variant)) want = Axolotl.Variant.WILD;
                    else if ("gold".equals(variant)) want = Axolotl.Variant.GOLD;
                    else if ("cyan".equals(variant)) want = Axolotl.Variant.CYAN;
                    else if ("blue".equals(variant)) want = Axolotl.Variant.BLUE;
                    ((dev.kollegen.client.mixin.AxolotlAccessor) ax).kollegen$setVariant(want);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static EntityType<?> getPetType(String petKind) {
        if (petKind == null) return EntityType.ALLAY;

        return switch (petKind.toLowerCase()) {
            case "blub" -> EntityType.ITEM_DISPLAY;
            case "allay", "ender_dragon", "dragon", "enderdragon" -> EntityType.ALLAY;
            case "wolf", "dog" -> EntityType.WOLF;
            case "cat", "ocelot" -> EntityType.CAT;
            case "parrot" -> EntityType.PARROT;
            case "fox" -> EntityType.FOX;
            case "rabbit", "bunny" -> EntityType.RABBIT;
            case "shulker" -> EntityType.SHULKER;
            case "chicken", "hen" -> EntityType.CHICKEN;
            case "bee" -> EntityType.BEE;
            case "axolotl" -> EntityType.AXOLOTL;
            case "bat" -> EntityType.BAT;
            case "squid" -> EntityType.SQUID;
            case "glow_squid", "glowsquid" -> EntityType.GLOW_SQUID;
            case "nautilus" -> EntityType.NAUTILUS;
            case "zombie_nautilus", "zombienautilus" -> EntityType.ZOMBIE_NAUTILUS;
            case "copper_golem", "coppergolem", "golem" -> EntityType.COPPER_GOLEM;
            default -> EntityType.ALLAY;
        };
    }

    static String effectiveKind(String petKind, String variant) {
        if (petKind == null) return null;
        String kind = petKind.toLowerCase();
        if ("glow".equals(variant) && kind.equals("squid")) return "glow_squid";
        if ("zombie".equals(variant) && kind.equals("nautilus")) return "zombie_nautilus";
        return petKind;
    }

    public static void ensurePetExists(UUID ownerId, Level level, String petKind, String variant) {
        if (ownerId == null || level == null || petKind == null) return;

        String effKind = effectiveKind(petKind, variant);
        PetInstance existing = PETS.get(ownerId);
        if (existing != null && existing.level == level && effKind.equals(existing.kind)
                && (variant == null ? existing.variant == null : variant.equals(existing.variant))) return;

        EntityType<?> type = getPetType(effKind);
        PETS.put(ownerId, new PetInstance(type, level, effKind, variant));
        LEVELS.put(ownerId, level);
    }
}
