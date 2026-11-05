package dev.kollegen.client.mods.modules;

import dev.kollegen.client.KollegenMod;
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
                    "Moderner Vulkan-Renderer für hardwarenahe Grafikberechnung und maximale Bildraten. Wirkt nach Neustart.",
                    Category.PERFORMANCE);
            this.enabled = RendererManager.desiredGroup() == RendererManager.Group.VULKAN;
        }

        @Override
        public void onEnable() {
            RendererManager.setDesired(RendererManager.Group.VULKAN);
            risk = "Vulkan AKTIV – starte Minecraft neu, um den nativen Vulkan-Renderer zu laden.";
        }

        @Override
        public void onDisable() {
            RendererManager.setDesired(RendererManager.Group.OPENGL);
            risk = "Vulkan DEAKTIVIERT – starte Minecraft neu, um den Standard-OpenGL-Renderer (Sodium+Iris) zu laden.";
        }
    }
}
