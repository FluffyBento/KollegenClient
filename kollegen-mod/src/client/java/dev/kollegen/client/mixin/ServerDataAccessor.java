package dev.kollegen.client.mixin;

import net.minecraft.client.multiplayer.ServerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;


@Mixin(ServerData.class)
public interface ServerDataAccessor {

    @Accessor("ip")
    String kollegen$getIp();
}
