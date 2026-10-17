package dev.kollegen.client.game;

import com.google.gson.JsonObject;
import dev.kollegen.client.mods.Palette;
import dev.kollegen.client.ui.Glass;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;

public class ChessScreen extends GameScreen {

    public static final String ID = "chess";

    private static final int WHITE = 0;
    private static final int BLACK = 1;

    private char[] board = new char[64];
    private int side = WHITE;
    private int castling = 15;
    private int ep = -1;
    private int halfmove = 0;

    private int mode = 0;
    private int myColor = WHITE;
    private boolean over = false;
    private String result = "";
    private boolean offerOpen = false;
    private boolean flip = false;
    private int hoverSq = -1;
    private int selectedSq = -1;
    private int[] targets = new int[0];
    private int lastFrom = -1;
    private int lastTo = -1;

    private volatile boolean thinking = false;
    private volatile int aiBest = -1;
    private volatile boolean aiReady = false;
    private volatile int aiSeq = 0;

    public ChessScreen(Screen parent) {
        super(parent, "Schach");
        this.frame(420, 470);
        GameNet.setSink(this::onNetMessage);
        newGame();
    }

    private void newGame() {
        this.board = new char[64];
        java.util.Arrays.fill(this.board, '.');
        String back = "RNBQKBNR";
        for (int f = 0; f < 8; f++) {
            this.board[f] = back.charAt(f);
            this.board[8 + f] = 'P';
            this.board[48 + f] = 'p';
            this.board[56 + f] = Character.toLowerCase(back.charAt(f));
        }
        this.side = WHITE;
        this.castling = 15;
        this.ep = -1;
        this.halfmove = 0;
        this.over = false;
        this.result = "";
        this.selectedSq = -1;
        this.targets = new int[0];
        this.lastFrom = -1;
        this.lastTo = -1;
        this.flip = this.mode == 2;
        this.aiReady = false;
        this.aiBest = -1;
        this.status = "";
        broadcast();
    }

    private static int sq(int file, int rank) {
        return rank * 8 + file;
    }

    private static int fileOf(int s) {
        return s & 7;
    }

    private static int rankOf(int s) {
        return s >> 3;
    }

    private static boolean onBoard(int f, int r) {
        return f >= 0 && f < 8 && r >= 0 && r < 8;
    }

    private static boolean isWhite(char p) {
        return p >= 'A' && p <= 'Z';
    }

    private static char lower(char p) {
        return Character.toLowerCase(p);
    }

    private static boolean inside(char p, int me) {
        return p != '.' && (me == WHITE) == isWhite(p);
    }

    private boolean inCheck(int me) {
        return inCheck(this.board, me);
    }

    private static boolean inCheck(char[] b, int me) {
        int k = findKing(b, me);
        if (k < 0) {
            return false;
        }
        return attacked(b, k, me == WHITE ? BLACK : WHITE);
    }

    private static int findKing(char[] b, int me) {
        char want = me == WHITE ? 'K' : 'k';
        for (int i = 0; i < 64; i++) {
            if (b[i] == want) {
                return i;
            }
        }
        return -1;
    }

    private static boolean attacked(char[] b, int s, int by) {
        int f0 = fileOf(s);
        int r0 = rankOf(s);

        int dir = by == WHITE ? -1 : 1;
        if (onBoard(f0 - 1, r0 - dir) && b[sq(f0 - 1, r0 - dir)] == (by == WHITE ? 'P' : 'p')) {
            return true;
        }
        if (onBoard(f0 + 1, r0 - dir) && b[sq(f0 + 1, r0 - dir)] == (by == WHITE ? 'P' : 'p')) {
            return true;
        }

        int[] nf = {-2, -2, -1, -1, 1, 1, 2, 2};
        int[] nr = {-1, 1, -2, 2, -2, 2, -1, 1};
        for (int i = 0; i < 8; i++) {
            int f = f0 + nf[i];
            int r = r0 + nr[i];
            if (onBoard(f, r) && b[sq(f, r)] == (by == WHITE ? 'N' : 'n')) {
                return true;
            }
        }

        for (int df = -1; df <= 1; df++) {
            for (int dr = -1; dr <= 1; dr++) {
                if (df == 0 && dr == 0) {
                    continue;
                }
                int f = f0 + df;
                int r = r0 + dr;
                if (onBoard(f, r) && b[sq(f, r)] == (by == WHITE ? 'K' : 'k')) {
                    return true;
                }
            }
        }

        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        for (int[] d : dirs) {
            int f = f0 + d[0];
            int r = r0 + d[1];
            int n = 1;
            while (onBoard(f, r)) {
                char p = b[sq(f, r)];
                if (p != '.') {
                    if ((by == WHITE) == isWhite(p)) {
                        return lower(p) == 'q'
                                || (lower(p) == 'r' && (d[0] == 0 || d[1] == 0))
                                || (lower(p) == 'b' && d[0] != 0 && d[1] != 0);
                    }
                    break;
                }
                n++;
                f += d[0];
                r += d[1];
                if (n > 7) {
                    break;
                }
            }
        }
        return false;
    }

    private static int[] pseudoMoves(char[] b, int me, int epSq) {
        int[] buf = new int[256];
        int n = 0;
        for (int s = 0; s < 64; s++) {
            char p = b[s];
            if (!inside(p, me)) {
                continue;
            }
            char t = lower(p);
            int f0 = fileOf(s);
            int r0 = rankOf(s);
            if (t == 'p') {
                int dir = me == WHITE ? 1 : -1;
                int startRank = me == WHITE ? 1 : 6;
                int promoRank = me == WHITE ? 7 : 0;
                int f1 = f0;
                int r1 = r0 + dir;
                if (onBoard(f1, r1) && b[sq(f1, r1)] == '.') {
                    if (r1 == promoRank) {
                        n = addPromo(buf, n, s, sq(f1, r1));
                    } else {
                        buf[n++] = s * 64 + sq(f1, r1);
                        if (r0 == startRank && b[sq(f1, r0 + 2 * dir)] == '.') {
                            buf[n++] = s * 64 + sq(f1, r0 + 2 * dir);
                        }
                    }
                }
                int[] capf = {f0 - 1, f0 + 1};
                for (int cf : capf) {
                    if (!onBoard(cf, r1)) {
                        continue;
                    }
                    char victim = b[sq(cf, r1)];
                    boolean epTake = victim == '.' && sq(cf, r1) == epSq;
                    if (victim != '.' && !inside(victim, me)) {
                        if (r1 == promoRank) {
                            n = addPromo(buf, n, s, sq(cf, r1));
                        } else {
                            buf[n++] = s * 64 + sq(cf, r1);
                        }
                    } else if (epTake) {
                        buf[n++] = s * 64 + sq(cf, r1);
                    }
                }
            } else if (t == 'n') {
                int[] nf = {-2, -2, -1, -1, 1, 1, 2, 2};
                int[] nr = {-1, 1, -2, 2, -2, 2, -1, 1};
                for (int i = 0; i < 8; i++) {
                    int f = f0 + nf[i];
                    int r = r0 + nr[i];
                    if (onBoard(f, r) && !inside(b[sq(f, r)], me)) {
                        buf[n++] = s * 64 + sq(f, r);
                    }
                }
            } else if (t == 'k') {
                for (int df = -1; df <= 1; df++) {
                    for (int dr = -1; dr <= 1; dr++) {
                        if (df == 0 && dr == 0) {
                            continue;
                        }
                        int f = f0 + df;
                        int r = r0 + dr;
                        if (onBoard(f, r) && !inside(b[sq(f, r)], me)) {
                            buf[n++] = s * 64 + sq(f, r);
                        }
                    }
                }
            } else {
                int[][] dirs = t == 'b' ? new int[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}}
                        : t == 'r' ? new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}
                        : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
                for (int[] d : dirs) {
                    int f = f0 + d[0];
                    int r = r0 + d[1];
                    while (onBoard(f, r)) {
                        if (inside(b[sq(f, r)], me)) {
                            break;
                        }
                        buf[n++] = s * 64 + sq(f, r);
                        if (b[sq(f, r)] != '.') {
                            break;
                        }
                        f += d[0];
                        r += d[1];
                    }
                }
            }
        }
        int[] out = new int[n];
        System.arraycopy(buf, 0, out, 0, n);
        return out;
    }

    private static int addPromo(int[] buf, int n, int from, int to) {
        buf[n++] = from * 64 + to;
        return n;
    }

    private static int[] legalMoves(char[] b, int me, int castleMask, int epSq) {
        int[] ps = pseudoMoves(b, me, epSq);
        int[] buf = new int[ps.length + 8];
        int n = 0;
        for (int i = 0; i < ps.length; i++) {
            char[] copy = b.clone();
            applyRaw(copy, ps[i] / 64, ps[i] % 64, me);
            if (!inCheck(copy, me)) {
                buf[n++] = ps[i];
            }
        }
        n = addCastles(b, me, castleMask, buf, n);
        int[] out = new int[n];
        System.arraycopy(buf, 0, out, 0, n);
        return out;
    }

    private static int addCastles(char[] b, int me, int mask, int[] buf, int n) {
        boolean white = me == WHITE;
        int kingFrom = white ? sq(4, 0) : sq(4, 7);
        if (b[kingFrom] != (white ? 'K' : 'k') || inCheck(b, me)) {
            return n;
        }
        int foe = white ? BLACK : WHITE;
        int base = white ? 0 : 56;

        if ((mask & (white ? 1 : 4)) != 0
                && b[sq(5, white ? 0 : 7)] == '.' && b[sq(6, white ? 0 : 7)] == '.'
                && !inside(b[sq(5, white ? 0 : 7)], me) && !inside(b[sq(6, white ? 0 : 7)], me)
                && !attacked(b, sq(5, white ? 0 : 7), foe) && !attacked(b, sq(6, white ? 0 : 7), foe)
                && b[base + 7] == (white ? 'R' : 'r')) {
            buf[n++] = kingFrom * 64 + sq(6, white ? 0 : 7);
        }

        if ((mask & (white ? 2 : 8)) != 0
                && b[sq(1, white ? 0 : 7)] == '.' && b[sq(2, white ? 0 : 7)] == '.' && b[sq(3, white ? 0 : 7)] == '.'
                && !inside(b[sq(1, white ? 0 : 7)], me) && !inside(b[sq(2, white ? 0 : 7)], me)
                && !inside(b[sq(3, white ? 0 : 7)], me)
                && !attacked(b, sq(2, white ? 0 : 7), foe) && !attacked(b, sq(3, white ? 0 : 7), foe)
                && b[base] == (white ? 'R' : 'r')) {
            buf[n++] = kingFrom * 64 + sq(2, white ? 0 : 7);
        }
        return n;
    }

    private static void applyRaw(char[] b, int from, int to, int me) {
        char p = b[from];
        int dir = me == WHITE ? 1 : -1;

        if (lower(p) == 'p' && fileOf(from) != fileOf(to) && b[to] == '.') {
            b[sq(fileOf(to), rankOf(from))] = '.';
        }
        b[to] = p;
        b[from] = '.';

        int promoRank = me == WHITE ? 7 : 0;
        if (rankOf(to) == promoRank && lower(p) == 'p') {
            b[to] = me == WHITE ? 'Q' : 'q';
        }

        if (lower(p) == 'k') {
            int base = rankOf(to) * 8;
            if (fileOf(to) == 6 && b[base + 7] != '.') {
                b[base + 5] = b[base + 7];
                b[base + 7] = '.';
            } else if (fileOf(to) == 2 && b[base] != '.') {
                b[base + 3] = b[base];
                b[base] = '.';
            }
        }
    }

    private static int[] PIECE_VALUE = {0, 100, 320, 330, 500, 900, 20000};

    private static final int[] PST_PAWN = {
        0, 0, 0, 0, 0, 0, 0, 0,
        50, 50, 50, 50, 50, 50, 50, 50,
        10, 10, 20, 30, 30, 20, 10, 10,
        5, 5, 10, 25, 25, 10, 5, 5,
        0, 0, 0, 20, 20, 0, 0, 0,
        5, -5, -10, 0, 0, -10, -5, 5,
        5, 10, 10, -20, -20, 10, 10, 5,
        0, 0, 0, 0, 0, 0, 0, 0};

    private static final int[] PST_KNIGHT = {
        -50, -40, -30, -30, -30, -30, -40, -50,
        -40, -20, 0, 0, 0, 0, -20, -40,
        -30, 0, 10, 15, 15, 10, 0, -30,
        -30, 5, 15, 20, 20, 15, 5, -30,
        -30, 0, 15, 20, 20, 15, 0, -30,
        -30, 5, 10, 15, 15, 10, 5, -30,
        -40, -20, 0, 5, 5, 0, -20, -40,
        -50, -40, -30, -30, -30, -30, -40, -50};

    private static final int[] PST_BISHOP = {
        -20, -10, -10, -10, -10, -10, -10, -20,
        -10, 0, 0, 0, 0, 0, 0, -10,
        -10, 0, 5, 10, 10, 5, 0, -10,
        -10, 5, 5, 10, 10, 5, 5, -10,
        -10, 0, 10, 10, 10, 10, 0, -10,
        -10, 10, 10, 10, 10, 10, 10, -10,
        -10, 5, 0, 0, 0, 0, 5, -10,
        -20, -10, -10, -10, -10, -10, -10, -20};

    private static final int[] PST_ROOK = {
        0, 0, 0, 0, 0, 0, 0, 0,
        5, 10, 10, 10, 10, 10, 10, 5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        0, 0, 0, 5, 5, 0, 0, 0};

    private static final int[] PST_QUEEN = {
        -20, -10, -10, -5, -5, -10, -10, -20,
        -10, 0, 0, 0, 0, 0, 0, -10,
        -10, 0, 5, 5, 5, 5, 0, -10,
        -5, 0, 5, 5, 5, 5, 0, -5,
        0, 0, 5, 5, 5, 5, 0, -5,
        -10, 5, 5, 5, 5, 5, 0, -10,
        -10, 0, 5, 0, 0, 0, 0, -10,
        -20, -10, -10, -5, -5, -10, -10, -20};

    private static final int[] PST_KING_MID = {
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -20, -30, -30, -40, -40, -30, -30, -20,
        -10, -20, -20, -20, -20, -20, -20, -10,
        20, 20, 0, 0, 0, 0, 20, 20,
        20, 30, 10, 0, 0, 10, 30, 20};

    private static final int[] PST_KING_END = {
        -50, -40, -30, -20, -20, -30, -40, -50,
        -30, -20, -10, 0, 0, -10, -20, -30,
        -30, -10, 20, 30, 30, 20, -10, -30,
        -30, -10, 30, 40, 40, 30, -10, -30,
        -30, -10, 30, 40, 40, 30, -10, -30,
        -30, -10, 20, 30, 30, 20, -10, -30,
        -30, -30, 0, 0, 0, 0, -30, -30,
        -50, -30, -30, -30, -30, -30, -30, -50};

    private static int pieceValue(char p) {
        switch (lower(p)) {
            case 'p': return PIECE_VALUE[1];
            case 'n': return PIECE_VALUE[2];
            case 'b': return PIECE_VALUE[3];
            case 'r': return PIECE_VALUE[4];
            case 'q': return PIECE_VALUE[5];
            case 'k': return PIECE_VALUE[6];
            default: return 0;
        }
    }

    private static int[] pstFor(char p) {
        switch (lower(p)) {
            case 'p': return PST_PAWN;
            case 'n': return PST_KNIGHT;
            case 'b': return PST_BISHOP;
            case 'r': return PST_ROOK;
            case 'q': return PST_QUEEN;
            default: return PST_KING_MID;
        }
    }

    private static int evaluate(char[] b, int me) {
        int score = 0;
        int material = 0;
        for (int i = 0; i < 64; i++) {
            if (b[i] == '.') {
                continue;
            }
            material += pieceValue(b[i]);
        }
        boolean endgame = material <= 2600;
        for (int i = 0; i < 64; i++) {
            char p = b[i];
            if (p == '.') {
                continue;
            }
            int f = fileOf(i);
            int r = rankOf(i);
            int idx = isWhite(p) ? r * 8 + f : (7 - r) * 8 + f;
            int[] table = lower(p) == 'k' ? (endgame ? PST_KING_END : PST_KING_MID) : pstFor(p);
            int val = pieceValue(p) + table[idx];
            score += isWhite(p) ? val : -val;
        }
        return me == WHITE ? score : -score;
    }

    private int negamax(char[] b, int depth, int alpha, int beta, int me, int epSq, int castleMask, long deadline) {
        if ((System.nanoTime() & 0x3FFF) == 0 && System.nanoTime() > deadline) {
            return 0;
        }
        if (depth == 0 || inCheck(b, me)) {
            int e = evaluate(b, me);
            if (inCheck(b, me)) {
                e -= 500;
            }
            return e;
        }
        int[] moves = legalMoves(b, me, castleMask, epSq);
        if (moves.length == 0) {
            return inCheck(b, me) ? -100000 : 0;
        }
        if (moves.length == 1) {
            depth++;
        }
        int best = -1000000;
        for (int mv : moves) {
            char[] copy = b.clone();
            int from = mv / 64;
            int to = mv % 64;
            int captured = pieceValue(copy[to]);
            boolean quiet = copy[to] == '.' && !(lower(copy[from]) == 'p' && to == epSq);
            applyRaw(copy, from, to, me);
            int score = -negamax(copy, depth - 1, -beta, -alpha, me == WHITE ? BLACK : WHITE,
                    newEpSquare(from, to, me), castleAfter(castleMask, copy, from, to), deadline);
            if (score > best) {
                best = score;
            }
            if (best > alpha) {
                alpha = best;
            }
            if (alpha >= beta) {

                if (quiet && captured == 0) {
                    best += 60;
                }
                break;
            }
        }
        return best;
    }

    private static int newEpSquare(int from, int to, int me) {
        if (Math.abs(to - from) == 16 && fileOf(from) == fileOf(to)) {
            return sq(fileOf(from), (rankOf(from) + rankOf(to)) / 2);
        }
        return -1;
    }

    private static int castleAfter(int mask, char[] b, int from, int to) {
        int m = mask;
        if (from == sq(4, 0) || to == sq(4, 0)) {
            m &= ~(1 | 2);
        }
        if (from == sq(4, 7) || to == sq(4, 7)) {
            m &= ~(4 | 8);
        }
        if (from == sq(7, 0) || to == sq(7, 0)) {
            m &= ~1;
        }
        if (from == sq(0, 0) || to == sq(0, 0)) {
            m &= ~2;
        }
        if (from == sq(7, 7) || to == sq(7, 7)) {
            m &= ~4;
        }
        if (from == sq(0, 7) || to == sq(0, 7)) {
            m &= ~8;
        }
        return m;
    }

    private void startAi() {
        if (this.thinking || this.over) {
            return;
        }
        this.thinking = true;
        final char[] start = this.board.clone();
        final int epStart = this.ep;
        final int maskStart = this.castling;
        final int me = this.side;
        final int seq = ++this.aiSeq;
        final long deadline = System.nanoTime() + 400_000_000L;
        Thread t = new Thread(() -> {
            int[] moves = legalMoves(start, me, maskStart, epStart);
            if (moves.length == 0) {
                this.aiBest = -1;
            } else {
                int bestMove = moves[0];
                int bestScore = -1000000;
                for (int depth = 1; depth <= 3; depth++) {
                    int localBest = bestMove;
                    int localScore = -1000000;
                    int alpha = -1000000;
                    for (int mv : moves) {
                        char[] copy = start.clone();
                        int from = mv / 64;
                        int to = mv % 64;
                        applyRaw(copy, from, to, me);
                        int score = -negamax(copy, depth - 1, -1000000, -alpha, me == WHITE ? BLACK : WHITE,
                                newEpSquare(from, to, me), castleAfter(maskStart, copy, from, to), deadline);
                        if (score > localScore) {
                            localScore = score;
                            localBest = mv;
                        }
                        if (score > alpha) {
                            alpha = score;
                        }
                        if (System.nanoTime() > deadline) {
                            break;
                        }
                    }
                    if (localScore > -1000000) {
                        bestScore = localScore;
                        bestMove = localBest;
                    }
                    if (System.nanoTime() > deadline) {
                        break;
                    }
                }
                this.aiBest = bestMove;
            }
            this.aiReady = true;
            if (this.aiSeq == seq) {
                this.thinking = false;
            }
        }, "kollegen-schach-ki");
        t.setDaemon(true);
        t.start();
    }

    private void playMove(int mv) {
        int from = mv / 64;
        int to = mv % 64;
        applyRaw(this.board, from, to, this.side);
        this.lastFrom = from;
        this.lastTo = to;
        this.ep = newEpSquare(from, to, this.side);
        this.castling = castleAfter(this.castling, this.board, from, to);
        if (lower(this.board[to]) != 'p' && this.board[to] != '.') {
            this.halfmove = 0;
        } else {
            this.halfmove++;
        }
        this.side = this.side == WHITE ? BLACK : WHITE;
        this.selectedSq = -1;
        this.targets = new int[0];
        checkEnd();
        broadcast();
    }

    private void checkEnd() {
        int[] moves = legalMoves(this.board, this.side, this.castling, this.ep);
        if (moves.length == 0) {
            this.over = true;
            if (inCheck(this.board, this.side)) {
                String winner = this.side == WHITE ? "Schwarz" : "Weiss";
                this.result = (this.side == this.myColor ? "Matt - du verlierst" : "Matt - " + winner + " gewinnt");
            } else {
                this.over = true;
                this.result = "Patt - Unentschieden";
            }
            return;
        }
        if (this.halfmove >= 100) {
            this.over = true;
            this.result = "Unentschieden (50-Zuege-Regel)";
        }
    }

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

    private String encode() {
        StringBuilder sb = new StringBuilder(72);
        for (int i = 0; i < 64; i++) {
            sb.append(this.board[i]);
        }
        sb.append(this.side == WHITE ? 'w' : 'b');
        sb.append(Integer.toHexString(this.castling & 15));
        if (this.ep < 0) {
            sb.append("--");
        } else {
            sb.append((char) ('a' + fileOf(this.ep)));
            sb.append((char) ('1' + rankOf(this.ep)));
        }
        String hm = Integer.toString(Math.min(1295, this.halfmove), 36);
        sb.append(hm.length() < 2 ? "0" + hm : hm);
        return sb.toString();
    }

    private void decode(String s) {
        if (s == null || s.length() < 70) {
            return;
        }
        for (int i = 0; i < 64; i++) {
            char c = s.charAt(i);
            this.board[i] = (c == '.' || "KQRBNPkqrbnp".indexOf(c) >= 0) ? c : '.';
        }
        this.side = s.charAt(64) == 'b' ? BLACK : WHITE;
        this.castling = 15;
        try {
            this.castling = Integer.parseInt(s.substring(65, 66), 16) & 15;
        } catch (Throwable ignored) {
        }
        String epStr = s.substring(66, 68);
        this.ep = "--".equals(epStr) ? -1
                : sq(epStr.charAt(0) - 'a', epStr.charAt(1) - '1');
        try {
            this.halfmove = Integer.parseInt(s.substring(68, 70), 36);
        } catch (Throwable ignored) {
            this.halfmove = 0;
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
                this.myColor = WHITE;
                this.flip = false;
                this.status = "Gegner ist dabei";
            } else if ("st".equals(type)) {
                if (this.mode == 0) {
                    this.mode = 1;
                    this.myColor = WHITE;
                }
                decode(o.get("b").getAsString());
                this.over = false;
                this.result = "";
                checkEnd();
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
        this.myColor = BLACK;
        this.flip = true;
        this.status = "Du bist Schwarz";
        try {
            GameNet.send(GameNet.msg(ID, "acc", GameNet.room(ID)));
        } catch (Throwable ignored) {
        }
        broadcast();
    }

    private int squareAt(double mx, double my) {
        float s = squareSize();
        float gx = boardX();
        float gy = boardY();
        if (!hit(mx, my, gx, gy, s * 8, s * 8)) {
            return -1;
        }
        int col = (int) ((mx - gx) / s);
        int row = (int) ((my - gy) / s);
        if (col < 0 || col > 7 || row < 0 || row > 7) {
            return -1;
        }
        return this.flip ? sq(7 - col, 7 - row) : sq(col, row);
    }

    private boolean canMove() {
        return !this.over && (this.mode == 0 || this.side == this.myColor);
    }

    private void clickSquare(int s) {
        if (s < 0 || !canMove()) {
            return;
        }
        if (this.selectedSq >= 0) {
            int mv = this.selectedSq * 64 + s;
            for (int legal : this.targets) {
                if (legal == mv) {
                    this.aiReady = false;
                    playMove(legal);
                    if (this.mode == 0) {
                        startAi();
                    }
                    return;
                }
            }
        }
        if (inside(this.board[s], this.side)) {
            this.selectedSq = s;
            this.targets = movesFrom(s);
        } else {
            this.selectedSq = -1;
            this.targets = new int[0];
        }
    }

    private int[] movesFrom(int s) {
        int[] all = legalMoves(this.board, this.side, this.castling, this.ep);
        int[] buf = new int[16];
        int n = 0;
        for (int mv : all) {
            if (mv / 64 == s) {
                buf[n++] = mv;
            }
        }
        int[] out = new int[n];
        System.arraycopy(buf, 0, out, 0, n);
        return out;
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        if (event.button() != 0) {
            return super.mouseClicked(event, bl);
        }
        if (this.closeClicked(event)) {
            this.leave();
            return true;
        }
        int sq = squareAt(event.x(), event.y());
        if (sq >= 0) {
            clickSquare(sq);
            return true;
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

    public boolean mouseMoved(net.minecraft.client.input.MouseButtonEvent e, double x, double y) {
        this.hoverSq = squareAt(x, y);
        return false;
    }

    private float squareSize() {
        float avail = Math.min((this.pw - 40.0f) / 8.0f, (this.ph - 104.0f) / 8.0f);
        return Math.max(20.0f, Math.min(46.0f, avail));
    }

    private float boardX() {
        float s = squareSize();
        return this.px + (this.pw - s * 8) / 2.0f;
    }

    private float boardY() {
        float s = squareSize();
        return this.py + 48.0f + ((this.ph - 104.0f) - s * 8) / 2.0f;
    }

    private static final String GLYPH_W = "\u2654\u2655\u2656\u2657\u2658\u2659";
    private static final String GLYPH_B = "\u265A\u265B\u265C\u265D\u265E\u265F";

    private static String glyph(char p) {
        int idx;
        switch (lower(p)) {
            case 'k': idx = 0; break;
            case 'q': idx = 1; break;
            case 'r': idx = 2; break;
            case 'b': idx = 3; break;
            case 'n': idx = 4; break;
            default: idx = 5; break;
        }
        return isWhite(p) ? String.valueOf(GLYPH_W.charAt(idx)) : String.valueOf(GLYPH_B.charAt(idx));
    }

    private static String sqName(int s) {
        return "" + (char) ('a' + fileOf(s)) + (char) ('1' + rankOf(s));
    }

    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {

        if (this.mode == 0 && this.aiReady && !this.over) {
            this.aiReady = false;
            int mv = this.aiBest;
            this.aiBest = -1;
            if (mv >= 0) {
                playMove(mv);
            }
        }
        if (this.mode == 0 && this.side == BLACK && !this.over && !this.thinking) {
            startAi();
        }
        beginFrame(g, mx, my, pt);
        String sub = this.mode == 0
                ? "Du bist Weiss - gegen die KI"
                : (this.mode == 1 ? "Du bist Weiss - Multiplayer" : "Du bist Schwarz - Multiplayer");
        drawChrome(g, "Schach", sub);

        float s = squareSize();
        float gx = boardX();
        float gy = boardY();

        Glass.rr(g, gx - 6, gy - 6, s * 8 + 12, s * 8 + 12, 10, Glass.a(Palette.PANEL, 0.9f));
        Glass.ring(g, gx - 6, gy - 6, s * 8 + 12, s * 8 + 12, 10, 0.6f, Glass.a(0xFFFFFFFF, 0.16f));

        int light = Glass.mix(0xFFEDE0C8, Palette.BG, 0.10f);
        int dark = Glass.mix(0xFF7A5C3E, Palette.BG, 0.25f);

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                int sqq = this.flip ? sq(7 - col, 7 - row) : sq(col, row);
                float x = gx + col * s;
                float y = gy + row * s;
                boolean isLight = (fileOf(sqq) + rankOf(sqq)) % 2 == 0;
                int fill = isLight ? light : dark;
                Glass.rr(g, x, y, s, s, 2, Glass.a(fill, 0.92f));

                if (sqq == this.selectedSq || (sqq == this.lastFrom && this.selectedSq < 0)) {
                    Glass.rr(g, x, y, s, s, 2, Glass.a(Palette.ACCENT, 0.30f));
                }
                if (sqq == this.lastTo) {
                    Glass.rr(g, x + s * 0.3f, y + s * 0.3f, s * 0.4f, s * 0.4f, s * 0.2f,
                            Glass.a(Palette.ACCENT, 0.45f));
                }
                for (int mv : this.targets) {
                    if (mv % 64 == sqq) {
                        Glass.disc(g, x + s / 2, y + s / 2, s * 0.14f, Glass.a(Palette.ACCENT, 0.65f));
                    }
                }
                if (sqq == this.hoverSq) {
                    Glass.ring(g, x, y, s, s, 3, 0.7f, Glass.a(0xFFFFFFFF, 0.30f));
                }
                char p = this.board[sqq];
                if (p != '.') {
                    String gl = glyph(p);
                    float cy = y + (s - 8.0f) / 2.0f;
                    Glass.textC(g, this.font, gl, Math.round(x + s / 2), Math.round(cy),
                            isWhite(p) ? 0xFFFFFDF6 : 0xFF23201C, true);
                }
            }
        }

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                int sqq = this.flip ? sq(7 - col, 7 - row) : sq(col, row);
                float x = gx + col * s;
                float y = gy + row * s;
                if (col == 0) {
                    Glass.text(g, this.font, String.valueOf((char) ('1' + rankOf(sqq))),
                            Math.round(x + 2), Math.round(y + 2), Glass.a(0xFF000000, 0.45f), false);
                }
                if (row == 7) {
                    Glass.textR(g, this.font, String.valueOf((char) ('a' + fileOf(sqq))),
                            Math.round(x + s - 2), Math.round(y + s - 9), Glass.a(0xFF000000, 0.45f), false);
                }
            }
        }

        String line;
        if (this.over) {
            line = this.result;
        } else if (this.mode != 0 && this.side != this.myColor) {
            line = "Gegner ist am Zug";
        } else if (this.thinking) {
            line = "KI denkt...";
        } else {
            line = "Du bist am Zug";
        }
        Glass.text(g, this.font, Glass.trunc(this.font, line, this.pw - 130), this.px + 16,
                this.py + this.ph - 56, this.over ? Palette.ACCENT : Palette.MUTED, false);
        if (this.lastTo >= 0 && !this.over) {
            Glass.textR(g, this.font, sqName(this.lastFrom) + "-" + sqName(this.lastTo),
                    this.px + this.pw - 18, this.py + this.ph - 56, Palette.MUTED, false);
        }

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
        this.aiSeq++;
        this.thinking = false;
        if (this.mode != 0) {
            try {
                GameNet.send(GameNet.msg(ID, "bye", GameNet.room(ID)));
            } catch (Throwable ignored) {
            }
        }
    }
}