package dev.kollegen.client.render;

public interface KollegenItemPhysicsState {
    boolean kollegen$isOnGround();
    void kollegen$setOnGround(boolean onGround);
    boolean kollegen$isBlock();
    void kollegen$setBlock(boolean isBlock);
    float kollegen$getYaw();
    void kollegen$setYaw(float yaw);
    float kollegen$getScale();
    void kollegen$setScale(float scale);
}
