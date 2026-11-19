package dev.kollegen.client.mixin;

import com.google.common.collect.ImmutableMap;
import dev.kollegen.client.shader.KollegenShaderLoader;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.TreeMap;

@Mixin(PackRepository.class)
public class PackRepositoryMixin {

    @Inject(method = "discoverAvailable", at = @At("RETURN"), cancellable = true)
    private void kollegen$injectShaderPack(CallbackInfoReturnable<Map<String, Pack>> cir) {
        Pack shaderPack = KollegenShaderLoader.getInstance().getRuntimeShaderPack();
        if (shaderPack != null) {
            Map<String, Pack> current = cir.getReturnValue();
            Map<String, Pack> mutable = new TreeMap<>(current);
            mutable.put(shaderPack.getId(), shaderPack);
            cir.setReturnValue(ImmutableMap.copyOf(mutable));
        }
    }
}
