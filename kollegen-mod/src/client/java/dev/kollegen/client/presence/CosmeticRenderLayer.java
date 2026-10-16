package dev.kollegen.client.presence;

import com.mojang.blaze3d.vertex.PoseStack;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CosmeticRenderLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private static boolean kollegen$loggedRender;
    private static boolean kollegen$loggedNoModel;
    public CosmeticRenderLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
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
            if (data.capeTexture != null) {
                try {
                    Player player = mc.level != null ? mc.level.getPlayerByUUID(id) : null;
                    ItemStack chest = player != null ? player.getItemBySlot(EquipmentSlot.CHEST) : null;
                    boolean elytra = chest != null && chest.getItem() == net.minecraft.world.item.Items.ELYTRA;
                    if (!elytra) {
                        renderSlot(poseStack, collector, model.body, data.capeTexture, data.capeModel, packedLight, capeSwing(state), 1.5F);
                    }
                } catch (Throwable ignored) {
                }
            }
            if (data.backAccessoryTexture != null) {
                renderSlot(poseStack, collector, model.body, data.backAccessoryTexture, data.backAccessoryModel, packedLight, null, 1.0F);
            }
            if (data.headAccessoryTexture != null) {
                renderSlot(poseStack, collector, model.head, data.headAccessoryTexture, data.headAccessoryModel, packedLight, null, 3.0F);
            }
            if (data.faceAccessoryTexture != null) {
                renderSlot(poseStack, collector, model.head, data.faceAccessoryTexture, data.faceAccessoryModel, packedLight, null, 3.0F);
            }
            if (data.auraTexture != null) {
                renderSlot(poseStack, collector, model.body, data.auraTexture, data.auraModel, packedLight, null, 1.0F);
            }
            if (data.shoulderAccessoryTexture != null) {
                renderSlot(poseStack, collector, model.body, data.shoulderAccessoryTexture, data.shoulderAccessoryModel, packedLight, null, 1.0F);
            }
            if (data.torsoAccessoryTexture != null) {
                renderSlot(poseStack, collector, model.body, data.torsoAccessoryTexture, data.torsoAccessoryModel, packedLight, null, 1.0F);
            }
            if (data.legsAccessoryTexture != null) {
                renderSlot(poseStack, collector, model.body, data.legsAccessoryTexture, data.legsAccessoryModel, packedLight, null, 1.0F);
            }
            if (data.shieldTexture != null) {
                try {
                    Player player = mc.level != null ? mc.level.getPlayerByUUID(id) : null;
                    if (player != null) {
                        renderShield(poseStack, collector, model, player, data.shieldTexture, data.shieldModel, packedLight);
                    }
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
            return new Quaternionf().rotateX(a).rotateZ(b).rotateY(-b);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void renderSlot(PoseStack poseStack, SubmitNodeCollector collector, ModelPart anchor, String texture, String modelPath, int packedLight, Quaternionf rootSwing, float idleScale) {
        try {
            if (anchor == null || texture == null) return;
            Identifier tex = Identifier.tryParse(texture);
            if (tex == null) return;
            BbModel model = null;
            if (modelPath != null) {
                Identifier modelId = Identifier.tryParse(modelPath);
                if (modelId != null) {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc != null) model = BbModel.load(mc.getResourceManager(), modelId);
                }
            }
            RenderType type = RenderTypes.entityTranslucent(tex);
            if (model == null) {
                renderFallback(poseStack, collector, anchor, type, packedLight);
                return;
            }
            poseStack.pushPose();
            anchor.translateAndRotate(poseStack);
            renderModelContents(poseStack, collector, type, model, packedLight, animTime(), rootSwing, 0, idleScale);
            poseStack.popPose();
        } catch (Throwable ignored) {
        }
    }

    private static void renderShield(PoseStack poseStack, SubmitNodeCollector collector, PlayerModel parent, Player player, String texture, String modelPath, int packedLight) {
        try {
            if (player == null || texture == null) return;
            ItemStack off = player.getItemBySlot(EquipmentSlot.OFFHAND);
            ItemStack main = player.getItemBySlot(EquipmentSlot.MAINHAND);
            boolean offShield = off != null && off.getItem() == net.minecraft.world.item.Items.SHIELD;
            boolean mainShield = main != null && main.getItem() == net.minecraft.world.item.Items.SHIELD;
            if (!offShield && !mainShield) return;
            boolean useLeft = offShield;
            try {
                useLeft = offShield
                        ? player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT
                        : player.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT;
            } catch (Throwable ignored) {
            }
            ModelPart arm = useLeft ? parent.leftArm : parent.rightArm;
            if (arm == null) return;
            Identifier tex = Identifier.tryParse(texture);
            if (tex == null) return;
            BbModel bb = null;
            if (modelPath != null) {
                Identifier modelId = Identifier.tryParse(modelPath);
                if (modelId != null) {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc != null) bb = BbModel.load(mc.getResourceManager(), modelId);
                }
            }
            if (bb == null) {
                if (!kollegen$loggedNoModel) {
                    kollegen$loggedNoModel = true;
                    System.out.println("KOLLEGENDBG shield-model-missing " + modelPath);
                }
                return;
            }
            if (!kollegen$loggedRender) {
                kollegen$loggedRender = true;
                System.out.println("KOLLEGENDBG render-custom-shield left=" + useLeft + " center=" + bb.cx + "," + bb.cy + "," + bb.cz);
            }
            RenderType type = RenderTypes.entityTranslucent(tex);
            poseStack.pushPose();
            arm.translateAndRotate(poseStack);
            float mountYaw = 0.0F;
            try {
                if (bb.bones != null && !bb.bones.isEmpty() && bb.bones.get(0) != null) {
                    mountYaw = bb.bones.get(0).ry;
                }
            } catch (Throwable ignored) {
            }
            poseStack.translate(0.0F - bb.cx, 0.6F - bb.cy, -0.22F - bb.cz);
            float yawDeg = (useLeft ? -90.0F : 90.0F) - mountYaw;
            poseStack.mulPose(new Quaternionf().rotateY((float) Math.toRadians(yawDeg)));
            renderModelContents(poseStack, collector, type, bb, packedLight, animTime(), null, 0, 1.0F);
            poseStack.popPose();
        } catch (Throwable ignored) {
        }
    }

    private static double animTime() {
        try {
            return System.currentTimeMillis() / 1000.0;
        } catch (Throwable ignored) {
            return 0.0;
        }
    }

    private static void renderModelContents(PoseStack poseStack, SubmitNodeCollector collector, RenderType type, BbModel model, int packedLight, double timeSec, Quaternionf rootSwing, int depth, float idleScale) {
        for (BbModel.Bone bone : model.bones) {
            renderBone(poseStack, collector, type, model, bone, packedLight, timeSec, depth == 0 ? rootSwing : null, depth, idleScale);
        }
        if (!model.loose.isEmpty()) {
            submitCubes(poseStack, collector, type, model, model.loose, packedLight, timeSec);
        }
    }

    private static void renderBone(PoseStack poseStack, SubmitNodeCollector collector, RenderType type, BbModel model, BbModel.Bone bone, int packedLight, double timeSec, Quaternionf rootSwing, int depth, float idleScale) {
        poseStack.pushPose();
        try {
            float[] animR = null;
            float[] animP = null;
            float[] animS = null;
            if (model.animLength > 0.0F && bone.name != null && !bone.name.isEmpty()) {
                BbModel.AnimBone ab = model.anims.get(bone.name);
                if (ab != null) {
                    float[] r = new float[3];
                    if (BbModel.sampleTrack(ab.rTimes, ab.rVals, ab.rInterp, timeSec, model.animLength, r)) {
                        r[0] *= idleScale;
                        r[1] *= idleScale;
                        r[2] *= idleScale;
                        animR = r;
                    }
                    float[] p = new float[3];
                    if (BbModel.sampleTrack(ab.pTimes, ab.pVals, ab.pInterp, timeSec, model.animLength, p)) {
                        p[0] *= idleScale;
                        p[1] *= idleScale;
                        p[2] *= idleScale;
                        animP = p;
                    }
                    float[] s = new float[]{1.0F, 1.0F, 1.0F};
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
                    poseStack.mulPose(new Quaternionf().rotationZYX(
                            (float) Math.toRadians(rz),
                            (float) Math.toRadians(ry),
                            (float) Math.toRadians(rx)));
                }
                if (hasS) poseStack.scale(animS[0], animS[1], animS[2]);
                poseStack.translate(-ox, -oy, -oz);
            }
            if (rootSwing != null) {
                try {
                    poseStack.mulPose(rootSwing);
                } catch (Throwable ignored) {
                }
            }
            List<BbModel.Cube> flat = new ArrayList<>();
            for (BbModel.Cube cube : bone.cubes) {
                if (!cube.hasRotation()) flat.add(cube);
            }
            if (!flat.isEmpty()) submitCubes(poseStack, collector, type, model, flat, packedLight, timeSec);
            for (BbModel.Cube cube : bone.cubes) {
                if (!cube.hasRotation()) continue;
                poseStack.pushPose();
                try {
                    applyPivot(poseStack, BbModel.pivotX(cube.ox), BbModel.pivotY(cube.oy), BbModel.pivotZ(cube.oz), cube.rx, cube.ry, cube.rz);
                    List<BbModel.Cube> single = new ArrayList<>();
                    single.add(cube);
                    submitCubes(poseStack, collector, type, model, single, packedLight, timeSec);
                } catch (Throwable ignored) {
                }
                poseStack.popPose();
            }
            for (BbModel.Bone child : bone.children) {
                renderBone(poseStack, collector, type, model, child, packedLight, timeSec, null, depth + 1, idleScale);
            }
        } catch (Throwable ignored) {
        }
        poseStack.popPose();
    }

    private static void submitCubes(PoseStack poseStack, SubmitNodeCollector collector, RenderType type, BbModel model, List<BbModel.Cube> cubes, int packedLight, double timeSec) {
        int lensFrame = (int) Math.floor(timeSec * 2.0) % 4;
        if (lensFrame < 0) lensFrame += 4;
        final int frame = lensFrame;
        try {
            collector.submitCustomGeometry(poseStack, type, (pose, vc) -> {
                try {
                    Matrix4f mat = pose.pose();
                    Vector3f tmpPos = new Vector3f();
                    Vector3f tmpN = new Vector3f();
                    for (BbModel.Cube cube : cubes) {
                        float shift = 0.0F;
                        if (cube.frameW > 0.0F && model != null && model.texWidth > 0.0F) {
                            shift = (frame * cube.frameW / model.texWidth) % 1.0F;
                        }
                        for (BbModel.Face face : cube.faces) {
                            float[][] corners = BbModel.cornersFor(cube, face.dir);
                            float[][] uvs = BbModel.uvsFor(face);
                            if (shift != 0.0F) {
                                for (int j = 0; j < 4; j++) {
                                    uvs[j][0] = (uvs[j][0] + shift) % 1.0F;
                                }
                            }
                            float[] n = BbModel.normalFor(face.dir);
                            Vector3f nn = pose.transformNormal(n[0], n[1], n[2], tmpN);
                            float nx = nn.x();
                            float ny = nn.y();
                            float nz = nn.z();
                            for (int i = 0; i < 4; i++) {
                                float[] c = corners[i];
                                float[] uv = uvs[i];
                                Vector3f p = mat.transformPosition(c[0], c[1], c[2], tmpPos);
                                vc.addVertex(p.x(), p.y(), p.z(), -1, uv[0], uv[1], OverlayTexture.NO_OVERLAY, packedLight, nx, ny, nz);
                            }
                        }
                    }
                } catch (Throwable ignored) {
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private static void applyPivot(PoseStack poseStack, float px, float py, float pz, float rxDeg, float ryDeg, float rzDeg) {
        if (rxDeg == 0.0F && ryDeg == 0.0F && rzDeg == 0.0F) return;
        float rx = (float) Math.toRadians(rxDeg);
        float ry = (float) Math.toRadians(ryDeg);
        float rz = (float) Math.toRadians(rzDeg);
        poseStack.translate(px, py, pz);
        poseStack.mulPose(new Quaternionf().rotationZYX(rz, ry, rx));
        poseStack.translate(-px, -py, -pz);
    }

    private static void renderFallback(PoseStack poseStack, SubmitNodeCollector collector, ModelPart anchor, RenderType type, int packedLight) {
        try {
            poseStack.pushPose();
            anchor.translateAndRotate(poseStack);
            poseStack.translate(0.0F, 0.5F, 0.1875F);
            collector.submitCustomGeometry(poseStack, type, (pose, vc) -> {
                try {
                    Matrix4f mat = pose.pose();
                    Vector3f tmp = new Vector3f();
                    Vector3f p0 = mat.transformPosition(-0.3125F, 0.0F, 0.0F, new Vector3f());
                    Vector3f p1 = mat.transformPosition(0.3125F, 0.0F, 0.0F, new Vector3f());
                    Vector3f p2 = mat.transformPosition(0.3125F, 1.0F, 0.0F, new Vector3f());
                    Vector3f p3 = mat.transformPosition(-0.3125F, 1.0F, 0.0F, new Vector3f());
                    Vector3f nn = pose.transformNormal(0.0F, 0.0F, 1.0F, tmp);
                    vc.addVertex(p0.x(), p0.y(), p0.z(), -1, 0.0F, 0.0F, OverlayTexture.NO_OVERLAY, packedLight, nn.x(), nn.y(), nn.z());
                    vc.addVertex(p1.x(), p1.y(), p1.z(), -1, 1.0F, 0.0F, OverlayTexture.NO_OVERLAY, packedLight, nn.x(), nn.y(), nn.z());
                    vc.addVertex(p2.x(), p2.y(), p2.z(), -1, 1.0F, 1.0F, OverlayTexture.NO_OVERLAY, packedLight, nn.x(), nn.y(), nn.z());
                    vc.addVertex(p3.x(), p3.y(), p3.z(), -1, 0.0F, 1.0F, OverlayTexture.NO_OVERLAY, packedLight, nn.x(), nn.y(), nn.z());
                } catch (Throwable ignored) {
                }
            });
            poseStack.popPose();
        } catch (Throwable ignored) {
        }
    }
}
