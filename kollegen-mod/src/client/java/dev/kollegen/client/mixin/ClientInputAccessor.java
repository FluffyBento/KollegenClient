package dev.kollegen.client.mixin;

import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;


@Mixin(ClientInput.class)
public interface ClientInputAccessor {

    @Accessor("keyPresses")
    void kollegen$setKeyPresses(Input keyPresses);

    @Accessor("moveVector")
    void kollegen$setMoveVector(Vec2 moveVector);
}