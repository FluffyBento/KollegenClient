package dev.kollegen.client.mixin;

import dev.kollegen.client.render.KollegenItemPhysicsState;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ItemEntityRenderState.class)
public class ItemEntityRenderStateMixin implements KollegenItemPhysicsState {

    @Unique
    private boolean kollegen$onGround;

    @Unique
    private boolean kollegen$isBlock;

    @Unique
    private float kollegen$yaw;

    @Unique
    private float kollegen$scale = 1.0f;

    @Override
    public boolean kollegen$isOnGround() {
        return this.kollegen$onGround;
    }

    @Override
    public void kollegen$setOnGround(boolean onGround) {
        this.kollegen$onGround = onGround;
    }

    @Override
    public boolean kollegen$isBlock() {
        return this.kollegen$isBlock;
    }

    @Override
    public void kollegen$setBlock(boolean isBlock) {
        this.kollegen$isBlock = isBlock;
    }

    @Override
    public float kollegen$getYaw() {
        return this.kollegen$yaw;
    }

    @Override
    public void kollegen$setYaw(float yaw) {
        this.kollegen$yaw = yaw;
    }

    @Override
    public float kollegen$getScale() {
        return this.kollegen$scale;
    }

    @Override
    public void kollegen$setScale(float scale) {
        this.kollegen$scale = scale;
    }
}
