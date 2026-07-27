package com.rtsbuilding.rtsbuilding.network;

public final class NetworkConstants {
    private NetworkConstants() {}

    // Interact constants (from C2SRtsInteractPayload)
    public static final byte INTERACT_TOOL_SLOT = 0;
    public static final byte INTERACT_PIN_ITEM = 1;
    public static final byte INTERACT_TOOL_SLOT_AIR = 2;
    public static final byte INTERACT_EMPTY_HAND = 3;
    public static final int NO_ENTITY = -1;

    // Link storage constants (from C2SRtsLinkStoragePayload)
    public static final byte MODE_BIDIRECTIONAL = 0;
    public static final byte MODE_EXTRACT_ONLY = 1;

    // Store fluid constants (from C2SRtsStoreFluidPayload)
    public static final byte FLUID_STORAGE_ITEM = 0;
    public static final byte FLUID_TOOL_SLOT = 1;
    public static final byte FLUID_PIN_ITEM = 2;

    // Area destroy constants
    public static final int MAX_POSITIONS = 32768;

    // Blueprint constants
    public static final int MAX_FILE_NAME_CHARS = 160;
    public static final int MAX_FILE_BYTES = 2 * 1024 * 1024;
}
