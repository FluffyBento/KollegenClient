package dev.kollegen.client.perf;

import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

public final class KollegenStutterDetective {

    private static final Logger LOGGER = LoggerFactory.getLogger("KollegenPerformance");
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);

    private static final int SAMPLES_SIZE = 1024;
    private static final int SAMPLES_MASK = SAMPLES_SIZE - 1;
    private static final long[] FRAME_SAMPLES = new long[SAMPLES_SIZE];
    private static int sampleCursor = 0;

    private static long frameStartNano = 0;
    private static long windowStartMillis = System.currentTimeMillis();
    private static long totalFrames = 0;
    private static long totalNanoAccum = 0;
    private static long runningAvgNano = 1_000_000L;

    private static boolean wasInLevel = false;
    private static boolean wasUnfocused = false;
    private static long warmupUntilMillis = 0;
    private static long levelNullSince = 0;

    private KollegenStutterDetective() {}

    public static void init() {
        if (!STARTED.compareAndSet(false, true)) return;

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
                "java.lang.invoke.ConstantCallSite",
                "net.minecraft.client.renderer.RenderType",
                "net.minecraft.client.renderer.chunk.SectionCompiler",
                "net.minecraft.client.renderer.culling.Frustum",
                "net.minecraft.client.renderer.entity.EntityRenderer",
                "net.minecraft.client.renderer.block.ModelBlockRenderer",
                "net.minecraft.world.level.block.state.BlockState",
                "net.minecraft.core.BlockPos",
                "net.minecraft.world.phys.Vec3",
                "net.minecraft.client.renderer.texture.TextureAtlas",
                "net.minecraft.client.renderer.texture.TextureAtlasSprite",
                "net.minecraft.client.gui.GuiGraphicsExtractor",
                "net.minecraft.client.gui.Font",
                "net.minecraft.network.chat.Component",
                "net.minecraft.network.chat.MutableComponent",
                "net.minecraft.client.renderer.MultiBufferSource",
                "net.minecraft.client.renderer.RenderBuffers",
                "net.minecraft.client.renderer.PostChain",
                "net.minecraft.client.renderer.GameRenderer",
                "net.minecraft.client.renderer.FogRenderer",
                "net.minecraft.client.renderer.LightTexture",
                "net.minecraft.client.renderer.CloudRenderer",
                "net.minecraft.client.renderer.WeatherEffectRenderer",
                "net.minecraft.client.renderer.entity.ItemRenderer",
                "net.minecraft.client.renderer.item.ItemModelResolver",
                "net.minecraft.client.resources.model.ModelManager",
                "net.minecraft.client.resources.model.BakedModel",
                "net.minecraft.client.multiplayer.ClientLevel",
                "net.minecraft.client.player.LocalPlayer",
                "net.minecraft.world.entity.Entity",
                "net.minecraft.world.entity.LivingEntity",
                "net.minecraft.world.item.ItemStack",
                "net.minecraft.world.level.chunk.LevelChunk",
                "net.minecraft.world.level.Level",
                "net.minecraft.server.level.ServerLevel",
                "net.minecraft.network.protocol.Packet",
                "net.minecraft.network.protocol.game.ClientboundLevelChunkPacket",
                "java.util.concurrent.ConcurrentHashMap",
                "java.util.ArrayList",
                "java.util.HashMap",
                "java.util.HashSet",
                "java.util.TreeMap",
                "java.util.concurrent.atomic.AtomicReference",
                "java.util.concurrent.locks.ReentrantLock",
                "java.lang.reflect.Proxy",
                "java.lang.reflect.Method",
                "java.lang.reflect.Field",
                "sun.misc.Unsafe",
                "jdk.internal.reflect.ReflectionFactory"
            };
            for (String cls : classes) {
                try {
                    Class.forName(cls, true, KollegenStutterDetective.class.getClassLoader());
                } catch (Throwable ignored) {}
            }
            try {
                java.lang.invoke.MethodHandles.Lookup lookup = java.lang.invoke.MethodHandles.lookup();
                java.lang.invoke.MethodHandle mh = lookup.findStatic(Math.class, "max", java.lang.invoke.MethodType.methodType(int.class, int.class, int.class));
                mh.invokeExact(1, 2);
            } catch (Throwable ignored) {}
        }, "Kollegen-ClassPrewarmer");
        prewarmThread.setDaemon(true);
        prewarmThread.setPriority(Thread.NORM_PRIORITY);
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

        Minecraft mc = Minecraft.getInstance();
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
            runningAvgNano = durationNano;
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

        runningAvgNano = (runningAvgNano * 31 + durationNano) >> 5;

        double durationMs = durationNano / 1_000_000.0;

        if (sampleCursor >= 64 && durationMs >= 16.0 && (sampleCursor & 7) == 0) {
            if (durationNano >= (runningAvgNano * 3L)) {
                double avgMs = runningAvgNano / 1_000_000.0;
                LOGGER.warn("[Stutter-Detektiv] Ruckler erkannt: {} ms (Durchschnitt: {} ms)",
                        String.format(java.util.Locale.ROOT, "%.2f", durationMs),
                        String.format(java.util.Locale.ROOT, "%.2f", avgMs));
            }
        }

        if (now - windowStartMillis >= 10_000 && totalFrames >= 100) {
            emitTelemetry(now);
        }
    }

    private static void emitTelemetry(long now) {
        if (totalFrames == 0) return;

        double avgFrametimeMs = (double) totalNanoAccum / (totalFrames * 1_000_000.0);
        int avgFps = avgFrametimeMs > 0.0 ? (int) Math.round(1000.0 / avgFrametimeMs) : 0;

        LOGGER.info("[KOLLEGEN_PERF_TELEMETRY] avg_fps={} samples={}", avgFps, totalFrames);

        windowStartMillis = now;
        totalFrames = 0;
        totalNanoAccum = 0;
    }
}
