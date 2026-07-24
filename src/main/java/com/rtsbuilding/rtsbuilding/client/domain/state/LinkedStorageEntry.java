package com.rtsbuilding.rtsbuilding.client.domain.state;

import net.minecraft.core.BlockPos;


public record LinkedStorageEntry(
        BlockPos pos,
        byte mode,
        boolean worldAvailable) {

    
    public static final byte MODE_BIDIRECTIONAL = 0;
    
    public static final byte MODE_EXTRACT_ONLY = 1;

    
    public boolean isBidirectional() {
        return mode == MODE_BIDIRECTIONAL;
    }

    
    public boolean isExtractOnly() {
        return mode == MODE_EXTRACT_ONLY;
    }
}
