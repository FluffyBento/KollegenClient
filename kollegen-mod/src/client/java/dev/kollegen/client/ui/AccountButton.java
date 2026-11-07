package dev.kollegen.client.ui;

import com.mojang.authlib.GameProfile;
import dev.kollegen.client.account.AccountManager;
import dev.kollegen.client.mods.Palette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public class AccountButton extends Button {
    private final int anchorX;
    private final boolean alignRight;
    private float hov = 0.0f;

    public AccountButton(int screenWidth, int y, Button.OnPress onPress) {
        this(screenWidth - 12, y, true, onPress);
    }

    public AccountButton(int anchorX, int y, boolean alignRight, Button.OnPress onPress) {
        super(alignRight ? anchorX - 110 : anchorX, y, 110, 22, Component.literal("Account"), onPress, DEFAULT_NARRATION);
        this.anchorX = anchorX;
        this.alignRight = alignRight;
    }

    protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float pt) {
        Glass.frame();
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        String name = AccountManager.getActiveUsername();
        int tw = font.width(name);
        int targetW = Math.max(90, tw + 36);
        this.width = targetW;
        if (this.alignRight) {
            this.setX(this.anchorX - targetW);
        } else {
            this.setX(this.anchorX);
        }

        boolean hovered = this.isMouseOver(mx, my) && this.active;
        this.hov = Glass.step(this.hov, hovered ? 1.0f : 0.0f, 14.0f);

        Glass.pill(g, this.getX(), this.getY(), this.width, this.height, this.hov, false, !this.active);

        int faceX = this.getX() + 3;
        int faceY = this.getY() + (this.height - 16) / 2;
        GameProfile prof = AccountManager.getActiveProfile();
        SkinFaceRenderer.drawFace(g, name, prof != null ? prof.id() : null, faceX, faceY, 16);

        int textX = faceX + 19;
        int textY = this.getY() + (this.height - 8) / 2;
        Glass.text(g, font, name, textX, textY, hovered ? 0xFFFFFFFF : Palette.TEXT, true);

        int arrowX = this.getX() + this.width - 10;
        Glass.text(g, font, "▾", arrowX, textY, hovered ? Palette.ACCENT : Palette.MUTED, false);
    }
}
