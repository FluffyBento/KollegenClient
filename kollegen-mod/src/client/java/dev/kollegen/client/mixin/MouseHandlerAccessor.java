package dev.kollegen.client.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;


@Mixin(MouseHandler.class)
public interface MouseHandlerAccessor {

    @Invoker("method_22684")
    void kollegen$click(long window, int button, int action, int mods);
}
