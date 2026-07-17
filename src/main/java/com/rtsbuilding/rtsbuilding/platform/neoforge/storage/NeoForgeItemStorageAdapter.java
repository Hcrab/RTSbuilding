package com.rtsbuilding.rtsbuilding.platform.neoforge.storage;

import com.rtsbuilding.rtsbuilding.compat.AnySlotInsertItemHandler;
import com.rtsbuilding.rtsbuilding.compat.RefreshableSnapshotHandler;
import com.rtsbuilding.rtsbuilding.compat.ReportedCountItemHandler;
import com.rtsbuilding.rtsbuilding.compat.ae2.RtsAe2Compat;
import com.rtsbuilding.rtsbuilding.compat.bd.RtsBdCompat;
import com.rtsbuilding.rtsbuilding.server.storage.port.RtsItemStorage;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.Objects;

/**
 * NeoForge 26.1 物品能力到 RTS 储存语义端口的唯一基础适配器。
 *
 * <p>本类可以认识 Loader 和第三方 handler 扩展，但这些类型不会继续向放置、
 * 挖掘、合成、页面或缓存业务扩散。旧版本或 Fabric 只需实现同一
 * {@link RtsItemStorage}，无需复制业务流程。</p>
 */
public final class NeoForgeItemStorageAdapter implements RtsItemStorage {
    private final IItemHandler delegate;

    private NeoForgeItemStorageAdapter(IItemHandler delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    public static RtsItemStorage wrap(IItemHandler handler) {
        return handler == null ? null : new NeoForgeItemStorageAdapter(handler);
    }

    /**
     * 仅供 NeoForge 边界释放第三方资源或完成临时兼容，不应由业务层调用。
     */
    public IItemHandler nativeHandler() {
        return this.delegate;
    }

    @Override
    public int slotCount() {
        return this.delegate.getSlots();
    }

    @Override
    public ItemStack stackInSlot(int slot) {
        return this.delegate.getStackInSlot(slot);
    }

    @Override
    public ItemStack insert(int slot, ItemStack stack, boolean simulate) {
        return this.delegate.insertItem(slot, stack, simulate);
    }

    @Override
    public ItemStack extract(int slot, int amount, boolean simulate) {
        return this.delegate.extractItem(slot, amount, simulate);
    }

    @Override
    public int slotLimit(int slot) {
        return this.delegate.getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return this.delegate.isItemValid(slot, stack);
    }

    @Override
    public Object identity() {
        return this.delegate;
    }

    @Override
    public long reportedCount(int slot) {
        if (this.delegate instanceof ReportedCountItemHandler reported) {
            return Math.max(0L, reported.getReportedCount(slot));
        }
        return RtsItemStorage.super.reportedCount(slot);
    }

    @Override
    public boolean hasAggregatedCounts() {
        return this.delegate instanceof ReportedCountItemHandler;
    }

    @Override
    public void refreshSnapshot() {
        if (this.delegate instanceof RefreshableSnapshotHandler refreshable) {
            refreshable.ensureFreshSnapshot();
        }
    }

    @Override
    public void release() {
        RtsAe2Compat.releaseNetworkHandler(this.delegate);
        RtsBdCompat.releaseNetworkHandler(this.delegate);
    }

    @Override
    public boolean supportsInsertAnywhere() {
        return this.delegate instanceof AnySlotInsertItemHandler;
    }

    @Override
    public ItemStack insertAnywhere(ItemStack stack, boolean simulate) {
        if (this.delegate instanceof AnySlotInsertItemHandler anySlot) {
            return anySlot.insertItemAnywhere(stack, simulate);
        }
        return RtsItemStorage.super.insertAnywhere(stack, simulate);
    }

    @Override
    public boolean supportsExtractAnywhere() {
        return this.delegate instanceof AnySlotInsertItemHandler
                || this.delegate instanceof RtsBdCompat.DirectExtractHandler;
    }

    @Override
    public ItemStack extractAnywhere(Item targetItem, int amount, boolean simulate) {
        if (this.delegate instanceof RtsBdCompat.DirectExtractHandler direct) {
            return direct.tryExtractItem(targetItem, amount, simulate);
        }
        if (this.delegate instanceof AnySlotInsertItemHandler anySlot) {
            return anySlot.extractItemAnywhere(targetItem, amount, simulate);
        }
        return RtsItemStorage.super.extractAnywhere(targetItem, amount, simulate);
    }
}
