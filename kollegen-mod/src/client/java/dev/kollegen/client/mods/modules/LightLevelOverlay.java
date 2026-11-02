package dev.kollegen.client.mods.modules;

import dev.kollegen.client.mods.BooleanSetting;
import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.KeybindSetting;
import dev.kollegen.client.mods.ModeSetting;
import dev.kollegen.client.mods.Module;
import dev.kollegen.client.mods.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public class LightLevelOverlay extends Module implements DebugRenderer.SimpleDebugRenderer {
    public static LightLevelOverlay INSTANCE;

    public final ModeSetting mode = new ModeSetting(
            "Anzeigemodus",
            "Filtert die angezeigten Lichtwerte.",
            new String[]{"Alle Level (0-15)", "Dunkel (<= 7)", "Nur Monster-Spawn (0)"},
            0, null
    );

    public final SliderSetting range = new SliderSetting(
            "Suchradius",
            "Radius um den Spieler in Blöcken.",
            12.0, 4.0, 24.0, 1.0,
            v -> Math.round(v) + " Blöcke", null
    );

    public final BooleanSetting showNumbers = new BooleanSetting(
            "Zahlen anzeigen",
            "Zeigt die genaue Lichtlevel-Zahl flach auf dem Boden an.",
            false
    );

    public final SliderSetting lineWidth = new SliderSetting(
            "Linienstärke",
            "Dicke des Rasters auf dem Boden.",
            1.5, 0.5, 4.0, 0.5,
            v -> String.format(java.util.Locale.ROOT, "%.1f px", v), null
    );

    public final BooleanSetting includeSky = new BooleanSetting(
            "Tageslicht einbeziehen",
            "Berücksichtigt Sonnenlicht/Himmelslicht.",
            false
    );

    public final KeybindSetting hotkey = new KeybindSetting(
            "Hotkey",
            "Taste zum Umschalten des Lichtlevel-Overlays.",
            -1
    );

    public LightLevelOverlay() {
        super("lightleveloverlay", "Licht-Level Boden", "Zeigt Lichtlevel-Raster direkt auf Blöcken am Boden an.", Category.WORLD);
        INSTANCE = this;
        add(mode);
        add(range);
        add(showNumbers);
        add(lineWidth);
        add(includeSky);
        add(hotkey);
    }

    public static boolean isOverlayEnabled() {
        return INSTANCE != null && INSTANCE.enabled;
    }

    @Override
    public void onKey() {
        this.enabled = !this.enabled;
        if (this.enabled) this.onEnable();
        else this.onDisable();
    }

    private static void drawDigit(double minX, double maxX, double minZ, double maxZ, double y, int digit, int color, float width) {
        double midZ = (minZ + maxZ) * 0.5;
        boolean top = (digit != 1 && digit != 4);
        boolean topLeft = (digit != 1 && digit != 2 && digit != 3 && digit != 7);
        boolean topRight = (digit != 5 && digit != 6);
        boolean mid = (digit != 0 && digit != 1 && digit != 7);
        boolean botLeft = (digit == 0 || digit == 2 || digit == 6 || digit == 8);
        boolean botRight = (digit != 2);
        boolean bot = (digit != 1 && digit != 4 && digit != 7);

        if (top) Gizmos.line(new Vec3(minX, y, minZ), new Vec3(maxX, y, minZ), color, width);
        if (topLeft) Gizmos.line(new Vec3(minX, y, minZ), new Vec3(minX, y, midZ), color, width);
        if (topRight) Gizmos.line(new Vec3(maxX, y, minZ), new Vec3(maxX, y, midZ), color, width);
        if (mid) Gizmos.line(new Vec3(minX, y, midZ), new Vec3(maxX, y, midZ), color, width);
        if (botLeft) Gizmos.line(new Vec3(minX, y, midZ), new Vec3(minX, y, maxZ), color, width);
        if (botRight) Gizmos.line(new Vec3(maxX, y, midZ), new Vec3(maxX, y, maxZ), color, width);
        if (bot) Gizmos.line(new Vec3(minX, y, maxZ), new Vec3(maxX, y, maxZ), color, width);
    }

    @Override
    public void emitGizmos(double cameraX, double cameraY, double cameraZ, DebugValueAccess access, Frustum frustum, float tickDelta) {
        if (!isOverlayEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) return;

        BlockPos playerPos = player.blockPosition();
        int r = (int) Math.round(this.range.value);
        int rSq = r * r;
        int m = this.mode.index;
        boolean sky = this.includeSky.value;
        boolean numbers = this.showNumbers.value;
        float lw = (float) this.lineWidth.value;
        float diagW = Math.max(0.4f, lw * 0.75f);
        Vec3 eyePos = player.getEyePosition(tickDelta);

        BlockPos.MutableBlockPos groundPos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos abovePos = new BlockPos.MutableBlockPos();

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > rSq) continue;

                for (int dy = -4; dy <= 4; dy++) {
                    groundPos.set(playerPos.getX() + dx, playerPos.getY() + dy, playerPos.getZ() + dz);
                    abovePos.set(groundPos.getX(), groundPos.getY() + 1, groundPos.getZ());

                    BlockState ground = level.getBlockState(groundPos);
                    if (ground.isAir()) continue;
                    VoxelShape shape = ground.getCollisionShape(level, groundPos);
                    if (shape.isEmpty()) continue;

                    BlockState above = level.getBlockState(abovePos);
                    if (!above.isAir() && !above.getCollisionShape(level, abovePos).isEmpty()) continue;

                    if (frustum != null && !frustum.isVisible(new AABB(groundPos.getX(), groundPos.getY(), groundPos.getZ(), groundPos.getX() + 1, groundPos.getY() + 2, groundPos.getZ() + 1))) {
                        continue;
                    }

                    double topY = shape.max(Direction.Axis.Y);
                    double gy = groundPos.getY() + topY + 0.003;
                    double gx = groundPos.getX();
                    double gz = groundPos.getZ();

                    Vec3 targetCenter = new Vec3(gx + 0.5, gy + 0.1, gz + 0.5);
                    BlockHitResult hit = level.clip(new ClipContext(
                            eyePos,
                            targetCenter,
                            ClipContext.Block.COLLIDER,
                            ClipContext.Fluid.NONE,
                            player
                    ));
                    if (hit.getType() != HitResult.Type.MISS) {
                        BlockPos hitPos = hit.getBlockPos();
                        if (!hitPos.equals(groundPos) && !hitPos.equals(abovePos)) {
                            continue;
                        }
                    }

                    int blockLight = level.getBrightness(LightLayer.BLOCK, abovePos);
                    int lightValue = blockLight;

                    if (sky) {
                        int skyLight = level.getBrightness(LightLayer.SKY, abovePos);
                        lightValue = Math.max(blockLight, skyLight);
                    }

                    if (m == 1 && lightValue > 7) continue;
                    if (m == 2 && lightValue > 0) continue;

                    int color;
                    if (lightValue == 0) {
                        color = 0xFFFF2222;
                    } else if (lightValue <= 7) {
                        color = 0xFFFFCC00;
                    } else {
                        color = 0xFF55FF55;
                    }

                    Gizmos.line(new Vec3(gx, gy, gz), new Vec3(gx + 1.0, gy, gz), color, lw);
                    Gizmos.line(new Vec3(gx + 1.0, gy, gz), new Vec3(gx + 1.0, gy, gz + 1.0), color, lw);
                    Gizmos.line(new Vec3(gx + 1.0, gy, gz + 1.0), new Vec3(gx, gy, gz + 1.0), color, lw);
                    Gizmos.line(new Vec3(gx, gy, gz + 1.0), new Vec3(gx, gy, gz), color, lw);

                    Gizmos.line(new Vec3(gx, gy, gz), new Vec3(gx + 1.0, gy, gz + 1.0), color, diagW);
                    Gizmos.line(new Vec3(gx + 1.0, gy, gz), new Vec3(gx, gy, gz + 1.0), color, diagW);

                    if (numbers) {
                        double cy = gy + 0.005;
                        double cx = gx + 0.5;
                        double cz = gz + 0.5;
                        double h = 0.32;
                        if (lightValue < 10) {
                            double w = 0.20;
                            drawDigit(cx - w * 0.5, cx + w * 0.5, cz - h * 0.5, cz + h * 0.5, cy, lightValue, color, lw);
                        } else {
                            double leftX = cx - 0.18;
                            double rightX = cx + 0.03;
                            drawDigit(leftX, leftX + 0.15, cz - h * 0.5, cz + h * 0.5, cy, lightValue / 10, color, lw);
                            drawDigit(rightX, rightX + 0.15, cz - h * 0.5, cz + h * 0.5, cy, lightValue % 10, color, lw);
                        }
                    }
                }
            }
        }
    }
}
