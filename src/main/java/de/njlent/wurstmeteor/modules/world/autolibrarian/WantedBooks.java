package de.njlent.wurstmeteor.modules.world.autolibrarian;

import de.njlent.wurstmeteor.modules.world.AutoLibrarianModule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class WantedBooks {
    private WantedBooks() {
    }

    public static List<BookOffer> parse(List<String> rawEntries) {
        ArrayList<BookOffer> offers = new ArrayList<>();

        for (String raw : rawEntries) {
            BookOffer offer = BookOffer.parse(raw);
            if (offer == null || !offer.isMostlyValid()) continue;
            if (offers.stream().anyMatch(existing -> existing.sameEnchantAndLevel(offer))) continue;
            offers.add(offer);
        }

        offers.sort(Comparator.naturalOrder());
        return offers;
    }

    public static int indexOf(List<BookOffer> offers, BookOffer match) {
        for (int i = 0; i < offers.size(); i++) {
            if (offers.get(i).sameEnchantAndLevel(match)) return i;
        }

        return -1;
    }

    public static List<String> applyUpdateMode(List<BookOffer> offers, int index, BookOffer foundOffer, AutoLibrarianModule.UpdateBooksMode mode) {
        if (index < 0 || index >= offers.size()) {
            return offers.stream().map(BookOffer::toSettingString).toList();
        }

        switch (mode) {
            case Off -> {
                return offers.stream().map(BookOffer::toSettingString).toList();
            }
            case Remove -> offers.remove(index);
            case Price -> {
                if (foundOffer.price() <= 1) offers.remove(index);
                else offers.set(index, new BookOffer(foundOffer.id(), foundOffer.level(), foundOffer.price() - 1));
            }
        }

        return offers.stream().map(BookOffer::toSettingString).toList();
    }
}
