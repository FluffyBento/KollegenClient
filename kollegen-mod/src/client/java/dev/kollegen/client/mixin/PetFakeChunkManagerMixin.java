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

    private static final java.lang.invoke.MethodHandle SODIUM_GET_TRACKER;
    private static final java.lang.invoke.MethodHandle SODIUM_ADD_STATUS;

    static {
        java.lang.invoke.MethodHandle getHandle = null;
        java.lang.invoke.MethodHandle addHandle = null;
        try {
            Class<?> holder = Class.forName("net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTrackerHolder");
            java.lang.invoke.MethodHandles.Lookup lookup = java.lang.invoke.MethodHandles.publicLookup();
            java.lang.reflect.Method gm = holder.getMethod("get", net.minecraft.client.multiplayer.ClientLevel.class);
            getHandle = lookup.unreflect(gm);
            Class<?> trackerClass = gm.getReturnType();
            java.lang.reflect.Method am = trackerClass.getMethod("onChunkStatusAdded", int.class, int.class, int.class);
            addHandle = lookup.unreflect(am);
        } catch (Throwable ignored) {
        }
        SODIUM_GET_TRACKER = getHandle;
        SODIUM_ADD_STATUS = addHandle;
    }

    @Shadow
    private ClientChunkCache clientChunkManager;

    @Inject(method = "shouldBeLoaded(II)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void kollegen$alwaysKeepLoaded(int x, int z, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if (dev.kollegen.client.mods.modules.Performance.KollegenDistance.isInfinite()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "unload(IIZ)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void kollegen$preventUnload(int x, int z, boolean save, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if (dev.kollegen.client.mods.modules.Performance.KollegenDistance.isInfinite()) {
            cir.setReturnValue(false);
        }
    }

    @org.spongepowered.asm.mixin.injection.ModifyArg(
            method = "update(ZLjava/util/function/BooleanSupplier;)V",
            at = @At(value = "INVOKE", target = "Lde/johni0702/minecraft/bobby/FakeChunkManager;update(ZLjava/util/function/BooleanSupplier;I)V"),
            index = 2,
            remap = false
    )
    private int kollegen$overrideFakeChunkViewDistance(int viewDistance) {
        if (dev.kollegen.client.mods.modules.Performance.KollegenDistance.isDistanceEnabled()) {
            return dev.kollegen.client.mods.modules.Performance.KollegenDistance.getDistanceLimit();
        }
        return viewDistance;
    }

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void kollegen$initStorageSync(net.minecraft.client.multiplayer.ClientLevel world, ClientChunkCache clientChunkManager, CallbackInfo ci) {
        try {
            java.lang.reflect.Field storageField = this.getClass().getDeclaredField("storage");
            storageField.setAccessible(true);
            Object storageObj = storageField.get(this);
            if (storageObj == null) return;

            Class<?> storageClass = Class.forName("de.johni0702.minecraft.bobby.FakeChunkStorage");
            java.lang.reflect.Field dirField = storageClass.getDeclaredField("directory");
            dirField.setAccessible(true);
            java.nio.file.Path activeDir = (java.nio.file.Path) dirField.get(storageObj);
            if (activeDir == null || !java.nio.file.Files.exists(activeDir)) return;

            java.nio.file.Path dimPath = activeDir.getParent();
            if (dimPath == null) return;
            java.nio.file.Path seedPath = dimPath.getParent();
            if (seedPath == null) return;
            java.nio.file.Path serverPath = seedPath.getParent();
            if (serverPath == null) return;
            java.nio.file.Path bobbyRoot = serverPath.getParent();
            if (bobbyRoot == null || !java.nio.file.Files.isDirectory(bobbyRoot)) return;

            String currentServer = serverPath.getFileName().toString();
            String baseServerName = currentServer.replaceAll("(_|:)\\d+$", "");
            java.nio.file.Path relDim = seedPath.relativize(activeDir);

            java.lang.reflect.Field storagesField = this.getClass().getDeclaredField("storages");
            storagesField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.List<Object> storagesList = (java.util.List<Object>) storagesField.get(this);

            java.lang.reflect.Method getForMethod = storageClass.getMethod("getFor", java.nio.file.Path.class, boolean.class);
            java.lang.reflect.Method loadTagMethod = storageClass.getMethod("loadTag", net.minecraft.world.level.ChunkPos.class);

            java.util.List<java.nio.file.Path> dirsToCopy = new java.util.ArrayList<>();

            try (java.util.stream.Stream<java.nio.file.Path> serverStream = java.nio.file.Files.list(bobbyRoot)) {
                serverStream.filter(java.nio.file.Files::isDirectory).forEach(candServer -> {
                    String candName = candServer.getFileName().toString();
                    String candBase = candName.replaceAll("(_|:)\\d+$", "");
                    if (candBase.equalsIgnoreCase(baseServerName)) {
                        try (java.util.stream.Stream<java.nio.file.Path> seedStream = java.nio.file.Files.list(candServer)) {
                            seedStream.filter(java.nio.file.Files::isDirectory).forEach(candSeed -> {
                                java.nio.file.Path candDimDir = candSeed.resolve(relDim);
                                if (java.nio.file.Files.isDirectory(candDimDir) && !candDimDir.equals(activeDir)) {
                                    dirsToCopy.add(candDimDir);
                                    try {
                                        Object fallbackStorage = getForMethod.invoke(null, candDimDir, false);
                                        if (fallbackStorage != null && storagesList != null) {
                                            java.util.function.Function<net.minecraft.world.level.ChunkPos, Object> fn = pos -> {
                                                try {
                                                    return loadTagMethod.invoke(fallbackStorage, pos);
                                                } catch (Throwable t) {
                                                    return java.util.concurrent.CompletableFuture.completedFuture(java.util.Optional.empty());
                                                }
                                            };
                                            storagesList.add(fn);
                                        }
                                    } catch (Throwable ignored) {
                                    }
                                }
                            });
                        } catch (Throwable ignored) {
                        }
                    }
                });
            }

            if (!dirsToCopy.isEmpty()) {
                java.util.concurrent.CompletableFuture.runAsync(() -> {
                    for (java.nio.file.Path candDimDir : dirsToCopy) {
                        try (java.util.stream.Stream<java.nio.file.Path> fileStream = java.nio.file.Files.list(candDimDir)) {
                            fileStream.filter(p -> p.getFileName().toString().endsWith(".mca")).forEach(mca -> {
                                java.nio.file.Path target = activeDir.resolve(mca.getFileName());
                                try {
                                    if (!java.nio.file.Files.exists(target) || java.nio.file.Files.size(target) < java.nio.file.Files.size(mca)) {
                                        java.nio.file.Files.copy(mca, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                                    }
                                } catch (Throwable ignored) {
                                }
                            });
                        } catch (Throwable ignored) {
                        }
                    }
                });
            }
        } catch (Throwable ignored) {
        }
    }

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
        if (SODIUM_GET_TRACKER != null && SODIUM_ADD_STATUS != null) {
            try {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mc != null && mc.level != null) {
                    Object tracker = SODIUM_GET_TRACKER.invoke(mc.level);
                    if (tracker != null) {
                        for (int dx = -1; dx <= 1; dx++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                int nx = x + dx;
                                int nz = z + dz;
                                if (this.clientChunkManager.getChunk(nx, nz, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false) != null) {
                                    SODIUM_ADD_STATUS.invoke(tracker, nx, nz, 3);
                                }
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }
}
