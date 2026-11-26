package dev.kollegen.client.perf;

import java.util.Arrays;

public final class KollegenTintCacheEntry {
    public static final int MASK = 1023;
    public final long[] keys = new long[1024];
    public final int[] values = new int[1024];
    private int epoch = -1;

    public KollegenTintCacheEntry() {
        Arrays.fill(keys, Long.MIN_VALUE);
    }

    public void sync(int targetEpoch) {
        if (epoch != targetEpoch) {
            Arrays.fill(keys, Long.MIN_VALUE);
            epoch = targetEpoch;
        }
    }
}
