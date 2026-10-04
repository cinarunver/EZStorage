package com.zerofall.ezstorage.client;

/** How the storage GUI orders items. */
public enum SortMode {

    AMOUNT("gui.ezstorage.sort.mode.amount"),
    NAME("gui.ezstorage.sort.mode.name"),
    MOD("gui.ezstorage.sort.mode.mod");

    public final String langKey;

    SortMode(String langKey) {
        this.langKey = langKey;
    }

    public SortMode next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
