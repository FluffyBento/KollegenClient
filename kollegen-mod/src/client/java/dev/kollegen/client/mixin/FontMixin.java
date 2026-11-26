package dev.kollegen.client.mixin;

import dev.kollegen.client.mods.modules.Performance;
import dev.kollegen.client.mods.modules.SevenTvEmoteManager;
import dev.kollegen.client.perf.KollegenFontCache;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.network.chat.FontDescription;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Font.class)
public class FontMixin {

    @Inject(method = "getGlyphSource(Lnet/minecraft/network/chat/FontDescription;)Lnet/minecraft/client/gui/GlyphSource;", at = @At("HEAD"), cancellable = true)
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

    @Inject(method = "width(Ljava/lang/String;)I", at = @At("HEAD"), cancellable = true)
    private void kollegen$fastWidth(String text, CallbackInfoReturnable<Integer> cir) {
        if (text == null || text.isEmpty()) {
            cir.setReturnValue(0);
            return;
        }

        if (!Performance.KollegenTricks.shouldCacheFont()) return;

        int idx = (text.hashCode() & 0x7FFFFFFF) & KollegenFontCache.MASK;
        KollegenFontCache.WidthEntry entry = KollegenFontCache.table()[idx];
        if (entry != null && entry.text.equals(text)) {
            cir.setReturnValue(entry.width);
        }
    }

    @Inject(method = "width(Ljava/lang/String;)I", at = @At("RETURN"))
    private void kollegen$storeWidth(String text, CallbackInfoReturnable<Integer> cir) {
        if (text == null || text.isEmpty() || !Performance.KollegenTricks.shouldCacheFont()) return;

        int idx = (text.hashCode() & 0x7FFFFFFF) & KollegenFontCache.MASK;
        KollegenFontCache.table()[idx] = new KollegenFontCache.WidthEntry(text, cir.getReturnValueI());
    }
}
