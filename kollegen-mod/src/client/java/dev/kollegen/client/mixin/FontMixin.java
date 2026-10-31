package dev.kollegen.client.mixin;

import dev.kollegen.client.mods.modules.Performance;
import dev.kollegen.client.mods.modules.SevenTvEmoteManager;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.network.chat.FontDescription;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Font.class)
public class FontMixin {

    private static final int KOLLEGEN$CACHE_MASK = 1023;
    private static final String[] KOLLEGEN$CACHED_STRINGS = new String[1024];
    private static final int[] KOLLEGEN$CACHED_WIDTHS = new int[1024];

    @Inject(method = "width(Ljava/lang/String;)I", at = @At("HEAD"), cancellable = true)
    private void kollegen$fastWidth(String text, CallbackInfoReturnable<Integer> cir) {
        if (text == null || text.isEmpty()) {
            cir.setReturnValue(0);
            return;
        }

        if (!Performance.KollegenTricks.shouldCacheFont()) return;

        int idx = (text.hashCode() & 0x7FFFFFFF) & KOLLEGEN$CACHE_MASK;
        String cached = KOLLEGEN$CACHED_STRINGS[idx];
        if (cached != null && cached.equals(text)) {
            cir.setReturnValue(KOLLEGEN$CACHED_WIDTHS[idx]);
        }
    }

    @Inject(method = "width(Ljava/lang/String;)I", at = @At("RETURN"))
    private void kollegen$storeWidth(String text, CallbackInfoReturnable<Integer> cir) {
        if (text == null || text.isEmpty() || !Performance.KollegenTricks.shouldCacheFont()) return;

        int idx = (text.hashCode() & 0x7FFFFFFF) & KOLLEGEN$CACHE_MASK;
        KOLLEGEN$CACHED_STRINGS[idx] = text;
        KOLLEGEN$CACHED_WIDTHS[idx] = cir.getReturnValueI();
    }

    @Inject(method = "getGlyphSource", at = @At("HEAD"), cancellable = true)
    private void kollegen$getGlyphSource(FontDescription fontLocation, CallbackInfoReturnable<GlyphSource> cir) {
        if (fontLocation instanceof FontDescription.AtlasSprite sprite) {
            if ("kollegen".equals(sprite.atlasId().getNamespace()) && "7tv".equals(sprite.atlasId().getPath())) {
                GlyphSource src = SevenTvEmoteManager.getGlyphSource(sprite.spriteId().getPath());
                if (src != null) {
                    cir.setReturnValue(src);
                }
            }
        }
    }
}
