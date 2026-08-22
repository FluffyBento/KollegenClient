package dev.kollegen.client.mods.modules;

import dev.kollegen.client.KollegenMod;
import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.Module;
import dev.kollegen.client.mods.ModuleManager;

/**
 * In-Game-Toggle für den Vulkan-Renderer. Aktiviert den Vulkan-Modus
 * (VulkanMod-Fork + Beryl-Shaderloader) und deaktiviert automatisch die
 * OpenGL-Gruppe (Sodium + Iris) – und umgekehrt.
 *
 * WICHTIG: Der Toggle darf die Renderer-Jars NIEMALS zur Laufzeit umbenennen
 * ({@code apply()}): Die Klassen der aktuell geladenen Gruppe sind im JVM
 * bereits geladen und hängen an nativen GL/Vulkan-Kontexten – ein Umbenennen
 * führt zu hartem SIGSEGV (libnvidia-glcore o.ä.). Der Toggle schreibt
 * ausschließlich die State-Datei; der Launcher übernimmt sie beim nächsten
 * Start (Zwei-Wege-Sync) und stimmt die .disabled-States ab, bevor Minecraft
 * hochfährt.
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
            // Nur Wunsch speichern – kein Datei-Umbau im laufenden Spiel!
            RendererManager.setDesired(RendererManager.Group.VULKAN);
            risk = "Vulkan AKTIV – starte Minecraft neu, damit VulkanMod+Beryl laden.";
        }

        @Override
        public void onDisable() {
            // Nur Wunsch speichern – kein Datei-Umbau im laufenden Spiel!
            RendererManager.setDesired(RendererManager.Group.OPENGL);
            risk = "Vulkan DEAKTIVIERT – starte Minecraft neu, damit Sodium+Iris laden.";
        }
    }
}
