package dev.kollegen.client.presence;

import net.minecraft.client.renderer.entity.state.AvatarRenderState;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public final class RenderStateIds {
    private static final Map<AvatarRenderState, UUID> IDS = Collections.synchronizedMap(new WeakHashMap<>());

    private RenderStateIds() {
    }

    public static void put(AvatarRenderState state, UUID id) {
        if (state == null || id == null) return;
        IDS.put(state, id);
    }

    public static UUID get(AvatarRenderState state) {
        if (state == null) return null;
        UUID id = IDS.get(state);
        if (id != null) return id;
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc != null && mc.level != null && state.id != 0) {
                net.minecraft.world.entity.Entity e = mc.level.getEntity(state.id);
                if (e instanceof net.minecraft.world.entity.player.Player p) {
                    UUID u = p.getUUID();
                    IDS.put(state, u);
                    return u;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
