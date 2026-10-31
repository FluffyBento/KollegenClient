package dev.kollegen.client.mods.modules;

import dev.kollegen.client.mods.BooleanSetting;
import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.Module;
import dev.kollegen.client.mods.SliderSetting;

public class ItemPhysics extends Module {
    public static ItemPhysics INSTANCE;

    public final BooleanSetting lieFlat = new BooleanSetting(
            "Flach am Boden liegen",
            "Gedroppte Items liegen flach auf Blöcken statt in der Luft zu schweben.",
            true
    );

    public final SliderSetting rotationSpeed = new SliderSetting(
            "Rotations-Geschwindigkeit",
            "Geschwindigkeit beim Fallen/Werfen (0 = keine Rotation).",
            1.0, 0.0, 3.0, 0.2,
            v -> String.format("%.1fx", v), null
    );

    public ItemPhysics() {
        super("itemphysics", "Item Physics", "Items fallen realistisch zu Boden und liegen flach auf Oberflächen.", Category.VISUAL);
        INSTANCE = this;
        this.enabled = true;
        add(lieFlat);
        add(rotationSpeed);
    }

    public static boolean isItemPhysicsEnabled() {
        return INSTANCE != null && INSTANCE.enabled;
    }

    public static boolean shouldLieFlat() {
        return INSTANCE != null && INSTANCE.enabled && INSTANCE.lieFlat.value;
    }

    public static float getRotationSpeed() {
        return INSTANCE != null ? (float) INSTANCE.rotationSpeed.value : 1.0f;
    }
}
