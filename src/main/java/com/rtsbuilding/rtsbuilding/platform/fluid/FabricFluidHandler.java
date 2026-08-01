package com.rtsbuilding.rtsbuilding.platform.fluid;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;

/**
 * Fabric Transfer API 流体储存到 RTS 槽位接口的事务适配器。
 *
 * <p>与物品适配器相同，槽位视图仅在 getTanks 时刷新，避免大型流体网络反复全量遍历。
 */
public final class FabricFluidHandler implements RtsFluidHandler {
    private final Storage<FluidVariant> storage;
    private List<StorageView<FluidVariant>> views = List.of();

    public FabricFluidHandler(Storage<FluidVariant> storage) {
        this.storage = storage;
    }

    public boolean wraps(Storage<FluidVariant> storage) {
        return this.storage == storage;
    }

    @Override
    public int getTanks() {
        List<StorageView<FluidVariant>> refreshed = new ArrayList<>();
        for (StorageView<FluidVariant> view : this.storage) {
            refreshed.add(view);
        }
        this.views = List.copyOf(refreshed);
        return this.views.size();
    }

    @Override
    public RtsFluidStack getFluidInTank(int tank) {
        StorageView<FluidVariant> view = view(tank);
        return view == null || view.isResourceBlank()
                ? RtsFluidStack.EMPTY
                : new RtsFluidStack(view.getResource(), view.getAmount());
    }

    @Override
    public int getTankCapacity(int tank) {
        StorageView<FluidVariant> view = view(tank);
        return view == null ? 0 : (int) Math.min(view.getCapacity(), Integer.MAX_VALUE);
    }

    @Override
    public boolean isFluidValid(int tank, RtsFluidStack stack) {
        return stack != null && !stack.isEmpty() && this.storage.supportsInsertion();
    }

    @Override
    public int fill(RtsFluidStack resource, FluidAction action) {
        if (resource == null || resource.isEmpty() || !this.storage.supportsInsertion()) {
            return 0;
        }
        long inserted;
        try (Transaction transaction = Transaction.openOuter()) {
            inserted = this.storage.insert(resource.variant(), resource.getAmount(), transaction);
            if (action == FluidAction.EXECUTE) {
                transaction.commit();
            }
        }
        return (int) Math.min(inserted, Integer.MAX_VALUE);
    }

    @Override
    public RtsFluidStack drain(RtsFluidStack resource, FluidAction action) {
        if (resource == null || resource.isEmpty() || !this.storage.supportsExtraction()) {
            return RtsFluidStack.EMPTY;
        }
        long extracted;
        try (Transaction transaction = Transaction.openOuter()) {
            extracted = this.storage.extract(resource.variant(), resource.getAmount(), transaction);
            if (action == FluidAction.EXECUTE) {
                transaction.commit();
            }
        }
        return extracted <= 0 ? RtsFluidStack.EMPTY : new RtsFluidStack(resource.variant(), extracted);
    }

    @Override
    public RtsFluidStack drain(int maxDrain, FluidAction action) {
        if (maxDrain <= 0) {
            return RtsFluidStack.EMPTY;
        }
        for (StorageView<FluidVariant> view : this.views) {
            if (!view.isResourceBlank() && view.getAmount() > 0) {
                return drain(new RtsFluidStack(view.getResource(), maxDrain), action);
            }
        }
        return RtsFluidStack.EMPTY;
    }

    private StorageView<FluidVariant> view(int tank) {
        return tank >= 0 && tank < this.views.size() ? this.views.get(tank) : null;
    }
}
