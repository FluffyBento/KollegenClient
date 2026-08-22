package dev.kollegen.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kollegen.client.mods.modules.World;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.world.level.MoonPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Blendet Sonne und Mond im Überworld-Himmel aus (Setting "Himmelskörper"
 * in der Welt-Kategorie). Die privaten Zeichenmethoden von {@link SkyRenderer}
 * werden an der HEAD abgebrochen, sobald das jeweilige Setting aktiv ist.
 * Sterne bleiben sichtbar.
 */
@Mixin(SkyRenderer.class)
public class SkyBodiesMixin {

    @Inject(method = "renderSun(FLcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("HEAD"), cancellable = true)
    private void kollegen$hideSun(float alpha, PoseStack matrices, CallbackInfo ci) {
        if (World.hideSun) ci.cancel();
    }

    @Inject(method = "renderMoon(Lnet/minecraft/world/level/MoonPhase;FLcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("HEAD"), cancellable = true)
    private void kollegen$hideMoon(MoonPhase moonPhase, float alpha, PoseStack matrices, CallbackInfo ci) {
        if (World.hideMoon) ci.cancel();
    }
}
