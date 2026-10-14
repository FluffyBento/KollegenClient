package dev.kollegen.client.presence;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MythicPets {
    public static final Set<String> KINDS = Set.of(
            "ember_drake", "spectre", "crystal_golem", "bolt_bot", "mooncap",
            "jelly_cube", "storm_cloud", "lantern_spirit", "grimoire", "mini_ufo",
            "void_wyrm", "flame_skull", "rune_sentinel", "shadow_reaper");

    private static final Map<UUID, MythicState> STATES = new ConcurrentHashMap<>();

    private MythicPets() {
    }

    public static boolean isMythicKind(String kind) {
        if (kind == null) return false;
        return KINDS.contains(kind.toLowerCase());
    }

    private static final class MythicState {
        Level level;
        String kind;
        PetModel model;
        Identifier texBase;
        Identifier texGlow;
        double x;
        double y;
        double z;
        float yaw;
        boolean placed;
        double wx;
        double wy;
        double wz;
        double ox;
        double oy;
        double oz;
        boolean hasOwner;
        double rx;
        double ry;
        double rz;
        float ryaw;
        boolean renderInit;
        long lastFrameMs;
        long wanderUntil;
        boolean resting;
        double stillTime;
        boolean sitting;
        double stride;
        double particleAcc;
        long lastTickMs;
        String animName = "idle";
    }

    public static void ensure(UUID ownerId, Level level, String kind) {
        if (ownerId == null || level == null || kind == null) return;
        String norm = kind.toLowerCase();
        if (!KINDS.contains(norm)) return;
        MythicState st = STATES.get(ownerId);
        if (st != null && st.level == level && norm.equals(st.kind)) return;
        MythicState fresh = new MythicState();
        fresh.level = level;
        fresh.kind = norm;
        fresh.texBase = Identifier.fromNamespaceAndPath("kollegen", "pets/" + norm + ".png");
        fresh.texGlow = Identifier.fromNamespaceAndPath("kollegen", "pets/" + norm + "_glow.png");
        fresh.lastTickMs = System.currentTimeMillis();
        STATES.put(ownerId, fresh);
    }

    public static void tickAll(Minecraft mc) {
        if (mc == null || mc.level == null) return;
        try {
            for (Map.Entry<UUID, MythicState> entry : new ArrayList<>(STATES.entrySet())) {
                UUID ownerId = entry.getKey();
                MythicState st = entry.getValue();
                if (st == null) continue;
                if (st.level != mc.level) {
                    STATES.remove(ownerId);
                    continue;
                }
                CosmeticData d = KollegenPresence.getCosmetics(ownerId);
                if (d == null || d.petKind == null || !d.petKind.toLowerCase().equals(st.kind)) {
                    STATES.remove(ownerId);
                    continue;
                }
                tickState(mc, ownerId, st, d);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void tickState(Minecraft mc, UUID ownerId, MythicState st, CosmeticData d) {
        try {
            Level level = mc.level;
            if (level == null) return;
            if (st.model == null) {
                try {
                    Identifier modelId = Identifier.fromNamespaceAndPath("kollegen", "pets/" + st.kind + ".pet.json");
                    st.model = PetModel.load(mc.getResourceManager(), modelId);
                } catch (Throwable ignored) {
                }
            }
            Player owner = null;
            try {
                if (mc.player != null && mc.player.getUUID().equals(ownerId)) owner = mc.player;
                else if (level != null) owner = level.getPlayerByUUID(ownerId);
            } catch (Throwable ignored) {
            }
            Vec3 ownerPos;
            float ownerYaw;
            boolean sprinting = false;
            boolean sneaking = false;
            if (owner != null) {
                ownerPos = owner.position();
                ownerYaw = owner.getYRot();
                try {
                    sprinting = owner.isSprinting();
                    sneaking = owner.isCrouching();
                } catch (Throwable ignored) {
                }
            } else {
                ownerPos = new Vec3(st.x, st.y, st.z);
                ownerYaw = st.yaw;
            }
            PetModel.Behavior bh = st.model != null ? st.model.behavior : null;
            String mode = d.petMode != null ? d.petMode : "follow";
            long now = System.currentTimeMillis();
            double dt = Math.min(0.25, Math.max(0.001, (now - st.lastTickMs) / 1000.0));
            st.lastTickMs = now;
            double speed = bh != null ? bh.followSpeed : 4.5;
            if (sprinting && bh != null) speed *= bh.sprintMult;
            boolean wantSit = sneaking && (bh == null || bh.sneakSit);
            double tx = st.x;
            double ty = st.y;
            double tz = st.z;
            if ("shoulder".equals(mode)) {
                double rad = Math.toRadians(ownerYaw + 180.0);
                tx = ownerPos.x - Math.sin(rad) * 0.45;
                ty = ownerPos.y + 1.55 + Math.sin(now / 1000.0) * 0.05;
                tz = ownerPos.z + Math.cos(rad) * 0.45;
                st.resting = false;
            } else if ("head".equals(mode)) {
                tx = ownerPos.x;
                ty = ownerPos.y + 2.35 + Math.sin(now / 1000.0) * 0.05;
                tz = ownerPos.z;
                st.resting = false;
            } else if ("hover".equals(mode)) {
                double rad = Math.toRadians(ownerYaw);
                double fx = -Math.sin(rad);
                double fz = Math.cos(rad);
                double rx = -Math.cos(rad);
                double rz = -Math.sin(rad);
                double h = bh != null ? bh.hoverHeight : 1.6;
                double amp = bh != null ? bh.hoverAmp : 0.1;
                double spd = bh != null ? bh.hoverSpeed : 1.0;
                tx = ownerPos.x - fx * 1.0 + rx * 0.8;
                ty = ownerPos.y + h + Math.sin(now / 1000.0 * spd) * amp;
                tz = ownerPos.z - fz * 1.0 + rz * 0.8;
                st.resting = false;
            } else {
                boolean groundKind = bh != null && "ground".equals(bh.mode);
                double dxo = st.x - ownerPos.x;
                double dzo = st.z - ownerPos.z;
                double ownerDistSq = dxo * dxo + dzo * dzo;
                double stop = bh != null ? bh.followStop : 1.4;
                if (!st.hasOwner) {
                    st.ox = ownerPos.x;
                    st.oy = ownerPos.y;
                    st.oz = ownerPos.z;
                    st.hasOwner = true;
                } else {
                    double odx = ownerPos.x - st.ox;
                    double ody = ownerPos.y - st.oy;
                    double odz = ownerPos.z - st.oz;
                    if (odx * odx + ody * ody + odz * odz > 2.25) {
                        st.ox = ownerPos.x;
                        st.oy = ownerPos.y;
                        st.oz = ownerPos.z;
                        double wa = Math.random() * Math.PI * 2.0;
                        double wr = 1.0 + Math.random() * 1.2;
                        st.wx = ownerPos.x + Math.cos(wa) * wr;
                        st.wz = ownerPos.z + Math.sin(wa) * wr;
                        if (groundKind) {
                            double g = PetRenderer.groundTop(level, st.wx, ownerPos.y, st.wz);
                            st.wy = Double.isNaN(g) ? ownerPos.y : g;
                        } else {
                            st.wy = Math.max(ownerPos.y - 0.2, ownerPos.y + 0.6 + Math.random() * 1.2);
                        }
                        st.wanderUntil = now + 4000 + (long) (Math.random() * 5000);
                        st.resting = false;
                    }
                }
                boolean rethink = now >= st.wanderUntil || ownerDistSq > 36.0;
                if (!rethink && !st.resting) {
                    double ddx = st.wx - st.x;
                    double ddy = st.wy - st.y;
                    double ddz = st.wz - st.z;
                    rethink = ddx * ddx + ddy * ddy + ddz * ddz < 0.64;
                }
                if (rethink) {
                    if (!st.resting && ownerDistSq <= 36.0 && Math.random() < 0.6) {
                        st.wx = st.x;
                        st.wy = st.y;
                        st.wz = st.z;
                        st.wanderUntil = now + 3000 + (long) (Math.random() * 4000);
                        st.resting = true;
                    } else {
                        double wa = Math.random() * Math.PI * 2.0;
                        double wr = ownerDistSq > 25.0 ? 1.2 : (1.0 + Math.random() * 2.5);
                        st.wx = ownerPos.x + Math.cos(wa) * wr;
                        st.wz = ownerPos.z + Math.sin(wa) * wr;
                        if (groundKind) {
                            double g = PetRenderer.groundTop(level, st.wx, ownerPos.y, st.wz);
                            st.wy = Double.isNaN(g) ? ownerPos.y : g;
                        } else {
                            double baseBob = 0.6 + Math.random() * 1.2;
                            if (bh != null && "hover".equals(bh.mode)) baseBob = Math.max(0.8, bh.hoverHeight * 0.6);
                            st.wy = Math.max(ownerPos.y - 0.2, ownerPos.y + baseBob);
                        }
                        st.wanderUntil = now + 4000 + (long) (Math.random() * 5000);
                        st.resting = false;
                    }
                }
                if (st.resting) {
                    double rr = Math.sqrt(ownerDistSq);
                    if (rr > stop + 2.0) st.resting = false;
                    else {
                        tx = st.x;
                        ty = st.y;
                        tz = st.z;
                    }
                }
                if (!st.resting) {
                    tx = st.wx;
                    ty = st.wy;
                    tz = st.wz;
                    if (!groundKind) {
                        double amp = bh != null ? bh.hoverAmp : 0.1;
                        double spd = bh != null ? bh.hoverSpeed : 1.0;
                        ty += Math.sin(now / 1000.0 * spd) * amp;
                    }
                }
            }
            double teleport = bh != null ? bh.teleportDistance : 16.0;
            double dx = tx - st.x;
            double dy = ty - st.y;
            double dz = tz - st.z;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double prevX = st.x;
            double prevZ = st.z;
            if (!st.placed || dist > teleport) {
                st.x = tx;
                st.y = ty;
                st.z = tz;
                st.placed = true;
                st.renderInit = false;
                st.stride = 0.0;
            } else if (dist > 0.005) {
                double step = Math.min(dist, speed * dt);
                st.x += dx / dist * step;
                st.y += dy / dist * step;
                st.z += dz / dist * step;
                st.stride = Math.min(1.0, step * 8.0);
                st.stillTime = 0.0;
                st.sitting = false;
            } else {
                st.stride = Math.max(0.0, st.stride - dt * 2.0);
                st.stillTime += dt;
            }
            boolean groundKind = bh != null && "ground".equals(bh.mode);
            if (groundKind && ("follow".equals(mode) || "hover".equals(mode))) {
                try {
                    double g = PetRenderer.groundTop(level, st.x, st.y, st.z);
                    if (!Double.isNaN(g) && st.y < g + 0.02) st.y = g + 0.02;
                } catch (Throwable ignored) {
                }
            }
            if (wantSit) {
                st.sitting = true;
            } else if (st.stride > 0.12) {
                st.sitting = false;
                st.stillTime = 0.0;
            } else if (bh != null && st.stillTime > bh.sitAfter && !"hover".equals(mode) && !"shoulder".equals(mode) && !"head".equals(mode)) {
                st.sitting = true;
            }
            if (st.sitting) {
                st.animName = bh != null && bh.sitAnim != null ? bh.sitAnim : "sit";
            } else if (st.stride > 0.12) {
                st.animName = bh != null && bh.moveAnim != null ? bh.moveAnim : "move";
            } else {
                st.animName = bh != null && bh.idleAnim != null ? bh.idleAnim : "idle";
            }
            float targetYaw = st.yaw;
            double movedX = st.x - prevX;
            double movedZ = st.z - prevZ;
            if (movedX * movedX + movedZ * movedZ > 0.0004) {
                targetYaw = (float) (Math.toDegrees(Math.atan2(movedZ, movedX)) - 90.0);
            } else if (bh != null && bh.lookAtPlayer) {
                double lx = ownerPos.x - st.x;
                double lz = ownerPos.z - st.z;
                if (lx * lx + lz * lz > 0.25) targetYaw = (float) (Math.toDegrees(Math.atan2(lz, lx)) - 90.0);
            }
            float dyaw = targetYaw - st.yaw;
            while (dyaw > 180.0F) dyaw -= 360.0F;
            while (dyaw < -180.0F) dyaw += 360.0F;
            st.yaw += Math.max(-6.0F, Math.min(6.0F, dyaw));
            if (bh != null && !bh.particles.isEmpty()) {
                try {
                    for (PetModel.Particle p : bh.particles) {
                        st.particleAcc += p.rate * dt / Math.max(1, bh.particles.size());
                    }
                    int spawn = Math.min(4, (int) st.particleAcc);
                    if (spawn > 0) {
                        st.particleAcc -= spawn;
                        double scale = d.petScale > 0.0 ? d.petScale : 1.0;
                        for (int i = 0; i < spawn; i++) {
                            PetModel.Particle p = bh.particles.get((int) (Math.random() * bh.particles.size()));
                            ParticleOptions opt = particleFor(p.type);
                            if (opt == null || level == null) continue;
                            double px = st.x + (Math.random() - 0.5) * 0.5 * scale;
                            double py = st.y + 0.4 * scale + Math.random() * 0.4 * scale;
                            double pz = st.z + (Math.random() - 0.5) * 0.5 * scale;
                            level.addParticle(opt, px, py, pz, (Math.random() - 0.5) * 0.03, 0.04 + Math.random() * 0.03, (Math.random() - 0.5) * 0.03);
                        }
                    }
                    if (st.particleAcc > 8.0) st.particleAcc = 8.0;
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static ParticleOptions particleFor(String type) {
        if (type == null) return null;
        try {
            return switch (type.toLowerCase()) {
                case "flame" -> ParticleTypes.FLAME;
                case "smoke" -> ParticleTypes.SMOKE;
                case "enchant" -> ParticleTypes.ENCHANT;
                case "item_slime" -> ParticleTypes.ITEM_SLIME;
                case "small_flame" -> ParticleTypes.SMALL_FLAME;
                case "end_rod" -> ParticleTypes.END_ROD;
                case "soul" -> ParticleTypes.SOUL;
                case "electric_spark" -> ParticleTypes.ELECTRIC_SPARK;
                case "portal" -> ParticleTypes.PORTAL;
                case "rain" -> ParticleTypes.RAIN;
                case "witch" -> ParticleTypes.WITCH;
                case "spore" -> ParticleTypes.SPORE_BLOSSOM_AIR;
                default -> null;
            };
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static void submitAll(Minecraft mc, PoseStack poseStack, LevelRenderState state, SubmitNodeCollector collector) {
        if (mc == null || mc.level == null || state == null || collector == null) return;
        Vec3 cam;
        try {
            cam = state.cameraRenderState.pos;
        } catch (Throwable ignored) {
            return;
        }
        if (cam == null) return;
        double timeSec = System.currentTimeMillis() / 1000.0;
        try {
            for (Map.Entry<UUID, MythicState> entry : STATES.entrySet()) {
                MythicState st = entry.getValue();
                if (st == null || st.level != mc.level) continue;
                CosmeticData d = KollegenPresence.getCosmetics(entry.getKey());
                if (d == null || d.petKind == null || !d.petKind.toLowerCase().equals(st.kind)) continue;
                if (st.model == null) {
                    try {
                        Identifier modelId = Identifier.fromNamespaceAndPath("kollegen", "pets/" + st.kind + ".pet.json");
                        st.model = PetModel.load(mc.getResourceManager(), modelId);
                    } catch (Throwable ignored) {
                    }
                }
                if (st.model == null || !st.placed) continue;
                submitPet(mc, poseStack, collector, state, st, d, cam, timeSec);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void submitPet(Minecraft mc, PoseStack poseStack, SubmitNodeCollector collector, LevelRenderState state, MythicState st, CosmeticData d, Vec3 cam, double timeSec) {
        try {
            PetModel model = st.model;
            if (model == null) return;
            double scale = d.petScale > 0.0 ? d.petScale : 1.0;
            int light = 15728880;
            long nowMs = System.currentTimeMillis();
            if (!st.renderInit) {
                st.rx = st.x;
                st.ry = st.y;
                st.rz = st.z;
                st.ryaw = st.yaw;
                st.renderInit = true;
                st.lastFrameMs = nowMs;
            }
            double fdt = 0.05;
            if (st.lastFrameMs > 0) fdt = Math.min(0.1, Math.max(0.0, (nowMs - st.lastFrameMs) / 1000.0));
            st.lastFrameMs = nowMs;
            double k = Math.min(1.0, fdt * 10.0);
            st.rx += (st.x - st.rx) * k;
            st.ry += (st.y - st.ry) * k;
            st.rz += (st.z - st.rz) * k;
            float rdy = st.yaw - st.ryaw;
            while (rdy > 180.0F) rdy -= 360.0F;
            while (rdy < -180.0F) rdy += 360.0F;
            st.ryaw += rdy * (float) Math.min(1.0, fdt * 8.0);
            poseStack.pushPose();
            try {
                poseStack.translate(st.rx - cam.x, st.ry - cam.y, st.rz - cam.z);
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - st.ryaw));
                poseStack.scale((float) scale, (float) scale, (float) scale);
                poseStack.scale(-1.0F, -1.0F, 1.0F);
                poseStack.translate(0.0F, -1.5F, 0.0F);
                PetModel.PetAnim anim = model.anims.get(st.animName);
                if (anim == null) anim = model.anims.get("idle");
                RenderType base = RenderTypes.entityTranslucent(st.texBase);
                renderBones(poseStack, collector, model, base, anim, timeSec, light);
                if (st.texGlow != null) {
                    RenderType glow = RenderTypes.entityTranslucent(st.texGlow);
                    renderBones(poseStack, collector, model, glow, anim, timeSec, 15728880);
                }
            } catch (Throwable ignored) {
            }
            poseStack.popPose();
            if (d.petName != null && !d.petName.isEmpty()) {
                try {
                    int nameColor = CosmeticText.parseColor(d.petNameColor, 0xFFFFFF);
                    Component comp = Component.literal(d.petName)
                            .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(nameColor)));
                    double h = (model.maxY - model.minY) * scale;
                    Vec3 anchor = new Vec3(st.rx - cam.x, st.ry + h + 0.5 - cam.y, st.rz - cam.z);
                    poseStack.pushPose();
                    try {
                        collector.submitNameTag(poseStack, anchor, 0, comp, false, 15728880, state.cameraRenderState);
                    } catch (Throwable ignored) {
                    }
                    poseStack.popPose();
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void renderBones(PoseStack poseStack, SubmitNodeCollector collector, PetModel model, RenderType type, PetModel.PetAnim anim, double timeSec, int packedLight) {
        try {
            for (PetModel.Bone bone : model.roots) {
                renderBone(poseStack, collector, model, type, bone, anim, timeSec, packedLight);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void renderBone(PoseStack poseStack, SubmitNodeCollector collector, PetModel model, RenderType type, PetModel.Bone bone, PetModel.PetAnim anim, double timeSec, int packedLight) {
        poseStack.pushPose();
        try {
            float[] r = new float[3];
            float[] p = new float[3];
            float[] s = new float[]{1.0F, 1.0F, 1.0F};
            if (anim != null) {
                PetModel.BoneChans chans = anim.bones.get(bone.name);
                if (chans != null) {
                    double phi = anim.length > 0.0F ? (timeSec % anim.length) / anim.length : 0.0;
                    if (phi < 0.0) phi += 1.0;
                    r[0] = (float) PetModel.evalWave(chans.rx, phi, 0.0);
                    r[1] = (float) PetModel.evalWave(chans.ry, phi, 0.0);
                    r[2] = (float) PetModel.evalWave(chans.rz, phi, 0.0);
                    p[0] = (float) PetModel.evalWave(chans.px, phi, 0.0);
                    p[1] = (float) PetModel.evalWave(chans.py, phi, 0.0);
                    p[2] = (float) PetModel.evalWave(chans.pz, phi, 0.0);
                    s[0] = (float) PetModel.evalWave(chans.sx, phi, 1.0);
                    s[1] = (float) PetModel.evalWave(chans.sy, phi, 1.0);
                    s[2] = (float) PetModel.evalWave(chans.sz, phi, 1.0);
                }
            }
            poseStack.translate(bone.px + p[0] / 16.0F, bone.py + p[1] / 16.0F, bone.pz + p[2] / 16.0F);
            poseStack.mulPose(new Quaternionf().rotationZYX(
                    (float) Math.toRadians(bone.rz + r[2]),
                    (float) Math.toRadians(bone.ry + r[1]),
                    (float) Math.toRadians(bone.rx + r[0])));
            if (s[0] != 1.0F || s[1] != 1.0F || s[2] != 1.0F) {
                poseStack.scale(s[0], s[1], s[2]);
            }
            poseStack.translate(-bone.px, -bone.py, -bone.pz);
            if (!bone.cubes.isEmpty()) {
                collector.submitCustomGeometry(poseStack, type, (pose, vc) -> {
                    try {
                        Matrix4f mat = pose.pose();
                        Vector3f tmpPos = new Vector3f();
                        Vector3f tmpN = new Vector3f();
                        for (PetModel.Cube cube : bone.cubes) {
                            for (BbModel.Face face : cube.faces) {
                                float[][] corners = BbModel.cornersFor(cube.minX, cube.minY, cube.maxX, cube.maxY, cube.minZ, cube.maxZ, face.dir);
                                float[][] uvs = BbModel.uvsFor(face);
                                float[] n = BbModel.normalFor(face.dir);
                                Vector3f nn = pose.transformNormal(n[0], n[1], n[2], tmpN);
                                for (int i = 0; i < 4; i++) {
                                    float[] c = corners[i];
                                    float[] uv = uvs[i];
                                    Vector3f pv = mat.transformPosition(c[0], c[1], c[2], tmpPos);
                                    vc.addVertex(pv.x(), pv.y(), pv.z(), -1, uv[0], uv[1], OverlayTexture.NO_OVERLAY, packedLight, nn.x(), nn.y(), nn.z());
                                }
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                });
            }
            for (PetModel.Bone child : bone.children) {
                renderBone(poseStack, collector, model, type, child, anim, timeSec, packedLight);
            }
        } catch (Throwable ignored) {
        }
        poseStack.popPose();
    }
}
