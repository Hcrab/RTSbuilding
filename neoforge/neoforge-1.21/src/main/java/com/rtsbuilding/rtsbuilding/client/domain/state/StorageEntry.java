package com.rtsbuilding.rtsbuilding.client.domain.state;

import net.minecraft.world.item.ItemStack;

public record StorageEntry(
        ItemStack stack,
        String itemId,
        long count,
        String namespace,
        String path,
        byte linkedMode
) {
    
    public static final byte MODE_BIDIRECTIONAL = 0;
    
    public static final byte MODE_EXTRACT_ONLY = 1;

    
    public boolean isBidirectional() {
        return linkedMode == MODE_BIDIRECTIONAL;
    }

    
    public boolean isExtractOnly() {
        return linkedMode == MODE_EXTRACT_ONLY;
    }
}
