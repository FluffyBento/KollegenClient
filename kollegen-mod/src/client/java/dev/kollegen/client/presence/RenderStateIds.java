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
        return IDS.get(state);
    }
}
