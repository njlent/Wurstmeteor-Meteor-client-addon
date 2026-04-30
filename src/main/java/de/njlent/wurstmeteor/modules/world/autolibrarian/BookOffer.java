package de.njlent.wurstmeteor.modules.world.autolibrarian;

import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;

import java.util.Optional;

public record BookOffer(String id, int level, int price) implements Comparable<BookOffer> {
    private static final int DEFAULT_MAX_PRICE = 64;

    public static BookOffer parse(String raw) {
        if (raw == null) return null;

        String[] parts = raw.split(";");
        if (parts.length < 2) return null;

        try {
            String id = parts[0].trim();
            int level = Integer.parseInt(parts[1].trim());
            int maxPrice = parts.length >= 3 ? Integer.parseInt(parts[2].trim()) : DEFAULT_MAX_PRICE;
            return new BookOffer(id, level, maxPrice);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public boolean isMostlyValid() {
        return Identifier.tryParse(id) != null && level >= 1 && price >= 1 && price <= 64;
    }

    public boolean isFullyValid(RegistryAccess registryManager) {
        if (!isMostlyValid() || registryManager == null) return false;

        var registry = registryManager.lookupOrThrow(Registries.ENCHANTMENT);
        Optional<Holder.Reference<Enchantment>> optional = registry.get(Identifier.parse(id));
        if (optional.isEmpty()) return false;

        Holder<Enchantment> entry = optional.get();
        if (!entry.is(net.minecraft.tags.EnchantmentTags.TRADEABLE)) return false;
        return level <= entry.value().getMaxLevel();
    }

    public boolean sameEnchantAndLevel(BookOffer other) {
        return id.equals(other.id) && level == other.level;
    }

    public String formattedPrice() {
        return price + " emerald" + (price == 1 ? "" : "s");
    }

    public String nameWithLevel(RegistryAccess registryManager) {
        var registry = registryManager.lookupOrThrow(Registries.ENCHANTMENT);
        Optional<Holder.Reference<Enchantment>> optional = registry.get(Identifier.parse(id));
        if (optional.isEmpty()) return id + " " + level;

        Enchantment enchantment = optional.get().value();
        String base = enchantment.description().getString();
        if (enchantment.getMaxLevel() > 1) base += " " + level;
        return base;
    }

    public String toSettingString() {
        if (price >= DEFAULT_MAX_PRICE) return id + ";" + level;
        return id + ";" + level + ";" + price;
    }

    @Override
    public int compareTo(BookOffer other) {
        int idCompare = id.compareTo(other.id);
        if (idCompare != 0) return idCompare;
        return Integer.compare(level, other.level);
    }
}
