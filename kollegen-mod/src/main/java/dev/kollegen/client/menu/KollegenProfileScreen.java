package dev.kollegen.client.menu;

import com.google.gson.Gson;
import dev.kollegen.client.KollegenMod;
import dev.kollegen.client.social.SocialData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.SkinTextures;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Profil-Screen (2D-Skin, verknüpfte Accounts, kopierbarer Freundes-Code,
 * Freund per Code hinzufügen). Freundes-Code-Anfragen werden nach
 * {@code ~/.kollegen/friend_add.json} geschrieben und vom Launcher verarbeitet.
 */
public class KollegenProfileScreen extends Screen {
    private final Screen parent;
    private EditBox codeField;
    private String status = "";
    private long statusUntil = 0;
    private SocialData data;

    public KollegenProfileScreen() {
        super(Component.literal("Kollegen Profil"));
        this.parent = Minecraft.getInstance().screen;
    }

    @Override
    protected void init() {
        super.init();
        this.data = SocialData.load();

        int cx = this.width / 2;
        int fieldW = 220;
        this.codeField = new EditBox(this.font, cx - fieldW / 2, this.height - 72, fieldW, 20, Component.literal("Freundes-Code"));
        this.codeField.setMaxLength(128);
        this.addRenderableWidget(this.codeField);

        this.addRenderableWidget(Button.builder(Component.literal("Freund hinzufügen"), b -> submit())
                .bounds(cx - fieldW / 2, this.height - 46, fieldW / 2 - 4, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Code kopieren"), b -> copyCode())
                .bounds(cx + 4, this.height - 46, fieldW / 2 - 4, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Schließen"), b -> this.onClose())
                .bounds(cx - 60, this.height - 20, 120, 16).build());
    }

    private void submit() {
        String code = this.codeField.getValue().trim();
        if (code.isEmpty()) {
            setStatus("Bitte Code eingeben");
            return;
        }
        writeFriendAdd(code);
        setStatus("Anfrage gesendet – der Launcher verarbeitet sie.");
        this.codeField.setValue("");
    }

    private void copyCode() {
        String code = this.data.meCode();
        if (code == null || code.isEmpty()) {
            setStatus("Kein Freundes-Code vorhanden");
            return;
        }
        Minecraft.getInstance().getClipboard().setClipboard(code);
        setStatus("Freundes-Code kopiert");
    }

    private void setStatus(String s) {
        this.status = s;
        this.statusUntil = System.currentTimeMillis() + 4000;
    }

    private void writeFriendAdd(String code) {
        try {
            String home = System.getProperty("user.home", ".");
            Path dir = Path.of(home, ".kollegen");
            Files.createDirectories(dir);
            Map<String, String> m = new HashMap<>();
            m.put("code", code);
            Files.writeString(dir.resolve("friend_add.json"), new Gson().toJson(m));
        } catch (Exception e) {
            KollegenMod.LOGGER.warn("Konnte friend_add.json nicht schreiben: {}", e.getMessage());
            setStatus("Fehler beim Schreiben");
        }
    }

    @Override
    protected void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Eigenes Hintergrund-Rendering in render() – Default overlay unterdrücken.
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, this.width, this.height, 0xD0101010);
        g.fill(0, 0, this.width, 30, 0xFF1B5E20);
        g.drawString(this.font, "Kollegen Profil", 10, 10, 0xFFFFFFFF);

        Minecraft mc = Minecraft.getInstance();
        int skinX = 30;
        int skinY = 60;
        if (mc.player != null) {
            ResourceLocation skin = mc.player.getSkinTextures().texture();
            g.blit(skin, skinX, skinY, 96, 96, 0f, 0f, 64, 64, 64, 64);
        }

        int tx = skinX + 120;
        int ty = 60;

        String name = data.meName();
        if (name == null && mc.player != null) name = mc.player.getName().getString();
        g.drawString(this.font, "Name: " + (name != null ? name : "-"), tx, ty, 0xFFFFFFFF);
        ty += 16;

        String uuid = data.meUuid();
        g.drawString(this.font, "UUID: " + (uuid != null ? uuid : "-"), tx, ty, 0xFF9E9E9E);
        ty += 20;

        g.drawString(this.font, "Verknüpfte Accounts:", tx, ty, 0xFF4CAF50);
        ty += 14;
        List<SocialData.Account> accs = data.accounts();
        if (accs.isEmpty()) {
            g.drawString(this.font, "  (keine)", tx, ty, 0xFF888888);
            ty += 14;
        } else {
            for (SocialData.Account a : accs) {
                g.drawString(this.font, "  " + a.type + ": " + a.name, tx, ty, 0xFFCCCCCC);
                ty += 14;
            }
        }

        ty += 6;
        String code = data.meCode();
        g.drawString(this.font, "Freundes-Code: " + (code != null ? code : "-"), tx, ty, 0xFF4CAF50);

        if (System.currentTimeMillis() < statusUntil && !status.isEmpty()) {
            int sw = this.font.width(status);
            g.drawString(this.font, status, this.width / 2 - sw / 2, this.height - 92, 0xFF4CAF50);
        }

        super.render(g, mx, my, pt);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }
}
