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
}
