package com.zerofall.ezstorage.client;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Decouples the storage screen from JEI: the JEI plugin installs accessors for its search text once its runtime is
 * available. Without JEI the bridge stays inactive.
 */
public final class RecipeViewerBridge {

    private static Supplier<String> textGetter;
    private static Consumer<String> textSetter;

    private RecipeViewerBridge() {}

    public static void install(Supplier<String> getter, Consumer<String> setter) {
        textGetter = getter;
        textSetter = setter;
    }

    public static void uninstall() {
        textGetter = null;
        textSetter = null;
    }

    public static boolean isAvailable() {
        return textGetter != null && textSetter != null;
    }

    public static String getSearchText() {
        return isAvailable() ? textGetter.get() : null;
    }

    public static void setSearchText(String text) {
        if (isAvailable()) {
            textSetter.accept(text);
        }
    }
}
