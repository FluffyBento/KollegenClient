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
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc != null && mc.level != null) {
                Class<?> holder = Class.forName("net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTrackerHolder");
                Object tracker = holder.getMethod("get", net.minecraft.client.multiplayer.ClientLevel.class).invoke(null, mc.level);
                if (tracker != null) {
                    java.lang.reflect.Method addMethod = tracker.getClass().getMethod("onChunkStatusAdded", int.class, int.class, int.class);
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            int nx = x + dx;
                            int nz = z + dz;
                            if (this.clientChunkManager.getChunk(nx, nz, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false) != null) {
                                addMethod.invoke(tracker, nx, nz, 3);
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
