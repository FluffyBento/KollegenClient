package dev.kollegen.client.mixin;

import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Frustum.class)
public interface FrustumAccessor {
    @Invoker("cubeInFrustum")
    int kollegen$cubeInFrustum(double minX, double minY, double minZ, double maxX, double maxY, double maxZ);
}
