package dev.kollegen.client.mixin;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Zugriff auf Slot.x/Slot.y ohne Reflection. Die Felder sind final, daher wird
 * ueber den refmap-remapped @Accessor-Setter (putfield) geschrieben – ein
 * direktes s.x = ... ist nicht moeglich (final) und Class.getField("x") wuerde
 * unter Intermediary mit NoSuchFieldException scheitern.
 */
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
