package dev.kollegen.client.scenery;

import dev.kollegen.client.mods.Palette;
import dev.kollegen.client.ui.Glass;
import dev.kollegen.client.ui.Icons;
import dev.kollegen.client.ui.UiStyleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public class SceneryButton extends Button {

    private final Button anchorRightButton;
    private float hov = 0.0f;

    public SceneryButton(Button anchorRightButton, int y, Button.OnPress onPress) {
        super(anchorRightButton != null ? anchorRightButton.getX() - 82 : 0, y, 76, 22, Component.literal("Szenerie"), onPress, DEFAULT_NARRATION);
        this.anchorRightButton = anchorRightButton;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float pt) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        String label = "Szenerie";
        int targetW = font.width(label) + 26;
        this.width = targetW;
        if (this.anchorRightButton != null) {
            this.setX(this.anchorRightButton.getX() - targetW - 6);
        }

        boolean hovered = this.isMouseOver(mx, my) && this.active;
        this.hov = Glass.step(this.hov, hovered ? 1.0f : 0.0f, 14.0f);

        Glass.pill(g, this.getX(), this.getY(), this.width, this.height, this.hov, false, !this.active);

        int iconCol = hovered ? (UiStyleManager.isMinecraftStyle() ? 0xFFFFFFA0 : 0xFF34D399) : 0xFF10B981;
        Icons.draw(g, Icons.GLOBE, this.getX() + 5, this.getY() + 5, 12, iconCol);

        int textCol = hovered ? (UiStyleManager.isMinecraftStyle() ? 0xFFFFFFA0 : 0xFFFFFFFF) : Palette.TEXT;
        Glass.text(g, font, label, this.getX() + 20, this.getY() + (this.height - 8) / 2, textCol, true);
    }
}
