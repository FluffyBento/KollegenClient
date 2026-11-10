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

@Mixin(ClientChunkCache.class)
public abstract class ClientChunkCacheMixin {

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
        }
    }
}
