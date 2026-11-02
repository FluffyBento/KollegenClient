package dev.kollegen.client.mixin;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;


@Mixin(MouseHandler.class)
public interface MouseHandlerAccessor {

    @Invoker("onButton")
    void kollegen$click(long window, MouseButtonInfo info, int action);
}
