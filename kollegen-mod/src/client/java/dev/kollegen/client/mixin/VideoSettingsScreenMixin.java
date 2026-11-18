package dev.kollegen.client.mixin;

import dev.kollegen.client.menu.KollegenVideoSettingsScreen;
import dev.kollegen.client.mods.modules.Performance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VideoSettingsScreen.class)
public abstract class VideoSettingsScreenMixin extends OptionsSubScreen {

    protected VideoSettingsScreenMixin() {
        super(null, null, null);
    }

    @Inject(method = "addOptions", at = @At("HEAD"), cancellable = true)
    private void kollegen$redirectVideoScreen(CallbackInfo ci) {
        ci.cancel();
        Screen parent = this.lastScreen;
        if (parent == null) {
            parent = new OptionsScreen(null, Minecraft.getInstance().options);
        }
        Minecraft.getInstance().setScreenAndShow(new KollegenVideoSettingsScreen(parent));
    }

    @Inject(method = "qualityOptions", at = @At("RETURN"), cancellable = true)
    private static void kollegen$overrideQualityOptions(Options options, CallbackInfoReturnable<OptionInstance<?>[]> cir) {
        try {
            if (Performance.KollegenDistance.isDistanceEnabled()) {
                OptionInstance<?>[] original = cir.getReturnValue();
                if (original != null && original.length >= 5) {
                    OptionInstance<Integer> inactiveChunksOption = new OptionInstance<>(
                            "kollegen.inactive_chunks",
                            OptionInstance.noTooltip(),
                            (caption, val) -> Component.literal("Inaktive Chunks: " + (val >= 250 ? "Unbegrenzt" : val + " Chunks")),
                            new OptionInstance.IntRange(16, 256),
                            Performance.KollegenDistance.getDistanceLimit(),
                            val -> {
                                Performance.KollegenDistance.distanceLimit.value = val;
                                Performance.KollegenDistance.syncDistance();
                            }
                    );
                    OptionInstance<?>[] modified = new OptionInstance<?>[original.length + 1];
                    modified[0] = inactiveChunksOption;
                    modified[1] = original[0];
                    modified[2] = options.renderDistance();
                    modified[3] = options.simulationDistance();
                    modified[4] = original[2];
                    modified[5] = original[4];
                    for (int i = 5; i < original.length; i++) {
                        modified[i + 1] = original[i];
                    }
                    cir.setReturnValue(modified);
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
