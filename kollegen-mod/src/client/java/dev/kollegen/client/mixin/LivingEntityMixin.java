package dev.kollegen.client.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Inject(method = "pushEntities", at = @At("HEAD"), cancellable = true)
    private void kollegen$noPetPushEntities(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.getId() <= -1000000) {
            ci.cancel();
        }
    }

    @Inject(method = "doPush", at = @At("HEAD"), cancellable = true)
    private void kollegen$noPetDoPush(Entity entity, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.getId() <= -1000000 || (entity != null && entity.getId() <= -1000000)) {
            ci.cancel();
        }
    }

    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void kollegen$noPetPush(Entity entity, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.getId() <= -1000000 || (entity != null && entity.getId() <= -1000000)) {
            ci.cancel();
        }
    }

    @Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
    private void kollegen$noPetIsPushable(CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.getId() <= -1000000) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "handleEntityEvent", at = @At("HEAD"))
    private void kollegen$totemCounter(byte status, CallbackInfo ci) {
        if (status == 35) {
            LivingEntity self = (LivingEntity) (Object) this;
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc != null && mc.player != null) {
                if (self == mc.player) {
                    dev.kollegen.client.mods.modules.Hud.TotemCounter.onPop(null);
                } else if (self instanceof net.minecraft.world.entity.player.Player p) {
                    dev.kollegen.client.mods.modules.Hud.TotemCounter.onPop(p.getName().getString());
                }
            }
        }
    }

    @Inject(method = "getItemBlockingWith", at = @At("HEAD"), cancellable = true)
    private void kollegen$instantShieldFix(CallbackInfoReturnable<net.minecraft.world.item.ItemStack> cir) {
        if (dev.kollegen.client.mods.modules.ShieldSettings.instantShieldFixes) {
            LivingEntity self = (LivingEntity) (Object) this;
            if (self.isUsingItem()) {
                net.minecraft.world.item.ItemStack useItem = self.getUseItem();
                if (useItem != null && useItem.has(net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS)) {
                    cir.setReturnValue(useItem);
                }
            }
        }
    }
}
