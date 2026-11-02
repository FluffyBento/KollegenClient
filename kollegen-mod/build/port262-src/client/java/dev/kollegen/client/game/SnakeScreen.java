package dev.kollegen.client.game;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kollegen.client.mods.Palette;
import dev.kollegen.client.ui.Glass;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;

public class SnakeScreen extends GameScreen {

    public static final String ID = "snake";

    private static final int COLS = 20;
    private static final int ROWS = 18;
    private static final int CELL = 18;

    private final List<int[]> snake = new ArrayList<>();
    private int dx = 1;
    private int dy = 0;
    private int ndx = 1;
    private int ndy = 0;
    private int fx;
    private int fy;
    private int score;
    private boolean over;
    private boolean started;
    private long lastMove;
    private int moveInterval = 140;
    private float foodPulse = 0.0f;

    public SnakeScreen(Screen parent) {
        super(parent, "Snake");
        this.frame(400, 440);
        reset();
    }

    private void reset() {
        snake.clear();
        snake.add(new int[]{COLS / 2, ROWS / 2});
        snake.add(new int[]{COLS / 2 - 1, ROWS / 2});
        snake.add(new int[]{COLS / 2 - 2, ROWS / 2});
        dx = 1;
        dy = 0;
        ndx = 1;
        ndy = 0;
        score = 0;
        over = false;
        started = false;
        moveInterval = 140;
        placeFood();
        lastMove = System.currentTimeMillis();
        this.status = "";
    }

    private void placeFood() {
        fx = (int) (Math.random() * COLS);
        fy = (int) (Math.random() * ROWS);
        for (int[] s : snake) {
            if (s[0] == fx && s[1] == fy) {
                placeFood();
                return;
            }
        }
    }

    private boolean step() {
        int hx = snake.get(0)[0] + dx;
        int hy = snake.get(0)[1] + dy;
        if (hx < 0 || hx >= COLS || hy < 0 || hy >= ROWS) {
            over = true;
            return false;
        }
        for (int[] s : snake) {
            if (s[0] == hx && s[1] == hy) {
                over = true;
                return false;
            }
        }
        snake.add(0, new int[]{hx, hy});
        if (hx == fx && hy == fy) {
            score += 10;
            moveInterval = Math.max(70, moveInterval - 3);
            placeFood();
        } else {
            snake.remove(snake.size() - 1);
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent ki) {
        int k = ki.key();
        if (k == InputConstants.KEY_ESCAPE || ki.isEscape()) {
            this.leave();
            return true;
        }
        if (over) {
            if (k == InputConstants.KEY_RETURN || k == InputConstants.KEY_NUMPADENTER || k == InputConstants.KEY_SPACE || ki.isConfirmation()) {
                reset();
                return true;
            }
            return super.keyPressed(ki);
        }
        if (k == InputConstants.KEY_LEFT || k == InputConstants.KEY_A || k == 65 || ki.isLeft()) {
            if (dx != 1) {
                ndx = -1;
                ndy = 0;
            }
            started = true;
            return true;
        }
        if (k == InputConstants.KEY_RIGHT || k == InputConstants.KEY_D || k == 68 || ki.isRight()) {
            if (dx != -1) {
                ndx = 1;
                ndy = 0;
            }
            started = true;
            return true;
        }
        if (k == InputConstants.KEY_UP || k == InputConstants.KEY_W || k == 87 || ki.isUp()) {
            if (dy != 1) {
                ndx = 0;
                ndy = -1;
            }
            started = true;
            return true;
        }
        if (k == InputConstants.KEY_DOWN || k == InputConstants.KEY_S || k == 83 || ki.isDown()) {
            if (dy != -1) {
                ndx = 0;
                ndy = 1;
            }
            started = true;
            return true;
        }
        return super.keyPressed(ki);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        try {
            if (!over && started) {
                long now = System.currentTimeMillis();
                int steps = 0;
                while (now - lastMove >= moveInterval && steps < 10) {
                    lastMove += moveInterval;
                    steps++;
                    dx = ndx;
                    dy = ndy;
                    if (!step()) {
                        break;
                    }
                }
                if (steps >= 10) {
                    lastMove = now;
                }
            }
            foodPulse += Glass.dt * 4.0f;
            if (foodPulse > 1.0f) {
                foodPulse = 0.0f;
            }
            beginFrame(g, mx, my, pt);
            int cw = COLS * CELL;
            int chh = ROWS * CELL;
            int ox = this.px + (this.pw - cw) / 2;
            int oy = this.py + 60;
            drawChrome(g, "Snake", null);

            Glass.rr(g, ox - 3, oy - 3, cw + 6, chh + 6, 12, Glass.a(Palette.PANEL, 0.90f));
            Glass.ring(g, ox - 3, oy - 3, cw + 6, chh + 6, 12, 0.8f, Glass.a(0xFFFFFFFF, 0.15f));
            g.fill(ox, oy, ox + cw, oy + chh, 0xFF0B0E14);

            for (int y = 0; y < ROWS; y++) {
                for (int x = 0; x < COLS; x++) {
                    if ((x + y) % 2 == 0) {
                        g.fill(ox + x * CELL, oy + y * CELL, ox + x * CELL + CELL - 1, oy + y * CELL + CELL - 1, 0x2211141C);
                    }
                }
            }

            float pulse = 0.85f + 0.15f * (float) Math.sin(foodPulse * Math.PI * 2.0f);
            int foodCol = Glass.a(0xFFF4534E, pulse);
            g.fill(ox + fx * CELL, oy + fy * CELL, ox + fx * CELL + CELL - 1, oy + fy * CELL + CELL - 1, foodCol);
            g.fill(ox + fx * CELL + 2, oy + fy * CELL + 2, ox + fx * CELL + CELL - 3, oy + fy * CELL + CELL - 3, 0xFF555555);

            int i = 0;
            for (int[] s : snake) {
                int col = i == 0 ? 0xFF4ADE80 : 0xFF3FB666;
                if (i > 0) {
                    col = Glass.mix(0xFF4ADE80, 0xFF3FB666, (float) i / snake.size());
                }
                g.fill(ox + s[0] * CELL, oy + s[1] * CELL, ox + s[0] * CELL + CELL - 1, oy + s[1] * CELL + CELL - 1, col);
                g.fill(ox + s[0] * CELL, oy + s[1] * CELL, ox + s[0] * CELL + CELL - 1, oy + s[1] * CELL + 2, Glass.a(col, 0.40f));
                i++;
            }

            Glass.text(g, this.font, "Score", ox, oy - 24, Palette.MUTED, false);
            Glass.text(g, this.font, String.valueOf(score), ox + this.font.width("Score") + 8, oy - 24, Palette.TEXT, false);

            if (!started) {
                String msg = "Pfeiltasten / WASD zum Starten";
                Glass.text(g, this.font, msg, ox + (cw - this.font.width(msg)) / 2, oy + chh / 2, Palette.MUTED, false);
            }
            if (over) {
                int pw2 = 280;
                int ph2 = 140;
                int gx = (this.width - pw2) / 2;
                int gy = (this.height - ph2) / 2;
                Glass.rr(g, gx, gy, pw2, ph2, 14, 0xEE11141C);
                Glass.ring(g, gx, gy, pw2, ph2, 14, 1.2f, Glass.a(Palette.ACCENT, 0.50f));
                Glass.textBold(g, this.font, "Game Over", gx + (pw2 - this.font.width("Game Over")) / 2, gy + 22, Palette.TEXT, true);
                Glass.text(g, this.font, "Score: " + score, gx + (pw2 - this.font.width("Score: " + score)) / 2, gy + 50, Palette.MUTED, false);
                Glass.text(g, this.font, "Enter / Leer = Neustart", gx + (pw2 - this.font.width("Enter / Leer = Neustart")) / 2, gy + 76, Palette.ACCENT, false);
            }
            this.drawClose(g);
            endFrame(g, mx, my, pt);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && this.closeClicked(event)) {
            this.leave();
            return true;
        }
        return super.mouseClicked(event, bl);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
