package dev.kollegen.client.mixin;

import net.minecraft.world.entity.animal.axolotl.Axolotl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Axolotl.class)
public interface AxolotlAccessor {
    @Invoker("setVariant")
    void kollegen$setVariant(Axolotl.Variant variant);
}
