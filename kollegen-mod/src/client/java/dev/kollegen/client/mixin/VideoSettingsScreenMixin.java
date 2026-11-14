package dev.kollegen.client.mixin;

import dev.kollegen.client.mods.modules.Performance;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VideoSettingsScreen.class)
public abstract class VideoSettingsScreenMixin extends OptionsSubScreen {

    protected VideoSettingsScreenMixin() {
        super(null, null, null);
    }

    @Inject(method = "addOptions", at = @At("TAIL"))
    private void kollegen$addDistanceOptions(CallbackInfo ci) {
        try {
            if (Performance.KollegenDistance.isDistanceEnabled() && this.list != null) {
                this.list.addHeader(Component.literal("§6Kollegen Distance (Erweiterte Sichtweite)"));
                OptionInstance<Integer> serverDistOption = new OptionInstance<>(
                        "kollegen.server_distance",
                        OptionInstance.noTooltip(),
                        (caption, val) -> Component.literal("Normale Chunks: " + val + " Chunks (Server)"),
                        new OptionInstance.IntRange(2, 32),
                        Performance.KollegenDistance.getActualDistance(),
                        val -> {
                            Performance.KollegenDistance.actualDistance.value = val;
                            Performance.KollegenDistance.syncDistance();
                        }
                );
                OptionInstance<Integer> fakeChunksOption = new OptionInstance<>(
                        "kollegen.fake_chunks",
                        OptionInstance.noTooltip(),
                        (caption, val) -> Component.literal("Fake-Chunks: " + (val >= 1000 ? "Unbegrenzt" : val + " Chunks")),
                        new OptionInstance.IntRange(32, 1024),
                        Performance.KollegenDistance.getDistanceLimit(),
                        val -> {
                            Performance.KollegenDistance.distanceLimit.value = val;
                            Performance.KollegenDistance.syncDistance();
                        }
                );
                this.list.addSmall(fakeChunksOption, serverDistOption);
            }
        } catch (Throwable ignored) {
        }
    }
}
