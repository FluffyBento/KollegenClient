package dev.kollegen.client.mixin;

import dev.kollegen.client.distance.KollegenDistanceManager;
import dev.kollegen.client.mods.modules.Performance;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
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

    @Inject(method = "drop", at = @At("HEAD"))
    private void kollegen$onDropChunk(ChunkPos pos, CallbackInfo ci) {
        if (!Performance.KollegenDistance.isDistanceEnabled()) return;

        try {
            LevelChunk chunk = this.getChunk(pos.x(), pos.z(), ChunkStatus.FULL, false);
            if (chunk != null) {
                KollegenDistanceManager.onChunkDrop(chunk, this.level);
            }
        } catch (Throwable ignored) {
        }
    }

    @Inject(
            method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/LevelChunk;",
            at = @At("RETURN"),
            cancellable = true
    )
    private void kollegen$getFakeChunk(int x, int z, ChunkStatus status, boolean require, CallbackInfoReturnable<LevelChunk> cir) {
        if (!Performance.KollegenDistance.isDistanceEnabled()) return;

        LevelChunk ret = cir.getReturnValue();
        if (ret == null || ret == this.emptyChunk) {
            LevelChunk fake = KollegenDistanceManager.getChunk(x, z);
            if (fake != null) {
                cir.setReturnValue(fake);
            }
        }
    }

    @Inject(method = "replaceWithPacketData", at = @At("HEAD"))
    private void kollegen$onPacketData(int x, int z, ClientboundLevelChunkPacketData data, CallbackInfoReturnable<LevelChunk> cir) {
        KollegenDistanceManager.onRealChunkLoaded(x, z);
    }
}
