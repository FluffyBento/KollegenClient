package dev.kollegen.client.game;

import com.google.gson.JsonObject;
import dev.kollegen.client.mods.Palette;
import dev.kollegen.client.ui.Glass;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * Connect 4 (Vier gewinnt) - gegen die KI oder gegen einen anderen Kollegen
 * auf demselben Server.
 *
 * Brett 7 x 6, Spieler 1 = Rot (Gastgeber), Spieler 2 = Gelb.
 * Multiplayer laeuft ueber {@link GameNet} (Chat-Relay); die komplette
 * Partie wird bei jedem Zug mitgesendet, damit ein spaeter Joinender den
 * Stand sieht.
 */
public class Connect4Screen extends GameScreen {

    public static final String ID = "c4";
    private static final int COLS = 7;
    private static final int ROWS = 6;
    private static final int EMPTY = 0;
    private static final int RED = 1;
    private static final int YELLOW = 2;

    private final int[][] board = new int[ROWS][COLS];
    /** Marker fuer den gerade fallenden Stein (Animation). */
    private final float[] dropAnim = new float[COLS];

    /** 0 = ki/lokal, 1 = ich bin Rot (host), 2 = ich bin Gelb (gast). */
    private int mode = 0;
    private int myColor = RED;
    private int turn = RED;
    private boolean over = false;
    private int winner = EMPTY;
    private int lastCol = -1;
    private int hoverCol = -1;
    private boolean offerOpen = false;
    private boolean thinking = false;
    private long aiAt = 0L;
    private int pendingCol = -1;

    public Connect4Screen(Screen parent) {
        super(parent, "Connect 4");
        this.frame(340, 400);
        GameNet.setSink(this::onNetMessage);
    }

    // ---------------------------------------------------------------- Logik

    private static int dropTo(int[][] b, int col) {
        for (int r = ROWS - 1; r >= 0; r--) {
            if (b[r][col] == EMPTY) {
                return r;
            }
        }
        return -1;
    }

    private static boolean canWin(int[][] b, int col, int color, int row) {
        int[][] dirs = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
        for (int[] d : dirs) {
            int n = 1;
            for (int s = -1; s <= 1; s += 2) {
                int r = row + d[1] * s;
                int c = col + d[0] * s;
                while (r >= 0 && r < ROWS && c >= 0 && c < COLS && b[r][c] == color) {
                    n++;
                    r += d[1] * s;
                    c += d[0] * s;
                }
            }
            if (n >= 4) {
                return true;
            }
        }
        return false;
    }

    private static boolean isFull(int[][] b) {
        for (int c = 0; c < COLS; c++) {
            if (dropTo(b, c) >= 0) {
                return false;
            }
        }
        return true;
    }

    private static int[] legalMoves(int[][] b) {
        int[] out = new int[COLS];
        int n = 0;
        for (int c = 0; c < COLS; c++) {
            if (dropTo(b, c) >= 0) {
                out[n++] = c;
            }
        }
        int[] res = new int[n];
        System.arraycopy(out, 0, res, 0, n);
        return res;
    }

    private void place(int col, int color) {
        int r = dropTo(this.board, col);
        if (r < 0) {
            return;
        }
        this.board[r][col] = color;
        this.lastCol = col;
        this.dropAnim[col] = 0.0f;
        if (canWin(this.board, col, color, r)) {
            this.over = true;
            this.winner = color;
            this.status = (color == myColor ? "Du gewinnst!" : "Rot/Gelb gewinnt") + " - Neues Spiel?";
        } else if (isFull(this.board)) {
            this.over = true;
            this.winner = EMPTY;
            this.status = "Unentschieden - Neues Spiel?";
        } else {
            this.turn = color == RED ? YELLOW : RED;
        }
    }

    private void newGame() {
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                this.board[r][c] = EMPTY;
            }
        }
        this.over = false;
        this.winner = EMPTY;
        this.lastCol = -1;
        this.turn = RED;
        this.offerOpen = false;
        this.pendingCol = -1;
        this.status = "";
        broadcast();
    }

    // ------------------------------------------------------------ KI (Alpha-Beta)

    private static int evaluate(int[][] b) {
        int score = 0;
        int[] columnWeight = {0, 1, 2, 4, 7, 4, 2};
        float[] stackFactor = {1.0f, 1.0f, 0.55f, 0.3f, 0.2f, 0.15f};
        for (int c = 0; c < COLS; c++) {
            int n = 0;
            for (int r = ROWS - 1; r >= 0; r--) {
                if (b[r][c] == EMPTY) {
                    break;
                }
                n++;
            }
            for (int r = 0; r < n; r++) {
                int f = r < stackFactor.length ? Math.round(stackFactor[r] * 100) : 15;
                int v = Math.round(columnWeight[c] * f / 100.0f);
                score += b[r][c] == RED ? v : -v;
            }
        }
        score += lines(b, RED) * 80 - lines(b, YELLOW) * 80;
        score += openThrees(b, RED) * 26 - openThrees(b, YELLOW) * 26;
        if (winningColumn(b, RED) >= 0) {
            score += 1200;
        }
        if (winningColumn(b, YELLOW) >= 0) {
            score -= 1200;
        }
        return score;
    }

    /** Dreierreihen, an die direkt ein Stein gelegt werden kann. */
    private static int openThrees(int[][] b, int color) {
        int n = 0;
        int[][] dirs = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (b[r][c] != color) {
                    continue;
                }
                for (int[] d : dirs) {
                    int ok = 1;
                    for (int s = 1; s <= 2; s++) {
                        int rr = r + d[1] * s;
                        int cc = c + d[0] * s;
                        if (rr < 0 || rr >= ROWS || cc < 0 || cc >= COLS || b[rr][cc] != color) {
                            ok = 0;
                            break;
                        }
                    }
                    if (ok == 0) {
                        continue;
                    }
                    int rr1 = r - d[1];
                    int cc1 = c - d[0];
                    int rr2 = r + d[1] * 2;
                    int cc2 = c + d[0] * 2;
                    if ((cc1 < 0 || cc1 >= COLS || b[Math.max(0, Math.min(ROWS - 1, rr1))][cc1] == EMPTY)
                            && (cc2 < 0 || cc2 >= COLS
                            || b[Math.max(0, Math.min(ROWS - 1, rr2))][cc2] == EMPTY)) {
                        n++;
                    }
                }
            }
        }
        return n;
    }

    private static int lines(int[][] b, int color) {
        int n = 0;
        int[][] dirs = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (b[r][c] != color) {
                    continue;
                }
                for (int[] d : dirs) {
                    int cnt = 1;
                    for (int s = 1; s <= 3; s++) {
                        int rr = r + d[1] * s;
                        int cc = c + d[0] * s;
                        if (rr >= 0 && rr < ROWS && cc >= 0 && cc < COLS && b[rr][cc] == color) {
                            cnt++;
                        }
                    }
                    if (cnt >= 4) {
                        n++;
                    }
                }
            }
        }
        return n;
    }

    private static final java.util.Random TIE = new java.util.Random();

    private static int negamax(int[][] b, int depth, int alpha, int beta, int color, int root) {
        int[] moves = legalMoves(b);
        if (moves.length == 0) {
            return 0;
        }
        int win = 100000 - (10 - depth);
        if (depth == 0) {
            return evaluate(b) * (color == RED ? 1 : -1);
        }
        int best = -999999;
        int other = color == RED ? YELLOW : RED;
        for (int col : moves) {
            int r = dropTo(b, col);
            b[r][col] = color;
            int v;
            if (canWin(b, col, color, r)) {
                v = color == root ? win : -win;
            } else {
                v = -negamax(b, depth - 1, -beta, -alpha, other, root);
            }
            b[r][col] = EMPTY;
            if (v > best) {
                best = v;
            }
            if (v > alpha) {
                alpha = v;
            }
            if (alpha >= beta) {
                break;
            }
        }
        return best;
    }

    /** Gewinnt die Farbe mit einem einzelnen Wurf? */
    private static int winningColumn(int[][] b, int color) {
        int[] moves = legalMoves(b);
        for (int col : moves) {
            int r = dropTo(b, col);
            b[r][col] = color;
            boolean w = canWin(b, col, color, r);
            b[r][col] = EMPTY;
            if (w) {
                return col;
            }
        }
        return -1;
    }

    private void aiMove() {
        int ai = myColor == RED ? YELLOW : RED;
        if (this.over || this.turn != ai) {
            return;
        }
        int winCol = winningColumn(this.board, ai);
        if (winCol >= 0) {
            this.pendingCol = winCol;
            this.thinking = true;
            this.aiAt = System.currentTimeMillis() + 180L;
            return;
        }
        int blockCol = winningColumn(this.board, myColor);
        int[] moves = legalMoves(this.board);
        if (moves.length == 0) {
            return;
        }
        int other = ai == RED ? YELLOW : RED;
        int best = blockCol >= 0 ? blockCol : moves[0];
        int bestScore = -999999;
        for (int col : moves) {
            int r = dropTo(this.board, col);
            this.board[r][col] = ai;
            int v = -negamax(this.board, 6, -999999, 999999, other, ai);
            this.board[r][col] = EMPTY;
            if (v > bestScore) {
                bestScore = v;
                best = col;
            } else if (v == bestScore && TIE.nextInt(3) == 0) {
                best = col;
            }
        }
        if (blockCol >= 0) {
            best = blockCol;
        }
        this.thinking = true;
        this.aiAt = System.currentTimeMillis() + 220L;
        this.pendingCol = best;
    }

    // ------------------------------------------------------ Multiplayer (Chat)

    private void broadcast() {
        if (this.mode == 0) {
            return;
        }
        try {
            JsonObject m = GameNet.msg(ID, "st", GameNet.room(ID));
            m.addProperty("b", encode());
            m.addProperty("m", this.myColor);
            GameNet.send(m);
        } catch (Throwable ignored) {
        }
    }

    private static final char[] ENC = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

    private static String enc(int v) {
        return Integer.toString(Math.max(0, v), 36);
    }

    /** Brett als kurzer String: jede Ziffer = Spieler (0 leer, 1 rot, 2 gelb). */
    private String encode() {
        StringBuilder sb = new StringBuilder(COLS * ROWS);
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                sb.append(this.board[r][c]);
            }
        }
        return sb.toString();
    }

    private void decode(String s) {
        if (s == null || s.length() != COLS * ROWS) {
            return;
        }
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                char ch = s.charAt(r * COLS + c);
                this.board[r][c] = ch >= '0' && ch <= '2' ? (ch - '0') : EMPTY;
            }
        }
    }

    public void onNetMessage(JsonObject o) {
        try {
            String type = o.has("t") ? o.get("t").getAsString() : "";
            String room = o.has("r") ? o.get("r").getAsString() : "";
            String from = o.has("p") ? o.get("p").getAsString() : "";
            if (!room.equals(GameNet.room(ID)) || from.equals(GameNet.meId())) {
                return;
            }
            if ("inv".equals(type)) {
                this.offerOpen = true;
                this.status = "Einladung von einem Kollegen";
            } else if ("acc".equals(type)) {
                // Gast hat angenommen: ich bin Rot, Gast ist Gelb.
                this.mode = 1;
                this.myColor = RED;
                this.turn = RED;
                this.status = "Gegner ist dabei";
            } else if ("st".equals(type)) {
                if (this.mode == 0) {
                    // Wir wurden ueber eine Annahme in die Partie geholt.
                    this.mode = 1;
                    this.myColor = RED;
                }
                decode(o.get("b").getAsString());
                this.over = false;
                this.winner = EMPTY;
                // Aus dem Brett den naechsten Zug ableiten (Letzter Stein fehlt in der
                // uebertragenen Ansicht nicht - wir berechnen ihn aus dem Brett).
                this.turn = inferTurn();
                broadcast();
            }
        } catch (Throwable ignored) {
        }
    }

    private int inferTurn() {
        int red = 0;
        int yellow = 0;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (this.board[r][c] == RED) {
                    red++;
                } else if (this.board[r][c] == YELLOW) {
                    yellow++;
                }
            }
        }
        if (isFull(this.board)) {
            this.over = true;
            return red == yellow ? RED : YELLOW;
        }
        // Gewinner pruefen
        for (int c = 0; c < COLS && !this.over; c++) {
            for (int r = 0; r < ROWS; r++) {
                if (this.board[r][c] == EMPTY) {
                    continue;
                }
                if (canWin(this.board, c, this.board[r][c], r)) {
                    this.over = true;
                    this.winner = this.board[r][c];
                    return this.board[r][c];
                }
                break;
            }
        }
        return red == yellow ? RED : YELLOW;
    }

    private void invite() {
        this.status = "Einladung gesendet";
        try {
            JsonObject m = GameNet.msg(ID, "inv", GameNet.room(ID));
            GameNet.send(m);
        } catch (Throwable ignored) {
        }
    }

    private void accept() {
        this.offerOpen = false;
        this.mode = 2;
        this.myColor = YELLOW;
        this.turn = RED;
        this.status = "Du bist Gelb";
        try {
            GameNet.send(GameNet.msg(ID, "acc", GameNet.room(ID)));
        } catch (Throwable ignored) {
        }
        broadcast();
    }

    // ---------------------------------------------------------------- Eingabe

    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        if (event.button() != 0) {
            return super.mouseClicked(event, bl);
        }
        if (this.closeClicked(event)) {
            this.leave();
            return true;
        }
        float gx = boardX();
        float gy = boardY();
        float cell = cellSize();
        if (hit(event.x(), event.y(), gx, gy, cell * COLS, cell * ROWS)) {
            int col = (int) ((event.x() - gx) / cell);
            if (col >= 0 && col < COLS) {
                humanMove(col);
                return true;
            }
        }
        // Buttons
        float bx = this.px + 16;
        float by = this.py + this.ph - 40;
        if (hit(event.x(), event.y(), bx, by, 90, 22)) {
            this.newGame();
            return true;
        }
        if (hit(event.x(), event.y(), bx + 98, by, 110, 22)) {
            this.invite();
            return true;
        }
        if (this.offerOpen && hit(event.x(), event.y(), bx + 216, by, 90, 22)) {
            this.accept();
            return true;
        }
        return super.mouseClicked(event, bl);
    }

    public boolean mouseMoved(net.minecraft.client.input.MouseButtonEvent e, double x, double y) {
        float gx = boardX();
        float gy = boardY();
        float cell = cellSize();
        if (hit(x, y, gx, gy, cell * COLS, cell * ROWS)) {
            this.hoverCol = (int) ((x - gx) / cell);
        } else {
            this.hoverCol = -1;
        }
        return false;
    }

    private void humanMove(int col) {
        if (this.over) {
            return;
        }
        if (this.mode == 0) {
            if (this.turn != myColor) {
                return;
            }
            this.place(col, myColor);
            this.aiMove();
            return;
        }
        if (this.turn != myColor) {
            this.status = "Warte auf den Gegner...";
            return;
        }
        if (dropTo(this.board, col) < 0) {
            this.status = "Diese Spalte ist voll";
            return;
        }
        this.place(col, myColor);
        broadcast();
    }

    private void runAi() {
        if (this.thinking && System.currentTimeMillis() >= this.aiAt) {
            this.thinking = false;
            int col = this.pendingCol;
            this.pendingCol = -1;
            if (col >= 0 && !this.over) {
                this.place(col, myColor == RED ? YELLOW : RED);
            }
        }
    }

    // ---------------------------------------------------------------- Zeichnen

    private float cellSize() {
        float avail = Math.min((this.pw - 40.0f) / COLS, (this.ph - 96.0f) / ROWS);
        return Math.max(18.0f, Math.min(46.0f, avail));
    }

    private float boardX() {
        float cell = cellSize();
        return this.px + (this.pw - cell * COLS) / 2.0f;
    }

    private float boardY() {
        float cell = cellSize();
        return this.py + 50.0f + ((this.ph - 96.0f) - cell * ROWS) / 2.0f;
    }

    private static int chipColor(int v) {
        if (v == RED) {
            return 0xFFF4534E;
        }
        if (v == YELLOW) {
            return 0xFFF5D90A;
        }
        return 0x00000000;
    }

    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        runAi();
        for (int c = 0; c < COLS; c++) {
            if (this.dropAnim[c] < 1.0f) {
                this.dropAnim[c] = Math.min(1.0f, this.dropAnim[c] + Glass.dt * 6.0f);
            }
        }
        beginFrame(g, mx, my, pt);
        String sub = this.mode == 0
                ? "Du bist Rot - gegen die KI"
                : (this.mode == 1 ? "Du bist Rot - Multiplayer" : "Du bist Gelb - Multiplayer");
        drawChrome(g, "Connect 4", sub);

        float cell = cellSize();
        float gx = boardX();
        float gy = boardY();
        float w = cell * COLS;
        float h = cell * ROWS;

        Glass.rr(g, gx - 4, gy - 4, w + 8, h + 8, 12, Glass.a(Palette.PANEL, 0.85f));
        Glass.ring(g, gx - 4, gy - 4, w + 8, h + 8, 12, 0.5f, Glass.a(0xFFFFFFFF, 0.18f));

        // Hover-Spalte andeuten
        if (this.hoverCol >= 0 && !this.over && (this.mode == 0 ? this.turn == myColor : this.turn == myColor)) {
            int r = dropTo(this.board, this.hoverCol);
            if (r >= 0) {
                Glass.rr(g, gx + this.hoverCol * cell + 1, gy + r * cell + 1, cell - 2, cell - 2, cell / 2 - 1,
                        Glass.a(myColor == RED ? chipColor(RED) : chipColor(YELLOW), 0.22f));
            }
        }

        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int v = this.board[r][c];
                float x = gx + c * cell;
                float y = gy + r * cell;
                Glass.rr(g, x + 1, y + 1, cell - 2, cell - 2, cell / 2 - 2, Glass.a(0xFF000000, 0.30f));
                if (v != EMPTY) {
                    int col = chipColor(v);
                    float anim = (this.lastCol == c && this.dropAnim[c] < 1.0f) ? this.dropAnim[c] : 1.0f;
                    float scale = 0.6f + 0.4f * Glass.ease(anim);
                    float rad = (cell - 2) * 0.5f * scale;
                    float cx = x + cell / 2.0f;
                    float cy = y + cell / 2.0f;
                    Glass.shadowSoft(g, cx - rad, cy - rad + 1.5f, rad * 2, rad * 2, rad, 2.0f, 1.0f, 0x55000000);
                    Glass.disc(g, cx, cy, rad, col);
                    Glass.disc(g, cx - rad * 0.25f, cy - rad * 0.25f, rad * 0.45f, Glass.a(0xFFFFFFFF, 0.22f));
                }
            }
        }

        // Zug-Indikator
        if (!this.over && (this.mode == 0 || this.turn == myColor)) {
            int next = this.turn == RED ? RED : YELLOW;
            Glass.disc(g, this.px + this.pw - 52, this.py + this.ph - 58, 7, chipColor(next));
            Glass.text(g, this.font, this.status.isEmpty() ? "Du bist am Zug" : this.status,
                    this.px + 16, this.py + this.ph - 56, Palette.MUTED, false);
        } else if (!this.over) {
            Glass.text(g, this.font, "Gegner ist am Zug", this.px + 16, this.py + this.ph - 56, Palette.MUTED, false);
        }

        // Buttons
        float bx = this.px + 16;
        float by = this.py + this.ph - 40;
        drawButton(g, mx, my, bx, by, 90, 22, this.over ? "Neues Spiel" : "Neu", this.over);
        drawButton(g, mx, my, bx + 98, by, 110, 22, "Einladen", false);
        if (this.offerOpen) {
            drawButton(g, mx, my, bx + 216, by, 100, 22, "Annehmen", true);
        }

        this.drawClose(g);
        endFrame(g, mx, my, pt);
    }

    @Override
    protected void closeGame() {
        GameNet.setSink(null);
        if (this.mode != 0) {
            try {
                JsonObject m = GameNet.msg(ID, "bye", GameNet.room(ID));
                GameNet.send(m);
            } catch (Throwable ignored) {
            }
        }
    }
}