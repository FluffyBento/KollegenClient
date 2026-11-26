package dev.kollegen.client.mixin;

import dev.kollegen.client.mods.modules.Performance;
import dev.kollegen.client.nativebridge.KollegenNative;
import dev.kollegen.client.perf.KollegenTintCacheEntry;
import net.minecraft.client.color.block.BlockTintCache;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockTintCache.class)
public class BlockTintCacheMixin {

    @Unique
    private volatile int kollegen$epoch;
    @Unique
    private volatile ThreadLocal<KollegenTintCacheEntry> kollegen$localCache;

    @Unique
    private KollegenTintCacheEntry kollegen$entry() {
        ThreadLocal<KollegenTintCacheEntry> tl = this.kollegen$localCache;
        if (tl == null) {
            synchronized (this) {
                tl = this.kollegen$localCache;
                if (tl == null) {
                    tl = ThreadLocal.withInitial(KollegenTintCacheEntry::new);
                    this.kollegen$localCache = tl;
                }
            }
        }
        KollegenTintCacheEntry entry = tl.get();
        entry.sync(this.kollegen$epoch);
        return entry;
    }

    @Inject(method = "getColor(Lnet/minecraft/core/BlockPos;)I", at = @At("HEAD"), cancellable = true)
    private void kollegen$l1TintLookup(BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (!Performance.KollegenTricks.shouldUseBedrockCache()) return;
        if (pos == null) return;

        if (Performance.KollegenTricks.shouldUseRustRuntime()) {
            String tname = Thread.currentThread().getName();
            if (!"Render thread".equals(tname)) {
                KollegenNative.idleBackgroundThreadOnce();
            }
        }

        KollegenTintCacheEntry l1 = kollegen$entry();
        long key = pos.asLong();
        int idx = (int) (key ^ (key >>> 16)) & KollegenTintCacheEntry.MASK;
        if (l1.keys[idx] == key) {
            cir.setReturnValue(l1.values[idx]);
        }
    }

    @Inject(method = "getColor(Lnet/minecraft/core/BlockPos;)I", at = @At("RETURN"))
    private void kollegen$l1TintStore(BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (!Performance.KollegenTricks.shouldUseBedrockCache()) return;
        if (pos == null) return;

        KollegenTintCacheEntry l1 = kollegen$entry();
        long key = pos.asLong();
        int idx = (int) (key ^ (key >>> 16)) & KollegenTintCacheEntry.MASK;
        l1.keys[idx] = key;
        l1.values[idx] = cir.getReturnValueI();
    }

    @Inject(method = "invalidateForChunk(II)V", at = @At("HEAD"))
    private void kollegen$invalidateChunk(int x, int z, CallbackInfo ci) {
        this.kollegen$epoch++;
    }

    @Inject(method = "invalidateAll()V", at = @At("HEAD"))
    private void kollegen$invalidateAll(CallbackInfo ci) {
        this.kollegen$epoch++;
    }
}
