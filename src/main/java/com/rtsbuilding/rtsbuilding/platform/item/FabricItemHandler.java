package com.rtsbuilding.rtsbuilding.platform.item;

import com.rtsbuilding.rtsbuilding.compat.AnySlotInsertItemHandler;
import com.rtsbuilding.rtsbuilding.compat.ReportedCountItemHandler;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.world.item.ItemStack;

/**
 * 把 Fabric Transfer API 的事务式物品储存适配为 RTSBuilding 的槽位式内部接口。
 *
 * <p>视图列表只在 {@link #getSlots()} 时刷新，随后同一轮扫描复用快照，避免大型储存网络
 * 因每个槽位都重新遍历而退化成 O(n²)。插入仍直接走底层 {@link Storage}，因此可以保留
 * Fabric 的整体路由与事务语义；本类不缓存物品数量，也不拥有储存生命周期。
 */
public final class FabricItemHandler
        implements RtsItemHandler, AnySlotInsertItemHandler, ReportedCountItemHandler {
    private final Storage<ItemVariant> storage;
    private List<StorageView<ItemVariant>> views = List.of();

    public FabricItemHandler(Storage<ItemVariant> storage) {
        this.storage = storage;
    }

    @Override
    public int getSlots() {
        List<StorageView<ItemVariant>> refreshed = new ArrayList<>();
        for (StorageView<ItemVariant> view : this.storage) {
            refreshed.add(view);
        }
        this.views = List.copyOf(refreshed);
        return this.views.size();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        StorageView<ItemVariant> view = view(slot);
        if (view == null || view.isResourceBlank() || view.getAmount() <= 0) {
            return ItemStack.EMPTY;
        }
        int displayed = (int) Math.min(view.getAmount(), view.getResource().getItem().getDefaultMaxStackSize());
        return view.getResource().toStack(Math.max(displayed, 1));
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        return insertItemAnywhere(stack, simulate);
    }

    @Override
    public ItemStack insertItemAnywhere(ItemStack stack, boolean simulate) {
        if (stack == null || stack.isEmpty() || !this.storage.supportsInsertion()) {
            return stack == null ? ItemStack.EMPTY : stack.copy();
        }
        long inserted;
        try (Transaction transaction = Transaction.openOuter()) {
            inserted = this.storage.insert(ItemVariant.of(stack), stack.getCount(), transaction);
            if (!simulate) {
                transaction.commit();
            }
        }
        if (inserted <= 0) {
            return stack.copy();
        }
        ItemStack remainder = stack.copy();
        remainder.shrink((int) Math.min(inserted, stack.getCount()));
        return remainder;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        StorageView<ItemVariant> view = view(slot);
        if (view == null || view.isResourceBlank() || amount <= 0 || !this.storage.supportsExtraction()) {
            return ItemStack.EMPTY;
        }
        ItemVariant variant = view.getResource();
        long extracted;
        try (Transaction transaction = Transaction.openOuter()) {
            extracted = view.extract(variant, amount, transaction);
            if (!simulate) {
                transaction.commit();
            }
        }
        return extracted <= 0 ? ItemStack.EMPTY : variant.toStack((int) Math.min(extracted, Integer.MAX_VALUE));
    }

    @Override
    public int getSlotLimit(int slot) {
        StorageView<ItemVariant> view = view(slot);
        return view == null ? 0 : (int) Math.min(view.getCapacity(), Integer.MAX_VALUE);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return stack != null && !stack.isEmpty() && this.storage.supportsInsertion();
    }

    @Override
    public long getReportedCount(int slot) {
        StorageView<ItemVariant> view = view(slot);
        return view == null || view.isResourceBlank() ? 0L : Math.max(0L, view.getAmount());
    }

    private StorageView<ItemVariant> view(int slot) {
        return slot >= 0 && slot < this.views.size() ? this.views.get(slot) : null;
    }
}
