package dev.kollegen.client.presence;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
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
            Identifier id = Identifier.fromNamespaceAndPath("kollegen", "blub");
            ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
            BLUB_ITEM = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)));
        } catch (Throwable t) {
            try {
                dev.kollegen.client.KollegenMod.LOGGER.warn("Blub item registration failed", t);
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

    public static void applyDisplayPose(Entity entity, float sx, float sy, float sz, float yaw) {
        try {
            if (!(entity instanceof Display.ItemDisplay)) return;
            EntityDataAccessor<org.joml.Vector3fc> scaleAccessor = dev.kollegen.client.mixin.DisplayAccessor.kollegen$getScaleAccessor();
            EntityDataAccessor<org.joml.Quaternionfc> rotAccessor = dev.kollegen.client.mixin.DisplayAccessor.kollegen$getLeftRotationAccessor();
            if (scaleAccessor == null || rotAccessor == null) return;
            entity.getEntityData().set(scaleAccessor, new org.joml.Vector3f(sx, sy, sz));
            entity.getEntityData().set(rotAccessor, new org.joml.Quaternionf().rotateY(yaw));
        } catch (Throwable ignored) {
        }
    }
}
