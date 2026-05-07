package de.njlent.wurstmeteor.modules.misc;

import de.njlent.wurstmeteor.WurstMeteorAddon;
import de.njlent.wurstmeteor.mixin.AbstractContainerScreenAccessor;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class EnchantmentHelperModule extends Module {
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

    private final Setting<Boolean> itemTooltips = sgGeneral.add(new BoolSetting.Builder()
        .name("item-tooltips")
        .description("Shows the normal item hover tooltip when hovering helper rows.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rankItems = sgGeneral.add(new BoolSetting.Builder()
        .name("rank-items")
        .description("Ranks enchanted items by legality and max/perfect enchantment sets.")
        .defaultValue(true)
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

    private final Setting<Integer> panelWidth = sgGeneral.add(new IntSetting.Builder()
        .name("panel-width")
        .description("Desired helper panel width. It is capped to avoid covering the open container UI.")
        .defaultValue(260)
        .range(140, 520)
        .sliderRange(180, 420)
        .build()
    );

    private AbstractContainerScreen<?> lastScreen;
    private List<Entry> currentEntries = List.of();
    private int panelX;
    private int panelY;
    private int currentPanelWidth;
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
        sortEntries(entries);
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
        currentPanelWidth = safePanelWidth(screen, graphics);

        graphics.fill(panelX, panelY, panelX + currentPanelWidth, panelY + panelHeight, 0xE0101010);
        graphics.outline(panelX, panelY, currentPanelWidth, panelHeight, 0xB04E7BFF);
        graphics.text(mc.font, "Enchantments", panelX + PANEL_PADDING, panelY + PANEL_PADDING, 0xFFEDEDED, true);
        String count = Integer.toString(currentEntries.size());
        graphics.text(mc.font, count, panelX + currentPanelWidth - PANEL_PADDING - mc.font.width(count), panelY + PANEL_PADDING, 0xFF9FB5FF, true);

        int rowY = panelY + PANEL_PADDING + 14;
        for (int i = 0; i < visibleRows; i++) {
            Entry entry = currentEntries.get(i);
            Tier tier = rankItems.get() ? entry.tier() : Tier.Enchanted;
            int color = rankItems.get() ? tier.textColor : categoryColor(entry.category());

            boolean hovered = mouseX >= panelX && mouseX <= panelX + currentPanelWidth && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            if (rankItems.get()) graphics.fill(panelX + 1, rowY, panelX + currentPanelWidth - 1, rowY + ROW_HEIGHT, tier.backgroundColor);
            if (hovered) graphics.fill(panelX + 1, rowY, panelX + currentPanelWidth - 1, rowY + ROW_HEIGHT, tier.hoverColor);
            if (rankItems.get()) graphics.fill(panelX + 1, rowY, panelX + 3, rowY + ROW_HEIGHT, tier.barColor);

            graphics.item(entry.stack(), panelX + PANEL_PADDING, rowY + 4);
            String rowText = rankItems.get() ? tier.label + " " + entry.enchantments() : entry.enchantments();
            String text = trimToWidth(rowText, currentPanelWidth - PANEL_PADDING * 3 - 16 - BUTTON_WIDTH - 8);
            drawSmallText(graphics, text, panelX + PANEL_PADDING + 21, rowY + 5, color);

            int buttonX = panelX + currentPanelWidth - PANEL_PADDING - BUTTON_WIDTH;
            int buttonY = rowY + 3;
            int buttonColor = entry.slotIndex() >= 0 ? 0xB0253348 : 0x70252525;
            graphics.fill(buttonX, buttonY, buttonX + BUTTON_WIDTH, buttonY + 12, buttonColor);
            graphics.outline(buttonX, buttonY, BUTTON_WIDTH, 12, entry.slotIndex() >= 0 ? 0xFF7FA4FF : 0xFF666666);
            drawSmallText(graphics, "Take", buttonX + 5, buttonY + 2, entry.slotIndex() >= 0 ? 0xFFEDEDED : 0xFF777777);

            if (hovered && itemTooltips.get()) {
                graphics.setTooltipForNextFrame(mc.font, entry.stack(), mouseX, mouseY);
            }

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
            int buttonX = panelX + currentPanelWidth - PANEL_PADDING - BUTTON_WIDTH;
            int buttonY = rowY + 3;
            if (mouseX >= buttonX && mouseX <= buttonX + BUTTON_WIDTH && mouseY >= buttonY && mouseY <= buttonY + 12) {
                if (entry.slotIndex() >= 0) {
                    mc.gameMode.handleContainerInput(screen.getMenu().containerId, entry.slotIndex(), 0, ContainerInput.QUICK_MOVE, mc.player);
                    currentEntries = scan(screen);
                    sortEntries(currentEntries);
                }
                return true;
            }

            rowY += ROW_HEIGHT;
        }

        return mouseX >= panelX && mouseX <= panelX + currentPanelWidth && mouseY >= panelY && mouseY <= panelY + PANEL_PADDING * 2 + 12 + visibleRows * ROW_HEIGHT;
    }

    private int safePanelWidth(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics) {
        int desiredWidth = panelWidth.get();
        int leftOfContainer = ((AbstractContainerScreenAccessor) screen).wurstmeteor$getLeftPos();
        int maxLeftWidth = leftOfContainer - panelX - 6;
        int fallbackMaxWidth = graphics.guiWidth() / 2 - panelX - 6;
        int maxWidth = Math.max(140, maxLeftWidth > 0 ? maxLeftWidth : fallbackMaxWidth);
        return Math.max(140, Math.min(desiredWidth, maxWidth));
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

    private void sortEntries(List<Entry> entries) {
        Comparator<Entry> comparator = Comparator.comparing(Entry::category).thenComparing(Entry::slot);
        if (rankItems.get()) comparator = Comparator.comparingInt((Entry entry) -> entry.tier().ordinal()).thenComparing(comparator);
        entries.sort(comparator);
    }

    private void collectStack(List<Entry> entries, ItemStack stack, int slotIndex, int slot, String source) {
        if (!booksOnly.get() || stack.is(Items.ENCHANTED_BOOK)) {
            List<EnchantData> enchantments = enchantments(stack);
            String enchants = enchantmentText(enchantments);
            if (!enchants.isEmpty()) entries.add(new Entry(category(stack), slotIndex, slot, stack.copy(), source + " #" + slot, enchants, tier(stack, enchantments)));
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

            List<EnchantData> enchantments = enchantments(child);
            String enchants = enchantmentText(enchantments);
            if (!enchants.isEmpty()) entries.add(new Entry("Shulker", -1, slot, child.copy(), source + " #" + slot + "." + childSlot, enchants, tier(child, enchantments)));
            childSlot++;
        }
    }

    private List<EnchantData> enchantments(ItemStack stack) {
        Set<Object2IntMap.Entry<Holder<Enchantment>>> entries = EnchantmentHelper.getEnchantmentsForCrafting(stack).entrySet();
        if (entries.isEmpty()) return List.of();

        List<EnchantData> result = new ArrayList<>();
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : entries) {
            Identifier id = entry.getKey().unwrapKey().map(key -> key.identifier()).orElse(null);
            Enchantment enchantment = entry.getKey().value();
            result.add(new EnchantData(id == null ? "unknown" : id.getPath(), entry.getIntValue(), enchantment.getMaxLevel(), entry.getKey(), enchantment));
        }

        result.sort(Comparator.comparing(data -> data.id));
        return result;
    }

    private String enchantmentText(List<EnchantData> enchantments) {
        List<String> parts = new ArrayList<>();
        for (EnchantData enchantment : enchantments) parts.add(humanize(enchantment.id()) + " " + enchantment.level());
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

    private Tier tier(ItemStack stack, List<EnchantData> enchantments) {
        if (enchantments.isEmpty()) return Tier.None;
        if (isIllegal(stack, enchantments)) return Tier.Illegal;

        Map<String, Integer> levels = levelsById(enchantments);
        PerfectSpec spec = stack.is(Items.ENCHANTED_BOOK) ? null : perfectSpec(stack);
        if (spec != null) {
            VariantScore score = bestVariantScore(levels, enchantments, spec);
            if (score.perfect()) return Tier.Perfect;
            if (score.closeToPerfect()) return Tier.Maxed;
            if (score.good()) return Tier.Strong;
        }

        if (isSingleMending(enchantments)) return Tier.Strong;
        if (enchantments.size() == 1) return Tier.Enchanted;

        boolean allPresentMax = enchantments.stream().allMatch(enchantment -> enchantment.level() >= enchantment.maxLevel());
        double ratio = enchantments.stream().mapToDouble(enchantment -> Math.min(1.0, enchantment.level() / (double) Math.max(1, enchantment.maxLevel()))).average().orElse(0.0);
        if (allPresentMax && enchantments.size() >= 3) return Tier.Maxed;
        return ratio >= 0.65 ? Tier.Strong : Tier.Enchanted;
    }

    private boolean isIllegal(ItemStack stack, List<EnchantData> enchantments) {
        for (EnchantData enchantment : enchantments) {
            if (enchantment.level() > enchantment.maxLevel()) return true;
            if (!stack.is(Items.ENCHANTED_BOOK) && !enchantment.enchantment().isSupportedItem(stack)) return true;
        }

        for (int i = 0; i < enchantments.size(); i++) {
            for (int j = i + 1; j < enchantments.size(); j++) {
                if (!Enchantment.areCompatible(enchantments.get(i).holder(), enchantments.get(j).holder())) return true;
            }
        }

        return false;
    }

    private Map<String, Integer> levelsById(List<EnchantData> enchantments) {
        Map<String, Integer> levels = new HashMap<>();
        for (EnchantData enchantment : enchantments) levels.put(enchantment.id(), enchantment.level());
        return levels;
    }

    private boolean isSingleMending(List<EnchantData> enchantments) {
        return enchantments.size() == 1 && enchantments.getFirst().id().equals("mending");
    }

    private VariantScore bestVariantScore(Map<String, Integer> levels, List<EnchantData> enchantments, PerfectSpec spec) {
        VariantScore best = VariantScore.empty();
        for (Map<String, Integer> variant : spec.variants()) {
            VariantScore score = scoreVariant(levels, enchantments, variant);
            if (score.compareTo(best) > 0) best = score;
        }

        return best;
    }

    private VariantScore scoreVariant(Map<String, Integer> levels, List<EnchantData> enchantments, Map<String, Integer> variant) {
        int matched = 0;
        boolean missingCore = false;
        for (Map.Entry<String, Integer> required : variant.entrySet()) {
            int level = levels.getOrDefault(required.getKey(), 0);
            if (level >= required.getValue()) matched++;
            else if (isCoreEnchant(required.getKey())) missingCore = true;
        }

        boolean allPresentMax = enchantments.stream().allMatch(enchantment -> enchantment.level() >= enchantment.maxLevel());
        boolean exact = levels.keySet().stream().allMatch(variant::containsKey);
        return new VariantScore(matched, variant.size(), missingCore, allPresentMax, exact, levels.containsKey("mending"));
    }

    private boolean isCoreEnchant(String enchantment) {
        return enchantment.equals("mending") || enchantment.equals("unbreaking");
    }

    private PerfectSpec perfectSpec(ItemStack stack) {
        String item = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();

        if (item.endsWith("_helmet")) return spec(required("mending", 1, "unbreaking", 3, "respiration", 3, "aqua_affinity", 1, "thorns", 3), oneOf("protection", 4, "projectile_protection", 4, "blast_protection", 4, "fire_protection", 4));
        if (item.endsWith("_chestplate")) return spec(required("mending", 1, "unbreaking", 3, "thorns", 3), oneOf("protection", 4, "projectile_protection", 4, "blast_protection", 4, "fire_protection", 4));
        if (item.endsWith("_leggings")) return spec(required("mending", 1, "unbreaking", 3, "swift_sneak", 3, "thorns", 3), oneOf("protection", 4, "projectile_protection", 4, "blast_protection", 4, "fire_protection", 4));
        if (item.endsWith("_boots")) return spec(required("mending", 1, "unbreaking", 3, "feather_falling", 4, "soul_speed", 3, "thorns", 3), oneOf("protection", 4, "projectile_protection", 4, "blast_protection", 4, "fire_protection", 4), oneOf("depth_strider", 3, "frost_walker", 2));
        if (item.endsWith("_sword")) return spec(required("mending", 1, "unbreaking", 3, "looting", 3, "fire_aspect", 2, "sweeping_edge", 3, "knockback", 2), oneOf("sharpness", 5, "smite", 5, "bane_of_arthropods", 5));
        if (item.endsWith("_pickaxe") || item.endsWith("_shovel") || item.endsWith("_hoe")) return spec(required("mending", 1, "unbreaking", 3, "efficiency", 5), oneOf("fortune", 3, "silk_touch", 1));
        if (item.endsWith("_axe")) return variants(
            required("mending", 1, "unbreaking", 3, "efficiency", 5, "fortune", 3),
            required("mending", 1, "unbreaking", 3, "efficiency", 5, "silk_touch", 1),
            required("mending", 1, "unbreaking", 3, "efficiency", 5, "sharpness", 5, "fortune", 3),
            required("mending", 1, "unbreaking", 3, "efficiency", 5, "sharpness", 5, "silk_touch", 1)
        );

        return switch (item) {
            case "bow" -> spec(required("power", 5, "unbreaking", 3, "flame", 1, "punch", 2), oneOf("infinity", 1, "mending", 1));
            case "crossbow" -> spec(required("quick_charge", 3, "unbreaking", 3, "mending", 1), oneOf("multishot", 1, "piercing", 4));
            case "trident" -> variants(
                required("mending", 1, "unbreaking", 3, "impaling", 5, "riptide", 3),
                required("mending", 1, "unbreaking", 3, "impaling", 5, "loyalty", 3, "channeling", 1)
            );
            case "mace" -> spec(required("mending", 1, "unbreaking", 3, "wind_burst", 3, "fire_aspect", 2), oneOf("density", 5, "breach", 4, "smite", 5, "bane_of_arthropods", 5));
            case "fishing_rod" -> variants(required("mending", 1, "unbreaking", 3, "luck_of_the_sea", 3, "lure", 3));
            case "shears" -> variants(required("mending", 1, "unbreaking", 3, "efficiency", 5), required("mending", 1, "unbreaking", 3, "efficiency", 5, "silk_touch", 1));
            case "flint_and_steel", "shield", "carrot_on_a_stick", "warped_fungus_on_a_stick", "elytra", "brush" -> variants(required("mending", 1, "unbreaking", 3));
            default -> null;
        };
    }

    @SafeVarargs
    private PerfectSpec spec(Map<String, Integer> required, Map<String, Integer>... groups) {
        List<Map<String, Integer>> variants = new ArrayList<>();
        variants.add(new HashMap<>(required));
        for (Map<String, Integer> group : groups) {
            List<Map<String, Integer>> expanded = new ArrayList<>();
            for (Map<String, Integer> variant : variants) {
                for (Map.Entry<String, Integer> option : group.entrySet()) {
                    Map<String, Integer> next = new HashMap<>(variant);
                    next.put(option.getKey(), option.getValue());
                    expanded.add(next);
                }
            }

            variants = expanded;
        }

        return new PerfectSpec(variants);
    }

    @SafeVarargs
    private final PerfectSpec variants(Map<String, Integer>... variants) {
        List<Map<String, Integer>> copy = new ArrayList<>();
        for (Map<String, Integer> variant : variants) copy.add(new HashMap<>(variant));
        return new PerfectSpec(copy);
    }

    private Map<String, Integer> required(Object... values) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) map.put((String) values[i], (Integer) values[i + 1]);
        return map;
    }

    private Map<String, Integer> oneOf(Object... values) {
        return required(values);
    }

    private int categoryColor(String category) {
        return switch (category) {
            case "Book" -> 0xFF7FD7FF;
            case "Weapon" -> 0xFFFF8888;
            case "Shulker" -> 0xFFD9A8FF;
            default -> 0xFFFFD66E;
        };
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

    private record EnchantData(String id, int level, int maxLevel, Holder<Enchantment> holder, Enchantment enchantment) {}
    private record PerfectSpec(List<Map<String, Integer>> variants) {}
    private record VariantScore(int matched, int total, boolean missingCore, boolean allPresentMax, boolean exact, boolean hasMending) implements Comparable<VariantScore> {
        private static VariantScore empty() {
            return new VariantScore(0, 1, true, false, false, false);
        }

        private boolean perfect() {
            return exact && allPresentMax && matched == total;
        }

        private boolean closeToPerfect() {
            return allPresentMax && !missingCore && matched >= total - 1 && total > 2;
        }

        private boolean good() {
            return hasMending || matched >= 2;
        }

        @Override
        public int compareTo(VariantScore other) {
            int result = Integer.compare(matched, other.matched);
            if (result != 0) return result;
            result = Boolean.compare(!missingCore, !other.missingCore);
            if (result != 0) return result;
            return Boolean.compare(allPresentMax, other.allPresentMax);
        }
    }
    private record Entry(String category, int slotIndex, int slot, ItemStack stack, String source, String enchantments, Tier tier) {}

    private enum Tier {
        Illegal("[ILLEGAL]", 0xFFFF4A4A, 0x90FF2020, 0xC0FF3030, 0xFFFF0000),
        Perfect("[PERFECT]", 0xFFFFD76A, 0x604F3A00, 0x806B5200, 0xFFFFD000),
        Maxed("[MAX]", 0xFF7FFFD0, 0x40208070, 0x6040A080, 0xFF66FFD0),
        Strong("[GOOD]", 0xFF9AFF9A, 0x30208030, 0x5040A050, 0xFF60FF80),
        Enchanted("[ENCH]", 0xFFFFD66E, 0x201A1A1A, 0x303A4A70, 0xFFFFD66E),
        None("", 0xFFEDEDED, 0x00000000, 0x303A4A70, 0xFFEDEDED);

        private final String label;
        private final int textColor;
        private final int backgroundColor;
        private final int hoverColor;
        private final int barColor;

        Tier(String label, int textColor, int backgroundColor, int hoverColor, int barColor) {
            this.label = label;
            this.textColor = textColor;
            this.backgroundColor = backgroundColor;
            this.hoverColor = hoverColor;
            this.barColor = barColor;
        }
    }
}
