package com.rtsbuilding.rtsbuilding.network.storage;

public enum RtsStorageSort {
    QUANTITY,
    MOD,
    NAME;

    public static RtsStorageSort byId(int id) {
        if (id < 0 || id >= values().length) {
            return QUANTITY;
        }
        return values()[id];
    }
}

