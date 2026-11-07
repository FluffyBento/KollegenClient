package dev.kollegen.client.feed;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Random;

public final class JustNowManager {

    private static final long FIVE_MINUTES_MS = 5 * 60 * 1000L;

    private static String todayDate = "";
    private static long todayAlertTime = 0;
    private static boolean alertTriggered = false;
    private static long justNowActiveUntil = 0;
    private static boolean postedToday = false;

    private static int currentStreak = 0;
    private static String lastStreakDate = "";
    private static int lastCoinsEarned = 0;
    private static int totalCoinsEarned = 0;

    private static boolean loaded = false;
    private static long lastTickCheck = 0;

    private JustNowManager() {
    }

    private static Path getConfigFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("kollegen-justnow.json");
    }

    public static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;

        LocalDate now = LocalDate.now();
        todayDate = now.toString();

        try {
            Path p = getConfigFile();
            if (Files.isRegularFile(p)) {
                JsonObject o = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
                if (o.has("currentStreak")) currentStreak = o.get("currentStreak").getAsInt();
                if (o.has("lastStreakDate")) lastStreakDate = o.get("lastStreakDate").getAsString();
                if (o.has("lastCoinsEarned")) lastCoinsEarned = o.get("lastCoinsEarned").getAsInt();
                if (o.has("totalCoinsEarned")) totalCoinsEarned = o.get("totalCoinsEarned").getAsInt();

                if (o.has("todayDate") && todayDate.equals(o.get("todayDate").getAsString())) {
                    todayAlertTime = o.has("todayAlertTime") ? o.get("todayAlertTime").getAsLong() : 0;
                    alertTriggered = o.has("alertTriggered") && o.get("alertTriggered").getAsBoolean();
                    justNowActiveUntil = o.has("justNowActiveUntil") ? o.get("justNowActiveUntil").getAsLong() : 0;
                    postedToday = o.has("postedToday") && o.get("postedToday").getAsBoolean();
                }
            }
        } catch (Throwable ignored) {
        }

        if (todayAlertTime <= 0) {
            setupTodaySchedule(now);
        }
    }

    private static void setupTodaySchedule(LocalDate now) {
        long currentMs = System.currentTimeMillis();
        Random rnd = new Random();
        int offsetMinutes = 1 + rnd.nextInt(8);
        todayAlertTime = currentMs + offsetMinutes * 60_000L;
        alertTriggered = false;
        justNowActiveUntil = 0;
        postedToday = false;
        save();
    }

    public static synchronized void tick(Minecraft mc) {
        ensureLoaded();

        LocalDate now = LocalDate.now();
        String dateStr = now.toString();
        if (!dateStr.equals(todayDate)) {
            todayDate = dateStr;
            setupTodaySchedule(now);
        }

        long currentMs = System.currentTimeMillis();
        if (currentMs - lastTickCheck < 1000) return;
        lastTickCheck = currentMs;

        if (!alertTriggered && !postedToday && currentMs >= todayAlertTime) {
            triggerAlert(mc);
        }
    }

    private static synchronized void triggerAlert(Minecraft mc) {
        alertTriggered = true;
        justNowActiveUntil = System.currentTimeMillis() + FIVE_MINUTES_MS;
        save();

        if (mc == null) return;

        if (mc.player != null) {
            mc.player.playSound(SoundEvents.PLAYER_LEVELUP, 1.0f, 1.2f);
        }

        int potentialCoins = getCoinsForStreak(currentStreak + 1);

        if (mc.gui != null && mc.gui.hud != null) {
            mc.gui.hud.setOverlayMessage(Component.literal("§d⚡ JUSTNOW JETZT AKTIV! §fNoch 5 Minuten für +" + potentialCoins + " Coins!"), false);

            if (mc.gui.hud.getChat() != null) {
                MutableComponent alert = Component.literal("§d§l[JustNow] ⚡ §eEs ist Zeit für dein tägliches JustNow!\n")
                        .append(Component.literal("§fDu hast jetzt §a5 Minuten §fZeit! Drücke §b[F2] §ffür einen Screenshot & verdiene bis zu §6" + potentialCoins + " Coins§f! 🔥\n"));

                MutableComponent btn = Component.literal("§a§l[📸 JETZT POSTEN]")
                        .withStyle(s -> s.withBold(true)
                                .withClickEvent(new ClickEvent.RunCommand("/justnow"))
                                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Klicken, um JustNow zu öffnen"))));

                alert.append(btn);
                mc.gui.hud.getChat().addClientSystemMessage(alert);
            }
        }
    }

    public static synchronized boolean isJustNowActive() {
        ensureLoaded();
        return alertTriggered && !postedToday && System.currentTimeMillis() <= justNowActiveUntil;
    }

    public static synchronized boolean canPostLate() {
        ensureLoaded();
        return !postedToday && (alertTriggered && System.currentTimeMillis() > justNowActiveUntil);
    }

    public static synchronized boolean hasPostedToday() {
        ensureLoaded();
        return postedToday;
    }

    public static synchronized long getRemainingSeconds() {
        if (!isJustNowActive()) return 0;
        return Math.max(0, (justNowActiveUntil - System.currentTimeMillis()) / 1000L);
    }

    public static synchronized String getRemainingFormatted() {
        long sec = getRemainingSeconds();
        long m = sec / 60;
        long s = sec % 60;
        return String.format("%02d:%02d", m, s);
    }

    public static synchronized int getStreak() {
        ensureLoaded();
        return currentStreak;
    }

    public static int getCoinsForStreak(int streakDay) {
        if (streakDay <= 1) return 100;
        if (streakDay == 2) return 150;
        if (streakDay <= 5) return 150 + (streakDay - 2) * 50;
        if (streakDay <= 10) return 300 + (streakDay - 5) * 20;
        if (streakDay < 15) return 400 + (streakDay - 10) * 20;
        return 500;
    }

    public static synchronized int postJustNow(String imagePath, String caption) {
        ensureLoaded();
        boolean onTime = isJustNowActive();

        LocalDate today = LocalDate.now();
        int newStreak = currentStreak;

        if (lastStreakDate == null || lastStreakDate.isEmpty()) {
            newStreak = 1;
        } else {
            try {
                LocalDate last = LocalDate.parse(lastStreakDate);
                if (last.equals(today.minusDays(1))) {
                    newStreak++;
                } else if (!last.equals(today)) {
                    newStreak = 1;
                }
            } catch (Throwable ignored) {
                newStreak = 1;
            }
        }

        currentStreak = newStreak;
        lastStreakDate = today.toString();
        postedToday = true;

        int coins = onTime ? getCoinsForStreak(currentStreak) : 0;
        lastCoinsEarned = coins;
        totalCoinsEarned += coins;
        save();

        if (coins > 0) {
            addCoinsToClient(coins);
        }

        FeedManager.addJustNowPost(caption, imagePath, onTime, currentStreak, coins);

        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.gui != null && mc.gui.hud != null) {
            if (onTime) {
                mc.gui.hud.setOverlayMessage(Component.literal("§a⚡ JustNow pünktlich! +" + coins + " Coins! (Streak: " + currentStreak + " Tage)"), false);
                if (mc.gui.hud.getChat() != null) {
                    mc.gui.hud.getChat().addClientSystemMessage(Component.literal("§d§l[JustNow] §a✓ Pünktlich gepostet! §e+" + coins + " Coins §fverdient. Streak: §6" + currentStreak + " Tage§f! 🔥"));
                }
            } else {
                mc.gui.hud.setOverlayMessage(Component.literal("§e⚠ JustNow zu spät gepostet (0 Coins · Streak: " + currentStreak + " Tage)"), false);
                if (mc.gui.hud.getChat() != null) {
                    mc.gui.hud.getChat().addClientSystemMessage(Component.literal("§d§l[JustNow] §e⚠ Zu spät gepostet! Keine Coins erhalten, aber deine Streak von §6" + currentStreak + " Tagen §fwurde gerettet."));
                }
            }
        }

        return coins;
    }

    private static void addCoinsToClient(int amount) {
        try {
            Path p = Path.of(System.getProperty("user.home"), ".kollegen", "client.json");
            if (Files.exists(p)) {
                JsonObject o = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
                if (o.has("me") && o.get("me").isJsonObject()) {
                    JsonObject me = o.getAsJsonObject("me");
                    int cur = me.has("points") ? me.get("points").getAsInt() : 0;
                    int tot = me.has("points_total") ? me.get("points_total").getAsInt() : cur;
                    me.addProperty("points", cur + amount);
                    me.addProperty("points_total", tot + amount);
                    Files.writeString(p, o.toString());
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public static synchronized void save() {
        try {
            Path p = getConfigFile();
            if (p.getParent() != null) Files.createDirectories(p.getParent());
            JsonObject o = new JsonObject();
            o.addProperty("todayDate", todayDate);
            o.addProperty("todayAlertTime", todayAlertTime);
            o.addProperty("alertTriggered", alertTriggered);
            o.addProperty("justNowActiveUntil", justNowActiveUntil);
            o.addProperty("postedToday", postedToday);
            o.addProperty("currentStreak", currentStreak);
            o.addProperty("lastStreakDate", lastStreakDate);
            o.addProperty("lastCoinsEarned", lastCoinsEarned);
            o.addProperty("totalCoinsEarned", totalCoinsEarned);
            Files.writeString(p, o.toString());
        } catch (Throwable ignored) {
        }
    }
}
