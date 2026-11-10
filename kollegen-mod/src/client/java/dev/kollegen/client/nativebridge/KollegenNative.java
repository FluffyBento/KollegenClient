package dev.kollegen.client.nativebridge;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public final class KollegenNative {

    private static volatile boolean available = false;
    private static volatile boolean initialized = false;
    private static String version = "none";
    private static String features = "none";

    private KollegenNative() {}

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;

        try {
            String osName = System.getProperty("os.name", "").toLowerCase();
            String arch = System.getProperty("os.arch", "").toLowerCase();
            String libName;
            String resPath;

            if (osName.contains("win")) {
                libName = "kollegen_native.dll";
                resPath = "/natives/windows-x86_64/" + libName;
            } else if (osName.contains("mac")) {
                libName = "libkollegen_native.dylib";
                resPath = "/natives/macos-" + (arch.contains("aarch64") || arch.contains("arm") ? "aarch64" : "x86_64") + "/" + libName;
            } else {
                libName = "libkollegen_native.so";
                resPath = "/natives/linux-x86_64/" + libName;
            }

            File homeDir = new File(System.getProperty("user.home", "."));
            File nativeDir = new File(new File(homeDir, ".kollegen"), "native");
            File nativeTarget = new File(nativeDir, libName);

            boolean loaded = false;

            if (nativeTarget.isFile() && nativeTarget.length() > 0) {
                try {
                    System.load(nativeTarget.getAbsolutePath());
                    loaded = true;
                } catch (Throwable ignored) {}
            }

            if (!loaded) {
                InputStream in = KollegenNative.class.getResourceAsStream(resPath);
                if (in != null) {
                    try {
                        nativeDir.mkdirs();
                        File tempLib = new File(nativeDir, libName + ".tmp");
                        Files.copy(in, tempLib.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        in.close();
                        if (tempLib.renameTo(nativeTarget) || nativeTarget.exists()) {
                            System.load(nativeTarget.getAbsolutePath());
                            loaded = true;
                        } else {
                            System.load(tempLib.getAbsolutePath());
                            loaded = true;
                        }
                    } catch (Throwable ignored) {}
                }
            }

            if (!loaded) {
                try {
                    System.loadLibrary("kollegen_native");
                    loaded = true;
                } catch (Throwable ignored) {}
            }

            if (loaded) {
                if (nativeInit() && nativeIsAvailable()) {
                    available = true;
                    version = nativeGetVersion();
                    features = nativeGetFeatures();
                }
            }
        } catch (Throwable ignored) {
            available = false;
        }
    }

    public static boolean isAvailable() {
        if (!initialized) {
            init();
        }
        return available;
    }

    public static String getVersion() {
        return version;
    }

    public static String getFeatures() {
        return features;
    }

    public static boolean favourRenderThread() {
        if (isAvailable()) {
            try {
                return nativeFavourCurrentThread();
            } catch (Throwable ignored) {}
        }
        try {
            Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean idleBackgroundThread() {
        if (isAvailable()) {
            try {
                return nativeIdleCurrentThread();
            } catch (Throwable ignored) {}
        }
        try {
            Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static int fastBoxInFrustum(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, float[] planes) {
        if (isAvailable() && planes != null && planes.length >= 24) {
            try {
                return nativeFastBoxInFrustum(minX, minY, minZ, maxX, maxY, maxZ, planes);
            } catch (Throwable ignored) {}
        }
        return fallbackBoxInFrustum(minX, minY, minZ, maxX, maxY, maxZ, planes);
    }

    public static int batchFrustumCull(double[] aabbs, int count, float[] planes, boolean[] results) {
        if (isAvailable() && aabbs != null && planes != null && results != null) {
            try {
                return nativeBatchFrustumCull(aabbs, count, planes, results);
            } catch (Throwable ignored) {}
        }
        int vis = 0;
        int limit = Math.min(count, Math.min(aabbs.length / 6, results.length));
        for (int i = 0; i < limit; i++) {
            int base = i * 6;
            int res = fallbackBoxInFrustum(aabbs[base], aabbs[base + 1], aabbs[base + 2], aabbs[base + 3], aabbs[base + 4], aabbs[base + 5], planes);
            boolean v = res != 0;
            results[i] = v;
            if (v) vis++;
        }
        return vis;
    }

    public static double sin(double rad) {
        if (isAvailable()) {
            try {
                return nativeFastSin(rad);
            } catch (Throwable ignored) {}
        }
        return Math.sin(rad);
    }

    public static double cos(double rad) {
        if (isAvailable()) {
            try {
                return nativeFastCos(rad);
            } catch (Throwable ignored) {}
        }
        return Math.cos(rad);
    }

    public static double distance3D(double dx, double dy, double dz) {
        if (isAvailable()) {
            try {
                return nativeFastDistance3D(dx, dy, dz);
            } catch (Throwable ignored) {}
        }
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public static long hashPos(int x, int y, int z) {
        if (isAvailable()) {
            try {
                return nativeFastHashPos(x, y, z);
            } catch (Throwable ignored) {}
        }
        long h = 0L;
        long seed = 0xf1357aea2e62a9c5L;
        h = (h + (x & 0xFFFFFFFFL)) * seed;
        h = (h + (y & 0xFFFFFFFFL)) * seed;
        h = (h + (z & 0xFFFFFFFFL)) * seed;
        return Long.rotateLeft(h, 26);
    }

    public static long getRssBytes() {
        if (isAvailable()) {
            try {
                long bytes = nativeGetMemoryRssBytes();
                if (bytes > 0) return bytes;
            } catch (Throwable ignored) {}
        }
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }

    private static int fallbackBoxInFrustum(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, float[] planes) {
        if (planes == null || planes.length < 24) return 1;
        boolean fullyInside = true;
        for (int i = 0; i < 24; i += 4) {
            float a = planes[i];
            float b = planes[i + 1];
            float c = planes[i + 2];
            float d = planes[i + 3];

            double px = a > 0.0f ? maxX : minX;
            double py = b > 0.0f ? maxY : minY;
            double pz = c > 0.0f ? maxZ : minZ;

            if (a * px + b * py + c * pz + d < 0.0) {
                return 0;
            }

            if (fullyInside) {
                double nx = a > 0.0f ? minX : maxX;
                double ny = b > 0.0f ? minY : maxY;
                double nz = c > 0.0f ? minZ : maxZ;
                if (a * nx + b * ny + c * nz + d < 0.0) {
                    fullyInside = false;
                }
            }
        }
        return fullyInside ? 2 : 1;
    }

    private static native boolean nativeInit();
    private static native boolean nativeIsAvailable();
    private static native String nativeGetVersion();
    private static native String nativeGetFeatures();
    private static native boolean nativeFavourCurrentThread();
    private static native boolean nativeIdleCurrentThread();
    private static native int nativeFastBoxInFrustum(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, float[] planes);
    private static native int nativeBatchFrustumCull(double[] aabbs, int count, float[] planes, boolean[] results);
    private static native double nativeFastSin(double rad);
    private static native double nativeFastCos(double rad);
    private static native double nativeFastDistance3D(double dx, double dy, double dz);
    private static native long nativeFastHashPos(int x, int y, int z);
    private static native long nativeGetMemoryRssBytes();
}
