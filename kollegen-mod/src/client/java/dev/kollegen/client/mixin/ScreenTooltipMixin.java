package dev.kollegen.client.mixin;

import dev.kollegen.client.mods.modules.Appleskin;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;


@Mixin(Screen.class)
public class ScreenTooltipMixin {

    @Inject(method = "getTooltipFromItem", at = @At("TAIL"))
    private static void kollegen$appleskinTooltips(Minecraft minecraft, ItemStack stack,
                                                   CallbackInfoReturnable<List<Component>> cir) {
        if (!Appleskin.tooltipsActive()) return;
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food == null) return;
        List<Component> lines = cir.getReturnValue();
        if (lines.isEmpty()) return;
        if (food.nutrition() > 0) {
            lines.add(Component.literal("+" + food.nutrition() + " Hunger")
                    .withStyle(ChatFormatting.GOLD));
        }
        if (food.saturation() > 0f) {
            lines.add(Component.literal("+"
                    + String.format(java.util.Locale.US, "%.1f", food.saturation()) + " Sättigung")
                    .withStyle(ChatFormatting.YELLOW));
        }
    }
}
