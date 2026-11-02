package dev.kollegen.client.gui;

import dev.kollegen.client.mods.modules.AutoReconnect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

public class AutoReconnectStatusWidget extends AbstractWidget {
    private final Font font;

    public AutoReconnectStatusWidget(int x, int y, int width, int height, Font font) {
        super(x, y, width, height, Component.empty());
        this.font = font;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTick) {
        if (!AutoReconnect.isReady()) return;
        String text;
        int color;
        if (AutoReconnect.isActive()) {
            int sec = AutoReconnect.getSecondsLeft();
            text = "Auto-Reconnect in " + sec + "s (Versuch " + (AutoReconnect.getAttemptCount() + 1) + ")...";
            color = 0xFF55FF55;
        } else {
            text = "Auto-Reconnect inaktiv";
            color = 0xFFAAAAAA;
        }
        gui.centeredText(this.font, text, this.getX() + this.width / 2, this.getY(), color);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
