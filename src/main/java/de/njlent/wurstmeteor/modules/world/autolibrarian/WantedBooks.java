package de.njlent.wurstmeteor.modules.world.autolibrarian;

import de.njlent.wurstmeteor.modules.world.AutoLibrarianModule;

import java.util.ArrayList;
import java.util.List;

public final class WantedBooks {
    private WantedBooks() {
    }

    public static List<BookOffer> sanitize(List<BookOffer> rawEntries) {
        ArrayList<BookOffer> offers = new ArrayList<>();
        if (rawEntries == null) return offers;

        for (BookOffer offer : rawEntries) {
            if (offer == null || !offer.isMostlyValid()) continue;
            if (offers.stream().anyMatch(existing -> existing.sameEnchantAndLevel(offer))) continue;
            offers.add(offer);
        }

        return BookOfferListSetting.sanitize(offers);
    }

    public static int indexOf(List<BookOffer> offers, BookOffer match) {
        for (int i = 0; i < offers.size(); i++) {
            if (offers.get(i).sameEnchantAndLevel(match)) return i;
        }

        return -1;
    }

    public static List<BookOffer> applyUpdateMode(List<BookOffer> offers, int index, BookOffer foundOffer, AutoLibrarianModule.UpdateBooksMode mode) {
        if (index < 0 || index >= offers.size()) {
            return BookOfferListSetting.sanitize(offers);
        }

        switch (mode) {
            case Off -> {
                return BookOfferListSetting.sanitize(offers);
            }
            case Remove -> offers.remove(index);
            case Price -> {
                if (foundOffer.price() <= 1) offers.remove(index);
                else offers.set(index, new BookOffer(foundOffer.id(), foundOffer.level(), foundOffer.price() - 1));
            }
        }

        return BookOfferListSetting.sanitize(offers);
    }
}
