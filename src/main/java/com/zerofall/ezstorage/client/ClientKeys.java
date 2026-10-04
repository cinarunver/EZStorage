package com.zerofall.ezstorage.client;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.zerofall.ezstorage.EZStorage;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

public final class ClientKeys {

    private ClientKeys() {}

    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(
        Identifier.fromNamespaceAndPath(EZStorage.MOD_ID, "main"));

    /** Opens the storage of a portable panel carried in the inventory. Unbound by default. */
    public static final KeyMapping OPEN_PANEL = new KeyMapping(
        "key.ezstorage.open_panel",
        KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_UNKNOWN,
        CATEGORY);

    /** Held while clicking in the storage GUI to move everything at once. */
    public static final KeyMapping BULK_ACTION = new KeyMapping(
        "key.ezstorage.bulk_action",
        KeyConflictContext.GUI,
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_SPACE,
        CATEGORY);

    /** Pulls the looked-at block out of the storage linked to a carried panel. Unbound by default. */
    public static final KeyMapping PICK_BLOCK = new KeyMapping(
        "key.ezstorage.pick_block",
        KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_UNKNOWN,
        CATEGORY);
}
