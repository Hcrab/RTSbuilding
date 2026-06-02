package com.rtsbuilding.rtsbuilding.server;

import java.util.ArrayList;
import java.util.List;

import com.rtsbuilding.rtsbuilding.compat.ae2.RtsAe2Compat;
import com.rtsbuilding.rtsbuilding.network.C2SRtsLinkStoragePayload;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

/**
 * Resolves the linked-storage edge of an {@link RtsStorageSession}.
 *
 * <p>This class is responsible for turning the session's linked refs into
 * item/fluid handlers, allow-store permissions, display names, and storage
 * summaries. It deliberately does not build pages, mutate inventories, craft,
 * transfer fluids, perform remote mining, read or write NBT, or send packets.
 * Those gameplay and transport flows remain owned by {@link RtsStorageManager}.
 *
 * <p>The resolver must preserve the existing AE2 network handler behavior,
 * normal block-container capability probing, and Forge capability lookup order.
 * It is also the dependency boundary future Transfer, Fluid, and Craft
 * extractions should call instead of reaching back into the full storage
 * manager.
 */
final class RtsLinkedStorageResolver {
    private static final byte LINK_MODE_BIDIRECTIONAL = C2SRtsLinkStoragePayload.MODE_BIDIRECTIONAL;
    private static final byte LINK_MODE_EXTRACT_ONLY = C2SRtsLinkStoragePayload.MODE_EXTRACT_ONLY;

    private RtsLinkedStorageResolver() {
    }

    /**
     * Forge item capability probing belongs here because linked refs are the
     * storage path that needs direct and sided block-entity handlers.
     */
    static IItemHandler findHandler(ServerLevel level, BlockPos pos, Direction side) {
        if (level == null || pos == null || !level.hasChunkAt(pos)) {
            return null;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return null;
        }
        return blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, side).resolve().orElse(null);
    }

    /**
     * Block capability probing belongs here because linked refs are the only
     * storage path that should scan direct and sided item handlers.
     */
    static IItemHandler findHandler(ServerPlayer player, BlockPos pos) {
        if (!player.serverLevel().hasChunkAt(pos)) {
            return null;
        }
        IItemHandler direct = findHandler(player.serverLevel(), pos, null);
        if (direct != null) {
            return direct;
        }
        for (Direction direction : Direction.values()) {
            IItemHandler sided = findHandler(player.serverLevel(), pos, direction);
            if (sided != null) {
                return sided;
            }
        }
        return null;
    }

    /**
     * AE2 virtual network lookup is part of resolving what a linked ref exposes
     * as an item handler.
     */
    static IItemHandler findLinkedItemHandler(ServerPlayer player, BlockPos pos) {
        IItemHandler ae2Network = RtsAe2Compat.createNetworkItemHandler(player, pos);
        if (ae2Network != null) {
            return ae2Network;
        }
        return findHandler(player, pos);
    }

    /**
     * Forge fluid capability probing stays with linked resolution so item and
     * fluid refs use the same chunk, side, and permission boundary.
     */
    static IFluidHandler findFluidHandler(ServerLevel level, BlockPos pos, Direction side) {
        if (level == null || pos == null || !level.hasChunkAt(pos)) {
            return null;
        }
        return FluidUtil.getFluidHandler(level, pos, side).resolve().orElse(null);
    }

    /**
     * Fluid capability probing stays with linked resolution so item and fluid
     * refs use the same chunk, side, and permission boundary.
     */
    static IFluidHandler findFluidHandler(ServerPlayer player, BlockPos pos) {
        if (!player.serverLevel().hasChunkAt(pos)) {
            return null;
        }
        IFluidHandler direct = findFluidHandler(player.serverLevel(), pos, null);
        if (direct != null) {
            return direct;
        }
        for (Direction direction : Direction.values()) {
            IFluidHandler sided = findFluidHandler(player.serverLevel(), pos, direction);
            if (sided != null) {
                return sided;
            }
        }
        return null;
    }

    /**
     * Linked labels are cached presentation for refs, so resolver owns the
     * fallback block-name lookup used by summaries and UI payloads.
     */
    static String resolveDisplayName(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getBlock().getName().getString();
    }

    /**
     * Resolves every currently accessible item endpoint into handlers that
     * already enforce extract-only store rules.
     */
    static List<LinkedHandler> resolveLinkedHandlers(ServerPlayer player, RtsStorageSession session) {
        sanitizeSessionDimension(player, session);
        if (session.linkedStorages.isEmpty()) {
            return List.of();
        }

        List<LinkedHandler> out = new ArrayList<>();
        ResourceKey<Level> currentDimension = player.serverLevel().dimension();
        for (LinkedStorageRef ref : session.linkedStorages) {
            if (ref == null || ref.pos() == null || !currentDimension.equals(ref.dimension())) {
                continue;
            }
            BlockPos pos = ref.pos();
            if (!RtsProgressionManager.canAccessHomeRadius(player, pos)) {
                continue;
            }
            if (!player.serverLevel().hasChunkAt(pos)) {
                continue;
            }
            IItemHandler handler = findLinkedItemHandler(player, pos);
            if (handler == null) {
                continue;
            }
            String name = session.linkedNames.computeIfAbsent(ref, ignored -> resolveDisplayName(player.serverLevel(), pos));
            boolean allowStore = !isExtractOnlyLink(session, ref);
            out.add(new LinkedHandler(ref, name, new LinkedItemHandlerView(handler, allowStore), allowStore));
        }
        return out;
    }

    /**
     * Resolves fluid endpoints alongside item endpoints so extract-only links
     * cannot accept stored fluid while still allowing extraction.
     */
    static List<LinkedFluidHandler> resolveLinkedFluidHandlers(ServerPlayer player, RtsStorageSession session) {
        sanitizeSessionDimension(player, session);
        if (session.linkedStorages.isEmpty()) {
            return List.of();
        }

        List<LinkedFluidHandler> out = new ArrayList<>();
        ResourceKey<Level> currentDimension = player.serverLevel().dimension();
        for (LinkedStorageRef ref : session.linkedStorages) {
            if (ref == null || ref.pos() == null || !currentDimension.equals(ref.dimension())) {
                continue;
            }
            BlockPos pos = ref.pos();
            if (!RtsProgressionManager.canAccessHomeRadius(player, pos)) {
                continue;
            }
            if (!player.serverLevel().hasChunkAt(pos)) {
                continue;
            }
            IFluidHandler handler = findFluidHandler(player, pos);
            if (handler == null) {
                continue;
            }
            String name = session.linkedNames.computeIfAbsent(ref, ignored -> resolveDisplayName(player.serverLevel(), pos));
            boolean allowStore = !isExtractOnlyLink(session, ref);
            out.add(new LinkedFluidHandler(ref, name, new LinkedFluidHandlerView(handler, allowStore), allowStore));
        }
        return out;
    }

    /**
     * Linked refs are world targets, so resolver owns the shared camera, chunk,
     * interaction, and home-radius gate used before resolving them.
     */
    static boolean canAccessWorldTarget(ServerPlayer player, BlockPos pos) {
        if (!RtsCameraManager.isActive(player) || pos == null) {
            return false;
        }

        ServerLevel level = player.serverLevel();
        if (!level.hasChunkAt(pos)) {
            return false;
        }
        if (!level.mayInteract(player, pos)) {
            return false;
        }
        if (!RtsCameraManager.isWithinActionRange(player, pos)) {
            return false;
        }
        return RtsProgressionManager.canAccessHomeRadius(player, pos);
    }

    /**
     * Storage availability is derived from linked refs on Forge because this
     * branch has no NeoForge BD network fallback.
     */
    static boolean hasAnyStorage(ServerPlayer player, RtsStorageSession session) {
        return session != null && !session.linkedStorages.isEmpty();
    }

    /**
     * The UI summary describes the currently linked-storage source, so it stays
     * paired with availability checks.
     */
    static String buildAnyStorageSummary(ServerPlayer player, RtsStorageSession session) {
        if (!hasAnyStorage(player, session)) {
            return "No Storage";
        }
        return buildLinkedSummary(session);
    }

    /**
     * Ref cleanup belongs to resolver so every lookup starts from the same
     * valid identity set without touching unrelated session state.
     */
    static void sanitizeSessionDimension(ServerPlayer player, RtsStorageSession session) {
        if (session == null || session.linkedStorages.isEmpty()) {
            return;
        }
        session.linkedStorages.removeIf(ref -> ref == null || ref.dimension() == null || ref.pos() == null);
        session.linkedNames.keySet().removeIf(ref -> ref == null || !session.linkedStorages.contains(ref));
        session.linkedModes.keySet().removeIf(ref -> ref == null || !session.linkedStorages.contains(ref));
    }

    /**
     * Summary text is presentation derived from linked refs and extract-only
     * modes, not page-building state.
     */
    static String buildLinkedSummary(RtsStorageSession session) {
        int count = session.linkedStorages.size();
        if (count <= 0) {
            return "No Storage";
        }
        if (count == 1) {
            LinkedStorageRef ref = session.linkedStorages.get(0);
            String name = session.linkedNames.getOrDefault(ref, "Linked Storage");
            return isExtractOnlyLink(session, ref) ? name + " [Extract]" : name;
        }
        int extractOnly = 0;
        for (LinkedStorageRef ref : session.linkedStorages) {
            if (isExtractOnlyLink(session, ref)) {
                extractOnly++;
            }
        }
        if (extractOnly <= 0) {
            return count + " linked storages";
        }
        return count + " linked storages (" + extractOnly + " extract-only)";
    }

    /**
     * Link mode normalization is reused by persistence and resolver permission
     * checks so saved data and runtime handlers cannot disagree.
     */
    static byte sanitizeLinkMode(byte linkMode) {
        return linkMode == LINK_MODE_EXTRACT_ONLY ? LINK_MODE_EXTRACT_ONLY : LINK_MODE_BIDIRECTIONAL;
    }

    /**
     * Insert-anywhere is a linked-handler capability detail. Transfer code asks
     * this boundary first so it does not need to know whether the endpoint is an
     * extract-only view, an AE2 virtual handler, or a normal slotted inventory.
     */
    static ItemStack insertItemAnywhereIfSupported(IItemHandler handler, ItemStack stack, boolean simulate) {
        if (handler == null || stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (handler instanceof LinkedItemHandlerView linkedView && linkedView.supportsAnySlotInsert()) {
            return linkedView.insertItemAnywhere(stack, simulate);
        }
        if (handler instanceof RtsAe2Compat.AnySlotInsertItemHandler anySlot) {
            return anySlot.insertItemAnywhere(stack, simulate);
        }
        return null;
    }

    static ItemStack insertItemAnywhere(IItemHandler handler, ItemStack stack, boolean simulate) {
        ItemStack supported = insertItemAnywhereIfSupported(handler, stack, simulate);
        if (supported != null) {
            return supported;
        }
        ItemStack remain = stack == null ? ItemStack.EMPTY : stack.copy();
        for (int slot = 0; handler != null && slot < handler.getSlots() && !remain.isEmpty(); slot++) {
            remain = handler.insertItem(slot, remain, simulate);
        }
        return remain;
    }


    /**
     * Extract-only is a linked-ref permission that directly controls the
     * resolver's handler views.
     */
    static boolean isExtractOnlyLink(RtsStorageSession session, LinkedStorageRef ref) {
        return session != null
                && ref != null
                && sanitizeLinkMode(session.linkedModes.getOrDefault(ref, LINK_MODE_BIDIRECTIONAL)) == LINK_MODE_EXTRACT_ONLY;
    }
}

record LinkedHandler(LinkedStorageRef ref, String name, IItemHandler handler, boolean allowStore) {
    BlockPos pos() {
        return this.ref.pos();
    }
}

record LinkedFluidHandler(LinkedStorageRef ref, String name, IFluidHandler handler, boolean allowStore) {
    BlockPos pos() {
        return this.ref.pos();
    }
}

final class LinkedItemHandlerView implements IItemHandler, RtsAe2Compat.ReportedCountItemHandler {
    private final IItemHandler delegate;
    private final boolean allowStore;

    LinkedItemHandlerView(IItemHandler delegate, boolean allowStore) {
        this.delegate = delegate;
        this.allowStore = allowStore;
    }

    @Override
    public int getSlots() {
        return this.delegate.getSlots();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return this.delegate.getStackInSlot(slot);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        return this.allowStore ? this.delegate.insertItem(slot, stack, simulate) : stack;
    }

    boolean supportsAnySlotInsert() {
        return this.allowStore && this.delegate instanceof RtsAe2Compat.AnySlotInsertItemHandler;
    }

    ItemStack insertItemAnywhere(ItemStack stack, boolean simulate) {
        if (!this.allowStore) {
            return stack == null ? ItemStack.EMPTY : stack.copy();
        }
        if (this.delegate instanceof RtsAe2Compat.AnySlotInsertItemHandler anySlot) {
            return anySlot.insertItemAnywhere(stack, simulate);
        }
        ItemStack remain = stack == null ? ItemStack.EMPTY : stack.copy();
        for (int slot = 0; slot < this.delegate.getSlots() && !remain.isEmpty(); slot++) {
            remain = this.delegate.insertItem(slot, remain, simulate);
        }
        return remain;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return this.delegate.extractItem(slot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        return this.delegate.getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return this.delegate.isItemValid(slot, stack);
    }

    @Override
    public long getReportedCount(int slot) {
        ItemStack stack = this.delegate.getStackInSlot(slot);
        return RtsAe2Compat.getReportedCount(this.delegate, slot, stack);
    }
}

final class LinkedFluidHandlerView implements IFluidHandler {
    private final IFluidHandler delegate;
    private final boolean allowStore;

    LinkedFluidHandlerView(IFluidHandler delegate, boolean allowStore) {
        this.delegate = delegate;
        this.allowStore = allowStore;
    }

    @Override
    public int getTanks() {
        return this.delegate.getTanks();
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return this.delegate.getFluidInTank(tank);
    }

    @Override
    public int getTankCapacity(int tank) {
        return this.delegate.getTankCapacity(tank);
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return this.delegate.isFluidValid(tank, stack);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        return this.allowStore ? this.delegate.fill(resource, action) : 0;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        return this.delegate.drain(resource, action);
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        return this.delegate.drain(maxDrain, action);
    }
}
