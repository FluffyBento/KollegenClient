package dev.kollegen.client.menu;

import dev.kollegen.client.KollegenMod;
import dev.kollegen.client.join.KollegenJoin;
import dev.kollegen.client.social.SocialData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Freundesliste (in Minecraft) mit Server-Status und „Joinen"-Knopf.
 * Klick auf „Joinen" verbindet direkt mit dem Server des Freundes (Host/Port
 * kommt aus der Presence des Backends, via social.json).
 */
public class KollegenFriendsScreen extends Screen {
    private final Screen parent;
    private SocialData data;

    public KollegenFriendsScreen() {
        super(Component.literal("Kollegen Freunde"));
        this.parent = Minecraft.getInstance().screen;
    }

    @Override
    protected void init() {
        super.init();
        this.data = SocialData.load();

        int y = 40;
        int rowH = 26;
        for (SocialData.Friend f : data.friends()) {
            if (f.online() && f.server != null) {
                final String server = f.server;
                this.addRenderableWidget(Button.builder(Component.literal("Joinen"),
                                b -> KollegenJoin.joinServer(server))
                        .bounds(this.width - 120, y, 100, 20).build());
            }
            y += rowH;
            if (y > this.height - 40) break;
        }

        this.addRenderableWidget(Button.builder(Component.literal("Schließen"), b -> this.onClose())
                .bounds(this.width / 2 - 60, this.height - 30, 120, 20).build());
    }

    @Override
    protected void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, this.width, this.height, 0xD0101010);
        g.fill(0, 0, this.width, 30, 0xFF1B5E20);
        g.drawString(this.font, "Kollegen Freunde", 10, 10, 0xFFFFFFFF);

        int y = 40;
        int rowH = 26;
        for (SocialData.Friend f : data.friends()) {
            String status = f.online() && f.server != null ? f.server : "offline";
            String label = (f.name() != null ? f.name() : "?") + "  –  " + status;
            g.drawString(this.font, label, 20, y + 4, f.online() ? 0xFF4CAF50 : 0xFF888888);
            y += rowH;
            if (y > this.height - 40) break;
        }
        if (data.friendCount() == 0) {
            g.drawString(this.font, "Noch keine Freunde – Code im Profil kopieren & teilen.", 20, 44, 0xFF888888);
        }

        super.render(g, mx, my, pt);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }
}
