package dev.kollegen.client;

import com.terraformersmc.modmenu.api.ModMenuApi;

import java.util.Set;

/**
 * Versteckt die vom Kollegen-Client eingebettet und verwaltet Renderer-Mods
 * (VulkanMod-Fork, Beryl, Sodium, Iris) in der ModMenu-Liste, sodass dort
 * faktisch nur die Kollegen-Client-Mod sichtbar ist. Die API ist optional:
 * ohne ModMenu wird dieser Entrypoint vom Loader ignoriert.
 */
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public Set<String> getModsToHide() {
        return Set.of("vulkanmod", "beryl", "sodium", "iris");
    }
}
