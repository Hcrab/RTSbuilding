package com.rtsbuilding.rtsbuilding.network;

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

