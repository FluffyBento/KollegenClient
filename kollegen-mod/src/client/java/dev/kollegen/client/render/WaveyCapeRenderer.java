package dev.kollegen.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.kollegen.client.mods.modules.Visual;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class WaveyCapeRenderer {
    private static final Map<Identifier, RenderType> TYPE_CACHE = new ConcurrentHashMap<>();
    private static final int SEGMENTS = 16;
    private static final float CAPE_WIDTH = 10.0F / 16.0F; // 0.625 blocks
    private static final float CAPE_THICKNESS = 1.0F / 16.0F; // 0.0625 blocks
    private static final float SEGMENT_HEIGHT = 1.0F / 16.0F; // 0.0625 blocks per segment

    private static RenderType getRenderType(Identifier id) {
        if (id == null) return null;
        return TYPE_CACHE.computeIfAbsent(id, RenderTypes::entityTranslucent);
    }

    public static void renderWaveyCape(PoseStack poseStack, SubmitNodeCollector collector, Identifier texture,
                                      int packedLight, AvatarRenderState state, boolean hasArmor) {
        if (texture == null || state == null || collector == null) return;
        RenderType renderType = getRenderType(texture);
        if (renderType == null) return;

        poseStack.pushPose();

        // Offset behind player body (account for chest armor)
        float zOffset = hasArmor ? 0.165F : 0.125F;
        float yOffset = hasArmor ? -0.02F : 0.0F;
        poseStack.translate(0.0F, yOffset, zOffset);

        // Calculate swing angles from state
        float leanX = (6.0F + state.capeLean / 2.0F + state.capeFlap) * (float) (Math.PI / 180.0);
        float leanZ = (state.capeLean2 / 2.0F) * (float) (Math.PI / 180.0);
        leanX *= 0.75F;
        leanZ *= 0.75F;

        // Base rotation
        Quaternionf baseRot = new Quaternionf().rotateZ(leanZ).rotateX(leanX);
        poseStack.rotate(baseRot);

        float waveStrength = Visual.WaveyCapes.waveStrength;
        double time = System.currentTimeMillis() * 0.004;

        collector.submitCustomGeometry(poseStack, renderType, (pose, vc) -> {
            Matrix4f mat = pose.pose();
            Vector3f tmpPos = new Vector3f();

            float halfW = CAPE_WIDTH / 2.0F;
            float halfT = CAPE_THICKNESS / 2.0F;

            // Compute positions along the cape spine
            float[] spineY = new float[SEGMENTS + 1];
            float[] spineZ = new float[SEGMENTS + 1];

            spineY[0] = 0.0F;
            spineZ[0] = 0.0F;

            float curY = 0.0F;
            float curZ = 0.0F;

            float runFactor = Math.min(2.5F, state.capeLean / 20.0F);

            for (int i = 0; i < SEGMENTS; i++) {
                // Wave ripple moving down the cape
                float wave = (float) Math.sin(time * 3.8 - i * 0.42) * 0.012F * waveStrength * (1.0F + runFactor);
                // Gravity / sag bend
                float sag = (i * 0.004F) * (1.0F - Math.min(1.0F, state.capeLean / 40.0F));
                float segmentAngle = (wave + sag);

                curY += SEGMENT_HEIGHT * (float) Math.cos(segmentAngle);
                curZ += SEGMENT_HEIGHT * (float) Math.sin(segmentAngle);

                spineY[i + 1] = curY;
                spineZ[i + 1] = curZ;
            }

            // Standard 64x32 cape UV mapping
            // Top face: [1, 11] x [0, 1] / (64, 32)
            // Bottom face: [11, 21] x [0, 1] / (64, 32)
            // Front face (towards player): [1, 11] x [1, 17] / (64, 32)
            // Back face (outwards): [12, 22] x [1, 17] / (64, 32)
            // Left edge: [0, 1] x [1, 17] / (64, 32)
            // Right edge: [11, 12] x [1, 17] / (64, 32)

            float uFrontMin = 1.0F / 64.0F, uFrontMax = 11.0F / 64.0F;
            float uBackMin = 12.0F / 64.0F, uBackMax = 22.0F / 64.0F;
            float uLeftMin = 0.0F / 64.0F, uLeftMax = 1.0F / 64.0F;
            float uRightMin = 11.0F / 64.0F, uRightMax = 12.0F / 64.0F;

            for (int i = 0; i < SEGMENTS; i++) {
                float y0 = spineY[i];
                float z0 = spineZ[i];
                float y1 = spineY[i + 1];
                float z1 = spineZ[i + 1];

                float v0 = (1.0F + i) / 32.0F;
                float v1 = (1.0F + i + 1) / 32.0F;

                // Back face (facing outwards, normal (0, 0, 1))
                emitQuad(vc, mat,
                        -halfW, y0, z0 + halfT, uBackMax, v0,
                        halfW, y0, z0 + halfT, uBackMin, v0,
                        halfW, y1, z1 + halfT, uBackMin, v1,
                        -halfW, y1, z1 + halfT, uBackMax, v1,
                        packedLight, 0.0F, 0.0F, 1.0F, tmpPos);

                // Front face (facing player, normal (0, 0, -1))
                emitQuad(vc, mat,
                        halfW, y0, z0 - halfT, uFrontMax, v0,
                        -halfW, y0, z0 - halfT, uFrontMin, v0,
                        -halfW, y1, z1 - halfT, uFrontMin, v1,
                        halfW, y1, z1 - halfT, uFrontMax, v1,
                        packedLight, 0.0F, 0.0F, -1.0F, tmpPos);

                // Left edge (normal (-1, 0, 0))
                emitQuad(vc, mat,
                        -halfW, y0, z0 - halfT, uLeftMin, v0,
                        -halfW, y0, z0 + halfT, uLeftMax, v0,
                        -halfW, y1, z1 + halfT, uLeftMax, v1,
                        -halfW, y1, z1 - halfT, uLeftMin, v1,
                        packedLight, -1.0F, 0.0F, 0.0F, tmpPos);

                // Right edge (normal (1, 0, 0))
                emitQuad(vc, mat,
                        halfW, y0, z0 + halfT, uRightMin, v0,
                        halfW, y0, z0 - halfT, uRightMax, v0,
                        halfW, y1, z1 - halfT, uRightMax, v1,
                        halfW, y1, z1 + halfT, uRightMin, v1,
                        packedLight, 1.0F, 0.0F, 0.0F, tmpPos);
            }

            // Top face (normal (0, -1, 0))
            emitQuad(vc, mat,
                    -halfW, spineY[0], spineZ[0] - halfT, 1.0F / 64.0F, 0.0F / 32.0F,
                    halfW, spineY[0], spineZ[0] - halfT, 11.0F / 64.0F, 0.0F / 32.0F,
                    halfW, spineY[0], spineZ[0] + halfT, 11.0F / 64.0F, 1.0F / 32.0F,
                    -halfW, spineY[0], spineZ[0] + halfT, 1.0F / 64.0F, 1.0F / 32.0F,
                    packedLight, 0.0F, -1.0F, 0.0F, tmpPos);

            // Bottom face (normal (0, 1, 0))
            emitQuad(vc, mat,
                    -halfW, spineY[SEGMENTS], spineZ[SEGMENTS] + halfT, 11.0F / 64.0F, 0.0F / 32.0F,
                    halfW, spineY[SEGMENTS], spineZ[SEGMENTS] + halfT, 21.0F / 64.0F, 0.0F / 32.0F,
                    halfW, spineY[SEGMENTS], spineZ[SEGMENTS] - halfT, 21.0F / 64.0F, 1.0F / 32.0F,
                    -halfW, spineY[SEGMENTS], spineZ[SEGMENTS] - halfT, 11.0F / 64.0F, 1.0F / 32.0F,
                    packedLight, 0.0F, 1.0F, 0.0F, tmpPos);
        });

        poseStack.popPose();
    }

    private static void emitQuad(VertexConsumer vc, Matrix4f mat,
                                 float x1, float y1, float z1, float u1, float v1,
                                 float x2, float y2, float z2, float u2, float v2,
                                 float x3, float y3, float z3, float u3, float v3,
                                 float x4, float y4, float z4, float u4, float v4,
                                 int packedLight, float nx, float ny, float nz, Vector3f tmpPos) {
        addVertex(vc, mat, x1, y1, z1, u1, v1, packedLight, nx, ny, nz, tmpPos);
        addVertex(vc, mat, x2, y2, z2, u2, v2, packedLight, nx, ny, nz, tmpPos);
        addVertex(vc, mat, x3, y3, z3, u3, v3, packedLight, nx, ny, nz, tmpPos);
        addVertex(vc, mat, x4, y4, z4, u4, v4, packedLight, nx, ny, nz, tmpPos);
    }

    private static void addVertex(VertexConsumer vc, Matrix4f mat, float x, float y, float z,
                                  float u, float v, int packedLight, float nx, float ny, float nz, Vector3f tmpPos) {
        Vector3f p = mat.transformPosition(x, y, z, tmpPos);
        vc.addVertex(p.x(), p.y(), p.z(), -1, u, v, OverlayTexture.NO_OVERLAY, packedLight, nx, ny, nz);
    }
}
