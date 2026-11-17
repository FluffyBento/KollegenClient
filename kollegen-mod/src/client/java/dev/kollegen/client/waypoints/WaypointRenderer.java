package dev.kollegen.client.waypoints;

import dev.kollegen.client.mods.modules.WaypointsModule;
import dev.kollegen.client.ui.Glass;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

public class WaypointRenderer implements DebugRenderer.SimpleDebugRenderer {
    public static final WaypointRenderer INSTANCE = new WaypointRenderer();

    private WaypointRenderer() {
    }

    @Override
    public void emitGizmos(double cameraX, double cameraY, double cameraZ, DebugValueAccess access, Frustum frustum, float tickDelta) {
        if (WaypointsModule.INSTANCE == null || !WaypointsModule.INSTANCE.enabled || !WaypointsModule.INSTANCE.showInWorld.value) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) return;

        List<Waypoint> list = WaypointManager.getVisibleForCurrentDimension();
        if (list.isEmpty()) return;

        double px = mc.player.getX();
        double py = mc.player.getY();
        double pz = mc.player.getZ();

        for (Waypoint wp : list) {
            int color = wp.getEffectiveColor();
            double dist = wp.distanceTo(px, py, pz);

            if (WaypointsModule.INSTANCE.beaconBeam.value) {
                Gizmos.line(
                        new Vec3(wp.x, Math.max(-64.0, wp.y - 64.0), wp.z),
                        new Vec3(wp.x, Math.min(320.0, wp.y + 256.0), wp.z),
                        color,
                        2.5f
                );
            }

            Gizmos.cuboid(
                    new AABB(wp.x - 0.35, wp.y, wp.z - 0.35, wp.x + 0.35, wp.y + 0.7, wp.z + 0.35),
                    GizmoStyle.strokeAndFill(color, 2.0f, Glass.a(color, 0.4f))
            );

            String label = wp.name + (WaypointsModule.INSTANCE.showDistance.value ? " (" + wp.formatDistance(dist) + ")" : "");
            BlockPos bp = new BlockPos((int) Math.floor(wp.x), (int) Math.floor(wp.y + 1.2), (int) Math.floor(wp.z));
            Gizmos.billboardTextOverBlock(label, bp, 0, color, 1.0f);
        }
    }

    public static void renderHudMarkers(GuiGraphicsExtractor g, float tickDelta) {
        if (WaypointsModule.INSTANCE == null || !WaypointsModule.INSTANCE.enabled || !WaypointsModule.INSTANCE.showInWorld.value) {
            return;
        }
        if (!WaypointsModule.INSTANCE.hudPins.value) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null || mc.gameRenderer == null) return;
        Camera camera = mc.gameRenderer.mainCamera();
        if (camera == null) return;

        List<Waypoint> list = WaypointManager.getVisibleForCurrentDimension();
        if (list.isEmpty()) return;

        Vec3 camPos = camera.position();
        Quaternionf camRot = camera.rotation();
        if (camRot == null) return;

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        float fovRad = (float) Math.toRadians(mc.options.fov().get());
        float halfH = (float) Math.tan(fovRad / 2.0f);
        float halfW = halfH * ((float) sw / (float) sh);

        Vector3f rel = new Vector3f();

        for (Waypoint wp : list) {
            rel.set(
                    (float) (wp.x - camPos.x),
                    (float) ((wp.y + 1.2) - camPos.y),
                    (float) (wp.z - camPos.z)
            );
            camRot.transformInverse(rel);

            if (rel.z >= -0.25f) {
                continue;
            }

            float forward = -rel.z;
            float ndcX = rel.x / (forward * halfW);
            float ndcY = rel.y / (forward * halfH);

            if (ndcX < -1.05f || ndcX > 1.05f || ndcY < -1.05f || ndcY > 1.05f) {
                continue;
            }

            double dist = wp.distanceTo(camPos.x, camPos.y, camPos.z);
            if (dist < 2.5) continue;

            float sx = (sw / 2.0f) * (1.0f + ndcX);
            float sy = (sh / 2.0f) * (1.0f - ndcY);

            int color = wp.getEffectiveColor();
            String label = wp.name + (WaypointsModule.INSTANCE.showDistance.value ? " (" + wp.formatDistance(dist) + ")" : "");
            int tw = mc.font.width(label);
            int bw = tw + 10;
            int bh = 14;
            int bx = (int) Math.round(sx - bw / 2.0f);
            int by = (int) Math.round(sy - 18.0f);

            Glass.fillRound(g, bx, by, bw, bh, 5, 0xCC11141A);
            Glass.ring(g, bx, by, bw, bh, 5, 0.5f, 0x44FFFFFF);
            Glass.disc(g, sx, sy, 3.5f, color);
            Glass.disc(g, sx, sy, 1.5f, 0xFFFFFFFF);
            Glass.text(g, mc.font, label, bx + 5, by + 3, color, true);
        }
    }
}
