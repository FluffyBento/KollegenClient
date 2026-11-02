package dev.kollegen.client.mods.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ShieldCooldownTracker {
    private static final Map<UUID, Long> COOLDOWNS = new ConcurrentHashMap<>();

    private ShieldCooldownTracker() {
    }

    public static void onShieldBreak(double x, double y, double z) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return;
        long expiry = System.currentTimeMillis() + 5000L; // 5 Sekunden Schild-Sperre

        double bestDist = 6.0 * 6.0;
        UUID bestPlayer = null;
        for (Player p : mc.level.players()) {
            double dx = p.getX() - x;
            double dy = p.getY() - y;
            double dz = p.getZ() - z;
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 < bestDist) {
                bestDist = d2;
                bestPlayer = p.getUUID();
            }
        }
        if (bestPlayer != null) {
            COOLDOWNS.put(bestPlayer, expiry);
        }
    }

    public static boolean isOnCooldown(UUID uuid) {
        if (uuid == null) return false;
        Long exp = COOLDOWNS.get(uuid);
        if (exp == null) return false;
        if (System.currentTimeMillis() > exp) {
            COOLDOWNS.remove(uuid);
            return false;
        }
        return true;
    }
}
