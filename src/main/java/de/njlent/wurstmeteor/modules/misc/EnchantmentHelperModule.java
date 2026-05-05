package de.njlent.wurstmeteor.modules.misc;

import de.njlent.wurstmeteor.WurstMeteorAddon;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
        .description("Maximum lines printed per container.")
        .defaultValue(12)
        .range(1, 50)
        .sliderRange(3, 25)
        .build()
    );

    private AbstractContainerScreen<?> lastScreen;

    public EnchantmentHelperModule() {
        super(WurstMeteorAddon.CATEGORY, "enchantment-helper", "Summarizes enchanted items in open containers.");
    }

    @Override
    public void onActivate() {
        lastScreen = null;
    }

    @Override
    public void onDeactivate() {
        lastScreen = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) {
            lastScreen = null;
            return;
        }

        if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) {
            lastScreen = null;
            return;
        }

        if (screen == lastScreen) return;
        lastScreen = screen;

        List<Entry> entries = scan(screen);
        if (entries.isEmpty()) return;

        entries.sort(Comparator.comparing(Entry::category).thenComparing(Entry::slot));
        info("EnchantmentHelper found %d enchanted stack%s.", entries.size(), entries.size() == 1 ? "" : "s");

        int limit = Math.min(maxLines.get(), entries.size());
        for (int i = 0; i < limit; i++) {
            Entry entry = entries.get(i);
            info("%s %s", entry.category(), entry.text());
        }

        if (entries.size() > limit) info("...and %d more.", entries.size() - limit);
    }

    private List<Entry> scan(AbstractContainerScreen<?> screen) {
        List<Entry> entries = new ArrayList<>();
        int playerSlotsStart = Math.max(0, screen.getMenu().slots.size() - 36);

        for (int i = 0; i < screen.getMenu().slots.size(); i++) {
            Slot slot = screen.getMenu().slots.get(i);
            boolean playerSlot = i >= playerSlotsStart;
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
        if (stack.is(Items.ENCHANTED_BOOK)) return ChatFormatting.AQUA + "Book" + ChatFormatting.RESET;
        if (stack.getItem().components().has(DataComponents.WEAPON)) return ChatFormatting.RED + "Weapon" + ChatFormatting.RESET;
        return ChatFormatting.GOLD + "Gear" + ChatFormatting.RESET;
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

    private record Entry(String category, int slot, String text) {}
}
