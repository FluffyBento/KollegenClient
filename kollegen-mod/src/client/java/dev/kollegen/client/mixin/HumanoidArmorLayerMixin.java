package dev.kollegen.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kollegen.client.mods.modules.ArmorTransparency;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidArmorLayer.class)
public class HumanoidArmorLayerMixin {

    @Inject(
        method = "renderArmorPiece",
        at = @At("HEAD"),
        cancellable = true
    )
    private void kollegen$onRenderArmorPiece(
        PoseStack poseStack,
        SubmitNodeCollector submitNodeCollector,
        ItemStack stack,
        EquipmentSlot slot,
        int lightCoords,
        HumanoidRenderState state,
        CallbackInfo ci
    ) {
        if (state instanceof AvatarRenderState ars) {
            boolean isSelf = ArmorTransparency.isSelf(ars);
            if (ArmorTransparency.shouldHideSlot(isSelf, slot)) {
                ci.cancel();
            }
        }
    }
}
