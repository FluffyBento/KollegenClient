package dev.kollegen.client.mixin;

import dev.kollegen.client.mods.modules.Performance;
import net.minecraft.client.color.block.BlockTintCache;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;

@Mixin(BlockTintCache.class)
public class BlockTintCacheMixin {

    @Unique
    private static final int KOLLEGEN$TINT_MASK = 1023;
    @Unique
    private final long[] kollegen$keys = new long[1024];
    @Unique
    private final int[] kollegen$values = new int[1024];

    @Inject(method = "<init>", at = @At("RETURN"))
    private void kollegen$init(CallbackInfo ci) {
        Arrays.fill(kollegen$keys, Long.MIN_VALUE);
    }

    @Inject(method = "getColor(Lnet/minecraft/core/BlockPos;)I", at = @At("HEAD"), cancellable = true)
    private void kollegen$l1TintLookup(BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (!Performance.KollegenTricks.shouldUseBedrockCache()) return;
        if (pos == null) return;

        long key = pos.asLong();
        int idx = (int) (key ^ (key >>> 16)) & KOLLEGEN$TINT_MASK;
        if (kollegen$keys[idx] == key) {
            cir.setReturnValue(kollegen$values[idx]);
        }
    }

    @Inject(method = "getColor(Lnet/minecraft/core/BlockPos;)I", at = @At("RETURN"))
    private void kollegen$l1TintStore(BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (!Performance.KollegenTricks.shouldUseBedrockCache()) return;
        if (pos == null) return;

        long key = pos.asLong();
        int idx = (int) (key ^ (key >>> 16)) & KOLLEGEN$TINT_MASK;
        kollegen$keys[idx] = key;
        kollegen$values[idx] = cir.getReturnValueI();
    }

    @Inject(method = "invalidateForChunk(II)V", at = @At("HEAD"))
    private void kollegen$invalidateChunk(int x, int z, CallbackInfo ci) {
        // Targeted chunk invalidation: keeps cache hot for all other chunks in view
        for (int i = 0; i < 1024; i++) {
            long k = kollegen$keys[i];
            if (k != Long.MIN_VALUE) {
                int bx = (int) (k >> 38);
                int bz = (int) ((k << 26) >> 38);
                if ((bx >> 4) == x && (bz >> 4) == z) {
                    kollegen$keys[i] = Long.MIN_VALUE;
                }
            }
        }
    }

    @Inject(method = "invalidateAll()V", at = @At("HEAD"))
    private void kollegen$invalidateAll(CallbackInfo ci) {
        Arrays.fill(kollegen$keys, Long.MIN_VALUE);
    }
}
