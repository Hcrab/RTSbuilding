package com.rtsbuilding.rtsbuilding.server.storage;


import com.rtsbuilding.rtsbuilding.server.storage.placement.RtsStoragePlacement;
import com.rtsbuilding.rtsbuilding.server.RtsStorageManager;


import com.rtsbuilding.rtsbuilding.common.BuilderMode;
import com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort;
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
import net.minecraft.world.item.ItemStack;
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
public class RtsStorageSession {
    /*
     * Player-facing RTS mode and linked storage selection. The refs are the
     * stable identity for a linked block, while names and modes are cached
     * presentation/permission data derived by manager-side scans.
     */
    public BuilderMode mode = BuilderMode.INTERACT;
    public final List<LinkedStorageRef> linkedStorages = new ArrayList<>();
    public final Map<LinkedStorageRef, String> linkedNames = new HashMap<>();
    public final Map<LinkedStorageRef, Byte> linkedModes = new HashMap<>();

    /*
     * Storage browser state. These fields describe how the player is viewing
     * the storage contents; they are not authoritative item counts.
     */
    public int page;
    public int pageSize = RtsStoragePageBuilder.DEFAULT_PAGE_SIZE;
    public String search = "";
    public String category = "all";
    public RtsStorageSort sort = RtsStorageSort.QUANTITY;
    public boolean ascending = false;
    public boolean pinyinSearchEnabled;
    public final Set<String> localizedSearchMatches = new HashSet<>();
    public boolean storageViewDirty;

    /*
     * Crafting browser state. The requested count defaults to the same batch
     * size used by the server packets so shift-import/craft preview behavior
     * stays identical after extraction.
     */
    String craftSearch = "";
    boolean craftShowUnavailable;
    int craftRequestedCount = RtsStorageManager.CRAFTABLE_BATCH_SIZE;
    public boolean craftPinyinSearchEnabled;
    public final Set<String> craftLocalizedSearchMatches = new HashSet<>();

    /*
     * Session toggles and virtual fluid storage. The Forge line intentionally
     * stays lean here: NeoForge-only BD network cache fields are not mirrored in
     * this branch. The manager still owns every mutation path.
     */
    public boolean autoStoreMinedDrops = true;
    public final Map<String, Long> internalFluidMb = new HashMap<>();

    /*
     * Drop funnel runtime state. Buffer contents are temporary server-side
     * work-in-progress, not saved storage contents.
     */
    public boolean funnelEnabled;
    public BlockPos funnelTarget;
    public int funnelTickCooldown;
    public final List<ItemStack> funnelBuffer = new ArrayList<>();

    /*
     * Remote mining and ultimine state. ToolLease lives with
     * RtsStorageMining because returning NBT-heavy tools safely is part of the
     * mining state machine, not passive session storage.
     */
    BlockPos miningPos;
    public int remoteMenuContainerId = -1;
    public BlockPos remoteMenuPos;
    final Deque<BlockPos> ultimineTargets = new ArrayDeque<>();
    BlockPos ultimineProgressPos;
    int ultimineTotalTargets;
    int ultimineProcessedTargets;
    boolean ultimineAbsorbedDrops;
    Direction miningFace = Direction.DOWN;
    int miningToolSlot;
    RtsStorageMining.ToolLease miningToolLease = RtsStorageMining.ToolLease.empty();
    float miningProgress;
    int miningStage = -1;
    long deferredStorageRefreshTick = -1L;
    public long nextQuestDetectTick;

    /*
     * Quick-build audio and queued placement state. The job type lives in
     * RtsStoragePlacement because that service owns world block placement and
     * the batch cursor, while this session only stores the pending queue.
     */
    public int quickBuildSoundPlacedCount;
    public long quickBuildCompletionSoundTick = -1L;
    public long lastQuickBuildPlaceSoundTick = Long.MIN_VALUE;
    public double quickBuildSoundX;
    public double quickBuildSoundY;
    public double quickBuildSoundZ;
    public final Deque<RtsStoragePlacement.PlaceBatchJob> placeBatchJobs = new ArrayDeque<>();

    /*
     * UI memory: recent entries, quick slots, and external GUI bindings. These
     * arrays use manager-owned constants so client packet validation and server
     * session storage stay locked to the same slot counts.
     */
    public final Deque<RecentEntry> recentEntries = new ArrayDeque<>();
    public final String[] quickSlotItemIds = new String[RtsStorageManager.QUICK_SLOT_COUNT];
    public final ItemStack[] quickSlotPreviews = new ItemStack[RtsStorageManager.QUICK_SLOT_COUNT];
    final GuiBinding[] guiBindings = new GuiBinding[RtsStorageManager.GUI_BINDING_SLOT_COUNT];

    public RtsStorageSession() {
        Arrays.fill(this.quickSlotItemIds, "");
        Arrays.fill(this.quickSlotPreviews, ItemStack.EMPTY);
    }
}
