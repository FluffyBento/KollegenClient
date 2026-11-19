package dev.kollegen.client.mixin;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ClientChunkCache.class, priority = 2000)
public abstract class ClientChunkCacheMixin {

    private static final java.lang.invoke.MethodHandle SODIUM_GET_TRACKER;
    private static final java.lang.invoke.MethodHandle SODIUM_ADD_STATUS;

    static {
        java.lang.invoke.MethodHandle getHandle = null;
        java.lang.invoke.MethodHandle addHandle = null;
        try {
            Class<?> holder = Class.forName("net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTrackerHolder");
            java.lang.invoke.MethodHandles.Lookup lookup = java.lang.invoke.MethodHandles.publicLookup();
            java.lang.reflect.Method gm = holder.getMethod("get", ClientLevel.class);
            getHandle = lookup.unreflect(gm);
            Class<?> trackerClass = gm.getReturnType();
            java.lang.reflect.Method am = trackerClass.getMethod("onChunkStatusAdded", int.class, int.class, int.class);
            addHandle = lookup.unreflect(am);
        } catch (Throwable ignored) {
        }
        SODIUM_GET_TRACKER = getHandle;
        SODIUM_ADD_STATUS = addHandle;
    }

    private static void kollegen$notifySodium(ClientLevel level, ClientChunkCache cache, int cx, int cz) {
        if (SODIUM_GET_TRACKER == null || SODIUM_ADD_STATUS == null || level == null || cache == null) return;
        try {
            Object tracker = SODIUM_GET_TRACKER.invoke(level);
            if (tracker == null) return;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int nx = cx + dx;
                    int nz = cz + dz;
                    if (cache.getChunk(nx, nz, ChunkStatus.FULL, false) != null) {
                        SODIUM_ADD_STATUS.invoke(tracker, nx, nz, 3);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    @Shadow @Final private LevelChunk emptyChunk;
    @Shadow @Final private ClientLevel level;

    @Shadow public abstract LevelChunk getChunk(int x, int z, ChunkStatus status, boolean require);
    @Shadow public abstract LongOpenHashSet addedEmptySections();
    @Shadow public abstract LongOpenHashSet removedEmptySections();
    @Shadow public abstract LongOpenHashSet addedLoadedChunks();
    @Shadow public abstract LongOpenHashSet removedLoadedChunks();

    @Inject(method = "drop", at = @At("RETURN"))
    private void kollegen$afterDropChunk(ChunkPos pos, CallbackInfo ci) {
        LevelChunk chunk = this.getChunk(pos.x(), pos.z(), ChunkStatus.FULL, false);
        if (chunk != null && chunk != this.emptyChunk) {
            this.addedLoadedChunks().add(pos.pack());
            this.removedLoadedChunks().remove(pos.pack());
            LongOpenHashSet addedEmpty = this.addedEmptySections();
            LongOpenHashSet removedEmpty = this.removedEmptySections();
            LevelChunkSection[] sections = chunk.getSections();
            for (int i = 0; i < sections.length; i++) {
                LevelChunkSection sec = sections[i];
                long node = SectionPos.asLong(pos.x(), chunk.getSectionYFromSectionIndex(i), pos.z());
                if (sec != null && !sec.hasOnlyAir()) {
                    addedEmpty.remove(node);
                    removedEmpty.add(node);
                }
            }
            for (int y = this.level.getMinSectionY(); y < this.level.getMaxSectionY(); y++) {
                this.level.setSectionDirtyWithNeighbors(pos.x(), y, pos.z());
            }
            kollegen$notifySodium(this.level, (ClientChunkCache) (Object) this, pos.x(), pos.z());
        }
    }

    @Inject(method = "replaceWithPacketData", at = @At("RETURN"))
    private void kollegen$onPacketData(int x, int z, ClientboundLevelChunkPacketData data, CallbackInfoReturnable<LevelChunk> cir) {
        LevelChunk chunk = cir.getReturnValue();
        if (chunk != null && chunk != this.emptyChunk) {
            LongOpenHashSet addedEmpty = this.addedEmptySections();
            LongOpenHashSet removedEmpty = this.removedEmptySections();
            LevelChunkSection[] sections = chunk.getSections();
            for (int i = 0; i < sections.length; i++) {
                LevelChunkSection sec = sections[i];
                long node = SectionPos.asLong(x, chunk.getSectionYFromSectionIndex(i), z);
                if (sec != null && !sec.hasOnlyAir()) {
                    addedEmpty.remove(node);
                    removedEmpty.add(node);
                }
            }
            kollegen$notifySodium(this.level, (ClientChunkCache) (Object) this, x, z);
        }
    }
}
