package dev.kollegen.client.mixin;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
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

@Mixin(SectionOcclusionGraph.class)
public abstract class SectionOcclusionGraphMixin {

    @Shadow @Final private LongOpenHashSet emptySections;
    @Shadow @Final private LongOpenHashSet loadedChunks;
    @Shadow private ViewArea viewArea;
    @Shadow public abstract void schedulePropagationFrom(SectionRenderDispatcher.RenderSection section);

    @Inject(method = "updateLoadedChunks", at = @At("HEAD"))
    private void kollegen$fixLoadedChunks(LongOpenHashSet addedLoadedChunks, LongOpenHashSet removedLoadedChunks, CallbackInfo ci) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.level == null) return;
            ClientLevel level = mc.level;
            if (removedLoadedChunks != null && !removedLoadedChunks.isEmpty()) {
                LongIterator it = removedLoadedChunks.longIterator();
                while (it.hasNext()) {
                    long packed = it.nextLong();
                    int cx = ChunkPos.getX(packed);
                    int cz = ChunkPos.getZ(packed);
                    LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
                    if (chunk != null) {
                        it.remove();
                        if (addedLoadedChunks != null) {
                            addedLoadedChunks.add(packed);
                        }
                        this.loadedChunks.add(packed);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "updateEmptySections", at = @At("HEAD"))
    private void kollegen$fixEmptySections(LongOpenHashSet addedEmptySections, LongOpenHashSet removedEmptySections, CallbackInfo ci) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.level == null) return;
            ClientLevel level = mc.level;
            if (addedEmptySections != null && !addedEmptySections.isEmpty()) {
                LongIterator it = addedEmptySections.longIterator();
                while (it.hasNext()) {
                    long node = it.nextLong();
                    int cx = SectionPos.x(node);
                    int cy = SectionPos.y(node);
                    int cz = SectionPos.z(node);
                    LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
                    if (chunk != null) {
                        int secIdx = chunk.getSectionIndexFromSectionY(cy);
                        LevelChunkSection[] sections = chunk.getSections();
                        if (secIdx >= 0 && secIdx < sections.length) {
                            LevelChunkSection sec = sections[secIdx];
                            if (sec != null && !sec.hasOnlyAir()) {
                                it.remove();
                                this.emptySections.remove(node);
                                if (removedEmptySections != null) {
                                    removedEmptySections.add(node);
                                }
                                if (this.viewArea != null) {
                                    BlockPos origin = SectionPos.of(node).origin();
                                    SectionRenderDispatcher.RenderSection rs = this.viewArea.getRenderSectionAt(origin);
                                    if (rs != null) {
                                        this.schedulePropagationFrom(rs);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
