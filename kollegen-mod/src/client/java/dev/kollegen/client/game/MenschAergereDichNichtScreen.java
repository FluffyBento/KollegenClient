package dev.kollegen.client.game;

import com.google.gson.JsonObject;
import dev.kollegen.client.mods.Palette;
import dev.kollegen.client.ui.Glass;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;

public class MenschAergereDichNichtScreen extends GameScreen {

    public static final String ID = "madt";

    private static final int MAIN = 56;
    private static final int HOME_LEN = 6;
    private static final int PIECES = 4;
    private static final int IN_START = 90;
    private static final int GRID = 15;

    private static final int ROT = 0;
    private static final int BLU = 1;

    private static final int[][] TRACK = {{0,6}, {0,7}, {0,8}, {1,8}, {2,8}, {3,8}, {4,8}, {5,8}, {6,8}, {6,9}, {6,10}, {6,11}, {6,12}, {6,13}, {6,14}, {7,14}, {8,14}, {8,13}, {8,12}, {8,11}, {8,10}, {8,9}, {8,8}, {9,8}, {10,8}, {11,8}, {12,8}, {13,8}, {14,8}, {14,7}, {14,6}, {13,6}, {12,6}, {11,6}, {10,6}, {9,6}, {8,6}, {8,5}, {8,4}, {8,3}, {8,2}, {8,1}, {8,0}, {7,0}, {6,0}, {6,1}, {6,2}, {6,3}, {6,4}, {6,5}, {6,6}, {5,6}, {4,6}, {3,6}, {2,6}, {1,6}};
    private static final int[][][] HOME_CELLS = {{{1,7}, {2,7}, {3,7}, {4,7}, {5,7}, {6,7}}, {{7,13}, {7,12}, {7,11}, {7,10}, {7,9}, {7,8}}, {{13,7}, {12,7}, {11,7}, {10,7}, {9,7}, {8,7}}, {{7,1}, {7,2}, {7,3}, {7,4}, {7,5}, {7,6}}};
    private static final int[][][] START_CELLS = {{{0,4}, {0,5}, {1,4}, {1,5}}, {{4,13}, {5,13}, {4,14}, {5,14}}, {{13,4}, {14,4}, {13,5}, {14,5}}, {{4,0}, {4,1}, {5,0}, {5,1}}};
    private static final int[] START_FIELD = {1, 15, 29, 43};
    private static final int[] HOME_ENTRY = {55, 17, 31, 45};

    private static final int[] QUAD = {0, 2};

    private final int[][] pos = new int[2][PIECES];
    private final int[][] homeStep = new int[2][PIECES];

    private final java.util.Random rnd = new java.util.Random();
    private int current = ROT;
    private int dice = 0;
    private int sixes = 0;
    private boolean over = false;
    private int winner = -1;
    private int mode = 0;
    private int myColor = ROT;
    private boolean offerOpen = false;
    private int selected = -1;
    private long aiAt = 0L;
    private float dicePulse = 0.0f;

    public MenschAergereDichNichtScreen(Screen parent) {
        super(parent, "Mensch aergere dich nicht");
        this.frame(460, 490);
        GameNet.setSink(this::onNetMessage);
        newGame();
    }

    private void newGame() {
        for (int p = 0; p < 2; p++) {
            for (int i = 0; i < PIECES; i++) {
                this.pos[p][i] = IN_START;
                this.homeStep[p][i] = -1;
            }
        }
        this.current = ROT;
        this.dice = 0;
        this.sixes = 0;
        this.over = false;
        this.winner = -1;
        this.selected = -1;
        this.aiAt = 0L;
        this.status = "";
        broadcast();
    }

    private int quadOf(int p) {
        return QUAD[p];
    }

    private int homeEntry(int p) {
        return HOME_ENTRY[quadOf(p)];
    }

    private int startField(int p) {
        return START_FIELD[quadOf(p)];
    }

    private boolean onTrack(int p, int i) {
        return this.homeStep[p][i] < 0 && this.pos[p][i] < IN_START;
    }

    private boolean atHome(int p, int i) {
        return this.homeStep[p][i] >= 0;
    }

    private boolean inStart(int p, int i) {
        return this.pos[p][i] == IN_START;
    }

    private int pieceOnTrack(int field, int p) {
        for (int i = 0; i < PIECES; i++) {
            if (onTrack(p, i) && this.pos[p][i] == field) {
                return i;
            }
        }
        return -1;
    }

    private int distanceToEntry(int p, int t) {
        return (homeEntry(p) - t + MAIN) % MAIN;
    }

    private boolean canMove(int p, int i, int d) {
        if (atHome(p, i)) {
            return false;
        }
        if (inStart(p, i)) {
            return d == 6;
        }
        int fwd = distanceToEntry(p, this.pos[p][i]);
        if (d <= fwd) {
            return true;
        }
        return d - fwd - 1 < HOME_LEN;
    }

    private int progress(int p, int i) {
        if (atHome(p, i)) {
            return MAIN + HOME_LEN + this.homeStep[p][i];
        }
        if (inStart(p, i)) {
            return -1;
        }
        return (this.pos[p][i] - startField(p) + MAIN) % MAIN;
    }

    private void movePiece(int p, int i, int d) {
        if (!canMove(p, i, d)) {
            return;
        }
        if (inStart(p, i)) {
            this.pos[p][i] = startField(p);
            return;
        }
        int fwd = distanceToEntry(p, this.pos[p][i]);
        if (d <= fwd) {
            int target = (this.pos[p][i] + d) % MAIN;
            int foe = p == ROT ? BLU : ROT;
            int taken = pieceOnTrack(target, foe);
            if (taken >= 0) {
                this.pos[foe][taken] = IN_START;
                this.homeStep[foe][taken] = -1;
                this.status = "Kollege zurueck auf Start!";
            }
            this.pos[p][i] = target;
        } else {
            int step = d - fwd - 1;
            this.homeStep[p][i] = step;
            this.pos[p][i] = 0;
        }
        if (allHome(p)) {
            this.over = true;
            this.winner = p;
            this.status = p == this.myColor ? "Du gewinnst!" : "Der Kollege gewinnt";
        }
    }

    private boolean allHome(int p) {
        for (int i = 0; i < PIECES; i++) {
            if (!atHome(p, i)) {
                return false;
            }
        }
        return true;
    }

    private int bestMove(int p, int d) {
        int foe = p == ROT ? BLU : ROT;
        int best = -1;
        int bestScore = Integer.MIN_VALUE;
        int waiting = 0;
        for (int i = 0; i < PIECES; i++) {
            if (inStart(p, i)) {
                waiting++;
            }
        }
        for (int i = 0; i < PIECES; i++) {
            if (!canMove(p, i, d)) {
                continue;
            }
            int score;
            if (inStart(p, i)) {
                score = 60 + waiting * 8;
            } else {
                score = progress(p, i) + 20;
                int fwd = distanceToEntry(p, this.pos[p][i]);
                if (d > fwd) {
                    score += 40 + (d - fwd - 1) * 30;
                } else {
                    int target = (this.pos[p][i] + d) % MAIN;
                    if (pieceOnTrack(target, foe) >= 0) {
                        score += 300;
                    }
                    int risk = 0;
                    for (int j = 0; j < PIECES; j++) {
                        if (!onTrack(foe, j)) {
                            continue;
                        }
                        int dd = (this.pos[foe][j] - target + MAIN) % MAIN;
                        if (dd >= 1 && dd <= 3) {
                            risk += 45 - dd * 10;
                        }
                    }
                    score -= risk;
                }
            }
            score += this.rnd.nextInt(20);
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    private void humanTurn(int i) {
        int p = this.current;
        int d = 1 + this.rnd.nextInt(6);
        this.dice = d;
        int idx = canMove(p, i, d) ? i : bestMove(p, d);
        if (idx < 0) {
            this.status = d == 6 ? "Sechs, aber keine Figur passt" : "Passt nicht - Zug vorbei";
            endTurn(d);
            broadcast();
            return;
        }
        movePiece(p, idx, d);
        if (!this.over) {
            endTurn(d);
        }
        broadcast();
    }

    private void endTurn(int d) {
        if (d == 6) {
            this.sixes++;
            if (this.sixes == 3) {
                this.status = "Drei Sechsen in Folge - Zug vorbei";
                this.sixes = 0;
                nextTurn();
                return;
            }
            if (this.sixes == 4) {
                int worst = -1;
                int worstProgress = -1;
                for (int i = 0; i < PIECES; i++) {
                    if (atHome(p(), i)) {
                        continue;
                    }
                    int pr = progress(p(), i);
                    if (pr > worstProgress) {
                        worstProgress = pr;
                        worst = i;
                    }
                }
                if (worst >= 0) {
                    this.pos[p()][worst] = IN_START;
                    this.homeStep[p()][worst] = -1;
                    this.status = "Vier Sechsen in Folge - Figur zurueck!";
                }
                this.sixes = 0;
                nextTurn();
                return;
            }
            this.status = "Sechs! Noch ein Wurf";
            return;
        }
        this.sixes = 0;
        nextTurn();
    }

    private int p() {
        return this.current;
    }

    private void nextTurn() {
        this.current = this.current == ROT ? BLU : ROT;
        this.selected = -1;
        if (this.mode != 0 && this.current != this.myColor) {
            this.status = "Warte auf den Kollegen...";
        }
        broadcast();
    }

    private void aiTurn() {
        int p = this.current;
        int d = 1 + this.rnd.nextInt(6);
        this.dice = d;
        int best = bestMove(p, d);
        if (best < 0) {
            this.status = "Die KI kann nicht ziehen";
            endTurn(d);
            broadcast();
            return;
        }
        movePiece(p, best, d);
        if (!this.over) {
            endTurn(d);
        }
        broadcast();
    }

    private String encode() {
        StringBuilder sb = new StringBuilder(12);
        sb.append(this.current == ROT ? '0' : '1');
        sb.append(this.sixes);
        for (int p = 0; p < 2; p++) {
            for (int i = 0; i < PIECES; i++) {
                int v = atHome(p, i) ? 70 + this.homeStep[p][i] : this.pos[p][i];
                sb.append((char) ('A' + v));
            }
        }
        return sb.toString();
    }

    private void decode(String s) {
        if (s == null || s.length() != 10) {
            return;
        }
        this.current = s.charAt(0) == '0' ? ROT : BLU;
        this.sixes = s.charAt(1) - '0';
        int k = 2;
        for (int p = 0; p < 2; p++) {
            for (int i = 0; i < PIECES; i++) {
                int v = s.charAt(k++) - 'A';
                if (v >= 70) {
                    this.homeStep[p][i] = v - 70;
                    this.pos[p][i] = 0;
                } else {
                    this.homeStep[p][i] = -1;
                    this.pos[p][i] = v;
                }
            }
        }
    }

    private void broadcast() {
        if (this.mode == 0) {
            return;
        }
        try {
            JsonObject m = GameNet.msg(ID, "st", GameNet.room(ID));
            m.addProperty("b", encode());
            GameNet.send(m);
        } catch (Throwable ignored) {
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
                this.mode = 1;
                this.myColor = ROT;
                this.status = "Gegner ist dabei";
            } else if ("st".equals(type)) {
                if (this.mode == 0) {
                    this.mode = 1;
                    this.myColor = ROT;
                }
                decode(o.get("b").getAsString());
                this.over = false;
                this.winner = -1;
                if (allHome(ROT)) {
                    this.over = true;
                    this.winner = ROT;
                } else if (allHome(BLU)) {
                    this.over = true;
                    this.winner = BLU;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void invite() {
        this.status = "Einladung gesendet";
        try {
            GameNet.send(GameNet.msg(ID, "inv", GameNet.room(ID)));
        } catch (Throwable ignored) {
        }
    }

    private void accept() {
        this.offerOpen = false;
        this.mode = 2;
        this.myColor = BLU;
        this.status = "Du bist Blau";
        try {
            GameNet.send(GameNet.msg(ID, "acc", GameNet.room(ID)));
        } catch (Throwable ignored) {
        }
        broadcast();
    }

    private float cell() {
        float avail = Math.min(this.pw - 36.0f, this.ph - 130.0f);
        return Math.max(12.0f, Math.min(34.0f, avail / GRID));
    }

    private float gridX() {
        float c = cell();
        return this.px + (this.pw - c * GRID) / 2.0f;
    }

    private float gridY() {
        float c = cell();
        return this.py + 52.0f;
    }

    private float cellX(int col) {
        return gridX() + col * cell();
    }

    private float cellY(int row) {
        return gridY() + row * cell();
    }

    private static int pieceColor(int p) {
        return p == ROT ? 0xFFF4534E : 0xFF4A9DFF;
    }

    private void drawCell(GuiGraphicsExtractor g, int row, int col, int base, boolean accent) {
        float c = cell();
        float x = cellX(col);
        float y = cellY(row);
        Glass.rr(g, x + 0.5f, y + 0.5f, c - 1.0f, c - 1.0f, Math.max(2.0f, c * 0.18f),
                Glass.a(base, 0.90f));
        if (accent) {
            Glass.ring(g, x + 0.5f, y + 0.5f, c - 1.0f, c - 1.0f, Math.max(2.0f, c * 0.18f),
                    0.8f, Glass.a(Palette.ACCENT, 0.45f));
        }
    }

    private void drawPiece(GuiGraphicsExtractor g, int row, int col, int p, boolean sel) {
        float c = cell();
        float rad = c * (sel ? 0.42f : 0.34f);
        float x = cellX(col) + c / 2.0f;
        float y = cellY(row) + c / 2.0f;
        Glass.shadowSoft(g, x - rad, y - rad + 1.5f, rad * 2, rad * 2, rad, 2.0f, 1.0f, 0x66000000);
        Glass.disc(g, x, y, rad, pieceColor(p));
        Glass.ring(g, x - rad, y - rad, rad * 2, rad * 2, rad, 0.6f, Glass.a(0xFFFFFFFF, 0.25f));
        Glass.disc(g, x - rad * 0.28f, y - rad * 0.28f, rad * 0.45f, Glass.a(0xFFFFFFFF, 0.20f));
    }

    private boolean myTurn() {
        return !this.over && this.current == this.myColor
                && (this.mode != 0 || this.current == ROT);
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        if (event.button() != 0) {
            return super.mouseClicked(event, bl);
        }
        if (this.closeClicked(event)) {
            this.leave();
            return true;
        }
        if (myTurn()) {
            float c = cell();
            int col = (int) ((event.x() - gridX()) / c);
            int row = (int) ((event.y() - gridY()) / c);
            if (row >= 0 && row < GRID && col >= 0 && col < GRID
                    && hit(event.x(), event.y(), cellX(col), cellY(row), c, c)) {
                int idx = pick(event.x(), event.y(), row, col);
                if (idx >= 0) {
                    this.selected = idx;
                    humanTurn(idx);
                    return true;
                }
            }
        }
        float bx = this.px + 16;
        float by = this.py + this.ph - 40;
        if (hit(event.x(), event.y(), bx, by, 90, 22)) {
            newGame();
            return true;
        }
        if (hit(event.x(), event.y(), bx + 98, by, 110, 22)) {
            invite();
            return true;
        }
        if (this.offerOpen && hit(event.x(), event.y(), bx + 216, by, 100, 22)) {
            accept();
            return true;
        }
        return super.mouseClicked(event, bl);
    }

    private int pick(double mx, double my, int row, int col) {
        int p = this.current;
        int q = quadOf(p);
        int sf = startField(p);
        for (int i = 0; i < PIECES; i++) {
            if (!onTrack(p, i)) {
                continue;
            }
            int[] t = TRACK[this.pos[p][i]];
            if (t[0] == row && t[1] == col) {
                return i;
            }
        }
        if (this.pos[p][0] >= IN_START) {
            int[] t = TRACK[sf];
            if (t[0] == row && t[1] == col) {
                for (int i = 0; i < PIECES; i++) {
                    if (inStart(p, i)) {
                        return i;
                    }
                }
            }
        }
        int[][] sc = START_CELLS[q];
        for (int i = 0; i < PIECES; i++) {
            if (inStart(p, i) && sc[i][0] == row && sc[i][1] == col) {
                return i;
            }
        }
        return -1;
    }

    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        if (this.mode == 0 && this.current == BLU && !this.over) {
            if (this.aiAt == 0L) {
                this.aiAt = System.currentTimeMillis() + 600L;
            } else if (System.currentTimeMillis() >= this.aiAt) {
                this.aiAt = 0L;
                aiTurn();
            }
        }
        dicePulse += Glass.dt * 3.0f;
        if (dicePulse > 1.0f) {
            dicePulse = 0.0f;
        }
        beginFrame(g, mx, my, pt);
        String sub = this.mode == 0
                ? "Du bist Rot - gegen die KI"
                : (this.mode == 1 ? "Du bist Rot - Multiplayer" : "Du bist Blau - Multiplayer");
        drawChrome(g, "Mensch aergere dich nicht", sub);

        int light = Glass.mix(0xFFEFE3CD, Palette.BG, 0.10f);
        int dark = Glass.mix(0xFFD9C7A6, Palette.BG, 0.26f);

        for (int i = 0; i < MAIN; i++) {
            int[] t = TRACK[i];
            boolean isStart = false;
            for (int q = 0; q < 4; q++) {
                if (START_FIELD[q] == i) {
                    isStart = true;
                }
            }
            boolean isEntry = false;
            for (int q = 0; q < 4; q++) {
                if (HOME_ENTRY[q] == i) {
                    isEntry = true;
                }
            }
            int base = isStart || isEntry ? Glass.mix(light, Palette.ACCENT, 0.16f) : light;
            drawCell(g, t[0], t[1], base, false);
        }

        for (int q = 0; q < 4; q++) {
            int p = ownerOfQuad(q);
            int cc = p >= 0 ? pieceColor(p) : Palette.PANEL;
            for (int s = 0; s < HOME_LEN; s++) {
                int[] h = HOME_CELLS[q][s];
                boolean done = false;
                if (p >= 0) {
                    for (int i = 0; i < PIECES; i++) {
                        if (atHome(p, i) && this.homeStep[p][i] == s) {
                            done = true;
                        }
                    }
                }
                drawCell(g, h[0], h[1], done ? Glass.mix(light, cc, 0.50f) : Glass.mix(light, cc, 0.14f), done);
            }
            int[][] sc = START_CELLS[q];
            for (int i = 0; i < PIECES; i++) {
                int[] s = sc[i];
                boolean occupied = false;
                if (p >= 0) {
                    for (int k = 0; k < PIECES; k++) {
                        if (inStart(p, k)) {
                            occupied = true;
                        }
                    }
                }
                drawCell(g, s[0], s[1], Glass.mix(light, p >= 0 ? cc : Palette.PANEL, occupied ? 0.12f : 0.05f), false);
            }
        }

        for (int p = 0; p < 2; p++) {
            for (int i = 0; i < PIECES; i++) {
                if (atHome(p, i)) {
                    int[] h = HOME_CELLS[quadOf(p)][this.homeStep[p][i]];
                    drawPiece(g, h[0], h[1], p, this.current == p && this.selected == i);
                } else if (onTrack(p, i)) {
                    int[] t = TRACK[this.pos[p][i]];
                    drawPiece(g, t[0], t[1], p, this.current == p && this.selected == i);
                } else {
                    int[] s = START_CELLS[quadOf(p)][i];
                    float c = cell();
                    float rad = c * 0.24f;
                    float x = cellX(s[1]) + c / 2.0f;
                    float y = cellY(s[0]) + c / 2.0f;
                    Glass.disc(g, x, y, rad, Glass.a(0xFF2A2A33, 0.75f));
                    Glass.ring(g, x - rad, y - rad, rad * 2, rad * 2, rad, 0.7f, Glass.a(pieceColor(p), 0.45f));
                }
            }
        }

        float pulse = 0.80f + 0.20f * (float) Math.sin(dicePulse * Math.PI * 2.0f);
        int diceCol = pieceColor(this.current);
        Glass.disc(g, this.px + 24, this.py + this.ph - 58, 7.0f * pulse, diceCol);
        Glass.ring(g, this.px + 17, this.py + this.ph - 65, 14.0f, 14.0f, 7.0f, 0.8f, Glass.a(diceCol, 0.30f));
        Glass.textR(g, this.font, this.over
                        ? (this.winner == this.myColor ? "Du gewinnst" : "Kollege gewinnt")
                        : (myTurn() ? "Du bist am Zug" : "Gegner ist am Zug"),
                this.px + this.pw - 18, this.py + this.ph - 24, Palette.MUTED, false);
        Glass.text(g, this.font, Glass.trunc(this.font,
                        this.status.isEmpty() ? "Eigene Figur anklicken zum Wuerfeln" : this.status, this.pw - 150),
                this.px + 16, this.py + this.ph - 24, Palette.ACCENT, false);

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

    private int ownerOfQuad(int q) {
        for (int p = 0; p < 2; p++) {
            if (quadOf(p) == q) {
                return p;
            }
        }
        return -1;
    }

    @Override
    protected void closeGame() {
        GameNet.setSink(null);
        if (this.mode != 0) {
            try {
                GameNet.send(GameNet.msg(ID, "bye", GameNet.room(ID)));
            } catch (Throwable ignored) {
            }
        }
    }
}
