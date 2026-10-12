package dev.kollegen.client.presence;

import dev.kollegen.client.KollegenMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class BlubPets {
    public static Item BLUB_ITEM;

    private BlubPets() {}

    public static void register() {
        try {
            if (BLUB_ITEM != null) return;
            BLUB_ITEM = Registry.register(BuiltInRegistries.ITEM, Identifier.fromNamespaceAndPath("kollegen", "blub"), new Item(new Item.Properties()));
        } catch (Throwable t) {
            try {
                KollegenMod.LOGGER.warn("Blub item registration failed", t);
            } catch (Throwable ignored) {
            }
        }
    }

    public static boolean isBlubDisplay(Entity entity) {
        try {
            return entity instanceof Display.ItemDisplay;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void setupDisplay(Entity entity) {
        try {
            if (!(entity instanceof Display.ItemDisplay)) return;
            if (BLUB_ITEM == null) return;
            EntityDataAccessor<ItemStack> accessor = dev.kollegen.client.mixin.ItemDisplayAccessor.kollegen$getItemStackAccessor();
            if (accessor == null) return;
            entity.getEntityData().set(accessor, new ItemStack(BLUB_ITEM));
        } catch (Throwable ignored) {
        }
    }
}
