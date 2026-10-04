package com.zerofall.ezstorage.client;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

/** Formats large counts for the 16px item cells (e.g. 1.2k, 35M), as in the 1.7.10 version. */
public final class ReadableNumberConverter {

    private static final int DIVISION_BASE = 1000;
    private static final char[] POSTFIXES = "kMGTPE".toCharArray();
    private static final DecimalFormat FORMAT;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setDecimalSeparator('.');
        symbols.setGroupingSeparator(',');
        FORMAT = new DecimalFormat(".#;0.#", symbols);
        FORMAT.setRoundingMode(RoundingMode.DOWN);
    }

    private ReadableNumberConverter() {}

    /** At most 3 characters wide. */
    public static String slim(long number) {
        return restrictToWidth(number, 3);
    }

    /** At most 4 characters wide. */
    public static String wide(long number) {
        return restrictToWidth(number, 4);
    }

    private static String restrictToWidth(long number, int width) {
        String plain = Long.toString(number);
        if (plain.length() <= width) {
            return plain;
        }
        long base = number;
        double last = base * 1000.0;
        int exponent = -1;
        String postfix = "";
        int size = plain.length();
        while (size > width) {
            last = base;
            base /= DIVISION_BASE;
            exponent++;
            size = Long.toString(base)
                .length() + 1;
            postfix = String.valueOf(POSTFIXES[exponent]);
        }
        String withPrecision;
        synchronized (FORMAT) {
            withPrecision = FORMAT.format(last / DIVISION_BASE) + postfix;
        }
        String withoutPrecision = base + postfix;
        return withPrecision.length() <= width ? withPrecision : withoutPrecision;
    }
}
