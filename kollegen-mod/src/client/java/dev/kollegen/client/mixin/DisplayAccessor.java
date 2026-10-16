package dev.kollegen.client.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Display.class)
public interface DisplayAccessor {
    @Accessor("DATA_SCALE_ID")
    static EntityDataAccessor<org.joml.Vector3fc> kollegen$getScaleAccessor() {
        throw new AssertionError();
    }

    @Accessor("DATA_LEFT_ROTATION_ID")
    static EntityDataAccessor<org.joml.Quaternionfc> kollegen$getLeftRotationAccessor() {
        throw new AssertionError();
    }
}
