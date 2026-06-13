package com.rtsbuilding.rtsbuilding.client.record;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

/**
 * Client display row for one linked storage block.
 *
 * <p>The row is decoded from the server storage-page payload and is used by
 * the detail window only. It deliberately carries enough data to
 * render icon/name/position/mode, but it does not decide whether a block is
 * still valid storage or whether unlink is allowed; those rules stay on the
 * server.
 */
public record LinkedStorageEntry(BlockPos pos, String label, byte mode, int priority, ItemStack preview,
                                  boolean worldAvailable) {
}
