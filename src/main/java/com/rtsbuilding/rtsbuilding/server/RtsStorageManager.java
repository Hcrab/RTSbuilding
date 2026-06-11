package com.rtsbuilding.rtsbuilding.server;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.reflect.Field;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.rtsbuilding.rtsbuilding.common.BuilderMode;
import com.rtsbuilding.rtsbuilding.server.history.ServerHistoryManager;
import com.rtsbuilding.rtsbuilding.compat.ftb.RtsFtbCompat;
import com.rtsbuilding.rtsbuilding.compat.remote.RtsRemoteMenuCompat;
import com.rtsbuilding.rtsbuilding.compat.sophisticatedbackpacks.RtsBackpackCompat;
import com.rtsbuilding.rtsbuilding.compat.sophisticatedstorage.RtsSophisticatedStorageCompat;
import com.rtsbuilding.rtsbuilding.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.storage.*;
import com.rtsbuilding.rtsbuilding.server.storage.mining.RtsMiningStateMachine;
import com.rtsbuilding.rtsbuilding.server.storage.placement.RtsPlacementBatch;
import com.rtsbuilding.rtsbuilding.server.storage.placement.RtsPlacementHelper;
import com.rtsbuilding.rtsbuilding.server.storage.placement.RtsPlacementSound;
import com.rtsbuilding.rtsbuilding.util.RtsCountUtil;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsInteractPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsLinkStoragePayload;
import com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort;
import com.rtsbuilding.rtsbuilding.network.progression.S2CRtsQuestDetectStatusPayload;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsRemoteMenuHintPayload;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStorageDirtyPayload;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;

import com.rtsbuilding.rtsbuilding.server.data.PlacedBlockTrackerData;
import com.rtsbuilding.rtsbuilding.server.data.RtsStorageSessionStore;

public final class RtsStorageManager {
    private static final double REMOTE_POV_BLOCK_REACH = 4.0D;
    private static final double REMOTE_POV_EPSILON = 0.1D;
    private static final double FUNNEL_RADIUS = 2.0D;
    private static final int FUNNEL_MAX_ENTITIES_PER_TICK = 24;
    private static final int FUNNEL_MAX_ITEMS_PER_TICK = 48;
    private static final int FUNNEL_BUFFER_MAX_STACKS = 16;
    private static final int FUNNEL_TICK_INTERVAL = 2;
    private static final int PLACED_RECOVERY_MAX_JOBS_PER_TICK = 4;
    private static final int PLACED_RECOVERY_MAX_STACKS_PER_TICK = 8;
    private static final int SHIFT_IMPORT_MAX_CRAFT_ITERATIONS = 64;
    // Shared with RtsStorageSession so the extracted state object cannot drift
    // from the packet/UI limits that RtsStorageManager still owns.
    public static final int CRAFTABLE_BATCH_SIZE = 12;
    public static final int RECENT_ENTRY_LIMIT = 24;
    private static final long QUEST_DETECT_COOLDOWN_TICKS = 60L;
    // Package-private for the page builder to reuse the same payload padding
    // limits without copying storage UI runtime rules.
    public static final int QUICK_SLOT_COUNT = 27;
    public static final int GUI_BINDING_SLOT_COUNT = 8;
    public static final byte LINK_MODE_BIDIRECTIONAL = C2SRtsLinkStoragePayload.MODE_BIDIRECTIONAL;
    private static final byte LINK_MODE_EXTRACT_ONLY = C2SRtsLinkStoragePayload.MODE_EXTRACT_ONLY;

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private RtsStorageManager() {
    }

    public static void onRtsEnabled(ServerPlayer player) {
        Session session = getOrCreateSession(player);
        sanitizeSessionDimension(player, session);
        requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
        // 进入 RTS 模式时同步历史状态，确保客户端 UI 显示正确的撤回/重做次数
        ServerHistoryManager.sendSync(player);
    }

    public static void warmCreativeTabCaches(MinecraftServer server) {
        if (server == null) {
            return;
        }
        synchronized (RtsStorageManager.class) {
            clearCreativeTabCacheState();
            ServerLevel level = server.overworld();
            if (level == null) {
                return;
            }
            warmCreativeTabCacheMode(level, false);
            warmCreativeTabCacheMode(level, true);
        }
    }

    public static void onRtsDisabled(ServerPlayer player) {
        // Keep linked-storage state across RTS toggles, but stop active mining.
        Session session = getOrCreateSession(player);
        stopActiveMining(player, session);
        session.placeBatchJobs.clear();
        disableFunnelAndFlushBuffer(player, session);
        closeTrackedRemoteMenu(player, session);
        clearRemoteMenuValidation(player, session);
        saveSessionToPlayerNbt(player, session);
    }

    public static void onPlayerLogout(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session != null) {
            session.placeBatchJobs.clear();
            disableFunnelAndFlushBuffer(player, session);
            closeTrackedRemoteMenu(player, session);
            clearRemoteMenuValidation(player, session);
            saveSessionToPlayerNbt(player, session);
        }
        SESSIONS.remove(player.getUUID());
    }

    public static void onPlayerTickPre(ServerPlayer player) {
        // RTS no longer spoofs player position for Sophisticated Storage menu validation.
    }

    public static void onPlayerTickPost(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        if (session.remoteMenuContainerId < 0 && !RtsSophisticatedStorageCompat.isSupportedRemoteMenu(player.containerMenu)) {
            clearRemoteMenuValidation(player, session);
        }
        if (session.remoteMenuContainerId >= 0
                && (player.containerMenu == null || player.containerMenu.containerId != session.remoteMenuContainerId)) {
            clearRemoteMenuValidation(player, session);
        }
        RtsPlacementBatch.tickPlaceBatchJobs(player, session);
    }

    public static void closeRemoteMenuFromClient(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.remoteMenuContainerId < 0) {
            return;
        }
        closeTrackedRemoteMenu(player, session);
        clearRemoteMenuValidation(player, session);
    }

    private static Session getOrCreateSession(ServerPlayer player) {
        Session existing = SESSIONS.get(player.getUUID());
        if (existing != null) {
            return existing;
        }
        Session created = new Session();
        loadSessionFromPersistentStorage(player, created);
        SESSIONS.put(player.getUUID(), created);
        return created;
    }

    private static void loadSessionFromPersistentStorage(ServerPlayer player, Session session) {
        CompoundTag root = RtsStorageSessionStore.loadSession(player);
        boolean loadedFromWorldStore = !root.isEmpty();
        if (!loadedFromWorldStore) {
            root = player.getPersistentData().getCompound(RtsStorageSessionCodec.ROOT_KEY);
        }
        if (root.isEmpty()) {
            return;
        }
        RtsStorageSessionCodec.load(player, session, root);
        if (!loadedFromWorldStore) {
            saveSessionToPlayerNbt(player, session);
        }
    }

    public static void saveSessionToPlayerNbt(ServerPlayer player, RtsStorageSession session) {
        CompoundTag root = RtsStorageSessionCodec.serialize(session);
        player.getPersistentData().put(RtsStorageSessionCodec.ROOT_KEY, root.copy());
        RtsStorageSessionStore.saveSession(player, root);
    }

    private static void applyBindingUpdate(ServerPlayer player, Session session, RtsStorageBindings.UpdateResult update) {
        if (player == null || session == null || update == null) {
            return;
        }
        if (update.saveSession()) {
            saveSessionToPlayerNbt(player, session);
        }
        if (update.refreshPage()) {
            requestPage(player, update.page(), session.search, session.category, session.sort, session.ascending);
        }
    }

    public static void tickMining(MinecraftServer server) {
        for (var entry : SESSIONS.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            Session session = entry.getValue();
            tickActiveMining(player, session);
            tickFunnel(player, session);
            tickPlacedRecoveryJobs(player, session);
        }
    }

    // Public binding wrappers stay in the manager so existing packet handlers
    // do not churn while RtsStorageBindings owns the session binding details.
    public static void setMode(ServerPlayer player, BuilderMode mode) {
        Session session = getOrCreateSession(player);
        if (RtsStorageBindings.setMode(session, mode)) {
            disableFunnelAndFlushBuffer(player, session);
            requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
        }
    }

    public static void setFunnelEnabled(ServerPlayer player, boolean enabled) {
        if (enabled && !RtsProgressionManager.canUse(player, RtsFeature.FUNNEL)) {
            return;
        }
        Session session = getOrCreateSession(player);
        if (session.funnelEnabled == enabled) {
            return;
        }
        if (enabled) {
            session.funnelEnabled = true;
            session.funnelTickCooldown = 0;
        } else {
            disableFunnelAndFlushBuffer(player, session);
        }
        requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
    }

    public static void updateFunnelTarget(ServerPlayer player, BlockPos target) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.FUNNEL)) {
            return;
        }
        Session session = getOrCreateSession(player);
        if (!session.funnelEnabled || target == null) {
            return;
        }
        session.funnelTarget = target.immutable();
    }

    public static void setAutoStoreMinedDrops(ServerPlayer player, boolean enabled) {
        if (enabled && !RtsProgressionManager.canUse(player, RtsFeature.AUTO_STORE_MINED_DROPS)) {
            return;
        }
        Session session = getOrCreateSession(player);
        session.autoStoreMinedDrops = enabled;
        saveSessionToPlayerNbt(player, session);
        requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
    }

    public static void setBdNetworkEnabled(ServerPlayer player, boolean enabled) {
        Session session = getOrCreateSession(player);
        if (session.useBdNetwork == enabled) {
            return;
        }
        session.useBdNetwork = enabled;
        session.cachedBdHandler = null;
        session.cachedBdFluidHandler = null;
        saveSessionToPlayerNbt(player, session);
        requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
    }

    public static void onLinkedStorageBlockBroken(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || level.getServer() == null) {
            return;
        }
        ResourceKey<Level> dimension = level.dimension();
        for (var entry : SESSIONS.entrySet()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            Session session = entry.getValue();
            if (markOrRemoveBrokenLinkedStorageRef(session, level, dimension, pos)) {
                saveSessionToPlayerNbt(player, session);
                requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
            }
        }
    }

    public static void onLinkedStorageBlockPlaced(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || level.getServer() == null || !RtsBackpackCompat.isAvailable()) {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        UUID backpackUuid = RtsBackpackCompat.getBackpackUuid(blockEntity).orElse(null);
        if (backpackUuid == null) {
            return;
        }
        String backpackItemId = RtsBackpackCompat.getBackpackItemId(blockEntity).orElse("");
        LinkedStorageRef newRef = new LinkedStorageRef(level.dimension(), pos.immutable());
        String displayName = RtsLinkedStorageResolver.resolveDisplayName(level, pos);
        for (var entry : SESSIONS.entrySet()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            Session session = entry.getValue();
            if (moveBackpackLinkedStorageRef(session, backpackUuid, backpackItemId, newRef, displayName)) {
                saveSessionToPlayerNbt(player, session);
                requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
            }
        }
    }

    private static boolean markOrRemoveBrokenLinkedStorageRef(RtsStorageSession session, ServerLevel level,
            ResourceKey<Level> dimension, BlockPos pos) {
        if (session == null || dimension == null || pos == null || session.linkedStorages.isEmpty()) {
            return false;
        }
        LinkedStorageRef ref = new LinkedStorageRef(dimension, pos.immutable());
        if (!session.linkedStorages.contains(ref)) {
            return false;
        }
        UUID backpackUuid = session.linkedBackpackUuids.get(ref);
        if (backpackUuid != null) {
            UUID breakingUuid = level == null ? null : RtsBackpackCompat.getBackpackUuid(level.getBlockEntity(pos)).orElse(null);
            if (!backpackUuid.equals(breakingUuid)) {
                return false;
            }
            return session.detachedBackpackRefs.add(ref);
        }
        return removeLinkedStorageRef(session, dimension, pos);
    }

    private static boolean moveBackpackLinkedStorageRef(RtsStorageSession session, UUID backpackUuid,
            String backpackItemId, LinkedStorageRef newRef, String displayName) {
        if (session == null || backpackUuid == null || newRef == null || session.linkedStorages.isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (LinkedStorageRef oldRef : List.copyOf(session.linkedStorages)) {
            if (!backpackUuid.equals(session.linkedBackpackUuids.get(oldRef))) {
                continue;
            }
            if (oldRef.equals(newRef)) {
                session.detachedBackpackRefs.remove(oldRef);
                session.linkedNames.put(oldRef, displayName);
                if (backpackItemId != null && !backpackItemId.isBlank()) {
                    session.linkedBackpackItemIds.put(oldRef, backpackItemId);
                }
                changed = true;
                continue;
            }
            if (session.linkedStorages.contains(newRef)
                    && !backpackUuid.equals(session.linkedBackpackUuids.get(newRef))) {
                continue;
            }
            byte mode = session.linkedModes.getOrDefault(oldRef, LINK_MODE_BIDIRECTIONAL);
            int priority = session.linkedPriorities.getOrDefault(oldRef, 0);
            int index = session.linkedStorages.indexOf(oldRef);
            if (index < 0) {
                continue;
            }
            if (session.linkedStorages.contains(newRef)) {
                removeLinkedStorageRef(session, oldRef.dimension(), oldRef.pos());
            } else {
                session.linkedStorages.set(index, newRef);
                session.linkedNames.remove(oldRef);
                session.linkedModes.remove(oldRef);
                session.linkedPriorities.remove(oldRef);
                session.linkedBackpackUuids.remove(oldRef);
                session.linkedBackpackItemIds.remove(oldRef);
                session.detachedBackpackRefs.remove(oldRef);
            }
            session.linkedNames.put(newRef, displayName);
            session.linkedModes.put(newRef, mode);
            session.linkedPriorities.put(newRef, sanitizeLinkedStoragePriority(priority));
            session.linkedBackpackUuids.put(newRef, backpackUuid);
            if (backpackItemId != null && !backpackItemId.isBlank()) {
                session.linkedBackpackItemIds.put(newRef, backpackItemId);
            }
            session.detachedBackpackRefs.remove(newRef);
            changed = true;
        }
        return changed;
    }

    private static boolean removeLinkedStorageRef(RtsStorageSession session, ResourceKey<Level> dimension, BlockPos pos) {
        if (session == null || dimension == null || pos == null || session.linkedStorages.isEmpty()) {
            return false;
        }
        boolean removed = session.linkedStorages.removeIf(ref ->
                ref != null && dimension.equals(ref.dimension()) && pos.equals(ref.pos()));
        if (removed) {
            session.linkedNames.keySet().removeIf(ref -> ref == null || !session.linkedStorages.contains(ref));
            session.linkedModes.keySet().removeIf(ref -> ref == null || !session.linkedStorages.contains(ref));
            session.linkedPriorities.keySet().removeIf(ref -> ref == null || !session.linkedStorages.contains(ref));
            session.linkedBackpackUuids.keySet().removeIf(ref -> ref == null || !session.linkedStorages.contains(ref));
            session.linkedBackpackItemIds.keySet().removeIf(ref -> ref == null || !session.linkedStorages.contains(ref));
            session.detachedBackpackRefs.removeIf(ref -> ref == null || !session.linkedStorages.contains(ref));
        }
        return removed;
    }

    public static BuilderMode getMode(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        return session == null ? BuilderMode.INTERACT : session.mode;
    }

    public static void linkStorage(ServerPlayer player, BlockPos pos, byte linkMode) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.LINK_STORAGE)) {
            return;
        }
        if (!canAccessWorldTarget(player, pos)) {
            return;
        }

        Session session = getOrCreateSession(player);
        applyBindingUpdate(player, session, RtsStorageBindings.linkStorage(player, session, pos, linkMode));
    }

    /**
     * Removes one storage binding from the player's own RTS storage session.
     *
     * <p>Unlike binding, unlink intentionally does not require the target block
     * to still be loaded or accessible. The detail panel is also a repair tool:
     * players need to clean up stale bindings after a chest, controller, or
     * network terminal was moved, broken, or unloaded. This method only removes
     * a same-dimension/same-position reference and refreshes the storage page;
     * it does not change insertion/extraction semantics or discover new storage.
     */
    public static void unlinkStorage(ServerPlayer player, BlockPos pos) {
        if (player == null || pos == null) {
            return;
        }
        Session session = getOrCreateSession(player);
        if (removeLinkedStorageRef(session, player.serverLevel().dimension(), pos)) {
            saveSessionToPlayerNbt(player, session);
            requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
        }
    }

    public static void updateLinkedStorageSettings(ServerPlayer player, BlockPos pos, byte linkMode, int priority) {
        if (player == null || pos == null) {
            return;
        }
        Session session = getOrCreateSession(player);
        applyBindingUpdate(player, session,
                RtsStorageBindings.updateLinkedStorageSettings(player, session, pos, linkMode, priority));
    }

    public static void openCraftTerminal(ServerPlayer player) {
        RtsStorageCrafting.openCraftTerminal(player, SESSIONS.get(player.getUUID()));
    }

    public static void detectQuests(ServerPlayer player, byte mode) {
        Session session = getOrCreateSession(player);
        if (session == null) {
            return;
        }
        sanitizeSessionDimension(player, session);
        runQuestDetect(player, session, true);
    }

    public static void rotateBlock(ServerPlayer player, BlockPos pos) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.ROTATE_BLOCK)) {
            return;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !canAccessWorldTarget(player, pos)) {
            return;
        }
        RtsPlacementHelper.rotatePlacedBlock(player.serverLevel(), pos, (byte) 1);
    }

    public static void storeHotbarSlotToLinked(ServerPlayer player, byte slotId) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        sanitizeSessionDimension(player, session);
        if (!hasAnyStorage(player, session)) {
            return;
        }

        List<LinkedHandler> activeLinked = resolveLinkedHandlers(player, session);
        if (activeLinked.isEmpty()) {
            return;
        }
        List<IItemHandler> handlers = RtsLinkedStorageResolver.itemHandlersForInsert(activeLinked);

        int slot = clampHotbarSlot(slotId);
        ItemStack inSlot = player.getInventory().getItem(slot);
        if (inSlot.isEmpty()) {
            return;
        }

        ItemStack remaining = storeToLinkedOnlyPreferExisting(handlers, inSlot.copy());
        if (remaining.getCount() == inSlot.getCount()) {
            return;
        }

        player.getInventory().setItem(slot, remaining.isEmpty() ? ItemStack.EMPTY : remaining);
        player.containerMenu.broadcastChanges();
        requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
        runQuestDetect(player, session, false);
    }

    public static void setQuickSlot(ServerPlayer player, byte slotId, String itemId) {
        Session session = getOrCreateSession(player);
        applyBindingUpdate(player, session, RtsStorageBindings.setQuickSlot(session, slotId, itemId));
    }

    public static void setGuiBinding(ServerPlayer player, byte slotId, boolean clear, BlockPos pos, Direction face, String itemIdHint) {
        if (!clear && !RtsProgressionManager.canUse(player, RtsFeature.REMOTE_GUI_BINDING)) {
            return;
        }
        Session session = getOrCreateSession(player);
        applyBindingUpdate(player, session, RtsStorageBindings.setGuiBinding(player, session, slotId, clear, pos, face, itemIdHint));
    }

    public static void openGuiBinding(ServerPlayer player, byte slotId) {
        Session session = SESSIONS.get(player.getUUID());
        applyBindingUpdate(player, session, RtsStorageBindings.openGuiBinding(player, session, slotId, REMOTE_POV_BLOCK_REACH));
    }

    public static long countLinkedItemsMatching(ServerPlayer player, Predicate<ItemStack> predicate) {
        if (player == null || predicate == null) {
            return 0L;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return 0L;
        }
        sanitizeSessionDimension(player, session);
        if (!hasAnyStorage(player, session)) {
            return 0L;
        }

        long total = 0L;
        List<LinkedHandler> linked = resolveLinkedHandlers(player, session);
        for (LinkedHandler linkedHandler : linked) {
            IItemHandler handler = linkedHandler.handler();
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (stack.isEmpty()) {
                    continue;
                }
                if (!predicate.test(stack)) {
                    continue;
                }
                total = RtsCountUtil.saturatedAdd(total, getHandlerReportedCount(handler, slot, stack));
            }
        }
        return total;
    }

    public static boolean canAccessBlueprintTarget(ServerPlayer player, BlockPos pos) {
        return canAccessWorldTarget(player, pos);
    }

    public static long countBlueprintMaterial(ServerPlayer player, Item item) {
        if (player == null || item == null || item == Items.AIR) {
            return 0L;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return 0L;
        }

        long total = 0L;
        for (LinkedHandler linkedHandler : resolveLinkedHandlers(player, session)) {
            IItemHandler handler = linkedHandler.handler();
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (!stack.isEmpty() && stack.getItem() == item) {
                    total = RtsCountUtil.saturatedAdd(total, getHandlerReportedCount(handler, slot, stack));
                }
            }
        }

        int start = getPlayerMainInventoryStart(player);
        int end = getPlayerMainInventoryEndExclusive(player);
        for (int slot = start; slot < end; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.getItem() == item) {
                total = RtsCountUtil.saturatedAdd(total, stack.getCount());
            }
        }
        return total;
    }

    public static ItemStack extractBlueprintMaterial(ServerPlayer player, Item item, int count) {
        if (player == null || item == null || item == Items.AIR || count <= 0) {
            return ItemStack.EMPTY;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return ItemStack.EMPTY;
        }
        List<LinkedHandler> activeLinked = resolveLinkedHandlers(player, session);
        List<IItemHandler> handlers = RtsLinkedStorageResolver.itemHandlersForExtract(activeLinked);
        return extractMatchingFromNetwork(handlers, player, item, count);
    }

    public static long countBlueprintFluidMb(ServerPlayer player, Fluid fluid) {
        if (player == null || fluid == null) {
            return 0L;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return 0L;
        }
        return RtsStorageFluids.countFluidInNetwork(session, resolveLinkedFluidHandlers(player, session), fluid);
    }

    public static boolean extractBlueprintFluid(ServerPlayer player, Fluid fluid, int amountMb) {
        if (player == null || fluid == null || amountMb <= 0) {
            return false;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return false;
        }
        return RtsStorageFluids.extractFluidFromNetwork(
                session,
                resolveLinkedFluidHandlers(player, session),
                fluid,
                amountMb,
                true) >= amountMb;
    }

    public static void refundBlueprintMaterial(ServerPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) {
            return;
        }
        Session session = SESSIONS.get(player.getUUID());
        List<IItemHandler> handlers = session == null
                ? List.of()
                : resolveLinkedHandlers(player, session).stream().map(LinkedHandler::handler).toList();
        refundToLinked(handlers, player, stack);
    }

    public static void noteBlueprintBlockPlaced(ServerPlayer player, BlockPos pos, String itemId) {
        if (player == null || pos == null) {
            return;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        RtsPlacementSound.playRemotePlacedBlockSound(player, player.serverLevel(), pos);
        recordRecentItem(session, itemId, S2CRtsStoragePagePayload.RECENT_ITEM_PLACED, 1L);
    }

    public static void refreshBlueprintStoragePage(ServerPlayer player) {
        Session session = player == null ? null : SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
    }

    private static boolean currentPinyinSearchEnabled(ServerPlayer player) {
        Session session = player == null ? null : SESSIONS.get(player.getUUID());
        return session != null && session.pinyinSearchEnabled;
    }

    private static List<String> currentLocalizedSearchMatches(ServerPlayer player) {
        Session session = player == null ? null : SESSIONS.get(player.getUUID());
        return session == null ? List.of() : List.copyOf(session.localizedSearchMatches);
    }

    private static boolean currentCraftPinyinSearchEnabled(ServerPlayer player) {
        Session session = player == null ? null : SESSIONS.get(player.getUUID());
        return session != null && session.craftPinyinSearchEnabled;
    }

    private static List<String> currentCraftLocalizedSearchMatches(ServerPlayer player) {
        Session session = player == null ? null : SESSIONS.get(player.getUUID());
        return session == null ? List.of() : List.copyOf(session.craftLocalizedSearchMatches);
    }

    private static Set<String> sanitizeLocalizedSearchMatches(List<String> localizedSearchMatches) {
        return RtsStoragePageBuilder.sanitizeLocalizedSearchMatches(localizedSearchMatches);
    }

    private static int sessionPageSize(ServerPlayer player) {
        Session session = player == null ? null : SESSIONS.get(player.getUUID());
        return session == null ? RtsStoragePageBuilder.DEFAULT_PAGE_SIZE : session.pageSize;
    }

    public static void requestPage(ServerPlayer player, int page, String search, String category, RtsStorageSort sort,
            boolean ascending) {
        requestPage(player, page, search, category, sort, ascending, currentPinyinSearchEnabled(player));
    }

    public static void requestPage(ServerPlayer player, int page, String search, String category, RtsStorageSort sort,
            boolean ascending, boolean pinyinSearchEnabled) {
        requestPage(
                player,
                page,
                search,
                category,
                sort,
                ascending,
                pinyinSearchEnabled,
                currentLocalizedSearchMatches(player));
    }

    public static void requestPage(ServerPlayer player, int page, String search, String category, RtsStorageSort sort,
            boolean ascending, boolean pinyinSearchEnabled, List<String> localizedSearchMatches) {
        requestPage(player, page, search, category, sort, ascending, sessionPageSize(player), pinyinSearchEnabled, localizedSearchMatches);
    }

    public static void requestPage(ServerPlayer player, int page, String search, String category, RtsStorageSort sort,
            boolean ascending, int pageSize, boolean pinyinSearchEnabled, List<String> localizedSearchMatches) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.STORAGE_BROWSER)) {
            return;
        }
        Session session = getOrCreateSession(player);
        refreshMissingGuiBindingIcons(player, session);
        session.search = search == null ? "" : search;
        session.category = normalizeCategory(category);
        session.sort = sort == null ? RtsStorageSort.QUANTITY : sort;
        session.ascending = ascending;
        session.pageSize = RtsStoragePageBuilder.sanitizePageSize(pageSize);
        session.pinyinSearchEnabled = pinyinSearchEnabled;
        session.localizedSearchMatches.clear();
        session.localizedSearchMatches.addAll(sanitizeLocalizedSearchMatches(localizedSearchMatches));

        sanitizeSessionDimension(player, session);
        session.cachedBdHandler = null;
        session.cachedBdFluidHandler = null;

        List<LinkedHandler> activeHandlers = resolveLinkedHandlers(player, session);
        List<LinkedFluidHandler> activeFluidHandlers = resolveLinkedFluidHandlers(player, session);
        RtsStoragePageBuilder.PageResult result = RtsStoragePageBuilder.build(
                player,
                session,
                page,
                session.pageSize,
                activeHandlers,
                activeFluidHandlers);
        PacketDistributor.sendToPlayer(player, result.payload());
        session.storageViewDirty = false;
        session.page = result.safePage();
        saveSessionToPlayerNbt(player, session);
    }

    public static void markStorageViewDirty(ServerPlayer player, RtsStorageSession session) {
        if (player == null || session == null) {
            return;
        }
        if (!RtsProgressionManager.canUse(player, RtsFeature.STORAGE_BROWSER)) {
            return;
        }
        if (session.storageViewDirty) {
            return;
        }
        session.storageViewDirty = true;
        PacketDistributor.sendToPlayer(player, new S2CRtsStorageDirtyPayload(true));
    }

    public static void requestCraftables(ServerPlayer player, String search, boolean showUnavailable, int offset, int limit) {
        requestCraftables(player, search, showUnavailable, offset, limit, currentCraftPinyinSearchEnabled(player));
    }

    public static void requestCraftables(ServerPlayer player, String search, boolean showUnavailable, int offset, int limit,
            boolean pinyinSearchEnabled) {
        requestCraftables(
                player,
                search,
                showUnavailable,
                offset,
                limit,
                pinyinSearchEnabled,
                currentCraftLocalizedSearchMatches(player));
    }

    public static void requestCraftables(ServerPlayer player, String search, boolean showUnavailable, int offset, int limit,
            boolean pinyinSearchEnabled, List<String> localizedSearchMatches) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.CRAFT_TERMINAL)) {
            return;
        }
        RtsStorageCrafting.requestCraftables(
                player,
                getOrCreateSession(player),
                search,
                showUnavailable,
                offset,
                limit,
                pinyinSearchEnabled,
                localizedSearchMatches);
    }

    public static void craftRecipeToLinked(ServerPlayer player, String recipeId) {
        craftRecipeToLinked(player, recipeId, 1);
    }

    public static void craftRecipeToLinked(ServerPlayer player, String recipeId, int craftCount) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.CRAFT_TERMINAL)) {
            return;
        }
        RtsStorageCrafting.craftRecipeToLinked(player, getOrCreateSession(player), recipeId, craftCount);
    }

    public static String normalizeCategory(String category) {
        return RtsStoragePageBuilder.normalizeCategory(category);
    }

    private static void warmCreativeTabCacheMode(ServerLevel level, boolean operatorTabs) {
        RtsStoragePageBuilder.warmCreativeTabCacheMode(level, operatorTabs);
    }

    private static void clearCreativeTabCacheState() {
        RtsStoragePageBuilder.clearCreativeTabCacheState();
    }

    public static void placeSelected(ServerPlayer player, BlockPos clickedPos, Direction face, double hitX, double hitY,
            double hitZ, byte rotateSteps, boolean forcePlace, boolean skipIfOccupied, String itemId,
            ItemStack itemPrototype, double rayOriginX, double rayOriginY, double rayOriginZ,
            double rayDirX, double rayDirY, double rayDirZ, boolean quickBuild, boolean forceEmptyHand) {
        double hitOffsetX = clickedPos == null ? 0.5D : hitX - clickedPos.getX();
        double hitOffsetY = clickedPos == null ? 0.5D : hitY - clickedPos.getY();
        double hitOffsetZ = clickedPos == null ? 0.5D : hitZ - clickedPos.getZ();
        RtsPlacementBatch.enqueuePlaceBatch(
                player,
                player == null ? null : SESSIONS.get(player.getUUID()),
                clickedPos == null ? List.of() : List.of(clickedPos),
                face,
                hitOffsetX,
                hitOffsetY,
                hitOffsetZ,
                rotateSteps,
                forcePlace,
                skipIfOccupied,
                itemId,
                itemPrototype,
                rayOriginX,
                rayOriginY,
                rayOriginZ,
                rayDirX,
                rayDirY,
                rayDirZ,
                quickBuild,
                forceEmptyHand,
                true);
    }

    public static void enqueuePlaceBatch(ServerPlayer player, List<BlockPos> clickedPositions, Direction face,
            double hitOffsetX, double hitOffsetY, double hitOffsetZ, byte rotateSteps,
            boolean forcePlace, boolean skipIfOccupied, String itemId,
            ItemStack itemPrototype, double rayOriginX, double rayOriginY, double rayOriginZ,
            double rayDirX, double rayDirY, double rayDirZ) {
        RtsPlacementBatch.enqueuePlaceBatch(
                player,
                player == null ? null : SESSIONS.get(player.getUUID()),
                clickedPositions,
                face,
                hitOffsetX,
                hitOffsetY,
                hitOffsetZ,
                rotateSteps,
                forcePlace,
                skipIfOccupied,
                itemId == null ? "" : itemId,
                itemPrototype,
                rayOriginX,
                rayOriginY,
                rayOriginZ,
                rayDirX,
                rayDirY,
                rayDirZ,
                true,
                false,
                false);
    }

    private static BlockPos resolvePlacementTargetPos(ServerLevel level, BlockPos clickedPos, Direction face) {
        if (level == null || clickedPos == null || face == null) {
            return null;
        }
        if (!level.hasChunkAt(clickedPos)) {
            return clickedPos;
        }
        return level.getBlockState(clickedPos).canBeReplaced() ? clickedPos : clickedPos.relative(face);
    }

    public static void storeFluidFromContainer(ServerPlayer player, byte sourceType, byte toolSlot, String itemId) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.FLUID_HANDLING)) {
            return;
        }
        Session session = getOrCreateSession(player);
        if (!RtsCameraManager.isActive(player)) {
            return;
        }
        sanitizeSessionDimension(player, session);

        List<LinkedHandler> activeItemHandlers = resolveLinkedHandlers(player, session);
        List<LinkedFluidHandler> activeFluidHandlers = resolveLinkedFluidHandlers(player, session);
        List<IItemHandler> extractItemHandlers = RtsLinkedStorageResolver.itemHandlersForExtract(activeItemHandlers);
        List<IItemHandler> insertItemHandlers = RtsLinkedStorageResolver.itemHandlersForInsert(activeItemHandlers);

        boolean changed = RtsStorageFluids.storeFluidFromContainer(
                player,
                session,
                extractItemHandlers,
                insertItemHandlers,
                activeFluidHandlers,
                sourceType,
                toolSlot,
                itemId);
        if (changed) {
            saveSessionToPlayerNbt(player, session);
            requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
        }
    }

    public static void placeFluid(ServerPlayer player, BlockPos clickedPos, Direction face, double hitX, double hitY,
            double hitZ, boolean forcePlace, String fluidId,
            double rayOriginX, double rayOriginY, double rayOriginZ,
            double rayDirX, double rayDirY, double rayDirZ) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.FLUID_HANDLING)) {
            return;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !canAccessFluidPlacementTarget(player, clickedPos)) {
            return;
        }
        sanitizeSessionDimension(player, session);
        List<LinkedFluidHandler> activeFluidHandlers = resolveLinkedFluidHandlers(player, session);
        if (RtsStorageFluids.placeFluid(player, session, activeFluidHandlers, clickedPos, face, hitX, hitY, hitZ, fluidId)) {
            saveSessionToPlayerNbt(player, session);
            requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
        }
    }

    public static void interactTarget(ServerPlayer player, int entityId, BlockPos clickedPos, Direction face, double hitX,
            double hitY, double hitZ, byte sourceType, byte toolSlot, String itemId,
            double rayOriginX, double rayOriginY, double rayOriginZ,
            double rayDirX, double rayDirY, double rayDirZ) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.INTERACT)) {
            return;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !RtsCameraManager.isActive(player)) {
            return;
        }
        sanitizeSessionDimension(player, session);
        RayContext rayContext = parseRayContext(
                rayOriginX, rayOriginY, rayOriginZ,
                rayDirX, rayDirY, rayDirZ);

        ServerLevel level = player.serverLevel();
        Entity targetEntity = null;
        BlockHitResult blockHit = null;
        BlockPos effectiveBlockPos = null;
        BlockState beforeClicked = null;
        BlockPos adjacentPos = null;
        BlockState beforeAdjacent = null;
        boolean useItemInAir = sourceType == C2SRtsInteractPayload.SOURCE_TOOL_SLOT_AIR;

        if (entityId >= 0) {
            targetEntity = level.getEntity(entityId);
            if (targetEntity == null || !targetEntity.isAlive()) {
                return;
            }
            effectiveBlockPos = targetEntity.blockPosition();
            if (!level.hasChunkAt(effectiveBlockPos) || !level.mayInteract(player, effectiveBlockPos)) {
                return;
            }
        } else {
            if (clickedPos == null || !canAccessWorldTarget(player, clickedPos)) {
                return;
            }
            effectiveBlockPos = clickedPos.immutable();
            if (!useItemInAir) {
                blockHit = new BlockHitResult(new Vec3(hitX, hitY, hitZ), face, effectiveBlockPos, false);
                beforeClicked = level.getBlockState(effectiveBlockPos);
                adjacentPos = effectiveBlockPos.relative(face);
                beforeAdjacent = level.hasChunkAt(adjacentPos) ? level.getBlockState(adjacentPos) : null;
            }
        }

        InteractionResult result = InteractionResult.PASS;
        Vec3 hit = new Vec3(hitX, hitY, hitZ);
        if (blockHit != null) {
            sendRemoteMenuOpenHint(player, effectiveBlockPos);
        }
        ItemStack toolSnapshot = sourceType == C2SRtsInteractPayload.SOURCE_TOOL_SLOT
                || sourceType == C2SRtsInteractPayload.SOURCE_TOOL_SLOT_AIR
                ? player.getInventory().getItem(clampHotbarSlot(toolSlot)).copy()
                : ItemStack.EMPTY;
        ItemStack soundStack = sourceType == C2SRtsInteractPayload.SOURCE_PIN_ITEM
                ? createSoundStack(itemId)
                : toolSnapshot.copy();
        AbstractContainerMenu menuBeforeInteract = player.containerMenu;
        if (sourceType == C2SRtsInteractPayload.SOURCE_TOOL_SLOT) {
            result = interactWithToolSlot(player, level, targetEntity, blockHit, hit, toolSlot, rayContext);
        } else if (sourceType == C2SRtsInteractPayload.SOURCE_TOOL_SLOT_AIR) {
            result = useItemInAirWithToolSlot(player, level, hit, toolSlot, rayContext);
        } else if (sourceType == C2SRtsInteractPayload.SOURCE_PIN_ITEM) {
            result = interactWithLinkedItem(player, level, session, targetEntity, blockHit, hit, itemId, rayContext);
        } else if (sourceType == C2SRtsInteractPayload.SOURCE_EMPTY_HAND) {
            result = interactWithEmptyHand(player, level, targetEntity, blockHit, hit, rayContext);
        }
        AbstractContainerMenu menuAfterInteract = player.containerMenu;
        if (menuAfterInteract != menuBeforeInteract) {
            markRemoteMenuOpen(player, session, menuAfterInteract, effectiveBlockPos);
        }

        boolean playedSpecificSound = false;
        if (result.consumesAction() && blockHit != null && beforeClicked != null) {
            BlockPos placedPos = RtsPlacementHelper.detectPlacedPos(
                    level,
                    effectiveBlockPos,
                    beforeClicked,
                    adjacentPos,
                    beforeAdjacent);
            if (placedPos != null) {
                PlacedBlockTrackerData.get(level).mark(placedPos);
                if (!soundStack.isEmpty() && soundStack.getItem() instanceof BlockItem) {
                    RtsPlacementSound.playRemotePlacedBlockAnimation(player, placedPos);
                    RtsPlacementSound.playRemotePlacedBlockSound(player, level, placedPos);
                } else {
                    playRemoteUseSound(player, level, targetEntity, placedPos, soundStack);
                }
                playedSpecificSound = true;
            }
        }
        if (result.consumesAction()) {
            if (!playedSpecificSound) {
                playRemoteUseSound(player, level, targetEntity, effectiveBlockPos, soundStack);
            }
            if (sourceType == C2SRtsInteractPayload.SOURCE_PIN_ITEM && itemId != null && !itemId.isBlank()) {
                recordRecentItem(session, itemId, S2CRtsStoragePagePayload.RECENT_ITEM_USED, 1L);
            } else if (!toolSnapshot.isEmpty()) {
                ResourceLocation toolId = BuiltInRegistries.ITEM.getKey(toolSnapshot.getItem());
                if (toolId != null) {
                    recordRecentItem(session, toolId.toString(), S2CRtsStoragePagePayload.RECENT_ITEM_USED, 1L);
                }
            }
        }

        requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
    }

    public static void returnCarriedToLinked(ServerPlayer player, String itemId, int amount) {
        RtsStorageTransfers.returnCarriedToLinked(player, SESSIONS.get(player.getUUID()), itemId, amount);
    }

    public static void quickDropLinkedItem(ServerPlayer player, String itemId, byte amount, double dropX, double dropY,
            double dropZ) {
        RtsStorageTransfers.quickDropLinkedItem(player, SESSIONS.get(player.getUUID()), itemId, amount, dropX, dropY, dropZ);
    }

    public static void importMenuSlotToLinked(ServerPlayer player, int menuSlot) {
        RtsStorageTransfers.importMenuSlotToLinked(player, SESSIONS.get(player.getUUID()), menuSlot);
    }

    public static void refillCraftGridFromLinked(ServerPlayer player, CraftingMenu craftingMenu, ItemStack[] blueprint) {
        RtsStorageCrafting.refillCraftGridFromLinked(player, SESSIONS.get(player.getUUID()), craftingMenu, blueprint);
    }

    public static void refillCurrentCraftGridFromBlueprintIds(
            ServerPlayer player,
            List<String> blueprintIds,
            String craftedItemId,
            int craftedCount) {
        RtsStorageCrafting.refillCurrentCraftGridFromBlueprintIds(
                player,
                SESSIONS.get(player.getUUID()),
                blueprintIds,
                craftedItemId,
                craftedCount);
    }

    public static void refillCurrentCraftGridFromBlueprintStacks(
            ServerPlayer player,
            List<ItemStack> blueprintStacks,
            String craftedItemId,
            int craftedCount) {
        RtsStorageCrafting.refillCurrentCraftGridFromBlueprintStacks(
                player,
                SESSIONS.get(player.getUUID()),
                blueprintStacks,
                craftedItemId,
                craftedCount);
    }

    public static void applyJeiTransfer(
            ServerPlayer player,
            String recipeId,
            List<ItemStack> ingredientPrototypes,
            boolean maxTransfer,
            boolean clearGridFirst) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.JEI_TRANSFER)) {
            return;
        }
        RtsStorageCrafting.applyJeiTransfer(
                player,
                getOrCreateSession(player),
                recipeId,
                ingredientPrototypes,
                maxTransfer,
                clearGridFirst);
    }

    public static void breakPlaced(ServerPlayer player, BlockPos pos, Direction face, boolean allowAdjacentFallback) {
        boolean undoRecovery = allowAdjacentFallback;
        if (!undoRecovery && !RtsProgressionManager.canUse(player, RtsFeature.REMOTE_BREAK)) {
            return;
        }
        if (undoRecovery && !RtsProgressionManager.canUse(player, RtsFeature.REMOTE_PLACE)) {
            return;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !canAccessWorldTarget(player, pos)) {
            return;
        }
        sanitizeSessionDimension(player, session);
        if (!undoRecovery && !hasAnyStorage(player, session)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        PlacedBlockTrackerData tracker = PlacedBlockTrackerData.get(level);
        BlockPos targetPos = pos.immutable();
        if (!tracker.isPlaced(targetPos)) {
            if (!allowAdjacentFallback) {
                return;
            }
            Direction resolvedFace = face == null ? Direction.UP : face;
            BlockPos adjacent = targetPos.relative(resolvedFace);
            if (!canAccessWorldTarget(player, adjacent) || !tracker.isPlaced(adjacent)) {
                return;
            }
            targetPos = adjacent;
        }

        BlockState state = level.getBlockState(targetPos);
        if (state.isAir()) {
            tracker.clear(targetPos);
            return;
        }

        // 记录破坏操作到历史
        // 仅在用户主动破坏时记录，不记录内部恢复流程
        if (!allowAdjacentFallback) {
            ServerHistoryManager.recordBreak(player, List.of(targetPos), face != null ? face : Direction.UP);
        }

        Set<UUID> dropIdsBeforeBreak = snapshotNearbyDropIds(level, targetPos);
        boolean removed = breakPlacedWithSimulatedSilkTool(player, level, targetPos);
        if (!removed || !level.getBlockState(targetPos).isAir()) {
            return;
        }

        RtsPlacementSound.playRemoteBlockBreakSound(player, level, targetPos);
        tracker.clear(targetPos);
        List<ItemEntity> droppedEntities = collectNewNearbyDrops(level, targetPos, dropIdsBeforeBreak);
        enqueuePlacedRecoveryJob(session, targetPos, droppedEntities);

        // If a linked storage block itself is broken, unlink it immediately.
        LinkedStorageRef targetRef = new LinkedStorageRef(player.serverLevel().dimension(), targetPos);
        if (session.linkedStorages.remove(targetRef)) {
            session.linkedNames.remove(targetRef);
            session.linkedModes.remove(targetRef);
            session.linkedPriorities.remove(targetRef);
            saveSessionToPlayerNbt(player, session);
        }

        RtsStorageMining.markMiningStorageDirty(player, session);
    }

    private static void enqueuePlacedRecoveryJob(Session session, BlockPos targetPos, List<ItemEntity> droppedEntities) {
        if (session == null || droppedEntities == null || droppedEntities.isEmpty()) {
            return;
        }
        Deque<ItemStack> stacks = new ArrayDeque<>();
        for (ItemEntity droppedEntity : droppedEntities) {
            if (droppedEntity == null) {
                continue;
            }
            ItemStack droppedStack = droppedEntity.getItem();
            if (droppedStack.isEmpty()) {
                continue;
            }
            stacks.addLast(droppedStack.copy());
            droppedEntity.discard();
        }
        if (!stacks.isEmpty()) {
            session.placedRecoveryJobs.addLast(new PlacedRecoveryJob(targetPos.immutable(), stacks));
        }
    }

    private static void tickPlacedRecoveryJobs(ServerPlayer player, Session session) {
        if (player == null || session == null || session.placedRecoveryJobs.isEmpty()) {
            return;
        }

        List<LinkedHandler> orderedLinked = RtsLinkedStorageResolver.orderHandlersForInsert(resolveLinkedHandlers(player, session));
        OverflowOutcome overflow = OverflowOutcome.EMPTY;
        boolean hasLinkedRecoveryTarget = false;
        boolean processedAny = false;
        int processedJobs = 0;
        int processedStacks = 0;

        while (!session.placedRecoveryJobs.isEmpty()
                && processedJobs < PLACED_RECOVERY_MAX_JOBS_PER_TICK
                && processedStacks < PLACED_RECOVERY_MAX_STACKS_PER_TICK) {
            PlacedRecoveryJob job = session.placedRecoveryJobs.peekFirst();
            if (job == null || job.stacks.isEmpty()) {
                session.placedRecoveryJobs.removeFirst();
                processedJobs++;
                continue;
            }

            List<IItemHandler> handlers = placedRecoveryHandlersExcluding(orderedLinked, job.targetPos);
            hasLinkedRecoveryTarget |= !handlers.isEmpty();
            while (!job.stacks.isEmpty() && processedStacks < PLACED_RECOVERY_MAX_STACKS_PER_TICK) {
                ItemStack droppedStack = job.stacks.removeFirst();
                if (droppedStack == null || droppedStack.isEmpty()) {
                    continue;
                }
                ItemStack remain = storeToLinkedOnlyPreferExisting(handlers, droppedStack);
                if (!remain.isEmpty()) {
                    overflow = overflow.merge(storeToLinkedWithFallback(handlers, player, remain));
                }
                processedStacks++;
                processedAny = true;
            }

            if (job.stacks.isEmpty()) {
                session.placedRecoveryJobs.removeFirst();
                processedJobs++;
            }
        }

        if (overflow.hasOverflow()) {
            if (hasLinkedRecoveryTarget) {
                sendStorageOverflowHint(player, "Absorb", overflow);
            } else if (overflow.dropped() > 0) {
                player.displayClientMessage(
                        Component.literal("Inventory full, dropped " + overflow.dropped() + "."),
                        true);
            }
        }
        if (processedAny) {
            RtsStorageMining.markMiningStorageDirty(player, session);
            runQuestDetect(player, session, false);
        }
    }

    private static List<IItemHandler> placedRecoveryHandlersExcluding(List<LinkedHandler> orderedLinked, BlockPos targetPos) {
        if (orderedLinked == null || orderedLinked.isEmpty()) {
            return List.of();
        }
        List<IItemHandler> handlers = new ArrayList<>(orderedLinked.size());
        for (LinkedHandler linked : orderedLinked) {
            // Never route recovered items into the container being broken.
            if (linked.pos().equals(targetPos)) {
                continue;
            }
            handlers.add(linked.handler());
        }
        return handlers;
    }

    private static boolean breakPlacedWithSimulatedSilkTool(ServerPlayer player, ServerLevel level, BlockPos targetPos) {
        ItemStack simulatedTool = createSimulatedSilkNetheritePick(level);
        return withTemporaryOnGround(player, true, () -> withTemporaryMainHandItem(player, simulatedTool, () -> player.gameMode.destroyBlock(targetPos)));
    }

    private static ItemStack createSimulatedSilkNetheritePick(ServerLevel level) {
        ItemStack tool = new ItemStack(Items.NETHERITE_PICKAXE);
        tool.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), 1);
        return tool;
    }

    private static Set<UUID> snapshotNearbyDropIds(ServerLevel level, BlockPos pos) {
        Set<UUID> ids = new HashSet<>();
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1.5D))) {
            if (entity == null || !entity.isAlive() || entity.getItem().isEmpty()) {
                continue;
            }
            ids.add(entity.getUUID());
        }
        return ids;
    }

    private static List<ItemEntity> collectNewNearbyDrops(ServerLevel level, BlockPos pos, Set<UUID> beforeIds) {
        List<ItemEntity> out = new ArrayList<>();
        Set<UUID> known = beforeIds == null ? Set.of() : beforeIds;
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1.5D))) {
            if (entity == null || !entity.isAlive() || entity.getItem().isEmpty()) {
                continue;
            }
            if (known.contains(entity.getUUID())) {
                continue;
            }
            out.add(entity);
        }
        return out;
    }

    public static void pickupLinkedToCarried(ServerPlayer player, ItemStack prototype, int amount) {
        RtsStorageTransfers.pickupLinkedToCarried(player, SESSIONS.get(player.getUUID()), prototype, amount);
    }

    public static void quickMoveLinkedItem(ServerPlayer player, ItemStack prototype) {
        RtsStorageTransfers.quickMoveLinkedItem(player, SESSIONS.get(player.getUUID()), prototype);
    }

    public static void fillPlayerInventoryFromLinked(ServerPlayer player) {
        RtsStorageTransfers.fillPlayerInventoryFromLinked(player, SESSIONS.get(player.getUUID()));
    }

    public static void mine(ServerPlayer player, BlockPos pos, Direction face, boolean start, byte toolSlot,
            String toolItemId, ItemStack toolPrototype, boolean allowPlacedBlockRecovery,
            boolean toolProtectionEnabled) {
        RtsStorageMining.mine(
                player,
                SESSIONS.get(player.getUUID()),
                pos,
                face,
                start,
                toolSlot,
                toolItemId,
                toolPrototype,
                allowPlacedBlockRecovery,
                toolProtectionEnabled);
    }

    public static void startUltimine(ServerPlayer player, BlockPos pos, Direction face, byte toolSlot, String toolItemId,
            ItemStack toolPrototype, int requestedLimit, byte mode, boolean toolProtectionEnabled) {
        RtsStorageMining.startUltimine(
                player,
                SESSIONS.get(player.getUUID()),
                pos,
                face,
                toolSlot,
                toolItemId,
                toolPrototype,
                requestedLimit,
                mode,
                toolProtectionEnabled);
    }

    public static void areaMine(ServerPlayer player,
            int minX, int maxX, int minY, int maxY, int minZ, int maxZ,
            byte toolSlot, String toolItemId, ItemStack toolPrototype,
            byte shapeType, byte fillType, boolean toolProtectionEnabled) {
        RtsStorageMining.areaMine(
                player,
                SESSIONS.get(player.getUUID()),
                minX, maxX, minY, maxY, minZ, maxZ,
                toolSlot,
                toolItemId,
                toolPrototype,
                shapeType,
                fillType,
                toolProtectionEnabled);
    }

    public static void areaDestroy(ServerPlayer player, List<BlockPos> positions,
            byte toolSlot, String toolItemId, ItemStack toolPrototype, boolean toolProtectionEnabled) {
        RtsStorageMining.areaDestroy(
                player,
                SESSIONS.get(player.getUUID()),
                positions,
                toolSlot,
                toolItemId,
                toolPrototype,
                toolProtectionEnabled);
    }

    private static void tickActiveMining(ServerPlayer player, RtsStorageSession session) {
        RtsStorageMining.tickActiveMining(player, session);
    }

    private static void stopActiveMining(ServerPlayer player, RtsStorageSession session) {
        if (session == null) {
            return;
        }
        RtsStorageMining.stopActiveMining(player, session);
    }

    private static <T> T withTemporaryOnGround(ServerPlayer player, boolean onGround, Supplier<T> action) {
        return RtsMiningStateMachine.withTemporaryOnGround(player, onGround, action);
    }

    public static <T> T withTemporaryMainHandItem(ServerPlayer player, ItemStack stack, Supplier<T> action) {
        return RtsMiningStateMachine.withTemporaryMainHandItem(player, stack, action);
    }
    private static int clampHotbarSlot(int slot) {
        return Math.max(0, Math.min(8, slot));
    }

    private static boolean isValidQuickSlotIndex(int slot) {
        return RtsStorageBindings.isValidQuickSlotIndex(slot);
    }

    private static boolean isValidGuiBindingSlot(int slot) {
        return RtsStorageBindings.isValidGuiBindingSlot(slot);
    }

    private static boolean canBindGuiTarget(ServerLevel level, BlockPos pos) {
        return RtsStorageBindings.canBindGuiTarget(level, pos);
    }

    private static MenuProvider resolveBindableMenuProvider(ServerLevel level, BlockPos pos) {
        return RtsStorageBindings.resolveBindableMenuProvider(level, pos);
    }

    private static String resolveGuiBindingIconItemId(ServerLevel level, BlockPos pos, Direction face, String itemIdHint, String label) {
        return RtsStorageBindings.resolveGuiBindingIconItemId(level, pos, face, itemIdHint, label);
    }

    private static void refreshMissingGuiBindingIcons(ServerPlayer player, Session session) {
        if (RtsStorageBindings.refreshMissingGuiBindingIcons(player, session)) {
            saveSessionToPlayerNbt(player, session);
        }
    }

    private static void accumulatePlayerMainInventoryCounts(ServerPlayer player, Map<String, Long> counts,
            Map<String, Long> namespaceTotals) {
        RtsStoragePageBuilder.accumulatePlayerMainInventoryCounts(player, counts, namespaceTotals);
    }

    private static int getPlayerMainInventoryStart(ServerPlayer player) {
        return RtsStoragePageBuilder.getPlayerMainInventoryStart(player);
    }

    private static int getPlayerMainInventoryEndExclusive(ServerPlayer player) {
        return RtsStoragePageBuilder.getPlayerMainInventoryEndExclusive(player);
    }

    private static boolean shouldIncludePlayerMainInventoryInStorageView(ServerPlayer player, Session session) {
        return RtsStoragePageBuilder.shouldIncludePlayerMainInventoryInStorageView(player, session);
    }

    private static ItemStack extractOne(IItemHandler handler, Item targetItem) {
        return RtsStorageTransfers.extractOne(handler, targetItem);
    }

    private static ItemStack extractMatching(IItemHandler handler, Item targetItem, int limit) {
        return RtsStorageTransfers.extractMatching(handler, targetItem, limit);
    }

    private static ItemStack extractMatching(IItemHandler handler, Item targetItem, ItemStack preferred, int limit) {
        return RtsStorageTransfers.extractMatching(handler, targetItem, preferred, limit);
    }

    private static ItemStack extractOneFromLinked(List<IItemHandler> handlers, Item targetItem) {
        return RtsStorageTransfers.extractOneFromLinked(handlers, targetItem);
    }

    private static ItemStack extractOneFromPlayerMainInventory(ServerPlayer player, Item targetItem) {
        return RtsStorageTransfers.extractOneFromPlayerMainInventory(player, targetItem);
    }

    private static ItemStack extractOneFromNetwork(List<IItemHandler> handlers, ServerPlayer player, Item targetItem) {
        return RtsStorageTransfers.extractOneFromNetwork(handlers, player, targetItem);
    }

    private static ItemStack extractMatchingFromLinked(List<IItemHandler> handlers, Item targetItem, int limit) {
        return RtsStorageTransfers.extractMatchingFromLinked(handlers, targetItem, limit);
    }

    private static ItemStack extractMatchingFromLinked(List<IItemHandler> handlers, Item targetItem, ItemStack preferred, int limit) {
        return RtsStorageTransfers.extractMatchingFromLinked(handlers, targetItem, preferred, limit);
    }

    private static ItemStack extractMatchingFromPlayerMainInventory(ServerPlayer player, Item targetItem, int limit) {
        return RtsStorageTransfers.extractMatchingFromPlayerMainInventory(player, targetItem, limit);
    }

    private static ItemStack extractMatchingFromPlayerMainInventory(ServerPlayer player, Item targetItem, ItemStack preferred,
            int limit) {
        return RtsStorageTransfers.extractMatchingFromPlayerMainInventory(player, targetItem, preferred, limit);
    }

    private static ItemStack extractMatchingFromPlayerHotbarForQuickDrop(ServerPlayer player, Item targetItem, int limit) {
        return RtsStorageTransfers.extractMatchingFromPlayerHotbarForQuickDrop(player, targetItem, limit);
    }

    private static ItemStack extractMatchingFromPlayerHotbarForQuickDrop(ServerPlayer player, Item targetItem, ItemStack preferred,
            int limit) {
        return RtsStorageTransfers.extractMatchingFromPlayerHotbarForQuickDrop(player, targetItem, preferred, limit);
    }

    private static ItemStack extractMatchingFromPlayerSlot(ServerPlayer player, Item targetItem, ItemStack preferred, int slot,
            int limit) {
        return RtsStorageTransfers.extractMatchingFromPlayerSlot(player, targetItem, preferred, slot, limit);
    }

    private static ItemStack mergeExtractedStacks(ItemStack into, ItemStack addition) {
        return RtsStorageTransfers.mergeExtractedStacks(into, addition);
    }

    private static ItemStack moveLinkedStackIntoOpenMenu(ServerPlayer player, ItemStack stack) {
        return RtsStorageTransfers.moveLinkedStackIntoOpenMenu(player, stack);
    }

    private static ItemStack extractMatchingFromNetwork(List<IItemHandler> handlers, ServerPlayer player, Item targetItem,
            int limit) {
        return RtsStorageTransfers.extractMatchingFromNetwork(handlers, player, targetItem, limit);
    }

    private static ItemStack extractMatchingFromNetwork(List<IItemHandler> handlers, ServerPlayer player, Item targetItem,
            ItemStack preferred, int limit) {
        return RtsStorageTransfers.extractMatchingFromNetwork(handlers, player, targetItem, preferred, limit);
    }

    private static ItemStack extractMatchingFromQuickDropSources(List<IItemHandler> handlers, ServerPlayer player, Item targetItem,
            int limit) {
        return RtsStorageTransfers.extractMatchingFromQuickDropSources(handlers, player, targetItem, limit);
    }

    private static void refundToLinked(List<IItemHandler> handlers, ServerPlayer player, ItemStack stack) {
        RtsStorageTransfers.refundToLinked(handlers, player, stack);
    }

    private static ItemStack insertToHandler(IItemHandler handler, ItemStack stack) {
        return RtsStorageTransfers.insertToHandler(handler, stack);
    }

    private static ItemStack storeToLinkedOnly(List<IItemHandler> handlers, ItemStack stack) {
        return RtsStorageTransfers.storeToLinkedOnly(handlers, stack);
    }

    private static OverflowOutcome storeToLinkedWithFallback(List<IItemHandler> handlers, ServerPlayer player, ItemStack stack) {
        return RtsStorageTransfers.storeToLinkedWithFallback(handlers, player, stack);
    }

    private static OverflowOutcome storeToLinkedWithFallbackPreferExisting(List<IItemHandler> handlers, ServerPlayer player,
            ItemStack stack) {
        return RtsStorageTransfers.storeToLinkedWithFallbackPreferExisting(handlers, player, stack);
    }

    private static ItemStack moveToPlayerInventoryOnly(ServerPlayer player, ItemStack stack) {
        return RtsStorageTransfers.moveToPlayerInventoryOnly(player, stack);
    }

    private static int[] snapshotPlayerMatchingCounts(ServerPlayer player, ItemStack prototype) {
        return RtsStorageTransfers.snapshotPlayerMatchingCounts(player, prototype);
    }

    public static ItemStack[] snapshotCraftGridBlueprint(CraftingMenu menu) {
        return RtsStorageCrafting.snapshotCraftGridBlueprint(menu);
    }

    public static void refillCraftGridFromBlueprint(CraftingMenu menu, List<IItemHandler> handlers, ServerPlayer player,
            ItemStack[] blueprint, boolean fillAll, boolean includePlayerFallback) {
        RtsStorageCrafting.refillCraftGridFromBlueprint(menu, handlers, player, blueprint, fillAll, includePlayerFallback);
    }

    private static ItemStack extractOneMatchingPrototypeCombined(List<IItemHandler> handlers, ServerPlayer player, ItemStack prototype) {
        return RtsStorageTransfers.extractOneMatchingPrototypeCombined(handlers, player, prototype);
    }

    private static ItemStack extractOneMatchingPrototypeFromLinked(List<IItemHandler> handlers, ItemStack prototype) {
        return RtsStorageTransfers.extractOneMatchingPrototypeFromLinked(handlers, prototype);
    }

    private static ItemStack extractOneMatchingPrototypeFromPlayer(ServerPlayer player, ItemStack prototype) {
        return RtsStorageTransfers.extractOneMatchingPrototypeFromPlayer(player, prototype);
    }

    private static ItemStack drainPlayerInventoryDelta(ServerPlayer player, ItemStack prototype, int[] before) {
        return RtsStorageTransfers.drainPlayerInventoryDelta(player, prototype, before);
    }

    private static ItemStack insertToHandlerPreferExisting(IItemHandler handler, ItemStack stack) {
        return RtsStorageTransfers.insertToHandlerPreferExisting(handler, stack);
    }

    private static ItemStack storeToLinkedOnlyPreferExisting(List<IItemHandler> handlers, ItemStack stack) {
        return RtsStorageTransfers.storeToLinkedOnlyPreferExisting(handlers, stack);
    }

    private static ItemStack addToFunnelBuffer(Session session, ItemStack stack) {
        ItemStack remain = stack.copy();
        if (remain.isEmpty()) {
            return ItemStack.EMPTY;
        }

        for (ItemStack buffered : session.funnelBuffer) {
            if (remain.isEmpty()) {
                break;
            }
            if (buffered.isEmpty() || !ItemStack.isSameItemSameComponents(buffered, remain)) {
                continue;
            }
            int free = Math.max(0, buffered.getMaxStackSize() - buffered.getCount());
            if (free <= 0) {
                continue;
            }
            int move = Math.min(free, remain.getCount());
            buffered.grow(move);
            remain.shrink(move);
        }

        while (!remain.isEmpty() && session.funnelBuffer.size() < FUNNEL_BUFFER_MAX_STACKS) {
            int move = Math.min(remain.getCount(), remain.getMaxStackSize());
            ItemStack chunk = remain.copy();
            chunk.setCount(move);
            session.funnelBuffer.add(chunk);
            remain.shrink(move);
        }
        return remain;
    }

    private static boolean flushFunnelBufferToDestinations(List<IItemHandler> handlers, ServerPlayer player, Session session) {
        if (session.funnelBuffer.isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (int i = 0; i < session.funnelBuffer.size(); i++) {
            ItemStack buffered = session.funnelBuffer.get(i);
            if (buffered.isEmpty()) {
                session.funnelBuffer.remove(i);
                i--;
                changed = true;
                continue;
            }
            ItemStack remain = storeToLinkedOnlyPreferExisting(handlers, buffered);
            if (!remain.isEmpty()) {
                remain = moveToPlayerInventoryOnly(player, remain);
            }
            if (remain.isEmpty()) {
                session.funnelBuffer.remove(i);
                i--;
                changed = true;
            } else if (remain.getCount() != buffered.getCount()) {
                session.funnelBuffer.set(i, remain);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean absorbDropsForFunnel(ServerPlayer player, BlockPos target, List<IItemHandler> handlers, Session session) {
        AABB box = new AABB(target).inflate(FUNNEL_RADIUS);
        List<ItemEntity> drops = player.serverLevel().getEntitiesOfClass(
                ItemEntity.class,
                box,
                entity -> entity != null && entity.isAlive() && !entity.getItem().isEmpty());

        int processedEntities = 0;
        int processedItems = 0;
        boolean changed = false;
        for (ItemEntity drop : drops) {
            if (processedEntities >= FUNNEL_MAX_ENTITIES_PER_TICK || processedItems >= FUNNEL_MAX_ITEMS_PER_TICK) {
                break;
            }
            processedEntities++;

            ItemStack worldStack = drop.getItem();
            if (worldStack.isEmpty()) {
                continue;
            }
            int remainingBudget = FUNNEL_MAX_ITEMS_PER_TICK - processedItems;
            int iterations = Math.min(worldStack.getCount(), remainingBudget);
            for (int i = 0; i < iterations; i++) {
                ItemStack one = worldStack.copy();
                one.setCount(1);
                ItemStack remain = storeToLinkedOnlyPreferExisting(handlers, one);
                if (!remain.isEmpty()) {
                    remain = moveToPlayerInventoryOnly(player, remain);
                }
                if (!remain.isEmpty()) {
                    remain = addToFunnelBuffer(session, remain);
                }
                if (!remain.isEmpty()) {
                    break;
                }
                worldStack.shrink(1);
                processedItems++;
                changed = true;
                if (worldStack.isEmpty()) {
                    break;
                }
            }
            if (worldStack.isEmpty()) {
                drop.discard();
            } else {
                drop.setItem(worldStack);
            }
        }
        return changed;
    }

    private static void disableFunnelAndFlushBuffer(ServerPlayer player, Session session) {
        session.funnelEnabled = false;
        session.funnelTarget = null;
        session.funnelTickCooldown = 0;
        if (session.funnelBuffer.isEmpty()) {
            return;
        }
        sanitizeSessionDimension(player, session);
        List<LinkedHandler> linked = resolveLinkedHandlers(player, session);
        List<IItemHandler> handlers = new ArrayList<>(linked.size());
        for (LinkedHandler itemHandler : linked) {
            handlers.add(itemHandler.handler());
        }

        for (ItemStack buffered : session.funnelBuffer) {
            if (buffered.isEmpty()) {
                continue;
            }
            ItemStack remain = storeToLinkedOnlyPreferExisting(handlers, buffered);
            if (!remain.isEmpty()) {
                storeToLinkedWithFallback(handlers, player, remain);
            }
        }
        session.funnelBuffer.clear();
    }

    private static void tickFunnel(ServerPlayer player, Session session) {
        if (!session.funnelEnabled || session.mode != BuilderMode.FUNNEL) {
            return;
        }
        if (session.funnelTickCooldown > 0) {
            session.funnelTickCooldown--;
            return;
        }
        session.funnelTickCooldown = FUNNEL_TICK_INTERVAL - 1;

        sanitizeSessionDimension(player, session);
        if (session.funnelTarget == null) {
            return;
        }
        if (!canAccessWorldTarget(player, session.funnelTarget)) {
            return;
        }
        if (!RtsCameraManager.isWithinActionRadius(player, session.funnelTarget)) {
            return;
        }

        List<LinkedHandler> linked = resolveLinkedHandlers(player, session);
        List<IItemHandler> handlers = new ArrayList<>(linked.size());
        for (LinkedHandler linkedHandler : linked) {
            handlers.add(linkedHandler.handler());
        }

        boolean changed = flushFunnelBufferToDestinations(handlers, player, session);
        changed |= absorbDropsForFunnel(player, session.funnelTarget, handlers, session);
        if (changed) {
            markStorageViewDirty(player, session);
            runQuestDetect(player, session, false);
        }
    }

    private static boolean absorbNearbyDropsIntoLinked(ServerPlayer player, BlockPos pos, Session session) {
        if (!hasAnyStorage(player, session)) {
            return false;
        }
        List<LinkedHandler> linked = resolveLinkedHandlers(player, session);
        if (linked.isEmpty()) {
            return false;
        }
        List<IItemHandler> handlers = new ArrayList<>(linked.size());
        for (LinkedHandler handler : linked) {
            handlers.add(handler.handler());
        }

        AABB box = new AABB(pos).inflate(1.25D);
        List<ItemEntity> drops = player.serverLevel().getEntitiesOfClass(
                ItemEntity.class,
                box,
                entity -> entity != null && entity.isAlive() && !entity.getItem().isEmpty());
        boolean changed = false;
        for (ItemEntity drop : drops) {
            ItemStack original = drop.getItem();
            if (original.isEmpty()) {
                continue;
            }
            ItemStack remain = storeToLinkedOnly(handlers, original);
            if (remain.getCount() != original.getCount()) {
                changed = true;
            }
            if (remain.isEmpty()) {
                drop.discard();
            } else if (remain.getCount() != original.getCount()) {
                drop.setItem(remain);
            }
        }
        return changed;
    }

    private static void sendStorageOverflowHint(ServerPlayer player, String context, OverflowOutcome overflow) {
        RtsStorageTransfers.sendStorageOverflowHint(player, context, overflow);
    }

    /*
     * Thin recent-entry wrappers keep the existing crafting, transfer, mining,
     * and placement call sites stable. Later page-builder or transfer splits
     * can route callers directly to RtsStorageRecentEntries.
     */
    public static void recordCraftedOutput(ServerPlayer player, ItemStack crafted) {
        RtsStorageCrafting.recordCraftedOutput(player, SESSIONS.get(player.getUUID()), crafted);
    }

    public static void recordRecentItem(RtsStorageSession session, String itemId, byte kind, long amount) {
        RtsStorageRecentEntries.recordRecentItem(session, itemId, kind, amount);
    }

    public static void runQuestDetect(ServerPlayer player, RtsStorageSession session, boolean force) {
        if (player == null || session == null) {
            return;
        }
        if (!RtsFtbCompat.isDetectAvailable()) {
            if (force) {
                sendQuestDetectStatus(player, S2CRtsQuestDetectStatusPayload.PHASE_UNAVAILABLE, 0, 0, 0);
            }
            return;
        }
        long now = player.serverLevel().getGameTime();
        if (!force && now < session.nextQuestDetectTick) {
            return;
        }
        session.nextQuestDetectTick = now + QUEST_DETECT_COOLDOWN_TICKS;
        if (force) {
            sendQuestDetectStatus(player, S2CRtsQuestDetectStatusPayload.PHASE_STARTED, 0, 0, 0);
        }
        RtsFtbCompat.QuestDetectResult result = RtsFtbCompat.detectNow(player);
        if (force) {
            byte phase = result.error()
                    ? S2CRtsQuestDetectStatusPayload.PHASE_ERROR
                    : result.available()
                            ? S2CRtsQuestDetectStatusPayload.PHASE_COMPLETE
                            : S2CRtsQuestDetectStatusPayload.PHASE_UNAVAILABLE;
            sendQuestDetectStatus(
                    player,
                    phase,
                    result.scannedTasks(),
                    result.scannedTasks(),
                    result.newlyCompletedTasks());
        }
    }

    private static void sendQuestDetectStatus(ServerPlayer player, byte phase, int scannedTasks, int totalTasks, int completedTasks) {
        PacketDistributor.sendToPlayer(
                player,
                new S2CRtsQuestDetectStatusPayload(
                        phase,
                        Math.max(0, scannedTasks),
                        Math.max(0, totalTasks),
                        Math.max(0, completedTasks)));
    }

    public static void playRemoteUseSound(ServerPlayer player, ServerLevel level, Entity targetEntity, BlockPos pos,
            ItemStack stack) {
        if (player == null || level == null || stack == null || stack.isEmpty()) {
            return;
        }
        SoundEvent sound = selectRemoteUseSound(stack);
        if (sound == null) {
            return;
        }
        SoundSource source = targetEntity == null ? SoundSource.BLOCKS : SoundSource.PLAYERS;
        Vec3 at = targetEntity == null
                ? new Vec3(
                        pos == null ? player.getX() : pos.getX() + 0.5D,
                        pos == null ? player.getY() : pos.getY() + 0.5D,
                        pos == null ? player.getZ() : pos.getZ() + 0.5D)
                : targetEntity.getBoundingBox().getCenter();
        sendDirectSound(player, sound, source, at.x, at.y, at.z, 1.0F, 1.0F);
    }

    private static SoundEvent selectRemoteUseSound(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof HoeItem) {
            return SoundEvents.HOE_TILL;
        }
        if (item instanceof ShovelItem) {
            return SoundEvents.SHOVEL_FLATTEN;
        }
        if (item instanceof AxeItem) {
            return SoundEvents.AXE_STRIP;
        }
        if (item instanceof ShearsItem) {
            return SoundEvents.SHEEP_SHEAR;
        }
        if (item instanceof BoneMealItem) {
            return SoundEvents.BONE_MEAL_USE;
        }
        if (item == Items.HONEYCOMB) {
            return SoundEvents.HONEYCOMB_WAX_ON;
        }
        return null;
    }

    private static ItemStack createSoundStack(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId == null ? "" : itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(BuiltInRegistries.ITEM.get(id));
    }

    public static void sendDirectSound(ServerPlayer player, SoundEvent sound, SoundSource source, double x, double y,
            double z, float volume, float pitch) {
        if (player == null || sound == null || sound == SoundEvents.EMPTY) {
            return;
        }
        player.connection.send(new ClientboundSoundPacket(
                BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound),
                source,
                x,
                y,
                z,
                volume,
                pitch,
                player.getRandom().nextLong()));
    }

    private static InteractionResult interactWithToolSlot(ServerPlayer player, ServerLevel level, Entity targetEntity,
            BlockHitResult blockHit, Vec3 hit, int toolSlot, RayContext rayContext) {
        int slot = clampHotbarSlot(toolSlot);
        int previousSelected = player.getInventory().selected;
        Vec3 interactionPos = resolveInteractionPosition(targetEntity, blockHit, hit);
        return withTemporaryUseItemContext(
                player,
                interactionPos,
                hit,
                rayContext,
                REMOTE_POV_BLOCK_REACH,
                () -> {
            player.getInventory().selected = slot;
            try {
                if (targetEntity != null) {
                    return interactEntityWithMainHand(player, level, targetEntity, hit);
                }
                if (blockHit != null) {
                    // Alt interaction: prefer non-build interaction first.
                    InteractionResult primaryResult = withTemporaryShiftKey(player, false, () -> player.gameMode.useItemOn(
                            player,
                            level,
                            player.getMainHandItem(),
                            InteractionHand.MAIN_HAND,
                            blockHit));
                    if (primaryResult.consumesAction()) {
                        return primaryResult;
                    }
                    InteractionResult primaryUseResult = withTemporaryShiftKey(player, false, () -> player.gameMode.useItem(
                            player,
                            level,
                            player.getMainHandItem(),
                            InteractionHand.MAIN_HAND));
                    if (primaryUseResult.consumesAction()) {
                        return primaryUseResult;
                    }
                    InteractionResult secondaryResult = withTemporaryShiftKey(player, true, () -> player.gameMode.useItemOn(
                            player,
                            level,
                            player.getMainHandItem(),
                            InteractionHand.MAIN_HAND,
                            blockHit));
                    if (secondaryResult.consumesAction()) {
                        return secondaryResult;
                    }
                    return withTemporaryShiftKey(player, true, () -> player.gameMode.useItem(
                            player,
                            level,
                            player.getMainHandItem(),
                            InteractionHand.MAIN_HAND));
                }
                return InteractionResult.PASS;
            } finally {
                player.getInventory().selected = previousSelected;
            }
                });
    }

    private static InteractionResult useItemInAirWithToolSlot(ServerPlayer player, ServerLevel level, Vec3 hit,
            int toolSlot, RayContext rayContext) {
        int slot = clampHotbarSlot(toolSlot);
        int previousSelected = player.getInventory().selected;
        Vec3 fallback = hit == null ? player.getEyePosition() : hit;
        return withTemporaryUseItemContext(
                player,
                fallback,
                fallback,
                rayContext,
                REMOTE_POV_BLOCK_REACH,
                () -> {
            player.getInventory().selected = slot;
            try {
                return withTemporaryShiftKey(player, false, () -> player.gameMode.useItem(
                        player,
                        level,
                        player.getMainHandItem(),
                        InteractionHand.MAIN_HAND));
            } finally {
                player.getInventory().selected = previousSelected;
            }
                });
    }

    private static InteractionResult interactWithEmptyHand(ServerPlayer player, ServerLevel level, Entity targetEntity,
            BlockHitResult blockHit, Vec3 hit, RayContext rayContext) {
        Vec3 interactionPos = resolveInteractionPosition(targetEntity, blockHit, hit);
        return withTemporaryUseItemContext(
                player,
                interactionPos,
                hit,
                rayContext,
                REMOTE_POV_BLOCK_REACH,
                () -> {
                    if (targetEntity != null) {
                        return useItemOnEntityWithMainHand(player, level, ItemStack.EMPTY, targetEntity, hit).result();
                    }
                    if (blockHit != null) {
                        UseOnOutcome primary = useItemOnWithMainHand(player, level, ItemStack.EMPTY, blockHit, false);
                        if (primary.result().consumesAction()) {
                            return primary.result();
                        }
                    }
                    return useItemWithMainHand(player, level, ItemStack.EMPTY, false).result();
                });
    }

    private static InteractionResult interactWithLinkedItem(ServerPlayer player, ServerLevel level, Session session,
            Entity targetEntity, BlockHitResult blockHit, Vec3 hit, String itemId, RayContext rayContext) {
        if (itemId == null || itemId.isBlank() || !hasAnyStorage(player, session)) {
            return InteractionResult.PASS;
        }

        List<LinkedHandler> activeLinked = resolveLinkedHandlers(player, session);
        if (activeLinked.isEmpty()) {
            return InteractionResult.PASS;
        }

        List<IItemHandler> extractHandlers = RtsLinkedStorageResolver.itemHandlersForExtract(activeLinked);
        List<IItemHandler> insertHandlers = RtsLinkedStorageResolver.itemHandlersForInsert(activeLinked);

        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return InteractionResult.PASS;
        }

        ItemStack extracted = extractOneFromNetwork(extractHandlers, player, BuiltInRegistries.ITEM.get(id));
        if (extracted.isEmpty()) {
            return InteractionResult.PASS;
        }

        Vec3 interactionPos = resolveInteractionPosition(targetEntity, blockHit, hit);
        UseOnOutcome outcome = withTemporaryUseItemContext(
                player,
                interactionPos,
                hit,
                rayContext,
                REMOTE_POV_BLOCK_REACH,
                () -> {
            if (targetEntity != null) {
                return useItemOnEntityWithMainHand(player, level, extracted, targetEntity, hit);
            }
            // Alt interaction: normal item interaction first, build-like secondary interaction later.
            UseOnOutcome primaryOn = useItemOnWithMainHand(player, level, extracted, blockHit, false);
            if (primaryOn.result().consumesAction()) {
                return primaryOn;
            }
            ItemStack afterPrimaryOn = primaryOn.remainder().isEmpty() ? extracted.copy() : primaryOn.remainder().copy();

            UseOnOutcome primaryUse = useItemWithMainHand(player, level, afterPrimaryOn, false);
            if (primaryUse.result().consumesAction()) {
                return primaryUse;
            }
            ItemStack afterPrimaryUse = primaryUse.remainder().isEmpty() ? afterPrimaryOn : primaryUse.remainder().copy();

            UseOnOutcome secondaryOn = useItemOnWithMainHand(player, level, afterPrimaryUse, blockHit, true);
            if (secondaryOn.result().consumesAction()) {
                return secondaryOn;
            }
            ItemStack afterSecondaryOn = secondaryOn.remainder().isEmpty() ? afterPrimaryUse : secondaryOn.remainder().copy();
            return useItemWithMainHand(player, level, afterSecondaryOn, true);
                });
        if (!outcome.remainder().isEmpty()) {
            refundToLinked(insertHandlers, player, outcome.remainder());
        }
        return outcome.result();
    }

    private static InteractionResult interactEntityWithMainHand(ServerPlayer player, ServerLevel level, Entity entity,
            Vec3 hit) {
        InteractionResult result = player.interactOn(entity, InteractionHand.MAIN_HAND);
        if (!result.consumesAction()) {
            Vec3 localHit = hit.subtract(entity.position());
            result = entity.interactAt(player, localHit, InteractionHand.MAIN_HAND);
        }
        if (!result.consumesAction()) {
            result = player.gameMode.useItem(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND);
        }
        return result;
    }

    public static Vec3 resolveInteractionPosition(Entity targetEntity, BlockHitResult blockHit, Vec3 hit) {
        if (targetEntity != null) {
            Vec3 center = targetEntity.getBoundingBox().getCenter();
            Vec3 delta = center.subtract(hit);
            if (delta.lengthSqr() < 1.0e-6D) {
                delta = new Vec3(0.0D, 0.0D, 1.0D);
            }
            Vec3 at = center.subtract(delta.normalize().scale(1.8D));
            return new Vec3(at.x, at.y + 0.2D, at.z);
        }
        if (blockHit != null) {
            Vec3 n = Vec3.atLowerCornerOf(blockHit.getDirection().getNormal());
            Vec3 at = blockHit.getLocation().subtract(n.scale(2.2D));
            return new Vec3(at.x, at.y + 1.1D, at.z);
        }
        return hit;
    }

    private static float[] yawPitchTo(Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        double xz = Math.sqrt(d.x * d.x + d.z * d.z);
        float yaw = (float) (Math.toDegrees(Math.atan2(-d.x, d.z)));
        float pitch = (float) (-Math.toDegrees(Math.atan2(d.y, xz)));
        return new float[] { yaw, pitch };
    }

    public static RayContext parseRayContext(
            double originX, double originY, double originZ,
            double dirX, double dirY, double dirZ) {
        if (!Double.isFinite(originX) || !Double.isFinite(originY) || !Double.isFinite(originZ)
                || !Double.isFinite(dirX) || !Double.isFinite(dirY) || !Double.isFinite(dirZ)) {
            return null;
        }
        Vec3 dir = new Vec3(dirX, dirY, dirZ);
        if (dir.lengthSqr() < 1.0e-6D) {
            return null;
        }
        return new RayContext(new Vec3(originX, originY, originZ), dir.normalize());
    }

    public static <T> T withTemporaryUseItemContext(ServerPlayer player, Vec3 fallbackPos, Vec3 fallbackLookAt,
            double reach, Supplier<T> action) {
        return withTemporaryUseItemContext(player, fallbackPos, fallbackLookAt, null, reach, action);
    }

    public static <T> T withTemporaryUseItemContext(ServerPlayer player, Vec3 fallbackPos, Vec3 fallbackLookAt,
            RayContext rayContext, double reach, Supplier<T> action) {
        if (rayContext == null) {
            return withTemporaryInteractionPosition(player, fallbackPos, fallbackLookAt, action);
        }
        Vec3 rayDir = rayContext.dir();
        if (!Double.isFinite(rayDir.x) || !Double.isFinite(rayDir.y) || !Double.isFinite(rayDir.z)
                || rayDir.lengthSqr() < 1.0e-6D) {
            return withTemporaryInteractionPosition(player, fallbackPos, fallbackLookAt, action);
        }
        double clampedReach = Math.max(2.0D, Math.min(8.0D, reach));
        double offset = Math.max(0.5D, clampedReach - REMOTE_POV_EPSILON);
        Vec3 normalizedDir = rayDir.normalize();
        Vec3 virtualEye = fallbackLookAt.subtract(normalizedDir.scale(offset));
        double eyeHeight = player.getEyeHeight(player.getPose());
        Vec3 virtualFeet = new Vec3(virtualEye.x, virtualEye.y - eyeHeight, virtualEye.z);
        Vec3 lookAt = virtualEye.add(normalizedDir.scale(clampedReach));
        return withTemporaryInteractionPosition(player, virtualFeet, lookAt, action);
    }

    private static <T> T withTemporaryInteractionPosition(ServerPlayer player, Vec3 position, Vec3 lookAt, Supplier<T> action) {
        Vec3 prevPos = player.position();
        float prevYRot = player.getYRot();
        float prevXRot = player.getXRot();
        float prevYHeadRot = player.getYHeadRot();
        float prevYBodyRot = player.yBodyRot;

        player.setPos(position.x, position.y, position.z);
        double eyeHeight = player.getEyeHeight(player.getPose());
        Vec3 eyePos = new Vec3(position.x, position.y + eyeHeight, position.z);
        float[] look = yawPitchTo(eyePos, lookAt);
        player.setYRot(look[0]);
        player.setXRot(look[1]);
        player.setYHeadRot(look[0]);
        player.yBodyRot = look[0];
        try {
            return action.get();
        } finally {
            player.setPos(prevPos.x, prevPos.y, prevPos.z);
            player.setYRot(prevYRot);
            player.setXRot(prevXRot);
            player.setYHeadRot(prevYHeadRot);
            player.yBodyRot = prevYBodyRot;
        }
    }

    public static <T> T withTemporaryShiftKey(ServerPlayer player, boolean active, Supplier<T> action) {
        boolean previous = player.isShiftKeyDown();
        if (previous == active) {
            return action.get();
        }
        player.setShiftKeyDown(active);
        try {
            return action.get();
        } finally {
            player.setShiftKeyDown(previous);
        }
    }

    public static UseOnOutcome useItemOnWithMainHand(ServerPlayer player, ServerLevel level, ItemStack handStack,
            BlockHitResult hit, boolean forceSecondaryUse) {
        ItemStack previousMainHand = player.getMainHandItem().copy();
        player.setItemInHand(InteractionHand.MAIN_HAND, handStack);
        InteractionResult result;
        ItemStack remainder;
        try {
            result = withTemporaryShiftKey(player, forceSecondaryUse, () -> player.gameMode.useItemOn(
                    player,
                    level,
                    player.getMainHandItem(),
                    InteractionHand.MAIN_HAND,
                    hit));
        } finally {
            remainder = player.getMainHandItem().copy();
            player.setItemInHand(InteractionHand.MAIN_HAND, previousMainHand);
        }
        return new UseOnOutcome(result, remainder);
    }

    public static UseOnOutcome useItemWithMainHand(ServerPlayer player, ServerLevel level, ItemStack handStack,
            boolean forceSecondaryUse) {
        ItemStack previousMainHand = player.getMainHandItem().copy();
        player.setItemInHand(InteractionHand.MAIN_HAND, handStack);
        InteractionResult result;
        ItemStack remainder;
        try {
            result = withTemporaryShiftKey(player, forceSecondaryUse, () -> player.gameMode.useItem(
                    player,
                    level,
                    player.getMainHandItem(),
                    InteractionHand.MAIN_HAND));
        } finally {
            remainder = player.getMainHandItem().copy();
            player.setItemInHand(InteractionHand.MAIN_HAND, previousMainHand);
        }
        return new UseOnOutcome(result, remainder);
    }

    private static UseOnOutcome useItemOnEntityWithMainHand(ServerPlayer player, ServerLevel level, ItemStack handStack,
            Entity entity, Vec3 hit) {
        ItemStack previousMainHand = player.getMainHandItem().copy();
        player.setItemInHand(InteractionHand.MAIN_HAND, handStack);
        InteractionResult result;
        ItemStack remainder;
        try {
            result = interactEntityWithMainHand(player, level, entity, hit);
        } finally {
            remainder = player.getMainHandItem().copy();
            player.setItemInHand(InteractionHand.MAIN_HAND, previousMainHand);
        }
        return new UseOnOutcome(result, remainder);
    }

    public static void relaxOpenedMenuValidation(AbstractContainerMenu menu) {
        if (menu == null) {
            return;
        }
        boolean preserveContainerIdentity = menu instanceof ChestMenu;
        Class<?> type = menu.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    Class<?> fieldType = field.getType();

                    if (ContainerLevelAccess.class.isAssignableFrom(fieldType)) {
                        Object current = field.get(menu);
                        if (current instanceof ContainerLevelAccess access
                                && !(access instanceof RelaxedContainerLevelAccess)) {
                            field.set(menu, new RelaxedContainerLevelAccess(access));
                        } else if (current == null) {
                            field.set(menu, ContainerLevelAccess.NULL);
                        }
                        continue;
                    }

                    if (fieldType == Container.class && !preserveContainerIdentity) {
                        Object current = field.get(menu);
                        if (current instanceof Container delegate && !(delegate instanceof AlwaysValidContainer)) {
                            field.set(menu, new AlwaysValidContainer(delegate));
                        }
                    }
                } catch (ReflectiveOperationException ignored) {
                    // If a field is inaccessible/final in this runtime, keep default validation for that field.
                }
            }
            type = type.getSuperclass();
        }
    }

    public static void markRemoteMenuOpen(ServerPlayer player, RtsStorageSession session, AbstractContainerMenu menu, BlockPos pos) {
        if (menu == null) {
            return;
        }
        AbstractContainerMenu remoteMenu = RtsSophisticatedStorageCompat.wrapRemoteMenu(menu);
        if (player != null && player.containerMenu != remoteMenu) {
            player.containerMenu = remoteMenu;
        }
        if (session != null) {
            session.remoteMenuContainerId = remoteMenu.containerId;
            session.remoteMenuPos = pos == null ? null : pos.immutable();
        }
        relaxOpenedMenuValidation(remoteMenu);
        if (session != null && RtsSophisticatedStorageCompat.isSupportedRemoteMenu(remoteMenu)) {
            RtsSophisticatedStorageCompat.markServerRemoteMenu(player, remoteMenu);
        } else {
            RtsSophisticatedStorageCompat.clearServerRemoteMenu(player);
        }
        if (session != null && RtsRemoteMenuCompat.isSupportedRemoteMenu(remoteMenu)) {
            RtsRemoteMenuCompat.markServerRemoteMenu(player, remoteMenu);
        } else {
            RtsRemoteMenuCompat.clearServerRemoteMenu(player);
        }
    }

    private static void clearRemoteMenuValidation(ServerPlayer player, Session session) {
        if (session != null) {
            session.remoteMenuContainerId = -1;
            session.remoteMenuPos = null;
        }
        RtsSophisticatedStorageCompat.clearServerRemoteMenu(player);
        RtsRemoteMenuCompat.clearServerRemoteMenu(player);
    }

    private static void closeTrackedRemoteMenu(ServerPlayer player, Session session) {
        if (player == null || session == null || session.remoteMenuContainerId < 0) {
            return;
        }
        if (player.containerMenu != null
                && player.containerMenu.containerId == session.remoteMenuContainerId
                && !(player.containerMenu instanceof InventoryMenu)) {
            player.closeContainer();
        }
        session.remoteMenuContainerId = -1;
        session.remoteMenuPos = null;
    }

    public static void sendRemoteMenuOpenHint(ServerPlayer player, BlockPos pos) {
        if (player == null || pos == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new S2CRtsRemoteMenuHintPayload(pos));
        if (!(player.level() instanceof ServerLevel level) || !level.hasChunkAt(pos)) {
            return;
        }
        player.connection.send(new ClientboundBlockUpdatePacket(level, pos));
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null) {
            player.connection.send(ClientboundBlockEntityDataPacket.create(blockEntity));
        }
    }

    private static void refundItem(IItemHandler handler, ServerPlayer player, ItemStack stack) {
        RtsStorageTransfers.refundItem(handler, player, stack);
    }

    // Thin wrappers keep existing manager call sites stable while linked
    // resolution moves behind a reviewable dependency boundary.
    private static IItemHandler findHandler(ServerPlayer player, BlockPos pos) {
        return RtsLinkedCapabilities.findHandler(player, pos);
    }

    private static IItemHandler findLinkedItemHandler(ServerPlayer player, BlockPos pos) {
        return RtsLinkedCapabilities.findLinkedItemHandler(player, pos);
    }

    private static String resolveDisplayName(ServerLevel level, BlockPos pos) {
        return RtsLinkedStorageResolver.resolveDisplayName(level, pos);
    }

    private static List<LinkedHandler> resolveLinkedHandlers(ServerPlayer player, Session session) {
        return RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
    }

    private static List<LinkedFluidHandler> resolveLinkedFluidHandlers(ServerPlayer player, Session session) {
        return RtsLinkedStorageResolver.resolveLinkedFluidHandlers(player, session);
    }

    private static boolean canAccessWorldTarget(ServerPlayer player, BlockPos pos) {
        return RtsLinkedStorageResolver.canAccessWorldTarget(player, pos);
    }

    private static boolean canAccessFluidPlacementTarget(ServerPlayer player, BlockPos pos) {
        if (!RtsCameraManager.isActive(player) || pos == null) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        if (!level.hasChunkAt(pos)) {
            return false;
        }

        if (level.mayInteract(player, pos)
                && RtsCameraManager.isWithinActionRange(player, pos)
                && RtsProgressionManager.canAccessHomeRadius(player, pos)) {
            return true;
        }

        // Fluid placement can target air blocks; also validate supporting block below.
        if (!level.getBlockState(pos).isAir()) {
            return false;
        }
        BlockPos below = pos.below();
        if (!level.hasChunkAt(below)) {
            return false;
        }
        return level.mayInteract(player, below)
                && RtsCameraManager.isWithinActionRange(player, pos)
                && RtsProgressionManager.canAccessHomeRadius(player, pos);
    }

    private static boolean hasAnyStorage(ServerPlayer player, Session session) {
        return RtsLinkedStorageResolver.hasAnyStorage(player, session);
    }

    private static String buildAnyStorageSummary(ServerPlayer player, Session session) {
        return RtsLinkedStorageResolver.buildAnyStorageSummary(player, session);
    }

    private static void sanitizeSessionDimension(ServerPlayer player, Session session) {
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
    }

    private static String buildLinkedSummary(Session session) {
        return RtsLinkedStorageResolver.buildLinkedSummary(session);
    }

    public static byte sanitizeLinkMode(byte linkMode) {
        return RtsLinkedStorageResolver.sanitizeLinkMode(linkMode);
    }

    public static int sanitizeLinkedStoragePriority(int priority) {
        return Mth.clamp(priority, -9999, 9999);
    }

    private static boolean isExtractOnlyLink(Session session, LinkedStorageRef ref) {
        return RtsLinkedStorageResolver.isExtractOnlyLink(session, ref);
    }

    public static ResourceKey<Level> parseDimensionKey(String dimensionId) {
        if (dimensionId == null || dimensionId.isBlank()) {
            return null;
        }
        ResourceLocation key = ResourceLocation.tryParse(dimensionId);
        return key == null ? null : ResourceKey.create(Registries.DIMENSION, key);
    }

    private static long getHandlerReportedCount(IItemHandler handler, int slot, ItemStack stack) {
        return RtsStoragePageBuilder.getHandlerReportedCount(handler, slot, stack);
    }

    private static long internalFluidCapacityMb(ServerPlayer player) {
        return RtsStoragePageBuilder.internalFluidCapacityMb(player);
    }

    // Package-visible because extracted placement code needs the validated ray
    // origin/direction, while parsing and reach checks remain manager-owned.
    public record RayContext(Vec3 origin, Vec3 dir) {
    }

    public record UseOnOutcome(InteractionResult result, ItemStack remainder) {
    }

    private static final class AlwaysValidContainer implements Container {
        private final Container delegate;

        private AlwaysValidContainer(Container delegate) {
            this.delegate = delegate;
        }

        @Override
        public int getContainerSize() {
            return this.delegate.getContainerSize();
        }

        @Override
        public boolean isEmpty() {
            return this.delegate.isEmpty();
        }

        @Override
        public ItemStack getItem(int slot) {
            return this.delegate.getItem(slot);
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            return this.delegate.removeItem(slot, amount);
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return this.delegate.removeItemNoUpdate(slot);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            this.delegate.setItem(slot, stack);
        }

        @Override
        public int getMaxStackSize() {
            return this.delegate.getMaxStackSize();
        }

        @Override
        public void setChanged() {
            this.delegate.setChanged();
        }

        @Override
        public boolean stillValid(net.minecraft.world.entity.player.Player player) {
            return true;
        }

        @Override
        public void startOpen(net.minecraft.world.entity.player.Player player) {
            this.delegate.startOpen(player);
        }

        @Override
        public void stopOpen(net.minecraft.world.entity.player.Player player) {
            this.delegate.stopOpen(player);
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return this.delegate.canPlaceItem(slot, stack);
        }

        @Override
        public void clearContent() {
            this.delegate.clearContent();
        }
    }

    private static final class RelaxedContainerLevelAccess implements ContainerLevelAccess {
        private final ContainerLevelAccess delegate;

        private RelaxedContainerLevelAccess(ContainerLevelAccess delegate) {
            this.delegate = delegate == null ? ContainerLevelAccess.NULL : delegate;
        }

        @Override
        public <T> Optional<T> evaluate(BiFunction<Level, BlockPos, T> evaluator) {
            Optional<T> result = this.delegate.evaluate(evaluator);
            if (result.isPresent() && result.get() instanceof Boolean) {
                @SuppressWarnings("unchecked")
                T forcedTrue = (T) Boolean.TRUE;
                return Optional.of(forcedTrue);
            }
            return result;
        }

        @Override
        public void execute(BiConsumer<Level, BlockPos> consumer) {
            this.delegate.execute(consumer);
        }
    }

    private static final class PlacedRecoveryJob {
        private final BlockPos targetPos;
        private final Deque<ItemStack> stacks;

        private PlacedRecoveryJob(BlockPos targetPos, Deque<ItemStack> stacks) {
            this.targetPos = targetPos;
            this.stacks = stacks;
        }
    }

    // Keep the old nested name as the local owner handle while the state fields
    // live in RtsStorageSession. This makes the first split behavior-neutral:
    // call sites still ask RtsStorageManager for a Session, and later PRs can
    // move persistence, linked-handler lookup, or mining state one boundary at a time.
    private static final class Session extends RtsStorageSession {
        private final Deque<PlacedRecoveryJob> placedRecoveryJobs = new ArrayDeque<>();

        private Session() {
            super();
        }
    }
}
