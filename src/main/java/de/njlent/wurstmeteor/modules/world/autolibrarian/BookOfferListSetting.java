package de.njlent.wurstmeteor.modules.world.autolibrarian;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WIntEdit;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WMinus;
import meteordevelopment.meteorclient.settings.IVisible;
import meteordevelopment.meteorclient.settings.Setting;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.EnchantmentTags;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

public class BookOfferListSetting extends Setting<List<BookOffer>> {
    private static final int MAX_PRICE = 64;

    public BookOfferListSetting(String name, String description, List<BookOffer> defaultValue,
                                Consumer<List<BookOffer>> onChanged,
                                Consumer<Setting<List<BookOffer>>> onModuleActivated,
                                IVisible visible) {
        super(name, description, defaultValue, onChanged, onModuleActivated, visible);
    }

    @Override
    protected List<BookOffer> parseImpl(String str) {
        ArrayList<BookOffer> offers = new ArrayList<>();
        if (str == null || str.isBlank()) return offers;

        for (String token : str.split(",")) {
            BookOffer offer = BookOffer.parse(token.trim());
            if (offer != null) offers.add(offer);
        }

        return sanitize(offers);
    }

    @Override
    protected boolean isValueValid(List<BookOffer> value) {
        return true;
    }

    @Override
    protected void resetImpl() {
        value = new ArrayList<>(defaultValue);
    }

    @Override
    protected NbtCompound save(NbtCompound tag) {
        NbtList valueTag = new NbtList();

        for (BookOffer offer : sanitize(get())) {
            NbtCompound offerTag = new NbtCompound();
            offerTag.putString("id", offer.id());
            offerTag.putInt("level", offer.level());
            if (offer.price() < MAX_PRICE) offerTag.putInt("max_price", offer.price());
            valueTag.add(offerTag);
        }

        tag.put("value", valueTag);
        return tag;
    }

    @Override
    protected List<BookOffer> load(NbtCompound tag) {
        get().clear();

        if (!tag.contains("value", NbtElement.LIST_TYPE)) {
            value = sanitize(get());
            return value;
        }

        NbtList valueTag = tag.getList("value", NbtElement.COMPOUND_TYPE);
        if (valueTag.isEmpty()) valueTag = tag.getList("value", NbtElement.STRING_TYPE);

        for (NbtElement element : valueTag) {
            if (element instanceof NbtCompound offerTag) {
                String id = offerTag.contains("id", NbtElement.STRING_TYPE) ? offerTag.getString("id") : "";
                int level = offerTag.contains("level", NbtElement.NUMBER_TYPE) ? offerTag.getInt("level") : 1;
                int price = offerTag.contains("max_price", NbtElement.NUMBER_TYPE) ? offerTag.getInt("max_price") : MAX_PRICE;
                BookOffer offer = new BookOffer(id, level, price);
                if (offer.isMostlyValid()) get().add(offer);
                continue;
            }

            // Backward-compat with old StringListSetting format.
            if (element instanceof NbtString stringTag) {
                BookOffer offer = BookOffer.parse(stringTag.asString());
                if (offer != null) get().add(offer);
            }
        }

        value = sanitize(get());
        return value;
    }

    public static void fillTable(GuiTheme theme, WTable table, BookOfferListSetting setting) {
        table.clear();

        ArrayList<BookOffer> offers = sanitize(setting.get());
        List<EnchantmentChoice> choices = getChoices();

        for (int i = 0; i < offers.size(); i++) {
            int offerIndex = i;
            BookOffer offer = offers.get(i);

            EnchantmentChoice selected = findChoice(choices, offer.id());
            if (selected == null && !choices.isEmpty()) selected = choices.getFirst();
            if (selected == null) continue;

            EnchantmentChoice[] selectedRef = {selected};

            ScrollableDropdown<EnchantmentChoice> enchantmentDropdown = table
                .add(new ScrollableDropdown<>(choices.toArray(EnchantmentChoice[]::new), selectedRef[0], 8))
                .expandX()
                .widget();

            int levelValue = MathHelper.clamp(offer.level(), 1, selectedRef[0].maxLevel());
            WIntEdit levelEdit = table
                .add(theme.intEdit(levelValue, 1, selectedRef[0].maxLevel(), 1, selectedRef[0].maxLevel(), true))
                .widget();

            int maxPrice = MathHelper.clamp(offer.price(), 1, MAX_PRICE);
            WIntEdit priceEdit = table
                .add(theme.intEdit(maxPrice, 1, MAX_PRICE, 1, MAX_PRICE, true))
                .widget();

            enchantmentDropdown.action = () -> {
                selectedRef[0] = enchantmentDropdown.get();
                int clampedLevel = MathHelper.clamp(levelEdit.get(), 1, selectedRef[0].maxLevel());
                offers.set(offerIndex, new BookOffer(selectedRef[0].id(), clampedLevel, MathHelper.clamp(priceEdit.get(), 1, MAX_PRICE)));
                setting.set(sanitize(offers));
                fillTable(theme, table, setting);
            };

            levelEdit.action = () -> {
                int clampedLevel = MathHelper.clamp(levelEdit.get(), 1, selectedRef[0].maxLevel());
                levelEdit.set(clampedLevel);
                offers.set(offerIndex, new BookOffer(selectedRef[0].id(), clampedLevel, MathHelper.clamp(priceEdit.get(), 1, MAX_PRICE)));
                setting.set(sanitize(offers));
            };

            priceEdit.action = () -> {
                int clampedPrice = MathHelper.clamp(priceEdit.get(), 1, MAX_PRICE);
                priceEdit.set(clampedPrice);
                offers.set(offerIndex, new BookOffer(selectedRef[0].id(), MathHelper.clamp(levelEdit.get(), 1, selectedRef[0].maxLevel()), clampedPrice));
                setting.set(sanitize(offers));
            };

            WMinus remove = table.add(theme.minus()).widget();
            remove.action = () -> {
                offers.remove(offerIndex);
                setting.set(sanitize(offers));
                fillTable(theme, table, setting);
            };

            table.row();
        }

        if (!offers.isEmpty()) {
            table.add(theme.horizontalSeparator()).expandX();
            table.row();
        }

        WButton add = table.add(theme.button("Add")).expandX().widget();
        add.action = () -> {
            List<EnchantmentChoice> currentChoices = getChoices();
            if (currentChoices.isEmpty()) return;

            EnchantmentChoice choice = currentChoices.getFirst();
            offers.add(new BookOffer(choice.id(), 1, MAX_PRICE));
            setting.set(sanitize(offers));
            fillTable(theme, table, setting);
        };

        WButton reset = table.add(theme.button(GuiRenderer.RESET)).widget();
        reset.action = () -> {
            setting.reset();
            fillTable(theme, table, setting);
        };
        reset.tooltip = "Reset";
    }

    public static ArrayList<BookOffer> sanitize(List<BookOffer> input) {
        ArrayList<BookOffer> result = new ArrayList<>();

        for (BookOffer offer : input) {
            if (offer == null || !offer.isMostlyValid()) continue;
            if (result.stream().anyMatch(existing -> existing.sameEnchantAndLevel(offer))) continue;
            result.add(new BookOffer(offer.id(), offer.level(), MathHelper.clamp(offer.price(), 1, MAX_PRICE)));
        }

        result.sort(Comparator.naturalOrder());
        return result;
    }

    private static EnchantmentChoice findChoice(List<EnchantmentChoice> choices, String id) {
        for (EnchantmentChoice choice : choices) {
            if (choice.id().equals(id)) return choice;
        }
        return null;
    }

    private static List<EnchantmentChoice> getChoices() {
        if (MeteorClient.mc.world == null) return getFallbackChoices();

        var registry = MeteorClient.mc.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
        ArrayList<EnchantmentChoice> choices = new ArrayList<>();

        for (var entry : registry.getEntrySet()) {
            RegistryKey<Enchantment> key = entry.getKey();
            Enchantment enchantment = entry.getValue();

            RegistryEntry<Enchantment> ref = registry.getEntry(enchantment);
            if (!ref.isIn(EnchantmentTags.TRADEABLE)) continue;

            String id = key.getValue().toString();
            String name = enchantment.description().getString();
            int maxLevel = Math.max(1, enchantment.getMaxLevel());
            choices.add(new EnchantmentChoice(id, name, maxLevel));
        }

        if (choices.isEmpty()) return getFallbackChoices();

        choices.sort(Comparator.comparing(EnchantmentChoice::name, String.CASE_INSENSITIVE_ORDER));
        return choices;
    }

    private static List<EnchantmentChoice> getFallbackChoices() {
        ArrayList<EnchantmentChoice> fallback = new ArrayList<>();

        for (BookOffer offer : sanitize(List.of(
            new BookOffer("minecraft:depth_strider", 3, MAX_PRICE),
            new BookOffer("minecraft:efficiency", 5, MAX_PRICE),
            new BookOffer("minecraft:feather_falling", 4, MAX_PRICE),
            new BookOffer("minecraft:fortune", 3, MAX_PRICE),
            new BookOffer("minecraft:looting", 3, MAX_PRICE),
            new BookOffer("minecraft:lunge", 3, MAX_PRICE),
            new BookOffer("minecraft:mending", 1, MAX_PRICE),
            new BookOffer("minecraft:protection", 4, MAX_PRICE),
            new BookOffer("minecraft:respiration", 3, MAX_PRICE),
            new BookOffer("minecraft:sharpness", 5, MAX_PRICE),
            new BookOffer("minecraft:silk_touch", 1, MAX_PRICE),
            new BookOffer("minecraft:unbreaking", 3, MAX_PRICE)
        ))) {
            fallback.add(new EnchantmentChoice(offer.id(), offer.id(), Math.max(1, offer.level())));
        }

        fallback.sort(Comparator.comparing(EnchantmentChoice::name, String.CASE_INSENSITIVE_ORDER));
        return fallback;
    }

    public static class Builder extends SettingBuilder<Builder, List<BookOffer>, BookOfferListSetting> {
        public Builder() {
            super(new ArrayList<>(0));
        }

        public Builder defaultValue(BookOffer... defaults) {
            return defaultValue(defaults != null ? List.of(defaults) : new ArrayList<>());
        }

        @Override
        public Builder defaultValue(List<BookOffer> defaultValue) {
            this.defaultValue = sanitize(defaultValue);
            return this;
        }

        @Override
        public BookOfferListSetting build() {
            return new BookOfferListSetting(name, description, sanitize(defaultValue), onChanged, onModuleActivated, visible);
        }
    }

    private record EnchantmentChoice(String id, String name, int maxLevel) {
        @Override
        public String toString() {
            return name;
        }
    }
}
