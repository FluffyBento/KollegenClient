package dev.kollegen.client.mixin;

import dev.kollegen.client.mods.modules.KollegenShader;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SectionCompiler.class)
public class SectionCompilerMixin {

    @Unique
    private static final ThreadLocal<RenderSectionRegion> COMPILE_REGION = new ThreadLocal<>();

    @Inject(method = "compile", at = @At("HEAD"))
    private void kollegen$beginCompileRegion(
            SectionPos sectionPos,
            RenderSectionRegion region,
            com.mojang.blaze3d.vertex.VertexSorting sorting,
            net.minecraft.client.renderer.SectionBufferBuilderPack builders,
            CallbackInfoReturnable<SectionCompiler.Results> cir
    ) {
        COMPILE_REGION.set(region);
    }

    @Redirect(
            method = "compile",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/block/FluidRenderer;tesselate(Lnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/client/renderer/block/FluidRenderer$Output;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/material/FluidState;)V"
            )
    )
    private void kollegen$tagFluid(
            FluidRenderer instance,
            BlockAndTintGetter level,
            BlockPos pos,
            FluidRenderer.Output output,
            BlockState state,
            FluidState fluid
    ) {
        KollegenShader.setTerrainFluidTag(fluid);
        instance.tesselate(level, pos, output, state, fluid);
    }

    @Redirect(
            method = "compile",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;getSeed(Lnet/minecraft/core/BlockPos;)J"
            )
    )
    private long kollegen$tagTerrainFoliage(BlockState state, BlockPos pos) {
        KollegenShader.setTerrainBlockTag(state, COMPILE_REGION.get(), pos);
        return state.getSeed(pos);
    }

    @Inject(method = "compile", at = @At("RETURN"))
    private void kollegen$clearTerrainFoliage(
            SectionPos sectionPos,
            RenderSectionRegion region,
            com.mojang.blaze3d.vertex.VertexSorting sorting,
            net.minecraft.client.renderer.SectionBufferBuilderPack builders,
            CallbackInfoReturnable<SectionCompiler.Results> cir
    ) {
        COMPILE_REGION.remove();
        KollegenShader.setTerrainBlockTag(null, null, null);
    }
}
