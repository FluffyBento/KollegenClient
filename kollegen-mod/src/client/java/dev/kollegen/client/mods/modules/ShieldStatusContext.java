package dev.kollegen.client.mods.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.player.Player;

public class ShieldStatusContext {
    private static final ThreadLocal<Boolean> FIRST_PERSON = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<AvatarRenderState> THIRD_PERSON_STATE = new ThreadLocal<>();
    private static final ThreadLocal<Player> CURRENT_PLAYER = new ThreadLocal<>();

    public static void setFirstPerson(boolean fp) {
        FIRST_PERSON.set(fp);
    }

    public static void setThirdPerson(AvatarRenderState state) {
        THIRD_PERSON_STATE.set(state);
    }

    public static void setPlayer(Player p) {
        CURRENT_PLAYER.set(p);
    }

    public static void clear() {
        FIRST_PERSON.remove();
        THIRD_PERSON_STATE.remove();
        CURRENT_PLAYER.remove();
    }

    public static int getCurrentColor() {
        if (!ShieldSettings.isStatusOutlineEnabled()) return 0;

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

        return 0;
    }
}
