package com.rtsbuilding.rtsbuilding.server;

import com.rtsbuilding.rtsbuilding.common.BuilderMode;
import com.rtsbuilding.rtsbuilding.network.RtsStorageSort;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

/**
 * Mutable per-player state for the RTS storage screen and remote storage tools.
 *
 * <p>This class owns only the session data that used to sit at the bottom of
 * {@link RtsStorageManager}: selected storage links, browser filters, quick
 * slots, remote mining progress, pending placement jobs, and short-lived UI
 * caches. It deliberately does not query block entities, resolve capabilities,
 * serialize NBT, mutate inventories, send packets, or decide gameplay rules.
 * Those behaviors stay in {@link RtsStorageManager} for this first PR so the
 * player's visible flow remains unchanged.
 *
 * <p>The split exists to give future storage work a clear landing zone. When a
 * later PR moves persistence, linked handler discovery, mining leases, or
 * quick-build batching, it should be able to use this file as the map of what
 * state belongs to the player's RTS storage session and what still belongs to
 * the manager/state machine.
 */
class RtsStorageSession {
    /*
     * Player-facing RTS mode and linked storage selection. The refs are the
     * stable identity for a linked block, while names and modes are cached
     * presentation/permission data derived by manager-side scans.
     */
    BuilderMode mode = BuilderMode.INTERACT;
    final List<LinkedStorageRef> linkedStorages = new ArrayList<>();
    final Map<LinkedStorageRef, String> linkedNames = new HashMap<>();
    final Map<LinkedStorageRef, Byte> linkedModes = new HashMap<>();

    /*
     * Storage browser state. These fields describe how the player is viewing
     * the storage contents; they are not authoritative item counts.
     */
    int page;
    String search = "";
    String category = "all";
    RtsStorageSort sort = RtsStorageSort.QUANTITY;
    boolean ascending = false;
    boolean pinyinSearchEnabled;
    final Set<String> localizedSearchMatches = new HashSet<>();

    /*
     * Crafting browser state. The requested count defaults to the same batch
     * size used by the server packets so shift-import/craft preview behavior
     * stays identical after extraction.
     */
    String craftSearch = "";
    boolean craftShowUnavailable;
    int craftRequestedCount = RtsStorageManager.CRAFTABLE_BATCH_SIZE;
    boolean craftPinyinSearchEnabled;
    final Set<String> craftLocalizedSearchMatches = new HashSet<>();

    /*
     * Session toggles and virtual fluid storage. The Forge line intentionally
     * stays lean here: NeoForge-only BD network cache fields are not mirrored in
     * this branch. The manager still owns every mutation path.
     */
    boolean autoStoreMinedDrops = true;
    final Map<String, Long> internalFluidMb = new HashMap<>();

    /*
     * Drop funnel runtime state. Buffer contents are temporary server-side
     * work-in-progress, not saved storage contents.
     */
    boolean funnelEnabled;
    BlockPos funnelTarget;
    int funnelTickCooldown;
    final List<ItemStack> funnelBuffer = new ArrayList<>();

    /*
     * Remote mining and ultimine state. ToolLease remains nested in
     * RtsStorageManager because returning NBT-heavy tools safely is gameplay
     * behavior, not passive session storage.
     */
    BlockPos miningPos;
    int remoteMenuContainerId = -1;
    BlockPos remoteMenuPos;
    final Deque<BlockPos> ultimineTargets = new ArrayDeque<>();
    BlockPos ultimineProgressPos;
    int ultimineTotalTargets;
    int ultimineProcessedTargets;
    boolean ultimineAbsorbedDrops;
    Direction miningFace = Direction.DOWN;
    int miningToolSlot;
    RtsStorageManager.ToolLease miningToolLease = RtsStorageManager.ToolLease.empty();
    float miningProgress;
    int miningStage = -1;
    long nextQuestDetectTick;

    /*
     * Quick-build audio and queued placement state. The job type lives in
     * RtsStoragePlacement because that service owns world block placement and
     * the batch cursor, while this session only stores the pending queue.
     */
    int quickBuildSoundPlacedCount;
    long quickBuildCompletionSoundTick = -1L;
    long lastQuickBuildPlaceSoundTick = Long.MIN_VALUE;
    double quickBuildSoundX;
    double quickBuildSoundY;
    double quickBuildSoundZ;
    final Deque<RtsStoragePlacement.PlaceBatchJob> placeBatchJobs = new ArrayDeque<>();

    /*
     * UI memory: recent entries, quick slots, and external GUI bindings. These
     * arrays use manager-owned constants so client packet validation and server
     * session storage stay locked to the same slot counts.
     */
    final Deque<RecentEntry> recentEntries = new ArrayDeque<>();
    final String[] quickSlotItemIds = new String[RtsStorageManager.QUICK_SLOT_COUNT];
    final GuiBinding[] guiBindings = new GuiBinding[RtsStorageManager.GUI_BINDING_SLOT_COUNT];

    RtsStorageSession() {
        Arrays.fill(this.quickSlotItemIds, "");
    }
}

/**
 * Stable identity for a linked storage block.
 *
 * <p>The dimension is part of the key so Nether/Overworld blocks at the same
 * coordinates never collide. This record should stay tiny: permission checks,
 * labels, and capability lookup belong outside the identity object.
 */
record LinkedStorageRef(ResourceKey<Level> dimension, BlockPos pos) {
}

/**
 * Snapshot used by the UI's recent list.
 *
 * <p>It records what the player recently saw or moved; it is not a source of
 * truth for item or fluid storage counts.
 */
record RecentEntry(String id, long amount, long capacity, byte kind) {
}

/**
 * Player-defined shortcut to reopen an external block GUI from RTS mode.
 *
 * <p>The binding stores the target block and display metadata only. Validation,
 * menu opening, and face-specific interaction behavior stay in the manager.
 */
record GuiBinding(BlockPos pos, ResourceKey<Level> dimension, String label, String itemId, Direction face) {
}
