package dev.kollegen.client.distance;

import dev.kollegen.client.mods.modules.Performance;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Iterator;

public final class KollegenDistanceManager {

    private static final int MAX_CACHE_SIZE = 4096;

    private static final Long2ObjectMap<LevelChunk> FAKE_CHUNKS = Long2ObjectMaps.synchronize(new Long2ObjectOpenHashMap<>(512));

    private static MethodHandle sodiumChunkStatusAddedHandle = null;
    private static MethodHandle sodiumChunkStatusRemovedHandle = null;
    private static MethodHandle sodiumTrackerGetHandle = null;
    private static boolean sodiumLookupDone = false;

    private static int lastPlayerChunkX = Integer.MIN_VALUE;
    private static int lastPlayerChunkZ = Integer.MIN_VALUE;
    private static long lastPurgeTime = 0L;

    private KollegenDistanceManager() {}

    public static LevelChunk getChunk(int x, int z) {
        if (!Performance.KollegenDistance.isDistanceEnabled()) return null;
        return FAKE_CHUNKS.get(ChunkPos.pack(x, z));
    }

    public static boolean isFakeChunk(ChunkPos pos) {
        if (!Performance.KollegenDistance.isDistanceEnabled()) return false;
        return FAKE_CHUNKS.containsKey(pos.pack());
    }

    public static void onChunkDrop(LevelChunk chunk, ClientLevel level) {
        if (!Performance.KollegenDistance.isDistanceEnabled()) return;
        if (chunk == null || level == null) return;

        ChunkPos pos = chunk.getPos();
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null && !Performance.KollegenDistance.isUnlimited()) {
            int renderDist = Performance.KollegenDistance.getMaxChunkDistance();
            if (mc.options != null) {
                renderDist = Math.max(renderDist, mc.options.renderDistance().get());
            }
            int maxDistSq = renderDist * renderDist;
            int px = mc.player.chunkPosition().x();
            int pz = mc.player.chunkPosition().z();
            int dx = pos.x() - px;
            int dz = pos.z() - pz;
            if (dx * dx + dz * dz > maxDistSq) {
                return;
            }
        }

        int maxCache = Performance.KollegenDistance.isUnlimited() ? 131072 : MAX_CACHE_SIZE;
        if (FAKE_CHUNKS.size() >= maxCache) {
            purgeFurthest(mc);
        }

        FAKE_CHUNKS.put(pos.pack(), chunk);
        notifySodiumChunkAdded(level, pos.x(), pos.z());
    }

    public static void onRealChunkLoaded(int x, int z) {
        FAKE_CHUNKS.remove(ChunkPos.pack(x, z));
    }

    public static void tick(ClientLevel level) {
        if (FAKE_CHUNKS.isEmpty() || level == null) return;
        if (!Performance.KollegenDistance.isDistanceEnabled()) {
            clear(level);
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;

        int px = mc.player.chunkPosition().x();
        int pz = mc.player.chunkPosition().z();
        long now = System.currentTimeMillis();

        // Nur berechnen, wenn der Spieler den Chunk gewechselt hat oder mindestens 5 Sekunden vergangen sind
        if (px == lastPlayerChunkX && pz == lastPlayerChunkZ && now - lastPurgeTime < 5000L) {
            return;
        }
        lastPlayerChunkX = px;
        lastPlayerChunkZ = pz;
        lastPurgeTime = now;

        int renderDist = Performance.KollegenDistance.getMaxChunkDistance();
        if (mc.options != null) {
            renderDist = Math.max(renderDist, mc.options.renderDistance().get());
        }
        int maxDistSq = renderDist * renderDist;

        if (!Performance.KollegenDistance.isUnlimited()) {
            synchronized (FAKE_CHUNKS) {
                Iterator<Long2ObjectMap.Entry<LevelChunk>> it = FAKE_CHUNKS.long2ObjectEntrySet().iterator();
                while (it.hasNext()) {
                    Long2ObjectMap.Entry<LevelChunk> entry = it.next();
                    LevelChunk chunk = entry.getValue();
                    ChunkPos pos = chunk.getPos();
                    int dx = pos.x() - px;
                    int dz = pos.z() - pz;
                    if (dx * dx + dz * dz > maxDistSq + 64) {
                        it.remove();
                        notifySodiumChunkRemoved(level, pos.x(), pos.z());
                        try {
                            chunk.clearAllBlockEntities();
                        } catch (Throwable ignored) {}
                    }
                }
            }
        }
    }

    public static void clear(ClientLevel level) {
        if (FAKE_CHUNKS.isEmpty()) return;
        synchronized (FAKE_CHUNKS) {
            if (level != null) {
                for (LevelChunk chunk : FAKE_CHUNKS.values()) {
                    notifySodiumChunkRemoved(level, chunk.getPos().x(), chunk.getPos().z());
                    try {
                        chunk.clearAllBlockEntities();
                    } catch (Throwable ignored) {}
                }
            }
            FAKE_CHUNKS.clear();
        }
    }

    private static void purgeFurthest(Minecraft mc) {
        if (mc == null || mc.player == null) {
            FAKE_CHUNKS.clear();
            return;
        }
        int px = mc.player.chunkPosition().x();
        int pz = mc.player.chunkPosition().z();
        long furthestKey = 0;
        int maxDist = -1;
        synchronized (FAKE_CHUNKS) {
            for (Long2ObjectMap.Entry<LevelChunk> entry : FAKE_CHUNKS.long2ObjectEntrySet()) {
                ChunkPos pos = entry.getValue().getPos();
                int dx = pos.x() - px;
                int dz = pos.z() - pz;
                int d = dx * dx + dz * dz;
                if (d > maxDist) {
                    maxDist = d;
                    furthestKey = entry.getLongKey();
                }
            }
            if (maxDist > -1) {
                LevelChunk removed = FAKE_CHUNKS.remove(furthestKey);
                if (removed != null) {
                    try {
                        removed.clearAllBlockEntities();
                    } catch (Throwable ignored) {}
                }
            }
        }
    }

    private static void ensureSodium() {
        if (sodiumLookupDone) return;
        sodiumLookupDone = true;
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            Class<?> holderClass = Class.forName("net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTrackerHolder");
            Class<?> trackerClass = Class.forName("net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTracker");
            Method getMethod = holderClass.getMethod("get", ClientLevel.class);
            Method addMethod = trackerClass.getMethod("onChunkStatusAdded", int.class, int.class, int.class);
            Method removeMethod = trackerClass.getMethod("onChunkStatusRemoved", int.class, int.class, int.class);
            sodiumTrackerGetHandle = lookup.unreflect(getMethod);
            sodiumChunkStatusAddedHandle = lookup.unreflect(addMethod);
            sodiumChunkStatusRemovedHandle = lookup.unreflect(removeMethod);
        } catch (Throwable ignored) {
        }
    }

    private static void notifySodiumChunkAdded(ClientLevel level, int x, int z) {
        try {
            ensureSodium();
            if (sodiumTrackerGetHandle != null && sodiumChunkStatusAddedHandle != null) {
                Object tracker = sodiumTrackerGetHandle.invoke(level);
                if (tracker != null) {
                    sodiumChunkStatusAddedHandle.invoke(tracker, x, z, 3);
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void notifySodiumChunkRemoved(ClientLevel level, int x, int z) {
        try {
            ensureSodium();
            if (sodiumTrackerGetHandle != null && sodiumChunkStatusRemovedHandle != null) {
                Object tracker = sodiumTrackerGetHandle.invoke(level);
                if (tracker != null) {
                    sodiumChunkStatusRemovedHandle.invoke(tracker, x, z, 3);
                }
            }
        } catch (Throwable ignored) {}
    }
}
