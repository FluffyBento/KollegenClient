package dev.kollegen.client.mods.modules;

import dev.kollegen.client.presence.RenderStateIds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

public class ShieldStatusContext {
    private static final ThreadLocal<Boolean> FIRST_PERSON = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<AvatarRenderState> THIRD_PERSON_STATE = new ThreadLocal<>();
    private static final ThreadLocal<Player> CURRENT_PLAYER = new ThreadLocal<>();
    private static final ThreadLocal<UUID> CURRENT_UUID = new ThreadLocal<>();

    public static void setFirstPerson(boolean fp) {
        FIRST_PERSON.set(fp);
        if (fp) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.player != null) {
                CURRENT_UUID.set(mc.player.getUUID());
            }
        }
    }

    public static void setThirdPerson(AvatarRenderState state) {
        THIRD_PERSON_STATE.set(state);
        if (state != null) {
            UUID id = RenderStateIds.get(state);
            CURRENT_UUID.set(id);
        }
    }

    public static void setPlayer(Player p) {
        CURRENT_PLAYER.set(p);
        if (p != null) {
            CURRENT_UUID.set(p.getUUID());
        }
    }

    public static UUID getCurrentPlayerUUID() {
        return CURRENT_UUID.get();
    }

    public static void clear() {
        FIRST_PERSON.remove();
        THIRD_PERSON_STATE.remove();
        CURRENT_PLAYER.remove();
        CURRENT_UUID.remove();
    }

    public static int getCurrentColor() {
        if (!ShieldSettings.isStatusOutlineEnabled() && !ShieldSettings.isStatusTintEnabled()) return 0;

        if (Boolean.TRUE.equals(FIRST_PERSON.get())) {
            return ShieldSettings.getSelfStatusColor();
        }

        Player p = CURRENT_PLAYER.get();
        if (p != null) {
            return ShieldSettings.getStatusColor(p);
        }

        AvatarRenderState st = THIRD_PERSON_STATE.get();
        if (st != null) {
            return ShieldSettings.getStatusColor(st);
        }

        UUID id = CURRENT_UUID.get();
        if (id != null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.player != null && id.equals(mc.player.getUUID())) {
                return ShieldSettings.getSelfStatusColor();
            }
            boolean cd = ShieldCooldownTracker.isOnCooldown(id);
            return cd ? ShieldSettings.getCooldownColor() : ShieldSettings.getReadyColor();
        }

        return 0;
    }
}
