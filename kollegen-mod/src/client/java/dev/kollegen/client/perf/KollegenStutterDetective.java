package dev.kollegen.client.perf;

import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingStream;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

public final class KollegenStutterDetective {

    private static final Logger LOGGER = LoggerFactory.getLogger("KollegenPerformance");
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);

    private static final int SAMPLES_SIZE = 8192;
    private static final int SAMPLES_MASK = SAMPLES_SIZE - 1;
    private static final long[] FRAME_SAMPLES = new long[SAMPLES_SIZE];
    private static int sampleCursor = 0;

    private static long frameStartNano = 0;
    private static long windowStartMillis = System.currentTimeMillis();
    private static long totalFrames = 0;
    private static long totalNanoAccum = 0;

    private static boolean wasInLevel = false;
    private static boolean wasUnfocused = false;
    private static long warmupUntilMillis = 0;
    private static long levelNullSince = 0;

    private static volatile long lastGcTime = 0;
    private static volatile long lastGcDurationMs = 0;
    private static volatile String lastGcName = "";

    private static volatile long lastClassLoadTime = 0;
    private static volatile String lastLoadedClass = "";

    private static volatile long lastVmOpTime = 0;
    private static volatile String lastVmOpName = "";

    private KollegenStutterDetective() {}

    public static void init() {
        if (!STARTED.compareAndSet(false, true)) return;

        Thread streamThread = new Thread(() -> {
            try (RecordingStream rs = new RecordingStream()) {
                rs.enable("jdk.GarbageCollection").withThreshold(Duration.ofMillis(1));
                rs.enable("jdk.ClassLoad").withoutThreshold();
                rs.enable("jdk.ExecuteVMOperation").withThreshold(Duration.ofMillis(1));

                rs.onEvent("jdk.GarbageCollection", event -> {
                    lastGcTime = System.currentTimeMillis();
                    lastGcDurationMs = event.getDuration().toMillis();
                    String name = event.getString("name");
                    lastGcName = (name != null ? name : "GC") + " (" + lastGcDurationMs + " ms)";
                });

                rs.onEvent("jdk.ClassLoad", event -> {
                    lastClassLoadTime = System.currentTimeMillis();
                    try {
                        RecordedClass rc = event.getClass("loadedClass");
                        lastLoadedClass = rc != null ? rc.getName() : "Unbekannt";
                    } catch (Throwable ignored) {
                        lastLoadedClass = "Class";
                    }
                });

                rs.onEvent("jdk.ExecuteVMOperation", event -> {
                    lastVmOpTime = System.currentTimeMillis();
                    String op = event.getString("operation");
                    lastVmOpName = op != null ? op : "VM-Operation";
                });

                rs.start();
            } catch (Throwable t) {
                LOGGER.info("JFR Stutter Detective im Passiv-Modus (JFR nicht gestartet: {})", t.getMessage());
            }
        }, "Kollegen-JFR-StutterDetective");
        streamThread.setDaemon(true);
        streamThread.start();

        Thread prewarmThread = new Thread(() -> {
            String[] classes = {
                "net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer",
                "net.minecraft.client.renderer.SkyRenderer",
                "net.minecraft.client.renderer.LevelRenderer",
                "sun.nio.cs.ArrayEncoder",
                "net.minecraft.client.renderer.entity.AvatarRenderer",
                "net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher",
                "java.lang.invoke.MethodHandles",
                "java.lang.invoke.MethodType",
                "java.lang.invoke.ConstantCallSite"
            };
            for (String cls : classes) {
                try {
                    Class.forName(cls, true, KollegenStutterDetective.class.getClassLoader());
                } catch (Throwable ignored) {}
            }
            try {
                java.lang.invoke.MethodHandles.Lookup lookup = java.lang.invoke.MethodHandles.lookup();
                java.lang.invoke.MethodHandle mh = lookup.findStatic(Math.class, "max", java.lang.invoke.MethodType.methodType(int.class, int.class, int.class));
                int val = (int) mh.invokeExact(1, 2);
            } catch (Throwable ignored) {}
        }, "Kollegen-ClassPrewarmer");
        prewarmThread.setDaemon(true);
        prewarmThread.setPriority(Thread.MIN_PRIORITY);
        prewarmThread.start();
    }

    public static void onFrameStart() {
        frameStartNano = System.nanoTime();
    }

    public static void onFrameEnd() {
        long end = System.nanoTime();
        if (frameStartNano == 0) return;
        long durationNano = end - frameStartNano;
        frameStartNano = 0;

        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        long now = System.currentTimeMillis();

        if (mc == null || mc.level == null) {
            if (levelNullSince == 0) {
                levelNullSince = now;
            } else if (now - levelNullSince > 3000L) {
                if (wasInLevel) {
                    if (totalFrames >= 2000 && (now - windowStartMillis) >= 8000L) {
                        emitTelemetry(now);
                    }
                    wasInLevel = false;
                }
            }
            return;
        }

        levelNullSince = 0;

        boolean isFocused = mc.isWindowActive() && (mc.getWindow() == null || mc.getWindow().isFocused());
        if (!isFocused) {
            wasUnfocused = true;
            return;
        }

        if (wasUnfocused) {
            wasUnfocused = false;
            windowStartMillis = now;
            totalFrames = 0;
            totalNanoAccum = 0;
            return;
        }

        if (!wasInLevel) {
            wasInLevel = true;
            warmupUntilMillis = now + 3000L;
            windowStartMillis = warmupUntilMillis;
            totalFrames = 0;
            totalNanoAccum = 0;
            sampleCursor = 0;
            LOGGER.info("[Stutter-Detektiv] Welt beigetreten. Starte 3s Warmup-Phase vor Benchmark...");
            return;
        }

        if (now < warmupUntilMillis) {
            int idx = sampleCursor & SAMPLES_MASK;
            FRAME_SAMPLES[idx] = durationNano;
            sampleCursor++;
            windowStartMillis = warmupUntilMillis;
            totalFrames = 0;
            totalNanoAccum = 0;
            return;
        }

        int idx = sampleCursor & SAMPLES_MASK;
        FRAME_SAMPLES[idx] = durationNano;
        sampleCursor++;
        totalFrames++;
        totalNanoAccum += durationNano;

        double durationMs = durationNano / 1_000_000.0;

        // Stutter detection:
        // Calculate median of recent frames
        if (sampleCursor >= 64 && durationMs >= 14.0) {
            long medianNano = computeMedian();
            double medianMs = medianNano / 1_000_000.0;

            if (durationNano >= (medianNano * 2L)) {
                String cause = "Main-Thread / GPU Driver Stall";
                if (now - lastGcTime < 100) {
                    cause = "GC-Kollektion (" + lastGcName + ")";
                } else if (now - lastClassLoadTime < 100) {
                    cause = "Klassenlade-Vorgang (" + lastLoadedClass + ")";
                } else if (now - lastVmOpTime < 100) {
                    cause = "JVM SafePoint / VM-Operation (" + lastVmOpName + ")";
                }
                LOGGER.warn("[Stutter-Detektiv] Ruckler erkannt: {} ms (Median: {} ms) | Ursache: {}",
                        String.format(java.util.Locale.ROOT, "%.2f", durationMs),
                        String.format(java.util.Locale.ROOT, "%.2f", medianMs),
                        cause);
            }
        }

        // Telemetrie alle 10 Sekunden für Selbstoptimierenden Launcher
        if (now - windowStartMillis >= 10_000 && totalFrames >= 100) {
            emitTelemetry(now);
        }
    }

    private static long computeMedian() {
        int count = Math.min(sampleCursor, SAMPLES_SIZE);
        if (count == 0) return 1_000_000L;
        long[] copy = new long[count];
        System.arraycopy(FRAME_SAMPLES, 0, copy, 0, count);
        Arrays.sort(copy);
        return copy[count / 2];
    }

    private static void emitTelemetry(long now) {
        int count = Math.min(sampleCursor, SAMPLES_SIZE);
        if (count < 50 || totalFrames == 0) return;

        long[] copy = new long[count];
        System.arraycopy(FRAME_SAMPLES, 0, copy, 0, count);
        Arrays.sort(copy);

        // 1% Low is the 99th percentile frametime (1% slowest frames)
        int p99Idx = Math.min((int) (count * 0.99), count - 1);
        int p999Idx = Math.min((int) (count * 0.999), count - 1);

        long p99Nano = copy[p99Idx];
        long p999Nano = copy[p999Idx];

        double avgFrametimeMs = (double) totalNanoAccum / (totalFrames * 1_000_000.0);
        int avgFps = avgFrametimeMs > 0.0 ? (int) Math.round(1000.0 / avgFrametimeMs) : 0;
        int p1LowFps = p99Nano > 0 ? (int) Math.round(1_000_000_000.0 / p99Nano) : 0;
        int p01LowFps = p999Nano > 0 ? (int) Math.round(1_000_000_000.0 / p999Nano) : 0;

        LOGGER.info("[KOLLEGEN_PERF_TELEMETRY] avg_fps={} p1_low={} p01_low={} samples={}",
                avgFps, p1LowFps, p01LowFps, totalFrames);

        windowStartMillis = now;
        totalFrames = 0;
        totalNanoAccum = 0;
    }
}
