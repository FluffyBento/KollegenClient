package dev.kollegen.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;


@Mixin(Minecraft.class)
public interface MinecraftAccessor {

    @Invoker("getCurrentServer")
    ServerData kollegen$getCurrentServer();
    
    
    @Invoker("startAttack")
    boolean kollegen$startAttack();
    
    
    @Invoker("startUseItem")
    void kollegen$startUseItem();

    @Accessor("submitNodeCollector")
    SubmitNodeCollector kollegen$getSubmitNodeCollector();

    @Accessor("gameRenderer")
    net.minecraft.client.renderer.GameRenderer kollegen$getGameRenderer();
}
