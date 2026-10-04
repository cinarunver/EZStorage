package com.zerofall.ezstorage.config;

import com.zerofall.ezstorage.client.SearchMode;
import com.zerofall.ezstorage.client.SortMode;
import com.zerofall.ezstorage.client.SortOrder;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class EZConfig {

    private EZConfig() {}

    public static final class Server {

        public final ModConfigSpec.IntValue basicCapacity;
        public final ModConfigSpec.IntValue condensedCapacity;
        public final ModConfigSpec.IntValue hyperCapacity;
        public final ModConfigSpec.IntValue maxItemTypes;

        Server(ModConfigSpec.Builder builder) {
            basicCapacity = builder.comment("Count of items that the basic storage box can hold.")
                .defineInRange("basicCapacity", 400, 1, Integer.MAX_VALUE);
            condensedCapacity = builder.comment("Count of items that the condensed storage box can hold.")
                .defineInRange("condensedCapacity", 4000, 1, Integer.MAX_VALUE);
            hyperCapacity = builder.comment("Count of items that the hyper storage box can hold.")
                .defineInRange("hyperCapacity", 400000, 1, Integer.MAX_VALUE);
            maxItemTypes = builder
                .comment(
                    "The maximum amount of different items that can be stored within one storage core. 0 disables the limit.")
                .defineInRange("maxItemTypes", 0, 0, Integer.MAX_VALUE);
        }
    }

    public static final class Client {

        public final ModConfigSpec.BooleanValue focusSearch;
        public final ModConfigSpec.EnumValue<SortMode> sortMode;
        public final ModConfigSpec.EnumValue<SortOrder> sortOrder;
        public final ModConfigSpec.EnumValue<SearchMode> searchMode;
        public final ModConfigSpec.BooleanValue saveSearch;
        public final ModConfigSpec.ConfigValue<String> searchText;

        Client(ModConfigSpec.Builder builder) {
            focusSearch = builder.comment("Focus the search field when opening a storage GUI.")
                .define("focusSearch", true);
            sortMode = builder.comment("Last used sort mode for the storage GUI.")
                .defineEnum("sortMode", SortMode.AMOUNT);
            sortOrder = builder.comment("Last used sort order for the storage GUI.")
                .defineEnum("sortOrder", SortOrder.DESCENDING);
            searchMode = builder.comment("Last used search mode for the storage GUI.")
                .defineEnum("searchMode", SearchMode.AUTO);
            saveSearch = builder.comment("Whether the search text is preserved when reopening the storage GUI.")
                .define("saveSearch", false);
            searchText = builder.comment("The saved search text, only used when saveSearch is true.")
                .define("searchText", "");
        }
    }

    public static final Server SERVER;
    public static final ModConfigSpec SERVER_SPEC;
    public static final Client CLIENT;
    public static final ModConfigSpec CLIENT_SPEC;

    static {
        ModConfigSpec.Builder serverBuilder = new ModConfigSpec.Builder();
        SERVER = new Server(serverBuilder);
        SERVER_SPEC = serverBuilder.build();

        ModConfigSpec.Builder clientBuilder = new ModConfigSpec.Builder();
        CLIENT = new Client(clientBuilder);
        CLIENT_SPEC = clientBuilder.build();
    }

    /** Server values are only readable once the world is loaded; fall back to defaults before that. */
    public static int maxItemTypes() {
        return SERVER_SPEC.isLoaded() ? SERVER.maxItemTypes.getAsInt() : 0;
    }
}
