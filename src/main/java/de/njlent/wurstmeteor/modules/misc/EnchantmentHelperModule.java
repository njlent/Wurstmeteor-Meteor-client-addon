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
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
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
    private static final int PANEL_WIDTH = 260;
    private static final int PANEL_PADDING = 5;
    private static final int ROW_HEIGHT = 18;
    private static final int BUTTON_WIDTH = 34;
    private static final float TEXT_SCALE = 0.78F;

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
        .defaultValue(20)
        .range(1, 50)
        .sliderRange(3, 30)
        .build()
    );

    private AbstractContainerScreen<?> lastScreen;
    private List<Entry> currentEntries = List.of();
    private int panelX;
    private int panelY;
    private int visibleRows;

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

        lastScreen = screen;

        List<Entry> entries = scan(screen);
        entries.sort(Comparator.comparing(Entry::category).thenComparing(Entry::slot));
        currentEntries = entries;
    }

    public void renderPanel(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (mc.player == null || currentEntries.isEmpty()) return;
        if (!hasExternalSlots(screen)) return;

        int maxRowsForScreen = Math.max(1, (graphics.guiHeight() - PANEL_PADDING * 2 - 16) / ROW_HEIGHT);
        visibleRows = Math.min(Math.min(maxLines.get(), maxRowsForScreen), currentEntries.size());
        int panelHeight = PANEL_PADDING * 2 + 12 + visibleRows * ROW_HEIGHT;
        panelX = 6;
        panelY = Math.max(4, (graphics.guiHeight() - panelHeight) / 2);

        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + panelHeight, 0xE0101010);
        graphics.outline(panelX, panelY, PANEL_WIDTH, panelHeight, 0xB04E7BFF);
        graphics.text(mc.font, "Enchantments", panelX + PANEL_PADDING, panelY + PANEL_PADDING, 0xFFEDEDED, true);
        String count = Integer.toString(currentEntries.size());
        graphics.text(mc.font, count, panelX + PANEL_WIDTH - PANEL_PADDING - mc.font.width(count), panelY + PANEL_PADDING, 0xFF9FB5FF, true);

        int rowY = panelY + PANEL_PADDING + 14;
        for (int i = 0; i < visibleRows; i++) {
            Entry entry = currentEntries.get(i);
            int color = switch (entry.category()) {
                case "Book" -> 0xFF7FD7FF;
                case "Weapon" -> 0xFFFF8888;
                case "Shulker" -> 0xFFD9A8FF;
                default -> 0xFFFFD66E;
            };

            boolean hovered = mouseX >= panelX && mouseX <= panelX + PANEL_WIDTH && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            if (hovered) graphics.fill(panelX + 1, rowY, panelX + PANEL_WIDTH - 1, rowY + ROW_HEIGHT, 0x303A4A70);

            graphics.item(entry.stack(), panelX + PANEL_PADDING, rowY + 4);
            String text = trimToWidth(entry.enchantments(), PANEL_WIDTH - PANEL_PADDING * 3 - 16 - BUTTON_WIDTH - 8);
            drawSmallText(graphics, text, panelX + PANEL_PADDING + 21, rowY + 5, color);

            int buttonX = panelX + PANEL_WIDTH - PANEL_PADDING - BUTTON_WIDTH;
            int buttonY = rowY + 3;
            int buttonColor = entry.slotIndex() >= 0 ? 0xB0253348 : 0x70252525;
            graphics.fill(buttonX, buttonY, buttonX + BUTTON_WIDTH, buttonY + 12, buttonColor);
            graphics.outline(buttonX, buttonY, BUTTON_WIDTH, 12, entry.slotIndex() >= 0 ? 0xFF7FA4FF : 0xFF666666);
            drawSmallText(graphics, "Take", buttonX + 5, buttonY + 2, entry.slotIndex() >= 0 ? 0xFFEDEDED : 0xFF777777);

            rowY += ROW_HEIGHT;
        }

        if (currentEntries.size() > visibleRows) {
            String more = "+" + (currentEntries.size() - visibleRows) + " more";
            graphics.text(mc.font, more, panelX + PANEL_PADDING, rowY - 2, 0xFFB8B8B8, true);
        }
    }

    public boolean handlePanelClick(AbstractContainerScreen<?> screen, double mouseX, double mouseY, int button) {
        if (button != 0 || mc.player == null || mc.gameMode == null || currentEntries.isEmpty()) return false;
        if (!hasExternalSlots(screen)) return false;

        int rowY = panelY + PANEL_PADDING + 14;
        for (int i = 0; i < visibleRows && i < currentEntries.size(); i++) {
            Entry entry = currentEntries.get(i);
            int buttonX = panelX + PANEL_WIDTH - PANEL_PADDING - BUTTON_WIDTH;
            int buttonY = rowY + 3;
            if (mouseX >= buttonX && mouseX <= buttonX + BUTTON_WIDTH && mouseY >= buttonY && mouseY <= buttonY + 12) {
                if (entry.slotIndex() >= 0) {
                    mc.gameMode.handleContainerInput(screen.getMenu().containerId, entry.slotIndex(), 0, ContainerInput.QUICK_MOVE, mc.player);
                    currentEntries = scan(screen);
                    currentEntries.sort(Comparator.comparing(Entry::category).thenComparing(Entry::slot));
                }
                return true;
            }

            rowY += ROW_HEIGHT;
        }

        return mouseX >= panelX && mouseX <= panelX + PANEL_WIDTH && mouseY >= panelY && mouseY <= panelY + PANEL_PADDING * 2 + 12 + visibleRows * ROW_HEIGHT;
    }

    private List<Entry> scan(AbstractContainerScreen<?> screen) {
        List<Entry> entries = new ArrayList<>();
        Inventory inventory = mc.player.getInventory();

        for (Slot slot : screen.getMenu().slots) {
            boolean playerSlot = slot.container == inventory;
            if (playerSlot && !includePlayerInventory.get()) continue;

            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            collectStack(entries, stack, playerSlot ? -1 : slot.index, slot.getContainerSlot() + 1, playerSlot ? "Inv" : "Box");
        }

        return entries;
    }

    private void collectStack(List<Entry> entries, ItemStack stack, int slotIndex, int slot, String source) {
        if (!booksOnly.get() || stack.is(Items.ENCHANTED_BOOK)) {
            String enchants = enchantments(stack);
            if (!enchants.isEmpty()) entries.add(new Entry(category(stack), slotIndex, slot, stack.copy(), source + " #" + slot, enchants));
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
            if (!enchants.isEmpty()) entries.add(new Entry("Shulker", -1, slot, child.copy(), source + " #" + slot + "." + childSlot, enchants));
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

    private String trimToWidth(String text, int width) {
        int scaledWidth = Math.round(width / TEXT_SCALE);
        if (mc.font.width(text) <= scaledWidth) return text;
        String suffix = "...";
        int suffixWidth = mc.font.width(suffix);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (mc.font.width(builder.toString() + text.charAt(i)) + suffixWidth > scaledWidth) break;
            builder.append(text.charAt(i));
        }

        return builder + suffix;
    }

    private void drawSmallText(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(TEXT_SCALE, TEXT_SCALE);
        graphics.text(mc.font, text, 0, 0, color, true);
        graphics.pose().popMatrix();
    }

    private record Entry(String category, int slotIndex, int slot, ItemStack stack, String source, String enchantments) {}
}
