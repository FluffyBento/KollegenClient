package dev.kollegen.client.mixin;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;


@Mixin(Slot.class)
public interface SlotAccessor {

    @Accessor("x")
    int kollegen$getX();

    @Accessor("x")
    void kollegen$setX(int x);

    @Accessor("y")
    int kollegen$getY();

    @Accessor("y")
    void kollegen$setY(int y);
}
