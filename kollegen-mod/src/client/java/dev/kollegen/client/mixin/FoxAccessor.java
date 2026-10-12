package dev.kollegen.client.mixin;

import net.minecraft.world.entity.animal.fox.Fox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Fox.class)
public interface FoxAccessor {
    @Invoker("setVariant")
    void kollegen$setVariant(Fox.Variant variant);
}
