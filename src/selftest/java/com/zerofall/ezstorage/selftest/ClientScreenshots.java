package com.zerofall.ezstorage.selftest;

import com.zerofall.ezstorage.EZStorage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** Saves a screenshot into the run directory's screenshots folder (CI uploads them for review). */
final class ClientScreenshots {

    private ClientScreenshots() {}

    static void take(Minecraft mc, String label) {
        try {
            Screenshot.grab(mc, false);
            EZStorage.LOG.info("[EZSTORAGE-CLIENTTEST] screenshot requested: " + label);
        } catch (Throwable t) {
            EZStorage.LOG.warn("[EZSTORAGE-CLIENTTEST] screenshot failed: " + label, t);
        }
    }
}
