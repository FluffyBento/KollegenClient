package dev.kollegen.client.game;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kollegen.client.mods.Palette;
import dev.kollegen.client.ui.Glass;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class TicTacToeScreen extends GameScreen {
    public static final String ID = "tictactoe";

    private final int[] board = new int[9]; // 0 = leer, 1 = X (Spieler), 2 = O (KI/Gegner)
    private int turn = 1;
    private int winner = 0; // 0 = läuft, 1 = X gewinnt, 2 = O gewinnt, 3 = Unentschieden
    private int[] winLine = null; // Indizes der 3 Gewinnfelder

    private int wins = 0;
    private int losses = 0;
    private int draws = 0;

    private boolean vsAi = true;
    private final float[] cellHover = new float[9];

    public TicTacToeScreen(Screen parent) {
        super(parent, "Tic-Tac-Toe");
        this.frame(360, 420);
        reset();
    }

    private void reset() {
        for (int i = 0; i < 9; i++) {
            board[i] = 0;
            cellHover[i] = 0.0f;
        }
        turn = 1;
        winner = 0;
        winLine = null;
        this.status = "Du bist am Zug (X)";
    }

    private void checkWin() {
        int[][] lines = {
                {0, 1, 2}, {3, 4, 5}, {6, 7, 8}, // Horizontal
                {0, 3, 6}, {1, 4, 7}, {2, 5, 8}, // Vertikal
                {0, 4, 8}, {2, 4, 6}             // Diagonal
        };

        for (int[] l : lines) {
            if (board[l[0]] != 0 && board[l[0]] == board[l[1]] && board[l[1]] == board[l[2]]) {
                winner = board[l[0]];
                winLine = l;
                if (winner == 1) {
                    wins++;
                    this.status = "Gewonnen! Glückwunsch!";
                } else {
                    losses++;
                    this.status = vsAi ? "Die KI hat gewonnen!" : "Spieler O hat gewonnen!";
                }
                return;
            }
        }

        boolean full = true;
        for (int i = 0; i < 9; i++) {
            if (board[i] == 0) {
                full = false;
                break;
            }
        }
        if (full) {
            winner = 3;
            draws++;
            this.status = "Unentschieden!";
        }
    }

    private void aiMove() {
        if (winner != 0) return;

        // 1. Kann die KI gewinnen?
        for (int i = 0; i < 9; i++) {
            if (board[i] == 0) {
                board[i] = 2;
                if (isWinning(2)) {
                    turn = 1;
                    checkWin();
                    if (winner == 0) this.status = "Du bist am Zug (X)";
                    return;
                }
                board[i] = 0;
            }
        }

        // 2. Muss die KI blocken?
        for (int i = 0; i < 9; i++) {
            if (board[i] == 0) {
                board[i] = 1;
                if (isWinning(1)) {
                    board[i] = 2;
                    turn = 1;
                    checkWin();
                    if (winner == 0) this.status = "Du bist am Zug (X)";
                    return;
                }
                board[i] = 0;
            }
        }

        // 3. Mitte bevorzugen
        if (board[4] == 0 && Math.random() < 0.75) {
            board[4] = 2;
            turn = 1;
            checkWin();
            if (winner == 0) this.status = "Du bist am Zug (X)";
            return;
        }

        // 4. Beliebiges freies Eckfeld
        int[] corners = {0, 2, 6, 8};
        for (int c : corners) {
            if (board[c] == 0 && Math.random() < 0.6) {
                board[c] = 2;
                turn = 1;
                checkWin();
                if (winner == 0) this.status = "Du bist am Zug (X)";
                return;
            }
        }

        for (int i = 0; i < 9; i++) {
            if (board[i] == 0) {
                board[i] = 2;
                turn = 1;
                checkWin();
                if (winner == 0) this.status = "Du bist am Zug (X)";
                return;
            }
        }
    }

    private boolean isWinning(int p) {
        int[][] lines = {
                {0, 1, 2}, {3, 4, 5}, {6, 7, 8},
                {0, 3, 6}, {1, 4, 7}, {2, 5, 8},
                {0, 4, 8}, {2, 4, 6}
        };
        for (int[] l : lines) {
            if (board[l[0]] == p && board[l[1]] == p && board[l[2]] == p) return true;
        }
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        try {
            beginFrame(g, mx, my, pt);
            drawChrome(g, "Tic-Tac-Toe", vsAi ? "Einzelspieler gegen KI" : "2 Spieler lokal");

            float gridW = Math.min(this.pw - 40, this.ph - 120);
            float cellSize = gridW / 3.0f;
            float startX = this.px + (this.pw - gridW) / 2.0f;
            float startY = this.py + 44;

            // Scoreboard
            String stats = String.format("§a%d Siege §8| §c%d Niederlagen §8| §7%d Remis", wins, losses, draws);
            Glass.textC(g, this.font, stats, Math.round(this.px + this.pw / 2.0f), Math.round(this.py + 32), Palette.MUTED, false);

            // 3x3 Spielfeld
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 3; c++) {
                    int idx = r * 3 + c;
                    float bx = startX + c * cellSize;
                    float by = startY + r * cellSize;
                    float pad = 4.0f;
                    float cw = cellSize - pad * 2;
                    float ch = cellSize - pad * 2;

                    boolean hov = winner == 0 && mx >= bx + pad && mx <= bx + pad + cw && my >= by + pad && my <= by + pad + ch;
                    cellHover[idx] = Glass.step(cellHover[idx], hov ? 1.0f : 0.0f, 16.0f);
                    float h = cellHover[idx];

                    boolean isWinCell = false;
                    if (winLine != null) {
                        for (int wl : winLine) {
                            if (wl == idx) isWinCell = true;
                        }
                    }

                    int bgCol = isWinCell ? Glass.a(Palette.GREEN, 0.35f) : Glass.a(0x000000, 0.25f + 0.15f * h);
                    int ringCol = isWinCell ? Palette.GREEN : (h > 0.01f ? Glass.a(Palette.ACCENT, 0.8f * h) : 0x22FFFFFF);

                    Glass.rr(g, bx + pad, by + pad, cw, ch, 8, bgCol);
                    Glass.ring(g, bx + pad, by + pad, cw, ch, 8, 1.0f, ringCol);

                    int val = board[idx];
                    float midX = bx + pad + cw / 2.0f;
                    float midY = by + pad + ch / 2.0f;

                    if (val == 1) { // X
                        float s = cw * 0.28f;
                        Glass.line(g, midX - s, midY - s, midX + s, midY + s, 3.5f, Palette.ACCENT);
                        Glass.line(g, midX + s, midY - s, midX - s, midY + s, 3.5f, Palette.ACCENT);
                    } else if (val == 2) { // O
                        float rSize = cw * 0.56f;
                        Glass.ring(g, midX - rSize / 2.0f, midY - rSize / 2.0f, rSize, rSize, rSize / 2.0f, 3.5f, 0xFFE08238);
                    } else if (hov && winner == 0) { // Vorschau
                        float s = cw * 0.20f;
                        int prevCol = Glass.a(Palette.ACCENT, 0.35f);
                        Glass.line(g, midX - s, midY - s, midX + s, midY + s, 2.0f, prevCol);
                        Glass.line(g, midX + s, midY - s, midX - s, midY + s, 2.0f, prevCol);
                    }
                }
            }

            // Steuerungs-Buttons unten
            float btnY = startY + gridW + 12;
            float btnW = (gridW - 10) / 2.0f;

            // Button 1: Neues Spiel
            boolean hovNew = mx >= startX && mx <= startX + btnW && my >= btnY && my <= btnY + 24;
            Glass.pill(g, startX, btnY, btnW, 24, hovNew ? 1.0f : 0.0f, true, false);
            Glass.textC(g, this.font, "Neues Spiel", Math.round(startX + btnW / 2.0f), Math.round(btnY + 8), 0xFFFFFFFF, false);

            // Button 2: Modus KI / 2-Spieler
            float btn2X = startX + btnW + 10;
            boolean hovMode = mx >= btn2X && mx <= btn2X + btnW && my >= btnY && my <= btnY + 24;
            Glass.pill(g, btn2X, btnY, btnW, 24, hovMode ? 1.0f : 0.0f, false, false);
            Glass.textC(g, this.font, vsAi ? "Modus: vs KI" : "Modus: 2-Spieler", Math.round(btn2X + btnW / 2.0f), Math.round(btnY + 8), Palette.TEXT, false);

            this.drawClose(g);
            endFrame(g, mx, my, pt);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            if (this.closeClicked(event)) {
                this.leave();
                return true;
            }

            double mx = event.x();
            double my = event.y();

            float gridW = Math.min(this.pw - 40, this.ph - 120);
            float cellSize = gridW / 3.0f;
            float startX = this.px + (this.pw - gridW) / 2.0f;
            float startY = this.py + 44;

            // Klick auf Spielfeld
            if (winner == 0) {
                for (int r = 0; r < 3; r++) {
                    for (int c = 0; c < 3; c++) {
                        float bx = startX + c * cellSize;
                        float by = startY + r * cellSize;
                        float pad = 4.0f;
                        if (mx >= bx + pad && mx <= bx + cellSize - pad && my >= by + pad && my <= by + cellSize - pad) {
                            int idx = r * 3 + c;
                            if (board[idx] == 0) {
                                board[idx] = turn;
                                checkWin();
                                if (winner == 0) {
                                    if (vsAi) {
                                        turn = 2;
                                        this.status = "KI überlegt...";
                                        aiMove();
                                    } else {
                                        turn = turn == 1 ? 2 : 1;
                                        this.status = "Spieler " + (turn == 1 ? "X" : "O") + " ist am Zug";
                                    }
                                }
                                return true;
                            }
                        }
                    }
                }
            }

            // Klick auf Buttons
            float btnY = startY + gridW + 12;
            float btnW = (gridW - 10) / 2.0f;

            if (mx >= startX && mx <= startX + btnW && my >= btnY && my <= btnY + 24) {
                reset();
                return true;
            }

            float btn2X = startX + btnW + 10;
            if (mx >= btn2X && mx <= btn2X + btnW && my >= btnY && my <= btnY + 24) {
                vsAi = !vsAi;
                reset();
                return true;
            }
        }
        return super.mouseClicked(event, bl);
    }
}
