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
                    "Integrierter Vulkan-Renderer (VulkanMod) + Beryl-Shaderloader. Inkompatibel mit Sodium/Iris – diese werden automatisch deaktiviert. Wirkt nach Neustart.",
                    Category.PERFORMANCE);
            
            this.enabled = RendererManager.desiredGroup() == RendererManager.Group.VULKAN;
        }

        @Override
        public void onEnable() {
            
            
            if (dev.kollegen.client.mods.modules.RendererManager.essentialPresent(
                    net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("mods"))) {
                this.enabled = false;
                risk = "Vulkan mit Essential-Mod inkompatibel – bleibt auf OpenGL.";
                return;
            }
            
            RendererManager.setDesired(RendererManager.Group.VULKAN);
            risk = "Vulkan AKTIV – starte Minecraft neu, damit VulkanMod+Beryl laden.";
        }

        @Override
        public void onDisable() {
            
            RendererManager.setDesired(RendererManager.Group.OPENGL);
            risk = "Vulkan DEAKTIVIERT – starte Minecraft neu, damit Sodium+Iris laden.";
        }
    }
}
