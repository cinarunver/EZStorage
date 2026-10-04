package com.zerofall.ezstorage.client;

/** Direction of the storage GUI ordering. */
public enum SortOrder {

    DESCENDING("gui.ezstorage.sort.order.descending"),
    ASCENDING("gui.ezstorage.sort.order.ascending");

    public final String langKey;

    SortOrder(String langKey) {
        this.langKey = langKey;
    }

    public SortOrder next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
