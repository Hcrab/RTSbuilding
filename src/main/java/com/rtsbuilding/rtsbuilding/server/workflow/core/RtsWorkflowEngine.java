package com.rtsbuilding.rtsbuilding.server.workflow.core;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.workflow.event.RtsWorkflowEventBus;
import com.rtsbuilding.rtsbuilding.server.workflow.event.WorkflowEvent;
import com.rtsbuilding.rtsbuilding.server.workflow.event.WorkflowEventListener;
import com.rtsbuilding.rtsbuilding.server.workflow.event.WorkflowEventType;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowPriority;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import com.rtsbuilding.rtsbuilding.server.workflow.service.RtsWorkflowSlotManager;
import com.rtsbuilding.rtsbuilding.server.workflow.service.RtsWorkflowSyncService;
import com.rtsbuilding.rtsbuilding.server.workflow.service.WorkflowPersistenceService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Forge 1.20.1 鐨勫伐浣滄祦鏍稿績寮曟搸銆? *
 * <p>杩欏眰鍙礋璐ｂ€滀换鍔℃Ы浣嶃€佺敓鍛藉懆鏈熴€佽繘搴﹀悓姝モ€濄€傚叿浣撴寲鎺樸€佹斁缃€佽摑鍥鹃€昏緫
 * 浠嶇敱鐜版湁鏈嶅姟鎷ユ湁锛岄伩鍏嶄负浜嗚拷骞?UI 鐘舵€佽€屾敼鍙樼帺瀹舵墜鎰熴€?/p>
 */
public final class RtsWorkflowEngine implements IWorkflowEngine {
    private static final RtsWorkflowEngine INSTANCE = new RtsWorkflowEngine();

    private final Map<UUID, Map<ResourceKey<Level>, RtsWorkflowSlotManager>> playerSlots = new ConcurrentHashMap<>();
    private final Map<UUID, ServerPlayer> playerRefs = new ConcurrentHashMap<>();
    private final RtsWorkflowEventBus eventBus = new RtsWorkflowEventBus();
    private final RtsWorkflowSyncService syncService = new RtsWorkflowSyncService();

    private RtsWorkflowEngine() {
    }

    public static RtsWorkflowEngine getInstance() {
        return INSTANCE;
    }


    @Override
    public Optional<RtsWorkflowToken> start(ServerPlayer player, RtsWorkflowType type, RtsWorkflowPriority priority, int totalBlocks) {
        if (player == null || type == null) {
            return Optional.empty();
        }
        RtsWorkflowSlotManager slots = getOrCreateSlots(player);
        if (slots.isFull()) {
            RtsWorkflowEntry replaced = slots.removeOldestReplaceableEntry();
            if (replaced != null) {
                fireEvent(WorkflowEventType.CANCELLED, player.getUUID(), replaced.id(), replaced);
                RtsbuildingMod.LOGGER.info("[Workflow] {} auto-replaced workflow #{}: {}",
                        player.getGameProfile().getName(), replaced.id(), replaced.type());
            }
        }
        RtsWorkflowEntry entry = slots.addEntry(priority == null ? RtsWorkflowPriority.NORMAL : priority);
        if (entry == null) {
            player.displayClientMessage(
                    Component.translatable("message.rtsbuilding.workflow.full_protected"), true);
            RtsbuildingMod.LOGGER.warn("[Workflow] {} workflow slots are full", player.getGameProfile().getName());
            return Optional.empty();
        }
        entry.setType(type);
        entry.setTotalBlocks(totalBlocks);
        this.playerRefs.put(player.getUUID(), player);
        fireEvent(WorkflowEventType.STARTED, player.getUUID(), entry.id(), entry);
        this.syncService.notifyPlayer(player, slots);
        return Optional.of(new RtsWorkflowToken(player.getUUID(), entry.id(), player.level().dimension(), this));
    }

    @Override
    public Optional<RtsWorkflowToken> from(ServerPlayer player, int entryId) {
        if (player == null) {
            return Optional.empty();
        }
        this.playerRefs.put(player.getUUID(), player);
        ResourceKey<Level> dimension = player.level().dimension();
        RtsWorkflowEntry entry = findEntry(player.getUUID(), dimension, entryId);
        if (entry == null) {
            return Optional.empty();
        }
        return Optional.of(new RtsWorkflowToken(player.getUUID(), entryId, dimension, this));
    }

    @Override
    public Optional<RtsWorkflowToken> lastActive(ServerPlayer player) {
        if (player == null) {
            return Optional.empty();
        }
        this.playerRefs.put(player.getUUID(), player);
        RtsWorkflowSlotManager slots = getSlots(player.getUUID(), player.level().dimension());
        RtsWorkflowEntry entry = slots == null ? null : slots.lastActive();
        if (entry == null) {
            return Optional.empty();
        }
        return Optional.of(new RtsWorkflowToken(player.getUUID(), entry.id(), player.level().dimension(), this));
    }

    @Override
    public void addListener(WorkflowEventListener listener) {
        this.eventBus.addListener(listener);
    }

    @Override
    public void removeListener(WorkflowEventListener listener) {
        this.eventBus.removeListener(listener);
    }

    @Override
    public RtsWorkflowStatus getProgress(RtsWorkflowToken token) {
        return token == null ? RtsWorkflowStatus.idle() : token.getProgress();
    }

    @Override
    public RtsWorkflowStatus getProgress(ServerPlayer player, int entryId) {
        if (player == null) {
            return RtsWorkflowStatus.idle();
        }
        RtsWorkflowEntry entry = findEntry(player.getUUID(), player.level().dimension(), entryId);
        return entry == null ? RtsWorkflowStatus.idle() : entry.snapshot();
    }

    @Override
    public List<RtsWorkflowStatus> getAllProgress(ServerPlayer player) {
        if (player == null) {
            return List.of();
        }
        RtsWorkflowSlotManager slots = getSlots(player.getUUID(), player.level().dimension());
        if (slots == null) {
            return List.of();
        }
        return slots.occupiedEntries().stream().map(RtsWorkflowEntry::snapshot).toList();
    }

    @Override
    public boolean hasActiveWorkflow(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        RtsWorkflowSlotManager slots = getSlots(player.getUUID(), player.level().dimension());
        return slots != null && slots.hasActiveWorkflow();
    }

    @Override
    public int activeWorkflowCount(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        RtsWorkflowSlotManager slots = getSlots(player.getUUID(), player.level().dimension());
        return slots == null ? 0 : slots.activeCount();
    }

    @Override
    public int occupiedSlotCount(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        RtsWorkflowSlotManager slots = getSlots(player.getUUID(), player.level().dimension());
        return slots == null ? 0 : slots.occupiedCount();
    }

    @Override
    public boolean isFull(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        RtsWorkflowSlotManager slots = getSlots(player.getUUID(), player.level().dimension());
        return slots != null && slots.isFull();
    }

    @Override
    public void setWorkflowExtraData(ServerPlayer player, int entryId, @Nullable CompoundTag data) {
        if (player == null) {
            return;
        }
        RtsWorkflowEntry entry = findEntry(player.getUUID(), player.level().dimension(), entryId);
        if (entry != null) {
            entry.setExtraData(data);
        }
    }

    @Override
    public @Nullable CompoundTag getWorkflowExtraData(ServerPlayer player, int entryId) {
        if (player == null) {
            return null;
        }
        RtsWorkflowEntry entry = findEntry(player.getUUID(), player.level().dimension(), entryId);
        return entry == null ? null : entry.getExtraData();
    }

    @Override
    public void deleteWorkflow(ServerPlayer player, int entryId) {
        if (player == null) {
            return;
        }
        RtsWorkflowSlotManager slots = getSlots(player.getUUID(), player.level().dimension());
        if (slots == null) {
            return;
        }
        RtsWorkflowEntry entry = slots.findEntryById(entryId);
        if (entry == null || !entry.isOccupied()) {
            return;
        }
        fireEvent(WorkflowEventType.CANCELLED, player.getUUID(), entryId, entry);
        slots.removeEntryById(entryId);
        if (slots.occupiedCount() > 0) {
            this.syncService.notifyPlayer(player, slots);
        } else {
            this.syncService.sendIdle(player);
        }
    }

    @Override
    public void setWorkflowProtected(ServerPlayer player, int entryId, boolean protectedWorkflow) {
        if (player == null) {
            return;
        }
        RtsWorkflowSlotManager slots = getSlots(player.getUUID(), player.level().dimension());
        if (slots == null) {
            return;
        }
        RtsWorkflowEntry entry = slots.findEntryById(entryId);
        if (entry == null || !entry.isOccupied()) {
            return;
        }
        entry.setProtectedWorkflow(protectedWorkflow);
        this.syncService.notifyPlayer(player, slots);
        player.displayClientMessage(Component.translatable(protectedWorkflow
                ? "message.rtsbuilding.workflow.protected"
                : "message.rtsbuilding.workflow.replaceable"), true);
    }

    @Override
    public void cancelAll(ServerPlayer player) {
        if (player == null) {
            return;
        }
        RtsWorkflowSlotManager slots = getSlots(player.getUUID(), player.level().dimension());
        if (slots == null) {
            return;
        }
        for (RtsWorkflowEntry entry : slots.occupiedEntries()) {
            fireEvent(WorkflowEventType.CANCELLED, player.getUUID(), entry.id(), entry);
        }
        slots.clear();
        this.syncService.sendIdle(player);
    }

    public void pauseAllActive(UUID playerId, boolean notify) {
        Map<ResourceKey<Level>, RtsWorkflowSlotManager> dimensions = this.playerSlots.get(playerId);
        if (dimensions == null) {
            return;
        }
        for (Map.Entry<ResourceKey<Level>, RtsWorkflowSlotManager> dimensionEntry : dimensions.entrySet()) {
            RtsWorkflowSlotManager slots = dimensionEntry.getValue();
            boolean changed = false;
            for (RtsWorkflowEntry entry : slots.occupiedEntries()) {
                if (!entry.suspended() && !entry.paused()) {
                    entry.setPaused(true);
                    fireEvent(WorkflowEventType.PAUSED, playerId, entry.id(), entry);
                    changed = true;
                }
            }
            ServerPlayer player = this.playerRefs.get(playerId);
            if (changed && notify && player != null && player.level().dimension().equals(dimensionEntry.getKey())) {
                this.syncService.notifyPlayer(player, slots);
            }
        }
    }

    @Override
    public void clearPlayerData(UUID playerId) {
        if (playerId == null) {
            return;
        }
        this.playerSlots.remove(playerId);
        this.playerRefs.remove(playerId);
    }

    @Override
    public void clearAllData() {
        this.playerSlots.clear();
        this.playerRefs.clear();
    }

    /**
     * 鐜╁绂荤嚎鏃跺彧涓㈠純杩愯鏈?player 寮曠敤锛屼繚鐣欏彲閲嶆柊鍚屾/鍐欑洏鐨勪换鍔℃Ы浣嶃€?     */
    public void forgetPlayerReference(UUID playerId) {
        if (playerId != null) {
            this.playerRefs.remove(playerId);
        }
    }

    public void saveAll(MinecraftServer server) {
        WorkflowPersistenceService.getInstance().saveAll(server, this.playerSlots);
    }

    public void loadPlayerFromStore(MinecraftServer server, ServerPlayer player) {
        if (server == null || player == null) {
            return;
        }
        UUID playerId = player.getUUID();
        Map<ResourceKey<Level>, RtsWorkflowSlotManager> loaded =
                WorkflowPersistenceService.getInstance().loadPlayerFromStore(server, playerId);
        if (loaded.isEmpty()) {
            return;
        }

        Map<ResourceKey<Level>, RtsWorkflowSlotManager> dimensions = this.playerSlots
                .computeIfAbsent(playerId, ignored -> new ConcurrentHashMap<>());
        loaded.forEach(dimensions::putIfAbsent);
        this.playerRefs.put(playerId, player);

        RtsWorkflowSlotManager currentSlots = getSlots(playerId, player.level().dimension());
        if (currentSlots != null && currentSlots.occupiedCount() > 0) {
            this.syncService.notifyPlayer(player, currentSlots);
        }
    }

    @Override
    public boolean isEntryPaused(UUID playerId, ResourceKey<Level> dimension, int entryId) {
        RtsWorkflowEntry entry = findEntry(playerId, dimension, entryId);
        return entry != null && entry.paused();
    }

    @Override
    public boolean isEntrySuspended(UUID playerId, ResourceKey<Level> dimension, int entryId) {
        RtsWorkflowEntry entry = findEntry(playerId, dimension, entryId);
        return entry != null && entry.suspended();
    }

    public void notifyPlayer(ServerPlayer player) {
        if (player == null) {
            return;
        }
        RtsWorkflowSlotManager slots = getSlots(player.getUUID(), player.level().dimension());
        if (slots == null) {
            this.syncService.sendIdle(player);
            return;
        }
        this.syncService.notifyPlayer(player, slots);
    }

    @Nullable
    RtsWorkflowEntry findEntry(UUID playerId, ResourceKey<Level> dimension, int entryId) {
        RtsWorkflowSlotManager slots = getSlots(playerId, dimension);
        return slots == null ? null : slots.findEntryById(entryId);
    }

    /**
     * 鍏紑缁?pipeline / tick registry 浣跨敤鐨勫彧璇绘煡鎵惧叆鍙ｃ€?     */
    @Nullable
    public RtsWorkflowEntry findEntryByPlayer(ServerPlayer player, int entryId) {
        if (player == null) {
            return null;
        }
        return findEntry(player.getUUID(), player.level().dimension(), entryId);
    }

    /**
     * 宸茬煡鐜╁ UUID 鍜岀淮搴︽椂鐨勫彧璇绘煡鎵惧叆鍙ｏ紝閬垮厤 tick 闃舵鍙嶅鏋勯€?token銆?     */
    @Nullable
    public RtsWorkflowEntry findEntryByPlayer(UUID playerId, ResourceKey<Level> dimension, int entryId) {
        return findEntry(playerId, dimension, entryId);
    }

    void removeEntry(UUID playerId, ResourceKey<Level> dimension, int entryId) {
        RtsWorkflowSlotManager slots = getSlots(playerId, dimension);
        if (slots == null || !slots.removeEntryById(entryId)) {
            return;
        }
        ServerPlayer player = this.playerRefs.get(playerId);
        if (player != null && player.level().dimension().equals(dimension)) {
            this.syncService.notifyPlayer(player, slots);
        }
    }

    void notifyPlayer(UUID playerId, ResourceKey<Level> dimension) {
        RtsWorkflowSlotManager slots = getSlots(playerId, dimension);
        ServerPlayer player = this.playerRefs.get(playerId);
        if (slots != null && player != null && player.level().dimension().equals(dimension)) {
            this.syncService.notifyPlayer(player, slots);
        }
    }

    void fireEvent(WorkflowEventType type, UUID playerId, int entryId, RtsWorkflowEntry entry) {
        this.eventBus.fire(new WorkflowEvent(type, playerId, entryId, entry.snapshot()));
    }

    /**
     * pipeline 鍚屾闃舵鍙渶瑕侀€氱煡鐩戝惉鍣紝涓嶅簲璇ョЩ闄ゅ伐浣滄祦鏉＄洰銆?     */
    public void firePipelineEvent(ServerPlayer player, int entryId, WorkflowEventType type) {
        if (player == null || type == null) {
            return;
        }
        RtsWorkflowEntry entry = findEntry(player.getUUID(), player.level().dimension(), entryId);
        if (entry != null) {
            fireEvent(type, player.getUUID(), entryId, entry);
        }
    }

    private RtsWorkflowSlotManager getOrCreateSlots(ServerPlayer player) {
        this.playerRefs.put(player.getUUID(), player);
        return this.playerSlots
                .computeIfAbsent(player.getUUID(), ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(player.level().dimension(), ignored -> new RtsWorkflowSlotManager());
    }

    private @Nullable RtsWorkflowSlotManager getSlots(UUID playerId, ResourceKey<Level> dimension) {
        if (playerId == null || dimension == null) {
            return null;
        }
        Map<ResourceKey<Level>, RtsWorkflowSlotManager> dimensions = this.playerSlots.get(playerId);
        return dimensions == null ? null : dimensions.get(dimension);
    }
}
