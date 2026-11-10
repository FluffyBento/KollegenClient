package dev.kollegen.client.presence;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CosmeticRenderLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

    private static final Map<String, Identifier> ID_CACHE = new ConcurrentHashMap<>();
    private static final Map<Identifier, RenderType> TYPE_CACHE = new ConcurrentHashMap<>();

    private static final ThreadLocal<float[][]> SCRATCH_TRACKS = ThreadLocal.withInitial(() -> new float[][]{
            new float[3],
            new float[3],
            new float[3]
    });
    private static final ThreadLocal<Quaternionf> ROT_SCRATCH = ThreadLocal.withInitial(Quaternionf::new);
    private static final ThreadLocal<Quaternionf> CAPE_SWING = ThreadLocal.withInitial(Quaternionf::new);

    public static Identifier getId(String path) {
        if (path == null) return null;
        return ID_CACHE.computeIfAbsent(path, p -> Identifier.tryParse(p.toLowerCase(java.util.Locale.ROOT)));
    }

    private static RenderType getRenderType(Identifier id) {
        if (id == null) return null;
        return TYPE_CACHE.computeIfAbsent(id, RenderTypes::entityTranslucent);
    }

    public CosmeticRenderLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    private static Player resolvePlayer(Minecraft mc, UUID id) {
        if (mc == null || id == null) return null;
        if (mc.player != null && id.equals(mc.player.getUUID())) return mc.player;
        if (mc.level != null) return mc.level.getPlayerByUUID(id);
        return null;
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int packedLight, AvatarRenderState state, float limbSwing, float limbSwingAmount) {
        try {
            if (state == null) return;
            UUID id = RenderStateIds.get(state);
            if (id == null) return;
            CosmeticData data = KollegenPresence.getCosmetics(id);
            if (data == null || data.isEmpty()) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return;
            PlayerModel model = getParentModel();
            if (model == null) return;

            double distSq = state.distanceToCameraSq;
            if (distSq > 2304.0) return;
            double timeSec = animTime();
            if (distSq > 1024.0) {
                timeSec = Math.floor(timeSec * 20.0) / 20.0;
            }

            Player cachedPlayer = (data.capeTexture != null && data.capeModel != null && !data.capeModel.isEmpty()) ? resolvePlayer(mc, id) : null;
            if (data.capeTexture != null && data.capeModel != null && !data.capeModel.isEmpty()) {
                try {
                    Player player = cachedPlayer;
                    ItemStack chest = player != null ? player.getItemBySlot(EquipmentSlot.CHEST) : null;
                    boolean elytra = chest != null && chest.getItem() == net.minecraft.world.item.Items.ELYTRA;
                    if (!elytra) {
                        renderSlot(poseStack, collector, model.body, data.capeTexture, data.capeModel, packedLight, capeSwing(state), 1.5F, timeSec);
                    }
                } catch (Throwable ignored) {
                }
            }
            if (data.backAccessoryTexture != null) {
                renderSlot(poseStack, collector, model.body, data.backAccessoryTexture, data.backAccessoryModel, packedLight, null, 1.0F, timeSec);
            }
            if (data.headAccessoryTexture != null) {
                renderSlot(poseStack, collector, model.head, data.headAccessoryTexture, data.headAccessoryModel, packedLight, null, 3.0F, timeSec);
            }
            if (data.faceAccessoryTexture != null) {
                renderSlot(poseStack, collector, model.head, data.faceAccessoryTexture, data.faceAccessoryModel, packedLight, null, 3.0F, timeSec);
            }
            if (data.auraTexture != null) {
                float bodyYaw = 0.0F;
                try {
                    bodyYaw = state.bodyRot;
                } catch (Throwable ignored) {
                }
                renderAura(poseStack, collector, model.body, bodyYaw, data.auraTexture, data.auraModel, packedLight, timeSec);
            }
            if (data.shoulderAccessoryTexture != null) {
                renderSlot(poseStack, collector, model.body, data.shoulderAccessoryTexture, data.shoulderAccessoryModel, packedLight, null, 1.0F, timeSec);
            }
            if (data.torsoAccessoryTexture != null) {
                renderSlot(poseStack, collector, model.body, data.torsoAccessoryTexture, data.torsoAccessoryModel, packedLight, null, 1.0F, timeSec);
            }
            if (data.legsAccessoryTexture != null) {
                renderSlot(poseStack, collector, model.body, data.legsAccessoryTexture, data.legsAccessoryModel, packedLight, null, 1.0F, timeSec);
            }
            if (data.glovesTexture != null) {
                renderSlot(poseStack, collector, model.body, data.glovesTexture, data.glovesModel, packedLight, null, 1.0F, timeSec);
            }
            if (data.maceTexture != null) {
                try {
                    Player player = cachedPlayer != null ? cachedPlayer : resolvePlayer(mc, id);
                    renderMace(poseStack, collector, model, state, player, data.maceTexture, data.maceModel, packedLight, timeSec);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static Quaternionf capeSwing(AvatarRenderState state) {
        try {
            if (state == null) return null;
            float a = (state.capeLean / 2.0F + state.capeFlap) * (float) (Math.PI / 180.0);
            float b = (state.capeLean2 / 2.0F) * (float) (Math.PI / 180.0);
            a *= 0.75F;
            b *= 0.75F;
            if (a == 0.0F && b == 0.0F) return null;
            return CAPE_SWING.get().identity().rotateX(a).rotateZ(b).rotateY(-b);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void renderSlot(PoseStack poseStack, SubmitNodeCollector collector, ModelPart anchor, String texture, String modelPath, int packedLight, Quaternionf rootSwing, float idleScale, double timeSec) {
        try {
            if (anchor == null || texture == null) return;
            Identifier tex = getId(texture);
            if (tex == null) return;
            BbModel model = null;
            if (modelPath != null) {
                Identifier modelId = getId(modelPath);
                if (modelId != null) {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc != null) model = BbModel.load(mc.getResourceManager(), modelId);
                }
            }
            RenderType type = getRenderType(tex);
            if (model == null) {
                renderFallback(poseStack, collector, anchor, type, packedLight);
                return;
            }
            poseStack.pushPose();
            anchor.translateAndRotate(poseStack);
            renderModelContents(poseStack, collector, type, model, packedLight, timeSec, rootSwing, 0, idleScale);
            poseStack.popPose();
        } catch (Throwable ignored) {
        }
    }

    private static void renderAura(PoseStack poseStack, SubmitNodeCollector collector, ModelPart anchor, float bodyYawDeg, String texture, String modelPath, int packedLight, double timeSec) {
        try {
            if (anchor == null || texture == null) return;
            Identifier tex = getId(texture);
            if (tex == null) return;
            BbModel model = null;
            if (modelPath != null) {
                Identifier modelId = getId(modelPath);
                if (modelId != null) {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc != null) model = BbModel.load(mc.getResourceManager(), modelId);
                }
            }
            RenderType type = getRenderType(tex);
            if (model == null) {
                renderFallback(poseStack, collector, anchor, type, packedLight);
                return;
            }
            poseStack.pushPose();
            anchor.translateAndRotate(poseStack);
            poseStack.rotate(ROT_SCRATCH.get().identity().rotationY((float) Math.toRadians(-bodyYawDeg)));
            renderModelContents(poseStack, collector, type, model, packedLight, timeSec, null, 0, 1.0F);
            poseStack.popPose();
        } catch (Throwable ignored) {
        }
    }

    private static void renderMace(PoseStack poseStack, SubmitNodeCollector collector, PlayerModel parent, AvatarRenderState state, Player player, String texture, String modelPath, int packedLight, double timeSec) {
        try {
            if (texture == null) return;
            boolean rightMace = state != null && state.rightHandItemStack != null && state.rightHandItemStack.getItem() == Items.MACE;
            boolean leftMace = state != null && state.leftHandItemStack != null && state.leftHandItemStack.getItem() == Items.MACE;
            if (!rightMace && !leftMace && player != null) {
                ItemStack main = player.getItemBySlot(EquipmentSlot.MAINHAND);
                ItemStack off = player.getItemBySlot(EquipmentSlot.OFFHAND);
                rightMace = (player.getMainArm() == HumanoidArm.RIGHT && main != null && main.getItem() == Items.MACE)
                         || (player.getMainArm() == HumanoidArm.LEFT && off != null && off.getItem() == Items.MACE);
                leftMace = (player.getMainArm() == HumanoidArm.LEFT && main != null && main.getItem() == Items.MACE)
                        || (player.getMainArm() == HumanoidArm.RIGHT && off != null && off.getItem() == Items.MACE);
            }
            if (!rightMace && !leftMace) return;

            Identifier tex = getId(texture);
            if (tex == null) return;
            BbModel bb = null;
            if (modelPath != null) {
                Identifier modelId = getId(modelPath);
                if (modelId != null) {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc != null) bb = BbModel.load(mc.getResourceManager(), modelId);
                }
            }
            if (bb == null) return;

            if (rightMace && parent.rightArm != null) {
                renderMaceArm(poseStack, collector, parent.rightArm, false, tex, bb, packedLight, timeSec, player);
            }
            if (leftMace && parent.leftArm != null) {
                renderMaceArm(poseStack, collector, parent.leftArm, true, tex, bb, packedLight, timeSec, player);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void renderMaceArm(PoseStack poseStack, SubmitNodeCollector collector, ModelPart arm, boolean isLeft, Identifier tex, BbModel bb, int packedLight, double timeSec, Player player) {
        RenderType type = getRenderType(tex);
        if (type == null) return;
        poseStack.pushPose();
        arm.translateAndRotate(poseStack);

        float armSide = isLeft ? 1.0F : -1.0F;
        poseStack.translate(armSide * 0.16F, 0.40F, -0.04F);

        float yawDeg = isLeft ? -90.0F : 90.0F;
        poseStack.rotate(ROT_SCRATCH.get().identity().rotateY((float) Math.toRadians(ywDegSafe(yawDeg))));
        poseStack.scale(0.85F, 0.85F, 0.85F);

        float[] grip = bb.findGrip();
        poseStack.translate(-grip[0], -grip[1], -grip[2]);

        int tintColor = -1;
        if (player != null && dev.kollegen.client.mods.modules.ShieldSettings.isStatusTintEnabled()) {
            int sc = dev.kollegen.client.mods.modules.ShieldSettings.getStatusColor(player);
            if (sc != 0) tintColor = sc;
        }

        renderModelContents(poseStack, collector, type, bb, packedLight, timeSec, null, 0, 1.0F, tintColor);
        poseStack.popPose();
    }

    public static boolean renderFirstPersonMace(PoseStack poseStack, SubmitNodeCollector collector, net.minecraft.world.InteractionHand hand, int packedLight) {
        try {
            UUID self = dev.kollegen.client.presence.KollegenPresence.getSelfId();
            CosmeticData data = self != null ? dev.kollegen.client.presence.KollegenPresence.getCosmetics(self) : null;
            if (data == null || data.maceTexture == null) return false;
            String texture = data.maceTexture;
            String modelPath = data.maceModel;
            Identifier tex = getId(texture);
            if (tex == null) return false;
            BbModel bb = null;
            if (modelPath != null) {
                Identifier modelId = getId(modelPath);
                if (modelId != null) {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc != null) bb = BbModel.load(mc.getResourceManager(), modelId);
                }
            }
            if (bb == null) return false;

            Minecraft mc = Minecraft.getInstance();
            boolean isLeft = false;
            if (mc != null && mc.player != null) {
                boolean mainLeft = mc.player.getMainArm() == HumanoidArm.LEFT;
                isLeft = (hand == net.minecraft.world.InteractionHand.MAIN_HAND) ? mainLeft : !mainLeft;
            }

            RenderType type = getRenderType(tex);
            if (type == null) return false;
            poseStack.pushPose();

            float tx = 0.0F;
            float ty = 3.0F / 16.0F;
            float tz = 0.8F / 16.0F;
            float rx = -85.0F;
            float ry = 80.0F;
            float rz = 25.0F;
            if (isLeft) {
                tx = -tx;
                ry = -ry;
                rz = -rz;
            }
            poseStack.translate(tx, ty, tz);
            poseStack.rotate(new Quaternionf().rotationXYZ(
                    (float) Math.toRadians(rx),
                    (float) Math.toRadians(ry),
                    (float) Math.toRadians(rz)
            ));
            poseStack.scale(0.42F, 0.42F, 0.42F);

            float[] grip = bb.findGrip();
            poseStack.translate(-grip[0], -grip[1], -grip[2]);

            int tintColor = -1;
            if (mc != null && mc.player != null && dev.kollegen.client.mods.modules.ShieldSettings.isStatusTintEnabled()) {
                int sc = dev.kollegen.client.mods.modules.ShieldSettings.getStatusColor(mc.player);
                if (sc != 0) tintColor = sc;
            }

            renderModelContents(poseStack, collector, type, bb, packedLight, animTime(), null, 0, 1.0F, tintColor);
            poseStack.popPose();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static float ywDegSafe(float deg) {
        while (deg <= -180.0F) deg += 360.0F;
        while (deg > 180.0F) deg -= 360.0F;
        return deg;
    }

    private static double animTime() {
        return System.currentTimeMillis() * 0.001;
    }

    public static void renderModelDirect(PoseStack poseStack, SubmitNodeCollector collector, RenderType type, BbModel model, int packedLight, double timeSec, int vertexColor) {
        renderModelContents(poseStack, collector, type, model, packedLight, timeSec, null, 0, 1.0F, vertexColor);
    }

    public static RenderType getCustomRenderType(Identifier tex) {
        return getRenderType(tex);
    }

    private static void renderModelContents(PoseStack poseStack, SubmitNodeCollector collector, RenderType type, BbModel model, int packedLight, double timeSec, Quaternionf rootSwing, int depth, float idleScale) {
        renderModelContents(poseStack, collector, type, model, packedLight, timeSec, rootSwing, depth, idleScale, -1);
    }

    private static void renderModelContents(PoseStack poseStack, SubmitNodeCollector collector, RenderType type, BbModel model, int packedLight, double timeSec, Quaternionf rootSwing, int depth, float idleScale, int vertexColor) {
        for (BbModel.Bone bone : model.bones) {
            renderBone(poseStack, collector, type, model, bone, packedLight, timeSec, depth == 0 ? rootSwing : null, depth, idleScale, vertexColor);
        }
        for (BbModel.Cube cube : model.loose) {
            submitSingleCube(poseStack, collector, type, model, cube, packedLight, timeSec, vertexColor);
        }
    }

    private static void renderBone(PoseStack poseStack, SubmitNodeCollector collector, RenderType type, BbModel model, BbModel.Bone bone, int packedLight, double timeSec, Quaternionf rootSwing, int depth, float idleScale, int vertexColor) {
        poseStack.pushPose();
        try {
            float[] animR = null;
            float[] animP = null;
            float[] animS = null;
            if (model.animLength > 0.0F && bone.name != null && !bone.name.isEmpty()) {
                BbModel.AnimBone ab = model.anims.get(bone.name);
                if (ab != null) {
                    float[][] scratch = SCRATCH_TRACKS.get();
                    float[] r = scratch[0];
                    if (BbModel.sampleTrack(ab.rTimes, ab.rVals, ab.rInterp, timeSec, model.animLength, r)) {
                        r[0] *= idleScale;
                        r[1] *= idleScale;
                        r[2] *= idleScale;
                        animR = r;
                    }
                    float[] p = scratch[1];
                    if (BbModel.sampleTrack(ab.pTimes, ab.pVals, ab.pInterp, timeSec, model.animLength, p)) {
                        p[0] *= idleScale;
                        p[1] *= idleScale;
                        p[2] *= idleScale;
                        animP = p;
                    }
                    float[] s = scratch[2];
                    s[0] = 1.0F; s[1] = 1.0F; s[2] = 1.0F;
                    if (BbModel.sampleTrack(ab.sTimes, ab.sVals, ab.sInterp, timeSec, model.animLength, s)) {
                        animS = s;
                    }
                }
            }
            float ox = BbModel.pivotX(bone.ox) + (animP != null ? animP[0] / 16.0F : 0.0F);
            float oy = BbModel.pivotY(bone.oy) - (animP != null ? animP[1] / 16.0F : 0.0F);
            float oz = BbModel.pivotZ(bone.oz) + (animP != null ? animP[2] / 16.0F : 0.0F);
            float rx = bone.rx + (animR != null ? animR[0] : 0.0F);
            float ry = bone.ry + (animR != null ? animR[1] : 0.0F);
            float rz = bone.rz + (animR != null ? animR[2] : 0.0F);
            boolean hasR = rx != 0.0F || ry != 0.0F || rz != 0.0F;
            boolean hasS = animS != null && (animS[0] != 1.0F || animS[1] != 1.0F || animS[2] != 1.0F);
            if (hasR || hasS) {
                poseStack.translate(ox, oy, oz);
                if (hasR) {
                    poseStack.rotate(ROT_SCRATCH.get().identity().rotationZYX(
                            (float) Math.toRadians(rz),
                            (float) Math.toRadians(ry),
                            (float) Math.toRadians(rx)));
                }
                if (hasS) poseStack.scale(animS[0], animS[1], animS[2]);
                poseStack.translate(-ox, -oy, -oz);
            }
            if (rootSwing != null) {
                try {
                    poseStack.rotate(rootSwing);
                } catch (Throwable ignored) {
                }
            }

            for (BbModel.Cube cube : bone.cubes) {
                if (cube.hasRotation()) {
                    poseStack.pushPose();
                    try {
                        applyPivot(poseStack, BbModel.pivotX(cube.ox), BbModel.pivotY(cube.oy), BbModel.pivotZ(cube.oz), cube.rx, cube.ry, cube.rz);
                        submitSingleCube(poseStack, collector, type, model, cube, packedLight, timeSec, vertexColor);
                    } catch (Throwable ignored) {
                    }
                    poseStack.popPose();
                } else {
                    submitSingleCube(poseStack, collector, type, model, cube, packedLight, timeSec, vertexColor);
                }
            }

            for (BbModel.Bone child : bone.children) {
                renderBone(poseStack, collector, type, model, child, packedLight, timeSec, null, depth + 1, idleScale, vertexColor);
            }
        } catch (Throwable ignored) {
        }
        poseStack.popPose();
    }

    private static void submitSingleCube(PoseStack poseStack, SubmitNodeCollector collector, RenderType type, BbModel model, BbModel.Cube cube, int packedLight, double timeSec, int vertexColor) {
        try {
            collector.submitCustomGeometry(poseStack, type, (pose, vc) -> {
                try {
                    Matrix4f mat = pose.pose();
                    Vector3f tmpPos = new Vector3f();
                    Vector3f tmpN = new Vector3f();

                    float shift = 0.0F;
                    if (cube.frameW > 0.0F && model != null && model.texWidth > 0.0F) {
                        int n = cube.frameN > 0 ? cube.frameN : 4;
                        int fps = cube.frameFps > 0 ? cube.frameFps : 2;
                        int fr = (int) Math.floor(timeSec * fps) % n;
                        if (fr < 0) fr += n;
                        shift = (fr * cube.frameW / model.texWidth) % 1.0F;
                    }

                    for (BbModel.Face face : cube.faces) {
                        emitFace(vc, mat, pose, cube, face, shift, packedLight, tmpPos, tmpN, vertexColor);
                    }
                } catch (Throwable ignored) {
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private static void emitVertex(VertexConsumer vc, Matrix4f mat, float x, float y, float z,
                                   float u, float v, int packedLight, float nx, float ny, float nz, Vector3f tmpPos, int vertexColor) {
        Vector3f p = mat.transformPosition(x, y, z, tmpPos);
        vc.addVertex(p.x(), p.y(), p.z(), vertexColor, u, v, OverlayTexture.NO_OVERLAY, packedLight, nx, ny, nz);
    }

    private static void emitFace(VertexConsumer vc, Matrix4f mat, PoseStack.Pose pose,
                                 BbModel.Cube cube, BbModel.Face face, float shift,
                                 int packedLight, Vector3f tmpPos, Vector3f tmpN, int vertexColor) {
        float[] n = BbModel.normalFor(face.dir);
        Vector3f nn = pose.transformNormal(n[0], n[1], n[2], tmpN);
        float nx = nn.x();
        float ny = nn.y();
        float nz = nn.z();

        float u1 = (face.u1 + shift) % 1.0F;
        float u2 = (face.u2 + shift) % 1.0F;
        float v1 = face.v1;
        float v2 = face.v2;

        float minX = cube.minX, minY = cube.minY, minZ = cube.minZ;
        float maxX = cube.maxX, maxY = cube.maxY, maxZ = cube.maxZ;

        switch (face.dir) {
            case BbModel.DOWN -> {
                emitVertex(vc, mat, maxX, minY, maxZ, u2, v1, packedLight, nx, ny, nz, tmpPos, vertexColor);
                emitVertex(vc, mat, minX, minY, maxZ, u1, v1, packedLight, nx, ny, nz, tmpPos, vertexColor);
                emitVertex(vc, mat, minX, minY, minZ, u1, v2, packedLight, nx, ny, nz, tmpPos, vertexColor);
                emitVertex(vc, mat, maxX, minY, minZ, u2, v2, packedLight, nx, ny, nz, tmpPos, vertexColor);
            }
            case BbModel.UP -> {
                emitVertex(vc, mat, maxX, maxY, minZ, u2, v1, packedLight, nx, ny, nz, tmpPos, vertexColor);
                emitVertex(vc, mat, minX, maxY, minZ, u1, v1, packedLight, nx, ny, nz, tmpPos, vertexColor);
                emitVertex(vc, mat, minX, maxY, maxZ, u1, v2, packedLight, nx, ny, nz, tmpPos, vertexColor);
                emitVertex(vc, mat, maxX, maxY, maxZ, u2, v2, packedLight, nx, ny, nz, tmpPos, vertexColor);
            }
            case BbModel.NORTH -> {
                emitVertex(vc, mat, maxX, minY, minZ, u2, v1, packedLight, nx, ny, nz, tmpPos, vertexColor);
                emitVertex(vc, mat, minX, minY, minZ, u1, v1, packedLight, nx, ny, nz, tmpPos, vertexColor);
                emitVertex(vc, mat, minX, maxY, minZ, u1, v2, packedLight, nx, ny, nz, tmpPos, vertexColor);
                emitVertex(vc, mat, maxX, maxY, minZ, u2, v2, packedLight, nx, ny, nz, tmpPos, vertexColor);
            }
            case BbModel.SOUTH -> {
                emitVertex(vc, mat, minX, minY, maxZ, u2, v1, packedLight, nx, ny, nz, tmpPos, vertexColor);
                emitVertex(vc, mat, maxX, minY, maxZ, u1, v1, packedLight, nx, ny, nz, tmpPos, vertexColor);
                emitVertex(vc, mat, maxX, maxY, maxZ, u1, v2, packedLight, nx, ny, nz, tmpPos, vertexColor);
                emitVertex(vc, mat, minX, maxY, maxZ, u2, v2, packedLight, nx, ny, nz, tmpPos, vertexColor);
            }
            case BbModel.WEST -> {
                emitVertex(vc, mat, minX, minY, minZ, u2, v1, packedLight, nx, ny, nz, tmpPos, vertexColor);
                emitVertex(vc, mat, minX, minY, maxZ, u1, v1, packedLight, nx, ny, nz, tmpPos, vertexColor);
                emitVertex(vc, mat, minX, maxY, maxZ, u1, v2, packedLight, nx, ny, nz, tmpPos, vertexColor);
                emitVertex(vc, mat, minX, maxY, minZ, u2, v2, packedLight, nx, ny, nz, tmpPos, vertexColor);
            }
            case BbModel.EAST -> {
                emitVertex(vc, mat, maxX, minY, maxZ, u2, v1, packedLight, nx, ny, nz, tmpPos, vertexColor);
                emitVertex(vc, mat, maxX, minY, minZ, u1, v1, packedLight, nx, ny, nz, tmpPos, vertexColor);
                emitVertex(vc, mat, maxX, maxY, minZ, u1, v2, packedLight, nx, ny, nz, tmpPos, vertexColor);
                emitVertex(vc, mat, maxX, maxY, maxZ, u2, v2, packedLight, nx, ny, nz, tmpPos, vertexColor);
            }
        }
    }

    private static void applyPivot(PoseStack ps, float px, float py, float pz, float rx, float ry, float rz) {
        ps.translate(px, py, pz);
        if (rx != 0.0F || ry != 0.0F || rz != 0.0F) {
            ps.rotate(ROT_SCRATCH.get().identity().rotationZYX(
                    (float) Math.toRadians(rz),
                    (float) Math.toRadians(ry),
                    (float) Math.toRadians(rx)));
        }
        ps.translate(-px, -py, -pz);
    }

    private static final net.minecraft.client.renderer.texture.UvMapping IDENTITY_UV = new net.minecraft.client.renderer.texture.UvMapping() {
        @Override public float getU(float u) { return u; }
        @Override public float getV(float v) { return v; }
    };

    private static void renderFallback(PoseStack poseStack, SubmitNodeCollector collector, ModelPart anchor, RenderType type, int packedLight) {
        try {
            collector.submitModelPart(anchor, poseStack, type, packedLight, OverlayTexture.NO_OVERLAY, IDENTITY_UV);
        } catch (Throwable ignored) {
        }
    }
}

