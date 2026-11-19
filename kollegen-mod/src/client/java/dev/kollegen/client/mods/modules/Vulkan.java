package dev.kollegen.client.mods.modules;

import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.Module;
import dev.kollegen.client.mods.ModuleManager;

public final class Vulkan {

    private Vulkan() {
    }

    public static void register() {
        ModuleManager.register(new VulkanModule());
    }

    private static final class VulkanModule extends Module {
        VulkanModule() {
            super("vulkan", "Vulkan Renderer",
                    "Moderner Vulkan-Renderer für hardwarenahe Grafikberechnung und maximale Bildraten mit nativer SPIR-V Pipeline.",
                    Category.PERFORMANCE);
            this.enabled = true;
            this.locked = true;
        }

        @Override
        public void onEnable() {
            this.enabled = true;
            RendererManager.setDesired(RendererManager.Group.VULKAN);
        }

        @Override
        public void onDisable() {
            this.enabled = true;
            RendererManager.setDesired(RendererManager.Group.VULKAN);
        }
    }
}
