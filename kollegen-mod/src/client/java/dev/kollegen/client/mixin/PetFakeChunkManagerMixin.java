package dev.kollegen.client.mixin;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "de.johni0702.minecraft.bobby.FakeChunkManager", remap = false)
public class PetFakeChunkManagerMixin {

    @Shadow
    private ClientChunkCache clientChunkManager;

    @Inject(method = "markAsLoaded", at = @At("TAIL"), remap = false)
    private void kollegen$fixMarkAsLoaded(int x, int z, LevelChunk chunk, CallbackInfo ci) {
        if (chunk == null || this.clientChunkManager == null) return;
        LongOpenHashSet addedEmptySections = this.clientChunkManager.addedEmptySections();
        LongOpenHashSet removedEmptySections = this.clientChunkManager.removedEmptySections();
        LevelChunkSection[] chunkSections = chunk.getSections();
        for (int i = 0; i < chunkSections.length; i++) {
            LevelChunkSection chunkSection = chunkSections[i];
            long sectionPos = SectionPos.asLong(x, chunk.getSectionYFromSectionIndex(i), z);
            if (chunkSection != null && !chunkSection.hasOnlyAir()) {
                addedEmptySections.remove(sectionPos);
                removedEmptySections.add(sectionPos);
            }
        }
    }

    @Inject(method = "markAsUnloaded", at = @At("TAIL"), remap = false)
    private void kollegen$fixMarkAsUnloaded(int x, int z, LevelChunk chunk, CallbackInfo ci) {
        if (chunk == null || this.clientChunkManager == null) return;
        LongOpenHashSet removedEmptySections = this.clientChunkManager.removedEmptySections();
        LongOpenHashSet addedEmptySections = this.clientChunkManager.addedEmptySections();
        LevelChunkSection[] chunkSections = chunk.getSections();
        for (int i = 0; i < chunkSections.length; i++) {
            long sectionPos = SectionPos.asLong(x, chunk.getSectionYFromSectionIndex(i), z);
            addedEmptySections.add(sectionPos);
            removedEmptySections.remove(sectionPos);
        }
    }
}
