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
    }

    @Inject(method = "replaceWithPacketData", at = @At("RETURN"))
    private void kollegen$onPacketData(int x, int z, ClientboundLevelChunkPacketData data, CallbackInfoReturnable<LevelChunk> cir) {
    }
}
