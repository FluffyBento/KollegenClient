package dev.kollegen.client.menu;

import dev.kollegen.client.mods.Palette;
import dev.kollegen.client.presence.StoreClient;
import dev.kollegen.client.ui.Glass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.chat.Component;

public class TetrisScreen extends Screen {
    private static final int COLS = 10;
    private static final int ROWS = 20;
    private static final int CELL = 15;

    private static final int[][][] SHAPES = {
            {{-1, 0}, {0, 0}, {1, 0}, {2, 0}},
            {{0, 0}, {1, 0}, {0, 1}, {1, 1}},
            {{-1, 0}, {0, 0}, {1, 0}, {0, 1}},
            {{0, 0}, {1, 0}, {-1, 1}, {0, 1}},
            {{-1, 0}, {0, 0}, {0, 1}, {1, 1}},
            {{-1, 0}, {-1, 1}, {0, 1}, {1, 1}},
            {{1, 0}, {-1, 1}, {0, 1}, {1, 1}},
    };
    private static final int[] COLORS = {
            0xFF35D6E5, 0xFFF5D90A, 0xFFB14BE0, 0xFF4ADE80,
            0xFFF4534E, 0xFF4E8DF5, 0xFFF59B23,
    };

    private final Screen parent;
    private final int[][] grid = new int[ROWS][COLS];
    private int curType;
    private int curRot;
    private int curX;
    private int curY;
    private int nextType;
    private int score;
    private int lines;
    private int level;
    private boolean over;
    private boolean submitted;
    private int awarded;
    private String awardMsg;
    private long lastFall;
    private int xX;
    private int xY;
    private int againX;
    private int againY;

    protected TetrisScreen(Screen parent) {
        super(Component.literal("Tetris"));
        this.parent = parent;
        reset();
    }

    private void reset() {
        try {
            for (int y = 0; y < ROWS; y++) {
                for (int x = 0; x < COLS; x++) grid[y][x] = 0;
            }
        } catch (Throwable ignored) {
        }
        score = 0;
        lines = 0;
        level = 1;
        over = false;
        submitted = false;
        awarded = 0;
        awardMsg = null;
        nextType = (int) (Math.random() * SHAPES.length);
        lastFall = System.currentTimeMillis();
        spawn();
    }

    private int[][] offsets(int type, int rot) {
        int[][] base = SHAPES[type % SHAPES.length];
        int[][] out = new int[4][2];
        for (int i = 0; i < 4; i++) {
            int x = base[i][0];
            int y = base[i][1];
            for (int r = 0; r < (rot & 3); r++) {
                int nx = -y;
                int ny = x;
                x = nx;
                y = ny;
            }
            out[i][0] = x;
            out[i][1] = y;
        }
        return out;
    }

    private boolean collides(int[][] off, int px, int py) {
        try {
            for (int[] b : off) {
                int nx = px + b[0];
                int ny = py + b[1];
                if (nx < 0 || nx >= COLS || ny >= ROWS) return true;
                if (ny >= 0 && grid[ny][nx] != 0) return true;
            }
        } catch (Throwable ignored) {
            return true;
        }
        return false;
    }

    private boolean tryMove(int dx, int dy) {
        try {
            if (!collides(offsets(curType, curRot), curX + dx, curY + dy)) {
                curX += dx;
                curY += dy;
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private boolean tryRotate() {
        try {
            int[][] kicks = {{0, 0}, {-1, 0}, {1, 0}, {0, -1}, {-1, -1}, {1, -1}, {0, 1}};
            int nr = (curRot + 1) & 3;
            int[][] off = offsets(curType, nr);
            for (int[] k : kicks) {
                if (!collides(off, curX + k[0], curY + k[1])) {
                    curRot = nr;
                    curX += k[0];
                    curY += k[1];
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private void spawn() {
        try {
            curType = nextType;
            nextType = (int) (Math.random() * SHAPES.length);
            curRot = 0;
            curX = 3;
            curY = 0;
            if (collides(offsets(curType, curRot), curX, curY)) {
                over = true;
                submitScore();
            }
        } catch (Throwable ignored) {
        }
    }

    private void lock() {
        try {
            int[][] off = offsets(curType, curRot);
            for (int[] b : off) {
                int nx = curX + b[0];
                int ny = curY + b[1];
                if (nx >= 0 && nx < COLS && ny >= 0 && ny < ROWS) {
                    grid[ny][nx] = curType + 1;
                }
            }
            int cleared = 0;
            for (int y = ROWS - 1; y >= 0; y--) {
                boolean full = true;
                for (int x = 0; x < COLS; x++) {
                    if (grid[y][x] == 0) {
                        full = false;
                        break;
                    }
                }
                if (full) {
                    cleared++;
                    for (int yy = y; yy > 0; yy--) {
                        System.arraycopy(grid[yy - 1], 0, grid[yy], 0, COLS);
                    }
                    for (int x = 0; x < COLS; x++) grid[0][x] = 0;
                    y++;
                }
            }
            if (cleared > 0) {
                int[] pts = {0, 100, 300, 500, 800};
                score += pts[Math.min(cleared, 4)] * level;
                lines += cleared;
                level = 1 + lines / 10;
            }
            spawn();
        } catch (Throwable ignored) {
        }
    }

    private long fallInterval() {
        try {
            long v = (long) (750.0 * Math.pow(0.86, level - 1));
            return Math.max(60L, v);
        } catch (Throwable ignored) {
            return 750L;
        }
    }

    private void hardDrop() {
        try {
            int dist = 0;
            int[][] off = offsets(curType, curRot);
            while (!collides(off, curX, curY + 1)) {
                curY++;
                dist++;
            }
            score += dist * 2;
            lock();
            lastFall = System.currentTimeMillis();
        } catch (Throwable ignored) {
        }
    }

    private void submitScore() {
        try {
            if (submitted || score <= 0) return;
            submitted = true;
            awardMsg = "Wird gutgeschrieben …";
            final int s = score;
            Thread t = new Thread(() -> {
                try {
                    com.google.gson.JsonObject r = StoreClient.submitMinigame("tetris", s);
                    if (r != null && r.has("awarded") && !r.get("awarded").isJsonNull()) {
                        try {
                            awarded = r.get("awarded").getAsInt();
                        } catch (Throwable ignored) {
                        }
                        awardMsg = awarded > 0 ? ("+" + awarded + " ★ erhalten!") : "Keine Punkte (Limit).";
                    } else if (r != null && r.has("error") && !r.get("error").isJsonNull()) {
                        awardMsg = "Fehler: " + r.get("error").getAsString();
                    } else {
                        awardMsg = "Fehler beim Gutschreiben.";
                    }
                } catch (Throwable e) {
                    awardMsg = "Fehler: " + String.valueOf(e.getMessage());
                }
            }, "kollegen-tetris-submit");
            t.setDaemon(true);
            t.start();
        } catch (Throwable ignored) {
        }
    }

    private void drawCell(GuiGraphicsExtractor g, int x, int y, int color, int alpha) {
        try {
            int c = (alpha << 24) | (color & 0x00FFFFFF);
            g.fill(x, y, x + CELL, y + CELL, c);
            int light = (Math.min(255, alpha) << 24) | 0x00FFFFFF;
            g.fill(x, y, x + CELL, y + 2, (light & 0x55FFFFFF));
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        try {
            if (!over) {
                long now = System.currentTimeMillis();
                long iv = fallInterval();
                int steps = 0;
                while (now - lastFall >= iv && steps < 8) {
                    lastFall += iv;
                    steps++;
                    int[][] off = offsets(curType, curRot);
                    if (!collides(off, curX, curY + 1)) {
                        curY++;
                    } else {
                        lock();
                        break;
                    }
                }
                if (steps >= 8) lastFall = now;
            }
            int bw = COLS * CELL;
            int bh = ROWS * CELL;
            int sideW = 190;
            int totalW = bw + 16 + sideW;
            int ox = (this.width - totalW) / 2;
            int oy = (this.height - bh) / 2;
            g.fill(0, 0, this.width, this.height, 0xCC000000);
            Glass.panelDark(g, ox - 12, oy - 12, totalW + 24, bh + 24, 10);
            g.text(this.font, "TETRIS", ox, oy - 30, Palette.ACCENT, false);
            xX = this.width - 40;
            xY = 12;
            drawMiniX(g, xX, xY, mx, my);
            for (int y = 0; y < ROWS; y++) {
                for (int x = 0; x < COLS; x++) {
                    int v = grid[y][x];
                    if (v <= 0) {
                        g.fill(ox + x * CELL, oy + y * CELL, ox + x * CELL + CELL, oy + y * CELL + CELL, 0xFF11141C);
                    } else {
                        drawCell(g, ox + x * CELL, oy + y * CELL, COLORS[(v - 1) % COLORS.length], 255);
                    }
                    g.fill(ox + x * CELL, oy + y * CELL, ox + x * CELL + CELL, oy + y * CELL + 1, 0x22000000);
                }
            }
            if (!over) {
                int[][] off = offsets(curType, curRot);
                int gy = curY;
                while (!collides(off, curX, gy + 1)) gy++;
                for (int[] b : off) {
                    int nx = curX + b[0];
                    int ny = gy + b[1];
                    if (nx >= 0 && nx < COLS && ny >= 0 && ny < ROWS && grid[ny][nx] == 0) {
                        int px = ox + nx * CELL;
                        int py = oy + ny * CELL;
                        g.fill(px, py, px + CELL, py + CELL, 0x44FFFFFF);
                    }
                }
                for (int[] b : off) {
                    int nx = curX + b[0];
                    int ny = curY + b[1];
                    if (nx >= 0 && nx < COLS && ny >= 0 && ny < ROWS) {
                        drawCell(g, ox + nx * CELL, oy + ny * CELL, COLORS[curType % COLORS.length], 255);
                    }
                }
            }
            int sx = ox + bw + 16;
            int sy = oy;
            g.text(this.font, "Score", sx, sy, Palette.MUTED, false);
            g.text(this.font, String.valueOf(score), sx, sy + 12, Palette.TEXT, false);
            g.text(this.font, "≈ " + (score / 100) + " ★", sx, sy + 26, Palette.ACCENT, false);
            g.text(this.font, "Level " + level, sx, sy + 48, Palette.TEXT, false);
            g.text(this.font, "Linien " + lines, sx, sy + 62, Palette.MUTED, false);
            g.text(this.font, "Next", sx, sy + 86, Palette.MUTED, false);
            int[][] noff = offsets(nextType, 0);
            for (int[] b : noff) {
                int px = sx + 20 + b[0] * 11;
                int py = sy + 104 + b[1] * 11;
                g.fill(px, py, px + 11, py + 11, COLORS[nextType % COLORS.length] | 0xFF000000);
            }
            g.text(this.font, "←→ bewegen", sx, sy + 170, Palette.MUTED, false);
            g.text(this.font, "↑ drehen", sx, sy + 184, Palette.MUTED, false);
            g.text(this.font, "↓ fallen · Space hart", sx, sy + 198, Palette.MUTED, false);
            g.text(this.font, "100 Punkte = 1 ★", sx, sy + 220, Palette.ACCENT, false);
            if (over) {
                int pw2 = 300;
                int ph2 = 150;
                int dx = (this.width - pw2) / 2;
                int dy = (this.height - ph2) / 2;
                Glass.panelDark(g, dx, dy, pw2, ph2, 10);
                g.text(this.font, "Game Over", dx + (pw2 - this.font.width("Game Over")) / 2, dy + 18, Palette.TEXT, false);
                g.text(this.font, "Score: " + score, dx + (pw2 - this.font.width("Score: " + score)) / 2, dy + 42, Palette.MUTED, false);
                String am = awardMsg != null ? awardMsg : "…";
                g.text(this.font, am, dx + (pw2 - this.font.width(am)) / 2, dy + 60, Palette.ACCENT, false);
                againX = dx + (pw2 - 160) / 2;
                againY = dy + 92;
                Glass.fillRound(g, againX, againY, 160, 30, 6, Palette.ACCENT);
                String nl = "Nochmal (Enter)";
                g.text(this.font, nl, againX + (160 - this.font.width(nl)) / 2, againY + (30 - this.font.lineHeight) / 2, 0xFF000000, false);
            }
        } catch (Throwable ignored) {
        }
    }

    private void drawMiniX(GuiGraphicsExtractor g, int x, int y, int mx, int my) {
        try {
            boolean hov = mx >= x && mx <= x + 24 && my >= y && my < y + 24;
            Glass.fillRound(g, x, y, 24, 24, 6, hov ? Palette.ACCENT : 0x80333A44);
            g.text(this.font, "X", x + (24 - this.font.width("X")) / 2, y + (24 - this.font.lineHeight) / 2, 0xFFFFFFFF, false);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean bl) {
        try {
            double mx = event.x();
            double my = event.y();
            if (event.button() == 0) {
                if (mx >= xX && mx <= xX + 24 && my >= xY && my < xY + 24) {
                    try {
                        Minecraft.getInstance().setScreenAndShow(parent);
                    } catch (Throwable ignored) {
                    }
                    return true;
                }
                if (over && mx >= againX && mx <= againX + 160 && my >= againY && my < againY + 30) {
                    reset();
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return super.mouseClicked(event, bl);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent ki) {
        try {
            int k = ki.key();
            if (k == InputConstants.KEY_ESCAPE) {
                try {
                    Minecraft.getInstance().setScreenAndShow(parent);
                } catch (Throwable ignored) {
                }
                return true;
            }
            if (over) {
                if (k == InputConstants.KEY_RETURN || k == InputConstants.KEY_NUMPADENTER || k == InputConstants.KEY_SPACE) {
                    reset();
                    return true;
                }
                return super.keyPressed(ki);
            }
            if (k == InputConstants.KEY_LEFT) {
                tryMove(-1, 0);
                return true;
            }
            if (k == InputConstants.KEY_RIGHT) {
                tryMove(1, 0);
                return true;
            }
            if (k == InputConstants.KEY_DOWN) {
                if (tryMove(0, 1)) score += 1;
                lastFall = System.currentTimeMillis();
                return true;
            }
            if (k == InputConstants.KEY_UP || k == 88) {
                tryRotate();
                return true;
            }
            if (k == 90) {
                for (int i = 0; i < 3; i++) tryRotate();
                return true;
            }
            if (k == InputConstants.KEY_SPACE) {
                hardDrop();
                return true;
            }
        } catch (Throwable ignored) {
        }
        return super.keyPressed(ki);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
