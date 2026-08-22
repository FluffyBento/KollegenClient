package dev.kollegen.client.mods.modules;

import dev.kollegen.client.KollegenMod;
import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.Module;
import dev.kollegen.client.mods.ModuleManager;

/**
 * In-Game-Toggle für den Vulkan-Renderer. Aktiviert den Vulkan-Modus
 * (VulkanMod-Fork + Beryl-Shaderloader) und deaktiviert automatisch die
 * OpenGL-Gruppe (Sodium + Iris) – und umgekehrt. Die eigentliche Exklusivität
 * wird von {@link RendererManager} durchgesetzt; dieser Toggle schreibt nur
 * den gewünschten Zustand und löst {@code apply()} aus. Wirkt nach Neustart.
 */
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
            // Toggle spiegelt den vom Launcher vorgegebenen Renderer-Zustand.
            this.enabled = RendererManager.desiredGroup() == RendererManager.Group.VULKAN;
        }

        @Override
        public void onEnable() {
            RendererManager.setDesired(RendererManager.Group.VULKAN);
            RendererManager.apply();
            risk = "Vulkan ist AKTIV – Minecraft neu starten, damit es lädt. (Sodium/Iris wurden automatisch deaktiviert.)";
        }

        @Override
        public void onDisable() {
            RendererManager.setDesired(RendererManager.Group.OPENGL);
            RendererManager.apply();
            risk = "Vulkan ist DEAKTIVIERT – Minecraft neu starten (lädt OpenGL). (Sodium/Iris wieder aktiv.)";
        }
    }
}
