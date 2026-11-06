package dev.kollegen.client.mods.modules;

import dev.kollegen.client.mods.BooleanSetting;
import dev.kollegen.client.mods.HudModule;
import dev.kollegen.client.mods.Palette;
import dev.kollegen.client.mods.SliderSetting;
import dev.kollegen.client.ui.Glass;
import dev.kollegen.client.ui.UiStyleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.*;

public class ItemChangesHudModule extends HudModule {

    public final SliderSetting duration = new SliderSetting("Anzeigedauer", "Dauer in Sekunden, wie lange Item-Änderungen sichtbar sind.", 3.5, 1.0, 10.0, 0.5, v -> v + "s", null);
    public final SliderSetting maxItems = new SliderSetting("Max. Einträge", "Maximale Anzahl an gleichzeitig angezeigten Einträgen.", 6.0, 1.0, 15.0, 1.0, v -> String.valueOf(v.intValue()), null);
    public final BooleanSetting showNames = new BooleanSetting("Item-Namen", "Zeigt den Namen des Items neben der Anzahl an.", true);
    public final BooleanSetting sound = new BooleanSetting("Ton abspielen", "Spielt einen dezenten Ton bei Änderungen ab.", false);

    private final Map<Item, Integer> lastCounts = new HashMap<>();
    private final List<ChangeEntry> history = new ArrayList<>();
    private boolean initialized = false;

    private static final class ChangeEntry {
        final ItemStack stack;
        int amount;
        long timestamp;
        float anim = 0.0f;

        ChangeEntry(ItemStack stack, int amount, long timestamp) {
            this.stack = stack;
            this.amount = amount;
            this.timestamp = timestamp;
        }
    }

    public ItemChangesHudModule() {
        super("itemchanges", "Item-Änderungen", "Zeigt aufgenommene (+) und abgelegte/verbrauchte (-) Items als fließende Benachrichtigung an.");
        add(duration);
        add(maxItems);
        add(showNames);
        add(sound);
    }

    @Override
    public void onRenderHud(GuiGraphicsExtractor g, float td) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            lastCounts.clear();
            history.clear();
            initialized = false;
            return;
        }

        updateInventoryTracking(mc);

        long now = System.currentTimeMillis();
        long maxDurationMs = (long) (duration.value * 1000.0);
        history.removeIf(e -> now - e.timestamp > maxDurationMs);

        int maxDisplay = (int) maxItems.value;
        List<ChangeEntry> displayList = new ArrayList<>();
        if (history.isEmpty() && editMode) {
            displayList.add(new ChangeEntry(new ItemStack(Items.DIAMOND), 16, now));
            displayList.add(new ChangeEntry(new ItemStack(Items.DIAMOND_SWORD), -1, now));
            displayList.add(new ChangeEntry(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE), 2, now));
        } else {
            for (int i = Math.max(0, history.size() - maxDisplay); i < history.size(); i++) {
                displayList.add(history.get(i));
            }
        }

        if (displayList.isEmpty()) {
            markBounds(0, 0, 0, 0);
            return;
        }

        int rowH = 22;
        int gap = 4;
        int baseH = displayList.size() * (rowH + gap) - gap;
        int baseW = 140;

        for (ChangeEntry e : displayList) {
            String sign = e.amount > 0 ? "+" + e.amount : String.valueOf(e.amount);
            String name = showNames.value ? " " + e.stack.getHoverName().getString() : "";
            int tw = mc.font.width(sign + name) + 32;
            if (tw > baseW) baseW = tw;
        }

        float sc = (float) scale.value;
        int w = Math.round(baseW * sc);
        int h = Math.round(baseH * sc);

        int[] a = anchor(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), w, h);
        int x = a[0];
        int y = a[1];

        markBounds(x, y, w, h);

        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(sc, sc);

        int cy = 0;
        for (ChangeEntry e : displayList) {
            float ageSec = (now - e.timestamp) / 1000.0f;
            float maxSec = (float) duration.value;
            float alpha = 1.0f;
            if (ageSec > maxSec - 0.6f) {
                alpha = Math.max(0.0f, (maxSec - ageSec) / 0.6f);
            }

            int bg = Glass.a(0x000000, 0.45f * alpha);
            UiStyleManager.drawCard(g, 0, cy, baseW, rowH, 6, Palette.PANEL, 0.0f, null);

            g.item(e.stack, 3, cy + 3);

            boolean gained = e.amount > 0;
            String text = gained ? "+" + e.amount : String.valueOf(e.amount);
            int textCol = gained ? Palette.GREEN : Palette.DANGER;
            textCol = Glass.a(textCol, alpha);

            g.text(mc.font, text, 24, cy + 7, textCol, true);

            if (showNames.value) {
                String name = Glass.trunc(mc.font, e.stack.getHoverName().getString(), baseW - 28 - mc.font.width(text));
                g.text(mc.font, name, 28 + mc.font.width(text), cy + 7, Glass.a(Palette.TEXT, alpha * 0.9f), false);
            }

            cy += rowH + gap;
        }

        g.pose().popMatrix();
    }

    private void updateInventoryTracking(Minecraft mc) {
        if (mc.player == null) return;
        Map<Item, Integer> current = new HashMap<>();

        Inventory inv = mc.player.getInventory();
        int size = inv.getContainerSize();
        for (int i = 0; i < size; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty()) {
                current.merge(s.getItem(), s.getCount(), Integer::sum);
            }
        }

        ItemStack off = mc.player.getOffhandItem();
        if (!off.isEmpty()) {
            current.merge(off.getItem(), off.getCount(), Integer::sum);
        }

        if (mc.player.containerMenu != null) {
            ItemStack carried = mc.player.containerMenu.getCarried();
            if (!carried.isEmpty()) {
                current.merge(carried.getItem(), carried.getCount(), Integer::sum);
            }
        }

        if (!initialized) {
            lastCounts.putAll(current);
            initialized = true;
            return;
        }

        Set<Item> allItems = new HashSet<>(lastCounts.keySet());
        allItems.addAll(current.keySet());

        long now = System.currentTimeMillis();
        for (Item item : allItems) {
            int oldCnt = lastCounts.getOrDefault(item, 0);
            int newCnt = current.getOrDefault(item, 0);
            int diff = newCnt - oldCnt;
            if (diff != 0) {
                boolean merged = false;
                for (ChangeEntry e : history) {
                    if (e.stack.is(item) && now - e.timestamp < 600 && ((diff > 0 && e.amount > 0) || (diff < 0 && e.amount < 0))) {
                        e.amount += diff;
                        e.timestamp = now;
                        merged = true;
                        break;
                    }
                }
                if (!merged) {
                    history.add(new ChangeEntry(new ItemStack(item, Math.abs(diff)), diff, now));
                }

                if (sound.value && mc.player != null) {
                    float pitch = diff > 0 ? 1.2f : 0.8f;
                    mc.player.playSound(SoundEvents.ITEM_PICKUP, 0.4f, pitch);
                }
            }
        }

        lastCounts.clear();
        lastCounts.putAll(current);
    }
}
