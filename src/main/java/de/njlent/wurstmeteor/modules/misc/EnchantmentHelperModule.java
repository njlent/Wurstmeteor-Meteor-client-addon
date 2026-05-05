package de.njlent.wurstmeteor.modules.misc;

import de.njlent.wurstmeteor.WurstMeteorAddon;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class EnchantmentHelperModule extends Module {
    private static final int PANEL_WIDTH = 230;
    private static final int PANEL_PADDING = 6;
    private static final int LINE_HEIGHT = 10;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> includePlayerInventory = sgGeneral.add(new BoolSetting.Builder()
        .name("include-player-inventory")
        .description("Also scans the player's inventory slots in the open container.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> includeShulkerContents = sgGeneral.add(new BoolSetting.Builder()
        .name("include-shulker-contents")
        .description("Also scans item data stored inside shulker boxes.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> booksOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("books-only")
        .description("Only reports enchanted books.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> maxLines = sgGeneral.add(new IntSetting.Builder()
        .name("max-lines")
        .description("Maximum visible lines in the panel.")
        .defaultValue(12)
        .range(1, 50)
        .sliderRange(3, 25)
        .build()
    );

    private AbstractContainerScreen<?> lastScreen;
    private List<Entry> currentEntries = List.of();

    public EnchantmentHelperModule() {
        super(WurstMeteorAddon.CATEGORY, "enchantment-helper", "Shows a side panel with enchanted items in open containers.");
    }

    @Override
    public void onActivate() {
        lastScreen = null;
        currentEntries = List.of();
    }

    @Override
    public void onDeactivate() {
        lastScreen = null;
        currentEntries = List.of();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) {
            lastScreen = null;
            currentEntries = List.of();
            return;
        }

        if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) {
            lastScreen = null;
            currentEntries = List.of();
            return;
        }

        if (!hasExternalSlots(screen)) {
            lastScreen = null;
            currentEntries = List.of();
            return;
        }

        if (screen == lastScreen && !currentEntries.isEmpty()) return;
        lastScreen = screen;

        List<Entry> entries = scan(screen);
        entries.sort(Comparator.comparing(Entry::category).thenComparing(Entry::slot));
        currentEntries = entries;
    }

    @EventHandler
    private void onRender(Render2DEvent event) {
        if (mc.player == null || !(mc.screen instanceof AbstractContainerScreen<?> screen) || currentEntries.isEmpty()) return;
        if (!hasExternalSlots(screen)) return;

        int visibleLines = Math.min(maxLines.get(), currentEntries.size());
        int panelHeight = PANEL_PADDING * 2 + LINE_HEIGHT * (visibleLines + 2);
        int x = Math.max(4, Math.min(event.screenWidth - PANEL_WIDTH - 4, 8));
        int y = Math.max(4, (event.screenHeight - panelHeight) / 2);

        event.graphics.fill(x, y, x + PANEL_WIDTH, y + panelHeight, 0xC0101010);
        event.graphics.outline(x, y, PANEL_WIDTH, panelHeight, 0x804E7BFF);
        event.graphics.text(mc.font, "Enchantment Helper", x + PANEL_PADDING, y + PANEL_PADDING, 0xFFEDEDED, true);
        event.graphics.text(mc.font, currentEntries.size() + " stack" + (currentEntries.size() == 1 ? "" : "s"), x + PANEL_WIDTH - 62, y + PANEL_PADDING, 0xFF9FB5FF, true);

        int lineY = y + PANEL_PADDING + LINE_HEIGHT + 4;
        for (int i = 0; i < visibleLines; i++) {
            Entry entry = currentEntries.get(i);
            int color = switch (entry.category()) {
                case "Book" -> 0xFF7FD7FF;
                case "Weapon" -> 0xFFFF8888;
                case "Shulker" -> 0xFFD9A8FF;
                default -> 0xFFFFD66E;
            };

            event.graphics.text(mc.font, trim(entry.text(), 36), x + PANEL_PADDING, lineY, color, true);
            lineY += LINE_HEIGHT;
        }

        if (currentEntries.size() > visibleLines) {
            event.graphics.text(mc.font, "+" + (currentEntries.size() - visibleLines) + " more", x + PANEL_PADDING, lineY, 0xFFB8B8B8, true);
        }
    }

    private List<Entry> scan(AbstractContainerScreen<?> screen) {
        List<Entry> entries = new ArrayList<>();
        Inventory inventory = mc.player.getInventory();

        for (Slot slot : screen.getMenu().slots) {
            boolean playerSlot = slot.container == inventory;
            if (playerSlot && !includePlayerInventory.get()) continue;

            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            collectStack(entries, stack, slot.getContainerSlot() + 1, playerSlot ? "Inv" : "Box");
        }

        return entries;
    }

    private void collectStack(List<Entry> entries, ItemStack stack, int slot, String source) {
        if (!booksOnly.get() || stack.is(Items.ENCHANTED_BOOK)) {
            String enchants = enchantments(stack);
            if (!enchants.isEmpty()) entries.add(new Entry(category(stack), slot, source + " #" + slot + " " + stack.getHoverName().getString() + " | " + enchants));
        }

        if (!includeShulkerContents.get()) return;

        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents == null) return;

        int childSlot = 1;
        for (ItemStack child : contents.nonEmptyItemCopyStream().toList()) {
            if (child.isEmpty()) continue;
            if (booksOnly.get() && !child.is(Items.ENCHANTED_BOOK)) {
                childSlot++;
                continue;
            }

            String enchants = enchantments(child);
            if (!enchants.isEmpty()) entries.add(new Entry("Shulker", slot, source + " #" + slot + "." + childSlot + " " + child.getHoverName().getString() + " | " + enchants));
            childSlot++;
        }
    }

    private String enchantments(ItemStack stack) {
        Set<Object2IntMap.Entry<Holder<Enchantment>>> entries = EnchantmentHelper.getEnchantmentsForCrafting(stack).entrySet();
        if (entries.isEmpty()) return "";

        List<String> parts = new ArrayList<>();
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : entries) {
            Identifier id = entry.getKey().unwrapKey().map(key -> key.identifier()).orElse(null);
            String name = id == null ? "unknown" : humanize(id.getPath());
            parts.add(name + " " + entry.getIntValue());
        }

        parts.sort(String.CASE_INSENSITIVE_ORDER);
        return String.join(", ", parts);
    }

    private String category(ItemStack stack) {
        if (stack.is(Items.ENCHANTED_BOOK)) return "Book";
        if (stack.getItem().components().has(DataComponents.WEAPON)) return "Weapon";
        return "Gear";
    }

    private String humanize(String path) {
        String[] parts = path.toLowerCase(Locale.ROOT).split("_");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }

        return result.isEmpty() ? path : result.toString();
    }

    private boolean hasExternalSlots(AbstractContainerScreen<?> screen) {
        if (mc.player == null) return false;
        Inventory inventory = mc.player.getInventory();
        for (Slot slot : screen.getMenu().slots) {
            if (slot.container != inventory) return true;
        }

        return false;
    }

    private String trim(String text, int max) {
        if (text.length() <= max) return text;
        return text.substring(0, Math.max(0, max - 3)) + "...";
    }

    private record Entry(String category, int slot, String text) {}
}
