package dev.kollegen.client.mods.modules;

import dev.kollegen.client.mods.BooleanSetting;
import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.ColorSetting;
import dev.kollegen.client.mods.Module;
import dev.kollegen.client.mods.ModuleManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.food.FoodProperties;


public final class Appleskin extends Module {

    
    private static Appleskin instance = null;

    public final BooleanSetting showSaturation = new BooleanSetting(
            "Sättigung", "Zeigt die aktuelle Sättigung als Balken über der Hungerleiste.", true);
    public final BooleanSetting showPreview = new BooleanSetting(
            "Essens-Vorschau", "Zeigt, was das gehaltene Essen auffüllen würde.", true);
    public final BooleanSetting showTooltips = new BooleanSetting(
            "Nährwert-Tooltips", "Fügt Speisen Hunger-/Sättigungswerte im Tooltip hinzu.", true);
    public final ColorSetting satColor = new ColorSetting(
            "Sättigungsfarbe", "", 0xFF_DFA33A);
    public final ColorSetting previewColor = new ColorSetting(
            "Vorschau-Farbe", "", 0xFF_3EC46D);

    private Appleskin() {
        super("appleskin", "AppleSkin",
                "Sättigungsbalken, Essens-Vorschau und Nährwert-Tooltips (wie die Mod AppleSkin, frei einstellbar).",
                Category.HUD);
        add(showSaturation);
        add(showPreview);
        add(showTooltips);
        add(satColor);
        add(previewColor);
        instance = this;
    }

    public static boolean tooltipsActive() {
        Appleskin a = instance;
        return a != null && a.enabled && a.showTooltips.value;
    }

    public static void register() {
        ModuleManager.register(new Appleskin());
    }

    @Override
    public void onRenderHud(GuiGraphics g, float td) {
        if (mc.player == null || mc.options == null || mc.options.hideGui) return;
        int w = g.guiWidth();
        int h = g.guiHeight();

        
        int rightEdge = w / 2 + 91;
        int barW = 81; 
        int x1 = rightEdge - barW;
        float sat = mc.player.getFoodData().getSaturationLevel();
        int hunger = mc.player.getFoodData().getFoodLevel();

        if (showSaturation.value && sat > 0) {
            g.fill(RenderPipelines.GUI, x1, h - 40, rightEdge, h - 37, 0x66_00_00_00);
            int filled = Math.round(barW * Math.min(sat, 20f) / 20f);
            if (filled > 0) {
                g.fill(RenderPipelines.GUI, rightEdge - filled, h - 40, rightEdge, h - 37,
                        satColor.value);
            }
        }

        
        
        if (showPreview.value) {
            FoodProperties fp = mc.player.getMainHandItem().get(DataComponents.FOOD);
            if (fp != null && (fp.nutrition() > 0 || fp.saturation() > 0)) {
                int newHunger = Math.min(20, hunger + fp.nutrition());
                float newSat = Math.min(newHunger, sat + fp.saturation());
                int previewW = Math.round(barW * Math.min(newSat, 20f) / 20f);
                if (previewW > 0) {
                    g.fill(RenderPipelines.GUI, rightEdge - previewW, h - 37, rightEdge, h - 36,
                            previewColor.value);
                }
                String label = "+" + fp.nutrition();
                g.drawString(mc.font, label, x1, h - 51, previewColor.value, true);
            }
        }
    }
}
