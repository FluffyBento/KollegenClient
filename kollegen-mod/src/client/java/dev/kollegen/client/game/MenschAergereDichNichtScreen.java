package dev.kollegen.client.game;

import com.google.gson.JsonObject;
import dev.kollegen.client.mods.Palette;
import dev.kollegen.client.ui.Glass;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;

public class MenschAergereDichNichtScreen extends GameScreen {

    public static final String ID = "madt";

    private static final int MAIN = 48;
    private static final int HOME_LEN = 6;
    private static final int PIECES = 4;
    private static final int IN_START = 90;

    private static final int ROT = 0;
    private static final int BLU = 1;

    private final int[][] pos = new int[2][PIECES];
    private final boolean[][] home = new boolean[2][PIECES];

    private int current = ROT;
    private int dice = 0;
    private int sixes = 0;
    private boolean over = false;
    private int winner = -1;
    private int mode = 0;
    private int myColor = ROT;
    private boolean offerOpen = false;
    private int selected = -1;
    private long rollAt = 0L;

    private static final int[] START_FIELD = {0, MAIN / 2};

    public MenschAergereDichNichtScreen(Screen parent) {
        super(parent, "Mensch aergere dich nicht");
        this.frame(430, 400);
        GameNet.setSink(this::onNetMessage);
        newGame();
    }

    private void newGame() {
        for (int p = 0; p < 2; p++) {
            for (int i = 0; i < PIECES; i++) {
                this.pos[p][i] = IN_START;
                this.home[p][i] = false;
            }
        }
        this.current = ROT;
        this.dice = 0;
        this.sixes = 0;
        this.over = false;
        this.winner = -1;
        this.selected = -1;
        this.status = "";
        broadcast();
    }

    private int startField(int p) {
        return START_FIELD[p];
    }

    private int homeEntry(int p) {
        return (startField(p) + MAIN - 1) % MAIN;
    }

    private boolean onTrack(int p, int i) {
        return !this.home[p][i] && this.pos[p][i] < IN_START;
    }

    private boolean homeField(int p, int i) {
        return this.home[p][i];
    }

    private int stepsToHome(int p, int i) {
        if (this.home[p][i]) {
            return 0;
        }
        if (this.pos[p][i] >= IN_START) {
            return HOME_LEN + 1 + ((homeEntry(p) - this.pos[p][i] + MAIN) % MAIN);
        }
        int e = homeEntry(p);
        int d = (e - this.pos[p][i] + MAIN) % MAIN;
        return d + 1;
    }

    private int pieceAt(int field, int p) {
        for (int i = 0; i < PIECES; i++) {
            if (onTrack(p, i) && this.pos[p][i] == field) {
                return i;
            }
        }
        return -1;
    }

    private boolean canMove(int p, int i, int d) {
        if (this.home[p][i] || this.pos[p][i] >= IN_START) {
            return false;
        }
        int e = homeEntry(p);
        int fwd = (e - this.pos[p][i] + MAIN) % MAIN;
        if (d <= fwd) {
            return true;
        }
        int into = d - fwd - 1;
        return into < HOME_LEN;
    }

    private void movePiece(int p, int i, int d) {
        if (!canMove(p, i, d)) {
            return;
        }
        int e = homeEntry(p);
        int fwd = (e - this.pos[p][i] + MAIN) % MAIN;
        if (d <= fwd) {
            int target = (this.pos[p][i] + d) % MAIN;
            int foe = pieceAt(target, p == ROT ? BLU : ROT);
            if (foe >= 0) {
                this.pos[p == ROT ? BLU : ROT][foe] = IN_START;
                this.status = "Kollege zurueck auf Start!";
            }
            this.pos[p][i] = target;
        } else {
            this.home[p][i] = true;
            this.pos[p][i] = HOME_LEN - 1 - (d - fwd - 1);
        }
        if (allHome(p)) {
            this.over = true;
            this.winner = p;
            this.status = (p == myColor ? "Du gewinnst!" : "Der Kollege gewinnt");
        }
    }

    private boolean allHome(int p) {
        for (int i = 0; i < PIECES; i++) {
            if (!this.home[p][i]) {
                return false;
            }
        }
        return true;
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
                int worst = 0;
                int worstSteps = Integer.MAX_VALUE;
                for (int i = 0; i < PIECES; i++) {
                    if (this.home[this.current][i]) {
                        continue;
                    }
                    int s = stepsToHome(this.current, i);
                    if (s < worstSteps) {
                        worstSteps = s;
                        worst = i;
                    }
                }
                if (worstSteps != Integer.MAX_VALUE && worstSteps < 1000) {
                    this.pos[this.current][worst] = IN_START;
                    this.home[this.current][worst] = false;
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

    private void nextTurn() {
        this.current = this.current == ROT ? BLU : ROT;
        this.selected = -1;
        if (this.mode != 0 && this.current != myColor) {
            this.status = "Warte auf den Kollegen...";
        } else if (this.status.isEmpty()) {
            this.status = "Du bist am Zug";
        }
        broadcast();
    }

    private boolean anyMove(int p, int d) {
        for (int i = 0; i < PIECES; i++) {
            if (canMove(p, i, d)) {
                return true;
            }
        }
        return false;
    }

    private void rollAndMove(int p, int i) {
        int d = 1 + this.rollSpin.nextInt(6);
        this.dice = d;
        if (!canMove(p, i, d)) {
            this.status = "Passt nicht - Zug vorbei";
            endTurn(d);
            broadcast();
            return;
        }
        movePiece(p, i, d);
        if (!this.over) {
            endTurn(d);
        }
        broadcast();
    }

    private void aiTurn() {
        int p = this.current;
        int d = 1 + this.rollSpin.nextInt(6);
        this.dice = d;
        int best = -1;
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < PIECES; i++) {
            if (!canMove(p, i, d)) {
                continue;
            }
            int target = (this.pos[p][i] + d) % MAIN;
            boolean homeNow = d > (homeEntry(p) - this.pos[p][i] + MAIN) % MAIN;
            int score = homeNow ? 220 : (MAIN - (homeEntry(p) - target + MAIN) % MAIN);
            int foe = pieceAt(target, p == ROT ? BLU : ROT);
            if (foe >= 0) {
                score += 400;
            }
            int risk = 0;
            for (int j = 0; j < PIECES; j++) {
                int q = p == ROT ? BLU : ROT;
                if (onTrack(q, j)) {
                    int dd = (this.pos[q][j] - target + MAIN) % MAIN;
                    if (dd >= 1 && dd <= 3) {
                        risk += 40 - dd * 8;
                    }
                }
            }
            score -= risk;
            score += this.rollSpin.nextInt(25);
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
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

    private final java.util.Random rollSpin = new java.util.Random();

    private String encode() {
        StringBuilder sb = new StringBuilder(28);
        sb.append(this.current == ROT ? '0' : '1');
        sb.append(this.sixes);
        for (int p = 0; p < 2; p++) {
            for (int i = 0; i < PIECES; i++) {
                int v = this.home[p][i] ? 80 + this.pos[p][i] : this.pos[p][i];
                sb.append((char) ('A' + Math.min(v, 90)));
            }
        }
        return sb.toString();
    }

    private void decode(String s) {
        if (s == null || s.length() != 11) {
            return;
        }
        this.current = s.charAt(0) == '0' ? ROT : BLU;
        this.sixes = s.charAt(1) - '0';
        int k = 2;
        for (int p = 0; p < 2; p++) {
            for (int i = 0; i < PIECES; i++) {
                int v = s.charAt(k++) - 'A';
                if (v >= 80) {
                    this.home[p][i] = true;
                    this.pos[p][i] = v - 80;
                } else {
                    this.home[p][i] = false;
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
                checkHomeEnd();
            }
        } catch (Throwable ignored) {
        }
    }

    private void checkHomeEnd() {
        if (allHome(ROT)) {
            this.over = true;
            this.winner = ROT;
        } else if (allHome(BLU)) {
            this.over = true;
            this.winner = BLU;
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

    private float trackRadius() {
        return Math.min((this.pw - 80.0f), (this.ph - 120.0f)) / 2.0f;
    }

    private void fieldPos(int field, float[] out) {
        float r = trackRadius();
        float a = (float) (-Math.PI / 2.0 + (2.0 * Math.PI * field / MAIN));
        float rx = r;
        float ry = r * 0.62f;
        out[0] = this.px + this.pw / 2.0f + rx * (float) Math.cos(a);
        out[1] = this.py + 74.0f + r * 0.62f + ry * (float) Math.sin(a);
    }

    private float cell() {
        return Math.max(9.0f, Math.min(19.0f, trackRadius() * 0.16f));
    }

    private static int pieceColor(int p) {
        return p == ROT ? 0xFFF4534E : 0xFF4A9DFF;
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        if (event.button() != 0) {
            return super.mouseClicked(event, bl);
        }
        if (this.closeClicked(event)) {
            this.leave();
            return true;
        }
        if (this.over) {
            return super.mouseClicked(event, bl);
        }
        if (this.mode != 0 && this.current != this.myColor) {
            return super.mouseClicked(event, bl);
        }
        if (this.mode == 0 && this.current == BLU) {
            return super.mouseClicked(event, bl);
        }
        float c = cell();
        float[] f = new float[2];
        int p = this.current;
        if (this.selected >= 0 && this.selected < PIECES && !this.home[p][this.selected]
                && this.pos[p][this.selected] < IN_START) {
            int idx = this.selected;
            fieldPos(this.pos[p][idx], f);
            if (hit(event.x(), event.y(), f[0] - c, f[1] - c, c * 2, c * 2)) {
                rollAndMove(p, idx);
                return true;
            }
        }
        for (int i = 0; i < PIECES; i++) {
            if (this.home[p][i] || this.pos[p][i] >= IN_START) {
                continue;
            }
            fieldPos(this.pos[p][i], f);
            if (hit(event.x(), event.y(), f[0] - c, f[1] - c, c * 2, c * 2)) {
                this.selected = i;
                this.status = "Wurf mit " + (this.dice > 0 ? this.dice : "?") + " oder neu würfeln";
                return true;
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

    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        if (this.mode == 0 && this.current == BLU && !this.over) {
            if (this.rollAt == 0L) {
                this.rollAt = System.currentTimeMillis() + 550L;
            } else if (System.currentTimeMillis() >= this.rollAt) {
                this.rollAt = 0L;
                aiTurn();
            }
        }
        beginFrame(g, mx, my, pt);
        String sub = this.mode == 0
                ? "Du bist Rot - gegen die KI"
                : (this.mode == 1 ? "Du bist Rot - Multiplayer" : "Du bist Blau - Multiplayer");
        drawChrome(g, "Mensch aergere dich nicht", sub);

        float c = cell();
        float[] f = new float[2];

        for (int i = 0; i < MAIN; i++) {
            fieldPos(i, f);
            int occ = pieceAt(i, ROT);
            int occB = pieceAt(i, BLU);
            int base = Glass.mix(0xFFEDE0C8, Palette.BG, 0.12f);
            if (i == startField(ROT) || i == startField(BLU)) {
                base = Glass.mix(base, pieceColor(i == startField(ROT) ? ROT : BLU), 0.25f);
            }
            Glass.rr(g, f[0] - c * 0.42f, f[1] - c * 0.42f, c * 0.84f, c * 0.84f, 2.5f, Glass.a(base, 0.85f));
            if (occ >= 0 || occB >= 0) {
                boolean mine = occ >= 0;
                int cc = pieceColor(mine ? ROT : BLU);
                boolean sel = mine && this.selected == occ && this.current == ROT;
                float rad = c * (sel ? 0.5f : 0.42f);
                Glass.shadowSoft(g, f[0] - rad, f[1] - rad + 1, rad * 2, rad * 2, rad, 2.0f, 1.0f, 0x55000000);
                Glass.disc(g, f[0], f[1], rad, cc);
                Glass.disc(g, f[0] - rad * 0.25f, f[1] - rad * 0.25f, rad * 0.42f, Glass.a(0xFFFFFFFF, 0.25f));
            }
        }

        for (int p = 0; p < 2; p++) {
            int entry = homeEntry(p);
            fieldPos(entry, f);
            float ex = f[0];
            float ey = f[1];
            float cx = this.px + this.pw / 2.0f;
            float cy = this.py + 74.0f + trackRadius() * 0.62f;
            float dx = cx - ex;
            float dy = cy - ey;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            dx /= len;
            dy /= len;
            for (int h = 0; h < HOME_LEN; h++) {
                float hx = ex + dx * (c * 0.95f * (h + 1));
                float hy = ey + dy * (c * 0.95f * (h + 1));
                int done = 0;
                for (int i = 0; i < PIECES; i++) {
                    if (this.home[p][i] && this.pos[p][i] == h) {
                        done = 1;
                    }
                }
                Glass.rr(g, hx - c * 0.36f, hy - c * 0.36f, c * 0.72f, c * 0.72f, 2.5f,
                        Glass.a(Glass.mix(0xFFEDE0C8, pieceColor(p), done > 0 ? 0.45f : 0.12f), 0.85f));
            }
            fieldPos(startField(p), f);
            for (int i = 0; i < PIECES; i++) {
                float sx = f[0] - dx * (c * 0.95f * (i + 1));
                float sy = f[1] - dy * (c * 0.95f * (i + 1));
                boolean sel = this.current == p && this.selected == i && this.mode != 0;
                float rad = c * (sel ? 0.44f : 0.36f);
                if (this.pos[p][i] >= IN_START) {
                    Glass.disc(g, sx, sy, rad, pieceColor(p));
                    Glass.disc(g, sx - rad * 0.25f, sy - rad * 0.25f, rad * 0.4f, Glass.a(0xFFFFFFFF, 0.22f));
                } else {
                    Glass.disc(g, sx, sy, rad, Glass.a(0xFF2A2A33, 0.75f));
                    Glass.ring(g, sx - rad, sy - rad, rad * 2, rad * 2, rad, 0.8f,
                            Glass.a(pieceColor(p), 0.5f));
                }
            }
        }

        float dw = 26.0f;
        float dx0 = this.px + this.pw - 64.0f;
        float dy0 = this.py + this.ph - 56.0f;
        float t = this.dice > 0 ? this.dice : 1;
        for (int i = 0; i < 6; i++) {
            float fx = dx0 + (i % 3) * (dw + 5.0f);
            float fy = dy0 + (i / 3) * (dw + 5.0f);
            float on = (i + 1) <= t ? 1.0f : 0.25f;
            Glass.rr(g, fx, fy, dw, dw, 5, Glass.a(0xFFFFFFFF, 0.10f + 0.12f * on));
            Glass.ring(g, fx, fy, dw, dw, 5, 0.8f, Glass.a(Palette.ACCENT, 0.25f * on + 0.1f));
        }
        Glass.disc(g, this.px + this.pw - 30.0f, this.py + 44.0f, 8.0f, pieceColor(this.current));
        Glass.textR(g, this.font, this.over
                        ? (this.winner == myColor ? "Du gewinnst" : "Kollege gewinnt")
                        : (this.mode == 0 || this.current == myColor ? "Du bist am Zug" : "Gegner ist am Zug"),
                this.px + this.pw - 18, this.py + this.ph - 24, Palette.MUTED, false);
        Glass.text(g, this.font, Glass.trunc(this.font,
                        this.status.isEmpty() ? "Figur waehlen und wuerfeln" : this.status, this.pw - 150),
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