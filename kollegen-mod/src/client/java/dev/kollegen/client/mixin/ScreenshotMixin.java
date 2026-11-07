package dev.kollegen.client.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.kollegen.client.feed.FeedImageManager;
import dev.kollegen.client.menu.ScreenshotShareScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.io.File;
import java.util.function.Consumer;

@Mixin(Screenshot.class)
public class ScreenshotMixin {

    @ModifyVariable(
        method = "grab(Ljava/io/File;Ljava/lang/String;Lcom/mojang/blaze3d/pipeline/RenderTarget;ILjava/util/function/Consumer;)V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private static Consumer<Component> kollegen$interceptScreenshotCallback(Consumer<Component> original) {
        return component -> {
            if (original != null) {
                original.accept(component);
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                mc.execute(() -> {
                    File latest = FeedImageManager.getLatestScreenshot();
                    if (latest != null) {
                        mc.setScreenAndShow(new ScreenshotShareScreen(mc.gui.screen(), latest));
                    }
                });
            }
        };
    }
}
