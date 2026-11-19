package dev.kollegen.client.mods.modules;

import dev.kollegen.client.mods.BooleanSetting;
import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.HudModule;
import dev.kollegen.client.mods.SliderSetting;
import dev.kollegen.client.ui.Glass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.sounds.SoundEvents;
import org.joml.Matrix3x2fStack;

public class JumpReset extends HudModule {
    public static volatile JumpReset INSTANCE;

    public final BooleanSetting showMs = new BooleanSetting(
            "Millisekunden anzeigen",
            "Zeigt die genaue zeitliche Abweichung in ms an.",
            true
    );

    public final BooleanSetting streak = new BooleanSetting(
            "Streak-Zähler",
            "Zählt aufeinanderfolgende perfekte Jump Resets.",
            true
    );

    public final BooleanSetting sound = new BooleanSetting(
            "Erfolgs-Sound",
            "Spielt einen dezenten Ton bei einem perfekten Jump Reset ab.",
            true
    );

    public final SliderSetting displayDuration = new SliderSetting(
            "Anzeigedauer (s)",
            "Dauer der Ergebnisanzeige auf dem Bildschirm.",
            2.2, 0.5, 5.0, 0.1,
            v -> String.format(java.util.Locale.ROOT, "%.1fs", v), null
    );

    private static long lastHitNano = 0L;
    private static long lastJumpNano = 0L;
    private static boolean waitingForJump = false;
    private static String currentStatus = "";
    private static long currentDiffMs = 0L;
    private static long resultTimestamp = 0L;
    private static int streakCount = 0;
    private static int statusColor = 0xFF22C55E;

    public JumpReset() {
        super("jumpreset", "Jump Reset", "Hilft beim perfekten Timing für Knockback-Reduktion durch Springen im Treffermoment.", Category.HUD);
        INSTANCE = this;
        this.position.index = 4;
        this.offsetY.value = 80.0;
        add(showMs);
        add(streak);
        add(sound);
        add(displayDuration);
    }

    public static void onPlayerDamage() {
        if (INSTANCE == null || !INSTANCE.enabled) return;
        long now = System.nanoTime();
        lastHitNano = now;
        if (lastJumpNano > 0L && (now - lastJumpNano <= 350_000_000L)) {
            long diff = (lastJumpNano - now) / 1_000_000L;
            if (diff >= -50L) {
                recordResult("PERFEKT", diff, 0xFF22C55E, true);
            } else {
                recordResult("ZU FRÜH", diff, 0xFFF59E0B, false);
            }
            waitingForJump = false;
        } else {
            waitingForJump = true;
        }
    }

    public static void onPlayerJump() {
        if (INSTANCE == null || !INSTANCE.enabled) return;
        long now = System.nanoTime();
        lastJumpNano = now;
        if (waitingForJump && lastHitNano > 0L && (now - lastHitNano <= 350_000_000L)) {
            long diff = (now - lastHitNano) / 1_000_000L;
            if (diff <= 50L) {
                recordResult("PERFEKT", diff, 0xFF22C55E, true);
            } else {
                recordResult("ZU SPÄT", diff, 0xFFEF4444, false);
            }
            waitingForJump = false;
        }
    }

    @Override
    public void onTick() {
        if (!enabled) return;
        if (waitingForJump && lastHitNano > 0L && (System.nanoTime() - lastHitNano > 350_000_000L)) {
            waitingForJump = false;
            recordResult("VERPASST", 0L, 0xFFEF4444, false);
        }
    }

    private static void recordResult(String status, long diffMs, int color, boolean perfect) {
        currentStatus = status;
        currentDiffMs = diffMs;
        statusColor = color;
        resultTimestamp = System.currentTimeMillis();
        if (perfect) {
            streakCount++;
            if (INSTANCE != null && INSTANCE.sound.value) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6f, 1.35f);
                }
            }
        } else {
            streakCount = 0;
        }
    }

    @Override
    public void onRenderHud(GuiGraphicsExtractor g, float tickDelta) {
        if (!enabled) return;
        long now = System.currentTimeMillis();
        boolean isPreview = editMode && (now - resultTimestamp > (long) (displayDuration.value * 1000L));
        if (resultTimestamp <= 0L && !isPreview) return;

        long elapsed = now - resultTimestamp;
        long totalMs = (long) (displayDuration.value * 1000L);
        if (!isPreview && elapsed > totalMs) return;

        float alpha = 1.0f;
        if (!isPreview && totalMs - elapsed < 400L) {
            alpha = Math.max(0.0f, (float) (totalMs - elapsed) / 400.0f);
        }

        String status = isPreview ? "PERFEKT" : currentStatus;
        long diff = isPreview ? 12L : currentDiffMs;
        int color = isPreview ? 0xFF22C55E : statusColor;
        int streakVal = isPreview ? 3 : streakCount;

        String diffText = "";
        if (showMs.value && !status.equals("VERPASST")) {
            diffText = (diff >= 0 ? "+" : "") + diff + " ms";
        }

        String streakText = "";
        if (streak.value && streakVal > 1) {
            streakText = "x" + streakVal;
        }

        int pillW = 118;
        if (!diffText.isEmpty() && !streakText.isEmpty()) {
            pillW = 138;
        }
        int pillH = 26;

        float s = (float) this.scale.value;
        int sw = Math.round(pillW * s);
        int sh = Math.round(pillH * s);
        int[] a = this.anchor(this.mc.getWindow().getGuiScaledWidth(), this.mc.getWindow().getGuiScaledHeight(), sw, sh);
        this.markBounds(a[0], a[1], sw, sh);

        Matrix3x2fStack p = g.pose();
        p.pushMatrix();
        p.translate((float) a[0], (float) a[1]);
        p.scale(s, s);

        int bgAlpha = (int) (220 * alpha);
        int cardBg = (bgAlpha << 24) | 0x10131C;
        int ringAlpha = (int) (180 * alpha);
        int cardRing = (ringAlpha << 24) | (color & 0xFFFFFF);

        Glass.fillRound(g, 0, 0, pillW, pillH, 8, cardBg);
        Glass.ring(g, 0, 0, pillW, pillH, 8, 1.2f, cardRing);

        int dotAlpha = (int) (255 * alpha);
        int dotColor = (dotAlpha << 24) | (color & 0xFFFFFF);
        Glass.fillRound(g, 8, 9, 8, 8, 4, dotColor);

        int textAlpha = (int) (255 * alpha);
        int textCol = (textAlpha << 24) | (color & 0xFFFFFF);
        Glass.text(g, this.mc.font, status, 22, 6, textCol, true);

        if (!streakText.isEmpty()) {
            int stCol = (textAlpha << 24) | 0xFFD700;
            Glass.text(g, this.mc.font, streakText, pillW - 20, 6, stCol, true);
        }

        if (!diffText.isEmpty()) {
            int subAlpha = (int) (190 * alpha);
            int subCol = (subAlpha << 24) | 0xCCD4E0;
            Glass.text(g, this.mc.font, diffText, 22, 16, subCol, false);
        }

        p.popMatrix();
    }
}
