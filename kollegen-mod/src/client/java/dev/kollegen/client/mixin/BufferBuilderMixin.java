package dev.kollegen.client.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.kollegen.client.mods.modules.KollegenShader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(BufferBuilder.class)
public class BufferBuilderMixin {

    @Shadow
    private boolean blockFormat;

    @ModifyVariable(
            method = "addVertex(FFFIFFIIFFF)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 2
    )
    private int kollegen$tagBlockVertexLight(int light) {
        if (!this.blockFormat) {
            return light;
        }
        return KollegenShader.applyTerrainTag(light);
    }

    @ModifyVariable(
            method = "setLight(I)Lcom/mojang/blaze3d/vertex/VertexConsumer;",
            at = @At("HEAD"),
            argsOnly = true
    )
    private int kollegen$tagSetLight(int light) {
        if (!this.blockFormat) {
            return light;
        }
        return KollegenShader.applyTerrainTag(light);
    }
}
