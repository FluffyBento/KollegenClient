package dev.kollegen.client.map;

import dev.kollegen.client.ui.Glass;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MapEngine {
    public static final MapEngine INSTANCE = new MapEngine();

    private final Map<Long, MapChunk> chunkCache = new ConcurrentHashMap<>();
    private String lastDimension = "";

    private MapEngine() {
    }

    public static long chunkKey(int cx, int cz) {
        return (((long) cx) << 32) | (cz & 0xFFFFFFFFL);
    }

    public void checkDimension(ClientLevel level) {
        if (level == null) return;
        String dim = level.dimension().identifier().toString();
        if (!dim.equals(this.lastDimension)) {
            this.chunkCache.clear();
            this.lastDimension = dim;
        }
    }

    public void clear() {
        this.chunkCache.clear();
    }

    public int getBlockColor(ClientLevel level, int worldX, int worldZ, int playerY) {
        if (level == null) return 0xFF181B20;
        int cx = worldX >> 4;
        int cz = worldZ >> 4;
        MapChunk chunk = getOrScanChunk(level, cx, cz, playerY);
        if (chunk == null) return 0xFF181B20;
        int lx = worldX & 15;
        int lz = worldZ & 15;
        return chunk.colors[lz * 16 + lx];
    }

    public MapChunk getOrScanChunk(ClientLevel level, int cx, int cz, int playerY) {
        if (level == null || !level.hasChunk(cx, cz)) {
            return null;
        }
        checkDimension(level);
        long key = chunkKey(cx, cz);
        MapChunk cached = this.chunkCache.get(key);
        long now = System.currentTimeMillis();
        if (cached != null && (now - cached.lastSampled) < 3000L) {
            return cached;
        }

        MapChunk fresh = scanChunk(level, cx, cz, playerY);
        if (fresh != null) {
            if (this.chunkCache.size() > 2000) {
                this.chunkCache.clear();
            }
            this.chunkCache.put(key, fresh);
            return fresh;
        }
        return cached;
    }

    private MapChunk scanChunk(ClientLevel level, int cx, int cz, int playerY) {
        MapChunk chunk = new MapChunk(cx, cz);
        boolean isNether = level.dimension().identifier().getPath().contains("nether");
        int baseWorldX = cx << 4;
        int baseWorldZ = cz << 4;
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();

        for (int z = 0; z < 16; z++) {
            int wz = baseWorldZ + z;
            for (int x = 0; x < 16; x++) {
                int wx = baseWorldX + x;

                if (!isNether) {
                    int topY = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz);
                    if (topY <= level.getMinY()) {
                        topY = playerY;
                    }
                    int blockY = Math.max(level.getMinY(), topY - 1);
                    mpos.set(wx, blockY, wz);
                    BlockState state = level.getBlockState(mpos);

                    Biome biome = null;
                    try {
                        var bHolder = level.getBiome(mpos);
                        if (bHolder != null) biome = bHolder.value();
                    } catch (Throwable ignored) {
                    }

                    int baseRgb;
                    boolean isWater = (state.getFluidState() != null && !state.getFluidState().isEmpty());
                    if (isWater) {
                        int waterColor = BlockColorPalette.getColor(state, wx, wz, biome);
                        int bedColor = 0xFF8A8585;
                        int depth = 1;
                        for (int y = blockY - 1; y >= Math.max(level.getMinY(), blockY - 12); y--) {
                            mpos.set(wx, y, wz);
                            BlockState under = level.getBlockState(mpos);
                            if (under.getFluidState() == null || under.getFluidState().isEmpty()) {
                                bedColor = BlockColorPalette.getColor(under, wx, wz, biome);
                                break;
                            }
                            depth++;
                        }
                        float waterFactor = Math.min(0.85f, 0.40f + depth * 0.10f);
                        baseRgb = Glass.mix(bedColor, waterColor, waterFactor);
                    } else {
                        baseRgb = BlockColorPalette.getColor(state, wx, wz, biome);
                    }

                    int prevN = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz - 1);
                    int prevW = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx - 1, wz);
                    int diffN = topY - prevN;
                    int diffW = topY - prevW;
                    float slope = (diffN * 0.65f + diffW * 0.35f);
                    float brightness = Math.max(0.68f, Math.min(1.32f, 1.0f + slope * 0.08f));

                    int r = Math.min(255, Math.max(0, (int) (((baseRgb >> 16) & 0xFF) * brightness)));
                    int g = Math.min(255, Math.max(0, (int) (((baseRgb >> 8) & 0xFF) * brightness)));
                    int b = Math.min(255, Math.max(0, (int) ((baseRgb & 0xFF) * brightness)));
                    chunk.colors[z * 16 + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
                } else {
                    int startY = Math.min(124, playerY + 16);
                    int minY = Math.max(level.getMinY(), playerY - 32);
                    int solidY = minY;
                    BlockState found = null;

                    for (int y = startY; y >= minY; y--) {
                        mpos.set(wx, y, wz);
                        BlockState s = level.getBlockState(mpos);
                        if (!s.isAir()) {
                            found = s;
                            solidY = y;
                            break;
                        }
                    }

                    Biome biome = null;
                    try {
                        var bHolder = level.getBiome(mpos);
                        if (bHolder != null) biome = bHolder.value();
                    } catch (Throwable ignored) {
                    }

                    int baseRgb = 0x652222;
                    if (found != null) {
                        baseRgb = BlockColorPalette.getColor(found, wx, wz, biome);
                    }

                    float brightness = ((solidY % 3) == 0) ? 1.10f : (((solidY % 3) == 1) ? 0.92f : 1.0f);
                    int r = Math.min(255, Math.max(0, (int) (((baseRgb >> 16) & 0xFF) * brightness)));
                    int g = Math.min(255, Math.max(0, (int) (((baseRgb >> 8) & 0xFF) * brightness)));
                    int b = Math.min(255, Math.max(0, (int) ((baseRgb & 0xFF) * brightness)));
                    chunk.colors[z * 16 + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
                }
            }
        }
        return chunk;
    }
}
