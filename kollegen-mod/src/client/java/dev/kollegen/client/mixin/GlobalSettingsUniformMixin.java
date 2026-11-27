package dev.kollegen.client.mixin;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import dev.kollegen.client.mods.modules.KollegenShader;
import net.minecraft.client.renderer.GlobalSettingsUniform;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.system.MemoryStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;

@Mixin(GlobalSettingsUniform.class)
public class GlobalSettingsUniformMixin {

    @Unique
    private static final int KOLL_UBO_SIZE = 384;

    @Shadow
    @Final
    @Mutable
    private GpuBuffer buffer;

    @Unique
    private final float[] kollegen$shaderData = new float[84];

    @Inject(method = "<init>", at = @At("RETURN"))
    private void kollegen$expandBuffer(CallbackInfo ci) {
        try {
            if (this.buffer != null) {
                this.buffer.close();
            }
            this.buffer = RenderSystem.getDevice().createBuffer(() -> "Global Settings UBO", 136, KOLL_UBO_SIZE);
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void kollegen$updateExpanded(
            int width,
            int height,
            double glintStrength,
            long gameTime,
            float worldPartialTicks,
            int menuBlur,
            Vec3 cameraPos,
            boolean rgss,
            CallbackInfo ci
    ) {
        if (this.buffer == null || this.buffer.isClosed() || this.buffer.size() < KOLL_UBO_SIZE) {
            return;
        }
        try {
            KollegenShader.populateGlobalUniforms(this.kollegen$shaderData, cameraPos, worldPartialTicks);
            int camX = Mth.floor(cameraPos.x);
            int camY = Mth.floor(cameraPos.y);
            int camZ = Mth.floor(cameraPos.z);
            float[] d = this.kollegen$shaderData;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                Std140Builder b = Std140Builder.onStack(stack, KOLL_UBO_SIZE)
                        .putIVec3(camX, camY, camZ)
                        .putFloat((float) glintStrength)
                        .putVec3((float) ((double) camX - cameraPos.x), (float) ((double) camY - cameraPos.y), (float) ((double) camZ - cameraPos.z))
                        .putFloat((float) (gameTime % 24000L) + worldPartialTicks)
                        .putVec2((float) width, (float) height)
                        .putInt(menuBlur)
                        .putInt(rgss ? 1 : 0)
                        .putVec4(d[0], d[1], d[2], d[3])
                        .putVec4(d[4], d[5], d[6], d[7])
                        .putVec4(d[8], d[9], d[10], d[11])
                        .putVec4(d[12], d[13], d[14], d[15]);
                for (int i = 0; i < 16; i++) {
                    int k = 16 + i * 4;
                    b.putVec4(d[k], d[k + 1], d[k + 2], d[k + 3]);
                }
                b.putVec4(d[80], d[81], d[82], d[83]);
                ByteBuffer buf = b.get();
                buf.limit(KOLL_UBO_SIZE);
                buf.position(0);
                RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.buffer.slice(), buf);
            }
            RenderSystem.setGlobalSettingsUniform(this.buffer);
            ci.cancel();
        } catch (Throwable ignored) {
        }
    }
}
