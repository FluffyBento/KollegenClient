package dev.kollegen.client.mixin;

import dev.kollegen.client.distance.KollegenDistanceManager;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.entity.TransientEntitySectionManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {

    @Shadow @Final private TransientEntitySectionManager<Entity> entityStorage;

    @Inject(method = "unload", at = @At("HEAD"), cancellable = true)
    private void kollegen$onUnloadChunk(LevelChunk chunk, CallbackInfo ci) {
        if (chunk != null && KollegenDistanceManager.isFakeChunk(chunk.getPos())) {
            ci.cancel();
            try {
                if (this.entityStorage != null) {
                    this.entityStorage.stopTicking(chunk.getPos());
                }
            } catch (Throwable ignored) {
            }
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void kollegen$tickDistance(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        ClientLevel level = (ClientLevel) (Object) this;
        if ((level.getGameTime() & 31) == 0) {
            KollegenDistanceManager.tick(level);
        }
    }

    @Inject(method = "disconnect(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"))
    private void kollegen$disconnectDistance(Component reason, CallbackInfo ci) {
        KollegenDistanceManager.clear((ClientLevel) (Object) this);
    }
}
