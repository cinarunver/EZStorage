package com.zerofall.ezstorage.client;

/** Whether the storage search box is kept in sync with the JEI search field. */
public enum SearchMode {

    AUTO("gui.ezstorage.search.mode.auto"),
    JEI_SYNC("gui.ezstorage.search.mode.jei_sync"),
    STANDARD("gui.ezstorage.search.mode.standard");

    public final String langKey;

    SearchMode(String langKey) {
        this.langKey = langKey;
    }

    public SearchMode next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
