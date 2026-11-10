package dev.kollegen.client.distance;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.kollegen.client.mods.modules.Performance;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class KollegenDistanceManager {

    private static final int MAGIC_KLOD = 0x4B4C4F44;
    private static final int MAX_CACHE_CHUNKS = 262144;
    private static final Long2ObjectMap<LodChunk> LOD_CACHE = Long2ObjectMaps.synchronize(new Long2ObjectOpenHashMap<>(32768));
    private static final Set<Long> VISITED_REGIONS = Collections.synchronizedSet(new HashSet<>());

    private static final ExecutorService DISK_IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "KollegenDistance-Disk");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    private static int lastPlayerChunkX = Integer.MIN_VALUE;
    private static int lastPlayerChunkZ = Integer.MIN_VALUE;
    private static long lastScanTime = 0L;

    private KollegenDistanceManager() {}

    private static int applyShade(int col, float shade) {
        int a = (col >> 24) & 0xFF;
        int r = (int) (((col >> 16) & 0xFF) * shade);
        int g = (int) (((col >> 8) & 0xFF) * shade);
        int b = (int) ((col & 0xFF) * shade);
        if (r > 255) r = 255;
        if (g > 255) g = 255;
        if (b > 255) b = 255;
        if (r < 0) r = 0;
        if (g < 0) g = 0;
        if (b < 0) b = 0;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int blendFog(int col, int fogCol, float factor) {
        if (factor <= 0.0f) return col;
        if (factor >= 1.0f) return fogCol;
        int a = (col >> 24) & 0xFF;
        int r1 = (col >> 16) & 0xFF;
        int g1 = (col >> 8) & 0xFF;
        int b1 = col & 0xFF;
        int r2 = (fogCol >> 16) & 0xFF;
        int g2 = (fogCol >> 8) & 0xFF;
        int b2 = fogCol & 0xFF;
        int r = (int) (r1 + (r2 - r1) * factor);
        int g = (int) (g1 + (g2 - g1) * factor);
        int b = (int) (b1 + (b2 - b1) * factor);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int getAtmosphericColor(ClientLevel level) {
        if (level == null) return 0xFF96B8E0;
        int darken = level.getSkyDarken();
        if (darken >= 6) {
            return 0xFF0D1424;
        } else if (darken >= 3) {
            return 0xFF506880;
        }
        return 0xFF9EBEEC;
    }

    public static File getCacheDir(ClientLevel level) {
        File home = new File(System.getProperty("user.home", "."));
        File base = new File(new File(home, ".kollegen"), "distance_cache");
        Minecraft mc = Minecraft.getInstance();
        String host = "world";
        if (mc != null) {
            if (mc.getCurrentServer() != null && mc.getCurrentServer().ip != null) {
                host = mc.getCurrentServer().ip.replaceAll("[^a-zA-Z0-9_.-]", "_");
            } else if (mc.getSingleplayerServer() != null) {
                try {
                    host = "sp_" + mc.getSingleplayerServer().getWorldData().getLevelName().replaceAll("[^a-zA-Z0-9_.-]", "_");
                } catch (Throwable ignored) {}
            }
        }
        String dim = "overworld";
        if (level != null) {
            try {
                dim = level.dimension().identifier().getPath();
            } catch (Throwable ignored) {}
        }
        return new File(new File(base, host), dim);
    }

    public static class LodChunk {
        public final int chunkX;
        public final int chunkZ;
        public final short[] cellHeights;
        public final int[] cellColors;
        public final boolean[] isWater;
        public final short minY;
        public final short maxY;
        public final short avgHeight;
        public final int avgColor;

        public LodChunk(int chunkX, int chunkZ, short[] cellHeights, int[] cellColors, boolean[] isWater, short minY, short maxY, short avgHeight, int avgColor) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.cellHeights = cellHeights;
            this.cellColors = cellColors;
            this.isWater = isWater;
            this.minY = minY;
            this.maxY = maxY;
            this.avgHeight = avgHeight;
            this.avgColor = avgColor;
        }

        public void render(PoseStack.Pose pose, VertexConsumer consumer, double camX, double camY, double camZ, long distSq, int skyCol, float fogStart, float fogEnd) {
            float baseX = (float) (chunkX * 16 - camX);
            float baseZ = (float) (chunkZ * 16 - camZ);

            float dist = (float) Math.sqrt(distSq);
            float fogFactor = Math.max(0.0f, Math.min(1.0f, (dist - fogStart) / Math.max(1.0f, fogEnd - fogStart)));
            fogFactor = fogFactor * fogFactor * (3.0f - 2.0f * fogFactor);

            float skirtLow = (float) (Math.max(minY - 4, -64) - camY);

            for (int gz = 0; gz < 4; gz++) {
                float z0 = baseZ + gz * 4.0f;
                float z1 = baseZ + (gz + 1) * 4.0f;
                for (int gx = 0; gx < 4; gx++) {
                    float x0 = baseX + gx * 4.0f;
                    float x1 = baseX + (gx + 1) * 4.0f;

                    int idx = gz * 4 + gx;
                    short h = cellHeights[idx];
                    float y = (float) (h - camY);
                    int baseCol = cellColors[idx];

                    float shade = 1.0f;
                    if (gx > 0 && cellHeights[idx] > cellHeights[gz * 4 + (gx - 1)]) shade += 0.05f;
                    if (gz > 0 && cellHeights[idx] < cellHeights[(gz - 1) * 4 + gx]) shade -= 0.05f;

                    int col = blendFog(applyShade(baseCol, shade), skyCol, fogFactor);

                    consumer.addVertex(pose, x0, y, z0).setColor(col);
                    consumer.addVertex(pose, x0, y, z1).setColor(col);
                    consumer.addVertex(pose, x1, y, z1).setColor(col);
                    consumer.addVertex(pose, x1, y, z0).setColor(col);

                    if (isWater[idx]) {
                        float yw = (float) (Math.max(h, 62) - camY);
                        int wCol = blendFog(0xD8256298, skyCol, fogFactor);
                        consumer.addVertex(pose, x0, yw, z0).setColor(wCol);
                        consumer.addVertex(pose, x0, yw, z1).setColor(wCol);
                        consumer.addVertex(pose, x1, yw, z1).setColor(wCol);
                        consumer.addVertex(pose, x1, yw, z0).setColor(wCol);
                    }

                    if (gx < 3) {
                        short nh = cellHeights[gz * 4 + (gx + 1)];
                        if (h != nh) {
                            float yLow = (float) (Math.min(h, nh) - camY);
                            float yHigh = (float) (Math.max(h, nh) - camY);
                            int sideCol = blendFog(applyShade(baseCol, 0.82f), skyCol, fogFactor);
                            consumer.addVertex(pose, x1, yLow, z0).setColor(sideCol);
                            consumer.addVertex(pose, x1, yHigh, z0).setColor(sideCol);
                            consumer.addVertex(pose, x1, yHigh, z1).setColor(sideCol);
                            consumer.addVertex(pose, x1, yLow, z1).setColor(sideCol);
                        }
                    } else if (h > minY + 2) {
                        int sideCol = blendFog(applyShade(baseCol, 0.82f), skyCol, fogFactor);
                        consumer.addVertex(pose, x1, skirtLow, z0).setColor(sideCol);
                        consumer.addVertex(pose, x1, y, z0).setColor(sideCol);
                        consumer.addVertex(pose, x1, y, z1).setColor(sideCol);
                        consumer.addVertex(pose, x1, skirtLow, z1).setColor(sideCol);
                    }

                    if (gz < 3) {
                        short nh = cellHeights[(gz + 1) * 4 + gx];
                        if (h != nh) {
                            float yLow = (float) (Math.min(h, nh) - camY);
                            float yHigh = (float) (Math.max(h, nh) - camY);
                            int sideCol = blendFog(applyShade(baseCol, 0.68f), skyCol, fogFactor);
                            consumer.addVertex(pose, x0, yLow, z1).setColor(sideCol);
                            consumer.addVertex(pose, x1, yLow, z1).setColor(sideCol);
                            consumer.addVertex(pose, x1, yHigh, z1).setColor(sideCol);
                            consumer.addVertex(pose, x0, yHigh, z1).setColor(sideCol);
                        }
                    } else if (h > minY + 2) {
                        int sideCol = blendFog(applyShade(baseCol, 0.68f), skyCol, fogFactor);
                        consumer.addVertex(pose, x0, skirtLow, z1).setColor(sideCol);
                        consumer.addVertex(pose, x1, skirtLow, z1).setColor(sideCol);
                        consumer.addVertex(pose, x1, y, z1).setColor(sideCol);
                        consumer.addVertex(pose, x0, y, z1).setColor(sideCol);
                    }

                    if (gx == 0 && h > minY + 2) {
                        int sideCol = blendFog(applyShade(baseCol, 0.82f), skyCol, fogFactor);
                        consumer.addVertex(pose, x0, skirtLow, z1).setColor(sideCol);
                        consumer.addVertex(pose, x0, y, z1).setColor(sideCol);
                        consumer.addVertex(pose, x0, y, z0).setColor(sideCol);
                        consumer.addVertex(pose, x0, skirtLow, z0).setColor(sideCol);
                    }

                    if (gz == 0 && h > minY + 2) {
                        int sideCol = blendFog(applyShade(baseCol, 0.68f), skyCol, fogFactor);
                        consumer.addVertex(pose, x1, skirtLow, z0).setColor(sideCol);
                        consumer.addVertex(pose, x0, skirtLow, z0).setColor(sideCol);
                        consumer.addVertex(pose, x0, y, z0).setColor(sideCol);
                        consumer.addVertex(pose, x1, y, z0).setColor(sideCol);
                    }
                }
            }
        }
    }

    public static void captureChunk(LevelChunk chunk) {
        if (!Performance.KollegenDistance.isDistanceEnabled() || chunk == null || chunk.isEmpty()) return;
        ChunkPos pos = chunk.getPos();
        long key = pos.pack();

        short[] cellHeights = new short[16];
        int[] cellColors = new int[16];
        boolean[] isWater = new boolean[16];

        int min = 320;
        int max = -64;
        long sumH = 0;
        long sumR = 0;
        long sumG = 0;
        long sumB = 0;

        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();

        for (int gz = 0; gz < 4; gz++) {
            for (int gx = 0; gx < 4; gx++) {
                int bxCenter = gx * 4 + 2;
                int bzCenter = gz * 4 + 2;

                int yGround = 64;
                try {
                    int y1 = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR, gx * 4 + 1, gz * 4 + 1);
                    int y2 = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR, gx * 4 + 3, gz * 4 + 1);
                    int y3 = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR, gx * 4 + 1, gz * 4 + 3);
                    int y4 = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR, gx * 4 + 3, gz * 4 + 3);
                    yGround = (y1 + y2 + y3 + y4) / 4;
                } catch (Throwable ignored) {
                }

                int ySurface = yGround;
                try {
                    int ys1 = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, gx * 4 + 1, gz * 4 + 1);
                    int ys2 = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, gx * 4 + 3, gz * 4 + 1);
                    int ys3 = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, gx * 4 + 1, gz * 4 + 3);
                    int ys4 = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, gx * 4 + 3, gz * 4 + 3);
                    int avgS = (ys1 + ys2 + ys3 + ys4) / 4;
                    if (avgS > yGround + 4) {
                        ySurface = yGround + 2;
                    } else {
                        ySurface = avgS;
                    }
                } catch (Throwable ignored) {
                }

                mpos.set(pos.getMinBlockX() + bxCenter, Math.max(ySurface - 1, -64), pos.getMinBlockZ() + bzCenter);

                boolean water = false;
                int col = 0xFF5C9E35;
                try {
                    BlockState bs = chunk.getBlockState(mpos);
                    if (bs.is(Blocks.WATER) || chunk.getBlockState(mpos.set(pos.getMinBlockX() + bxCenter, 62, pos.getMinBlockZ() + bzCenter)).is(Blocks.WATER)) {
                        water = true;
                        ySurface = Math.max(ySurface, 62);
                        col = 0xFF285E8E;
                    } else {
                        if (bs.is(BlockTags.LEAVES)) {
                            col = 0xFF4A7D2C;
                        } else {
                            MapColor mc = bs.getMapColor(chunk.getLevel(), mpos);
                            if (mc != null && mc.col != 0) {
                                col = mc.col | 0xFF000000;
                            }
                        }
                    }
                } catch (Throwable ignored) {
                }

                if (ySurface < -64) ySurface = -64;
                if (ySurface > 320) ySurface = 320;

                int idx = gz * 4 + gx;
                cellHeights[idx] = (short) ySurface;
                cellColors[idx] = col;
                isWater[idx] = water;

                if (ySurface < min) min = ySurface;
                if (ySurface > max) max = ySurface;

                sumH += ySurface;
                sumR += (col >> 16) & 0xFF;
                sumG += (col >> 8) & 0xFF;
                sumB += col & 0xFF;
            }
        }

        short avgH = (short) (sumH / 16);
        int avgCol = 0xFF000000 | (((int) (sumR / 16)) << 16) | (((int) (sumG / 16)) << 8) | ((int) (sumB / 16));

        if (LOD_CACHE.size() >= MAX_CACHE_CHUNKS) {
            purgeFurthest();
        }

        LodChunk lod = new LodChunk(pos.x(), pos.z(), cellHeights, cellColors, isWater, (short) min, (short) max, avgH, avgCol);
        LOD_CACHE.put(key, lod);

        if (chunk.getLevel() instanceof ClientLevel cl) {
            File cacheDir = getCacheDir(cl);
            DISK_IO.submit(() -> writeChunkToDisk(cacheDir, lod));
        }
    }

    private static void writeChunkToDisk(File dir, LodChunk lod) {
        if (dir == null || lod == null) return;
        try {
            dir.mkdirs();
            int rx = lod.chunkX >> 5;
            int rz = lod.chunkZ >> 5;
            File file = new File(dir, "r." + rx + "." + rz + ".kld");
            try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
                int lx = lod.chunkX & 31;
                int lz = lod.chunkZ & 31;
                int slot = lz * 32 + lx;
                long offset = (long) slot * 128L;
                raf.seek(offset);

                ByteBuffer buf = ByteBuffer.allocate(128);
                buf.putInt(MAGIC_KLOD);
                buf.putInt(lod.chunkX);
                buf.putInt(lod.chunkZ);
                buf.putShort(lod.minY);
                buf.putShort(lod.maxY);
                buf.putShort(lod.avgHeight);
                buf.putInt(lod.avgColor);
                short wm = 0;
                for (int i = 0; i < 16; i++) {
                    if (lod.isWater[i]) wm |= (short) (1 << i);
                }
                buf.putShort(wm);
                for (int i = 0; i < 16; i++) buf.putShort(lod.cellHeights[i]);
                for (int i = 0; i < 16; i++) buf.putInt(lod.cellColors[i]);
                while (buf.hasRemaining()) buf.put((byte) 0);

                raf.write(buf.array());
            }
        } catch (Throwable ignored) {
        }
    }

    private static void readRegionFromDisk(File dir, int rx, int rz) {
        if (dir == null) return;
        File file = new File(dir, "r." + rx + "." + rz + ".kld");
        if (!file.isFile()) return;
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            if (bytes.length < 128) return;
            ByteBuffer buf = ByteBuffer.wrap(bytes);
            int totalSlots = Math.min(1024, bytes.length / 128);
            for (int slot = 0; slot < totalSlots; slot++) {
                buf.position(slot * 128);
                if (buf.getInt() != MAGIC_KLOD) continue;
                int cx = buf.getInt();
                int cz = buf.getInt();
                long key = ChunkPos.pack(cx, cz);
                if (LOD_CACHE.containsKey(key)) continue;

                short minY = buf.getShort();
                short maxY = buf.getShort();
                short avgH = buf.getShort();
                int avgCol = buf.getInt();
                short wm = buf.getShort();
                short[] cellHeights = new short[16];
                for (int i = 0; i < 16; i++) cellHeights[i] = buf.getShort();
                int[] cellColors = new int[16];
                for (int i = 0; i < 16; i++) cellColors[i] = buf.getInt();
                boolean[] isWater = new boolean[16];
                for (int i = 0; i < 16; i++) isWater[i] = ((wm >> i) & 1) != 0;

                LOD_CACHE.put(key, new LodChunk(cx, cz, cellHeights, cellColors, isWater, minY, maxY, avgH, avgCol));
            }
        } catch (Throwable ignored) {
        }
    }

    public static void render(PoseStack poseStack, LevelRenderState state, SubmitNodeCollector collector) {
        if (true) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) return;
        if (state == null || state.cameraRenderState == null) return;

        CameraRenderState camState = state.cameraRenderState;
        if (camState.pos == null) return;
        double camX = camState.pos.x();
        double camY = camState.pos.y();
        double camZ = camState.pos.z();
        Frustum frustum = camState.cullFrustum;

        int maxDist = Performance.KollegenDistance.getMaxChunkDistance();
        int vanillaDist = mc.options != null ? mc.options.renderDistance().get() : 12;
        boolean uncapped = maxDist == Integer.MAX_VALUE;
        long maxDistSq = uncapped ? Long.MAX_VALUE : ((long) maxDist * (long) maxDist);
        long vanillaDistSq = (long) Math.max(0, vanillaDist - 1) * (long) Math.max(0, vanillaDist - 1);

        int px = mc.player.chunkPosition().x();
        int pz = mc.player.chunkPosition().z();

        List<LodChunk> visible = new ArrayList<>();
        long maxFoundDistSq = 0;

        synchronized (LOD_CACHE) {
            for (LodChunk lod : LOD_CACHE.values()) {
                long dx = lod.chunkX - px;
                long dz = lod.chunkZ - pz;
                long dSq = dx * dx + dz * dz;
                if (dSq > maxDistSq || dSq <= vanillaDistSq) {
                    continue;
                }

                if (frustum != null) {
                    double minX = lod.chunkX * 16.0;
                    double minZ = lod.chunkZ * 16.0;
                    double maxX = minX + 16.0;
                    double maxZ = minZ + 16.0;
                    double minY = lod.minY - 16.0;
                    double maxY = lod.maxY + 16.0;
                    if (!frustum.isVisible(new AABB(minX, minY, minZ, maxX, maxY, maxZ))) {
                        continue;
                    }
                }
                visible.add(lod);
                if (dSq > maxFoundDistSq) maxFoundDistSq = dSq;
            }
        }

        if (visible.isEmpty()) return;

        visible.sort((a, b) -> {
            long da = (long) (a.chunkX - px) * (a.chunkX - px) + (long) (a.chunkZ - pz) * (a.chunkZ - pz);
            long db = (long) (b.chunkX - px) * (b.chunkX - px) + (long) (b.chunkZ - pz) * (b.chunkZ - pz);
            return Long.compare(db, da);
        });

        int skyCol = getAtmosphericColor(mc.level);
        float fogStart = vanillaDist * 0.80f;
        float fogEnd = uncapped ? Math.max(512.0f, (float) Math.sqrt(maxFoundDistSq) * 1.15f) : (maxDist * 0.95f);

        collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, consumer) -> {
            for (LodChunk lod : visible) {
                long dx = lod.chunkX - px;
                long dz = lod.chunkZ - pz;
                long dSq = dx * dx + dz * dz;
                lod.render(pose, consumer, camX, camY, camZ, dSq, skyCol, fogStart, fogEnd);
            }
        });
    }

    public static void tick(ClientLevel level) {
        if (level == null) return;
        if (!Performance.KollegenDistance.isDistanceEnabled()) {
            clear(level);
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;

        int px = mc.player.chunkPosition().x();
        int pz = mc.player.chunkPosition().z();
        long now = System.currentTimeMillis();

        if (px == lastPlayerChunkX && pz == lastPlayerChunkZ && now - lastScanTime < 3000L) {
            return;
        }
        lastPlayerChunkX = px;
        lastPlayerChunkZ = pz;
        lastScanTime = now;

        int maxDist = Performance.KollegenDistance.getMaxChunkDistance();
        boolean uncapped = maxDist == Integer.MAX_VALUE;

        if (!uncapped) {
            long maxDistSq = (long) (maxDist + 16) * (long) (maxDist + 16);
            synchronized (LOD_CACHE) {
                LOD_CACHE.values().removeIf(lod -> {
                    long dx = lod.chunkX - px;
                    long dz = lod.chunkZ - pz;
                    return (dx * dx + dz * dz) > maxDistSq;
                });
            }
        }

        int prx = px >> 5;
        int prz = pz >> 5;
        int scanRadius = uncapped ? 8 : Math.max(1, Math.min(8, (maxDist >> 5) + 1));
        File cacheDir = getCacheDir(level);

        for (int rz = prz - scanRadius; rz <= prz + scanRadius; rz++) {
            for (int rx = prx - scanRadius; rx <= prx + scanRadius; rx++) {
                long rKey = ChunkPos.pack(rx, rz);
                if (VISITED_REGIONS.add(rKey)) {
                    final int frx = rx;
                    final int frz = rz;
                    DISK_IO.submit(() -> readRegionFromDisk(cacheDir, frx, frz));
                }
            }
        }
    }

    private static void purgeFurthest() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            LOD_CACHE.clear();
            return;
        }
        int px = mc.player.chunkPosition().x();
        int pz = mc.player.chunkPosition().z();

        long worstKey = -1L;
        long maxDistSq = -1;

        synchronized (LOD_CACHE) {
            for (LodChunk lod : LOD_CACHE.values()) {
                long dx = lod.chunkX - px;
                long dz = lod.chunkZ - pz;
                long dSq = dx * dx + dz * dz;
                if (dSq > maxDistSq) {
                    maxDistSq = dSq;
                    worstKey = ChunkPos.pack(lod.chunkX, lod.chunkZ);
                }
            }
            if (worstKey != -1L) {
                LOD_CACHE.remove(worstKey);
            }
        }
    }

    public static void clear(ClientLevel level) {
        LOD_CACHE.clear();
        VISITED_REGIONS.clear();
        lastPlayerChunkX = Integer.MIN_VALUE;
        lastPlayerChunkZ = Integer.MIN_VALUE;
    }
}
