package com.rtsbuilding.rtsbuilding.client.record;

import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import java.util.Objects;

/**
 * Client display row for one linked storage block.
 *
 * <p>The row is decoded from the server storage-page payload and is used by
 * the detail window only. It deliberately carries enough data to
 * render icon/name/position/mode, but it does not decide whether a block is
 * still valid storage or whether unlink is allowed; those rules stay on the
 * server.
 */
public final class LinkedStorageEntry {
    private final int dimension;
    private final BlockPos pos;
    private final String label;
    private final byte mode;
    private final int priority;
    private final ItemStack preview;
    private final boolean worldAvailable;

    public LinkedStorageEntry(int dimension, BlockPos pos, String label, byte mode, int priority,
                              ItemStack preview, boolean worldAvailable) {
        this.dimension = dimension;
        this.pos = pos;
        this.label = label;
        this.mode = mode;
        this.priority = priority;
        this.preview = ClientRecordSupport.copyStack(preview);
        this.worldAvailable = worldAvailable;
    }

    public int dimension() { return dimension; }
    public BlockPos pos() { return pos; }
    public String label() { return label; }
    public byte mode() { return mode; }
    public int priority() { return priority; }
    public ItemStack preview() { return ClientRecordSupport.copyStack(preview); }
    public boolean worldAvailable() { return worldAvailable; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof LinkedStorageEntry)) return false;
        LinkedStorageEntry value = (LinkedStorageEntry) other;
        return dimension == value.dimension && mode == value.mode && priority == value.priority
                && worldAvailable == value.worldAvailable && Objects.equals(pos, value.pos)
                && Objects.equals(label, value.label)
                && ClientRecordSupport.stackEquals(preview, value.preview);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(dimension, pos, label, mode, priority, worldAvailable)
                + ClientRecordSupport.stackHash(preview);
    }

    @Override
    public String toString() {
        return "LinkedStorageEntry[dimension=" + dimension + ", pos=" + pos + ", label=" + label + ", mode=" + mode
                + ", priority=" + priority + ", preview=" + preview
                + ", worldAvailable=" + worldAvailable + ']';
    }
}
