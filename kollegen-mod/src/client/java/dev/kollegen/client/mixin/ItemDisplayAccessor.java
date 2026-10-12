package dev.kollegen.client.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Display.ItemDisplay.class)
public interface ItemDisplayAccessor {
    @Accessor("DATA_ITEM_STACK_ID")
    static EntityDataAccessor<ItemStack> kollegen$getItemStackAccessor() {
        throw new AssertionError();
    }

    @Accessor("DATA_SCALE_ID")
    static EntityDataAccessor<org.joml.Vector3fc> kollegen$getScaleAccessor() {
        throw new AssertionError();
    }

    @Accessor("DATA_LEFT_ROTATION_ID")
    static EntityDataAccessor<org.joml.Quaternionfc> kollegen$getLeftRotationAccessor() {
        throw new AssertionError();
    }
}
