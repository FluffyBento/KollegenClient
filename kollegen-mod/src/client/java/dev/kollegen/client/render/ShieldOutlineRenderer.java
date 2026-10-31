package dev.kollegen.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.shapes.Shapes;
import org.joml.Matrix4f;

public final class ShieldOutlineRenderer {

    private ShieldOutlineRenderer() {}

    public static void renderOutline(PoseStack poseStack, SubmitNodeCollector collector,
                                     float minX, float minY, float minZ,
                                     float maxX, float maxY, float maxZ,
                                     int color) {
        if (color == 0) return;
        int a = (color >> 24) & 0xFF;
        if (a == 0) a = 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        float offset = 0.012F;
        float x0 = minX - offset;
        float x1 = maxX + offset;
        float y0 = minY - offset;
        float y1 = maxY + offset;
        float z0 = minZ - offset;
        float z1 = maxZ + offset;

        float w = 0.095F;

        final int fa = a;
        final int fr = r;
        final int fg = g;
        final int fb = b;

        collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, vc) -> {
            Matrix4f mat = pose.pose();

            emitBoxQuads(vc, mat, x0, y0, z0, x1, y0 + w, z1, fr, fg, fb, fa);
            emitBoxQuads(vc, mat, x0, y1 - w, z0, x1, y1, z1, fr, fg, fb, fa);
            emitBoxQuads(vc, mat, x0, y0 + w, z0, x0 + w, y1 - w, z1, fr, fg, fb, fa);
            emitBoxQuads(vc, mat, x1 - w, y0 + w, z0, x1, y1 - w, z1, fr, fg, fb, fa);
        });

        try {
            collector.submitShapeOutline(
                    poseStack,
                    Shapes.box(x0, y0, z0, x1, y1, z1),
                    RenderTypes.lines(),
                    color,
                    3.5F,
                    false
            );
        } catch (Throwable ignored) {
        }
    }

    private static void emitBoxQuads(VertexConsumer vc, Matrix4f mat,
                                     float x0, float y0, float z0,
                                     float x1, float y1, float z1,
                                     int r, int g, int b, int a) {
        emitDoubleSidedQuad(vc, mat, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, r, g, b, a);
        emitDoubleSidedQuad(vc, mat, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r, g, b, a);
        emitDoubleSidedQuad(vc, mat, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, r, g, b, a);
        emitDoubleSidedQuad(vc, mat, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, r, g, b, a);
        emitDoubleSidedQuad(vc, mat, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, r, g, b, a);
        emitDoubleSidedQuad(vc, mat, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, g, b, a);
    }

    private static void emitDoubleSidedQuad(VertexConsumer vc, Matrix4f mat,
                                            float x1, float y1, float z1,
                                            float x2, float y2, float z2,
                                            float x3, float y3, float z3,
                                            float x4, float y4, float z4,
                                            int r, int g, int b, int a) {
        vc.addVertex(mat, x1, y1, z1).setColor(r, g, b, a);
        vc.addVertex(mat, x2, y2, z2).setColor(r, g, b, a);
        vc.addVertex(mat, x3, y3, z3).setColor(r, g, b, a);
        vc.addVertex(mat, x4, y4, z4).setColor(r, g, b, a);

        vc.addVertex(mat, x4, y4, z4).setColor(r, g, b, a);
        vc.addVertex(mat, x3, y3, z3).setColor(r, g, b, a);
        vc.addVertex(mat, x2, y2, z2).setColor(r, g, b, a);
        vc.addVertex(mat, x1, y1, z1).setColor(r, g, b, a);
    }
}
