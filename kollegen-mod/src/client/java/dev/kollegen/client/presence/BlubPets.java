package dev.kollegen.client.presence;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Quaternionfc;

public final class BlubPets {
    private static final Identifier MODEL_ID =
            Identifier.fromNamespaceAndPath("kollegen", "models/item/blub.json");
    private static final Identifier TEX_ID =
            Identifier.fromNamespaceAndPath("kollegen", "textures/item/blub.png");

    private BlubPets() {}

    public static boolean isBlubDisplay(Entity entity) {
        try {
            return entity instanceof Display.ItemDisplay;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void applyDisplayPose(Entity entity, float sx, float sy, float sz, float yaw) {
        try {
            if (!(entity instanceof Display.ItemDisplay)) return;
            EntityDataAccessor<org.joml.Vector3fc> scaleAccessor = dev.kollegen.client.mixin.DisplayAccessor.kollegen$getScaleAccessor();
            EntityDataAccessor<org.joml.Quaternionfc> rotAccessor = dev.kollegen.client.mixin.DisplayAccessor.kollegen$getLeftRotationAccessor();
            if (scaleAccessor == null || rotAccessor == null) return;
            entity.getEntityData().set(scaleAccessor, new org.joml.Vector3f(sx, sy, sz));
            entity.getEntityData().set(rotAccessor, new org.joml.Quaternionf().rotateY(yaw));
        } catch (Throwable ignored) {
        }
    }

    public static void renderBlub(Minecraft mc, PoseStack poseStack, SubmitNodeCollector collector,
            LevelRenderState state, Vec3 cam, Entity entity, CosmeticData d) {
        try {
            if (mc == null || mc.level == null || entity == null || cam == null || collector == null) return;
            BbModel model = BbModel.load(mc.getResourceManager(), MODEL_ID);
            if (model == null || model.loose.isEmpty()) return;
            float loY = Float.MAX_VALUE;
            float hiY = -Float.MAX_VALUE;
            for (BbModel.Cube cube : model.loose) {
                if (cube.minY < loY) loY = cube.minY;
                if (cube.maxY > hiY) hiY = cube.maxY;
            }
            if (!(loY <= hiY)) {
                loY = 0.0F;
                hiY = 0.7F;
            }
            Vec3 pos = entity.position();
            float sx = 1.0F;
            float sy = 1.0F;
            float sz = 1.0F;
            Quaternionf rot = null;
            try {
                EntityDataAccessor<Vector3fc> sc = dev.kollegen.client.mixin.DisplayAccessor.kollegen$getScaleAccessor();
                EntityDataAccessor<Quaternionfc> rt = dev.kollegen.client.mixin.DisplayAccessor.kollegen$getLeftRotationAccessor();
                if (sc != null) {
                    Vector3fc s = entity.getEntityData().get(sc);
                    if (s != null) {
                        sx = s.x();
                        sy = s.y();
                        sz = s.z();
                    }
                }
                if (rt != null) {
                    Quaternionfc q = entity.getEntityData().get(rt);
                    if (q != null) rot = new Quaternionf(q);
                }
            } catch (Throwable ignored) {
            }
            final float fsx = sx;
            final float fsy = sy;
            final float fsz = sz;
            final Quaternionf frot = rot;
            RenderType type = RenderTypes.entityTranslucent(TEX_ID);
            poseStack.pushPose();
            try {
                poseStack.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);
                if (frot != null) poseStack.mulPose(frot);
                poseStack.scale(fsx, fsy, fsz);
                poseStack.scale(1.0F, -1.0F, 1.0F);
                poseStack.translate(-model.cx, hiY, -model.cz);
                collector.submitCustomGeometry(poseStack, type, (pose, vc) -> {
                    try {
                        Matrix4f mat = pose.pose();
                        Vector3f tmpPos = new Vector3f();
                        Vector3f tmpN = new Vector3f();
                        for (BbModel.Cube cube : model.loose) {
                            for (BbModel.Face face : cube.faces) {
                                float[][] corners = BbModel.cornersFor(cube.minX, cube.minY, cube.maxX, cube.maxY, cube.minZ, cube.maxZ, face.dir);
                                float[][] uvs = BbModel.uvsFor(face);
                                float[] n = BbModel.normalFor(face.dir);
                                Vector3f nn = pose.transformNormal(n[0], n[1], n[2], tmpN);
                                for (int i = 0; i < 4; i++) {
                                    float[] c = corners[i];
                                    float[] uv = uvs[i];
                                    Vector3f pv = mat.transformPosition(c[0], c[1], c[2], tmpPos);
                                    vc.addVertex(pv.x(), pv.y(), pv.z(), -1, uv[0], uv[1], OverlayTexture.NO_OVERLAY, 15728880, nn.x(), nn.y(), nn.z());
                                }
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                });
            } catch (Throwable ignored) {
            }
            poseStack.popPose();
            if (d != null && d.petName != null && !d.petName.isEmpty()) {
                try {
                    int nameColor = CosmeticText.parseColor(d.petNameColor, 0xFFFFFF);
                    Component comp = Component.literal(d.petName)
                            .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(nameColor)));
                    double h = (hiY - loY) * Math.max(0.1, fsy);
                    Vec3 anchor = new Vec3(pos.x - cam.x, pos.y + h + 0.4 - cam.y, pos.z - cam.z);
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
}
