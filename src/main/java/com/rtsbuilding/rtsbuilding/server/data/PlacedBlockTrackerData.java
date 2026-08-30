package com.rtsbuilding.rtsbuilding.server.data;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 服务端世界中的已放置方块凭据仓库。
 *
 * <p>凭据只属于 RTS 自己的 {@link SavedData}，不向方块状态、方块实体或第三方
 * 物品写入 marker。V2 记录 owner、实际方块注册 ID 和持久化 generation；旧版只有
 * 坐标的记录会以明确的 {@link CredentialKind#LEGACY} 形式加载，并在访问已加载位置
 * 时绑定当时的方块注册 ID。</p>
 *
 * <p>V2 使用位置、方块和 owner palette，避免大型蓝图为每个位置重复保存字符串和 UUID。
 * 所有输入数组、palette 索引和记录数量都在分配 map 前校验；损坏 payload 会 fail closed
 * 为空仓库，不会把 shadow 坐标再次误读成 legacy。</p>
 */
public final class PlacedBlockTrackerData extends SavedData {
    private static final String DATA_NAME = "rtsbuilding_placed_blocks";
    private static final String KEY_SCHEMA = "schema";
    private static final String KEY_PLACED_SHADOW = "placed";
    private static final String KEY_POSITIONS = "positions";
    private static final String KEY_BLOCK_PALETTE = "block_palette";
    private static final String KEY_BLOCK_INDICES = "block_indices";
    private static final String KEY_OWNER_PALETTE = "owner_palette";
    private static final String KEY_OWNER_INDICES = "owner_indices";
    private static final String KEY_GENERATIONS = "generations";
    private static final String KEY_KINDS = "kinds";
    private static final String KEY_NEXT_GENERATION = "next_generation";

    /** V2 的当前格式；schema 缺失时才进入 V1 placed shadow 兼容路径。 */
    public static final int SCHEMA_VERSION = 2;
    /** 防止损坏存档通过数组长度迫使服务端一次性分配无限对象。 */
    public static final int MAX_TRACKED_ENTRIES = 1_000_000;
    private static final int MAX_PALETTE_ENTRIES = 65_536;
    private static final int MAX_BLOCK_ID_LENGTH = 256;
    private static final ResourceLocation AIR_ID =
            ResourceLocation.fromNamespaceAndPath("minecraft", "air");

    private static final Factory<PlacedBlockTrackerData> FACTORY = new Factory<>(
            PlacedBlockTrackerData::new,
            PlacedBlockTrackerData::load);

    private final Long2ObjectOpenHashMap<CredentialSnapshot> credentials;
    private long nextGeneration;

    PlacedBlockTrackerData() {
        this.credentials = new Long2ObjectOpenHashMap<>();
        this.nextGeneration = 1L;
    }

    /** 获取指定维度的凭据仓库；SavedData 本身按维度隔离。 */
    public static PlacedBlockTrackerData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    /**
     * 在实际方块已经落地后写入一条新的 V2 凭据。
     *
     * <p>block ID 从世界最终状态读取，而不是从请求物品 ID 推断，因而覆盖、旋转、
     * 状态预设和第三方放置回调都不会把错误的方块类型写入 tracker。</p>
     */
    public @Nullable CredentialSnapshot markPlaced(BlockPos pos, UUID owner, BlockState actualState) {
        if (actualState == null || actualState.isAir()) {
            clear(pos);
            return null;
        }
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(actualState.getBlock());
        return markPlaced(pos, owner, blockId);
    }

    /** 在调用方已经解析出实际注册 ID 时写入 V2 凭据。 */
    public @Nullable CredentialSnapshot markPlaced(
            BlockPos pos, UUID owner, @Nullable ResourceLocation blockId) {
        if (pos == null) return null;
        if (blockId == null || AIR_ID.equals(blockId)) {
            clear(pos);
            return null;
        }
        if (owner == null) {
            return null;
        }
        long packed = pos.asLong();
        CredentialSnapshot previous = credentials.get(packed);
        // 原版事件与 RTS 入口可能对同一次放置各通知一次；相同 owner/ID 保留 token，
        // 避免重复事件制造无意义的 generation 变化。
        if (previous != null
                && previous.kind() == CredentialKind.V2
                && owner.equals(previous.owner())
                && blockId.equals(previous.blockId())) {
            return previous;
        }
        CredentialSnapshot snapshot = new CredentialSnapshot(
                owner, blockId, allocateGeneration(), CredentialKind.V2);
        if (!snapshot.equals(previous)) {
            credentials.put(packed, snapshot);
            setDirty();
        }
        return snapshot;
    }

    /**
     * 仅用于 V1 兼容测试/迁移工具的显式 legacy 写入；生产放置入口不得调用它。
     */
    public void markLegacy(BlockPos pos) {
        if (pos == null) return;
        long packed = pos.asLong();
        CredentialSnapshot previous = credentials.get(packed);
        if (previous == null || previous.kind() != CredentialKind.LEGACY) {
            credentials.put(packed, CredentialSnapshot.legacy(null, allocateGeneration()));
            setDirty();
        }
    }

    /** 清除坐标凭据；普通破坏和回收成功都使用同一入口。 */
    public void clear(@Nullable BlockPos pos) {
        if (pos != null && credentials.remove(pos.asLong()) != null) {
            setDirty();
        }
    }

    /** 捕获不可变凭据快照；不存在时返回 null。 */
    public @Nullable CredentialSnapshot captureSnapshot(@Nullable BlockPos pos) {
        return pos == null ? null : credentials.get(pos.asLong());
    }

    /**
     * 恢复历史事务携带的凭据快照。null 表示操作成功后该坐标应没有凭据；不会把当前
     * 操作者的 UUID 写入快照。
     */
    public void restoreSnapshot(@Nullable BlockPos pos, @Nullable CredentialSnapshot snapshot) {
        if (pos == null) return;
        long packed = pos.asLong();
        if (snapshot == null) {
            clear(pos);
            return;
        }
        CredentialSnapshot previous = credentials.put(packed, snapshot);
        reserveAfter(snapshot.generation());
        if (!snapshot.equals(previous)) setDirty();
    }

    /** 兼容旧调用点的只读查询；新回收逻辑应使用结构化 checkRecovery。 */
    public boolean isPlaced(BlockPos pos) {
        return pos != null && credentials.containsKey(pos.asLong());
    }

    /**
     * 按当前已加载世界状态判定一次回收资格，并在需要时惰性迁移 legacy。
     * 此方法只访问当前区块，不会因为迁移而主动加载世界的其他区块。
     */
    public RecoveryCheck checkRecovery(ServerLevel level, BlockPos pos, @Nullable UUID actor) {
        if (level == null || pos == null || !level.isLoaded(pos)) {
            return new RecoveryCheck(RecoveryStatus.NOT_LOADED, captureSnapshot(pos));
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            if (isPlaced(pos)) clear(pos);
            return new RecoveryCheck(RecoveryStatus.AIR, null);
        }
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return checkRecovery(pos, actor, blockId, false);
    }

    /**
     * 纯数据版本的回收资格判定，便于契约/unit 测试复用同一匹配规则。
     * block ID 不同会清除陈旧凭据；owner 不同只返回 OWNER_MISMATCH 并保留凭据。
     */
    public RecoveryCheck checkRecovery(
            BlockPos pos, @Nullable UUID actor, @Nullable ResourceLocation actualBlockId,
            boolean actualIsAir) {
        CredentialSnapshot stored = captureSnapshot(pos);
        if (stored == null) return new RecoveryCheck(RecoveryStatus.NOT_TRACKED, null);
        if (actualIsAir || actualBlockId == null) {
            clear(pos);
            return new RecoveryCheck(RecoveryStatus.AIR, null);
        }
        if (stored.blockId() == null) {
            CredentialSnapshot migrated = stored.asLegacyForBlock(actualBlockId);
            credentials.put(pos.asLong(), migrated);
            reserveAfter(migrated.generation());
            setDirty();
            stored = migrated;
        } else if (!actualBlockId.equals(stored.blockId())) {
            clear(pos);
            return new RecoveryCheck(RecoveryStatus.BLOCK_ID_MISMATCH, stored);
        }
        if (stored.kind() == CredentialKind.LEGACY) {
            return new RecoveryCheck(RecoveryStatus.LEGACY_MATCH, stored);
        }
        if (actor == null || !actor.equals(stored.owner())) {
            return new RecoveryCheck(RecoveryStatus.OWNER_MISMATCH, stored);
        }
        return new RecoveryCheck(RecoveryStatus.MATCH, stored);
    }

    /** 将历史/测试快照编码为小型可选 CompoundTag；缺失该字段表示旧历史。 */
    public static CompoundTag encodeSnapshot(@Nullable CredentialSnapshot snapshot) {
        CompoundTag tag = new CompoundTag();
        if (snapshot == null) return tag;
        tag.putString("block", snapshot.blockId() == null ? "" : snapshot.blockId().toString());
        tag.putLong("generation", snapshot.generation());
        tag.putByte("kind", (byte) snapshot.kind().ordinal());
        if (snapshot.owner() != null) tag.putUUID("owner", snapshot.owner());
        return tag;
    }

    /** 解码历史快照；旧字段缺失时由调用方传 null，不会伪造当前操作者 owner。 */
    public static @Nullable CredentialSnapshot decodeSnapshot(@Nullable CompoundTag tag) {
        if (tag == null) return null;
        if (tag.isEmpty()) throw new IllegalArgumentException("placed credential snapshot 为空");
        if (!tag.contains("block", Tag.TAG_STRING)
                || !tag.contains("generation", Tag.TAG_LONG)
                || !tag.contains("kind", Tag.TAG_BYTE)) {
            throw new IllegalArgumentException("placed credential snapshot 不完整");
        }
        String blockText = tag.getString("block");
        if (blockText.length() > MAX_BLOCK_ID_LENGTH) {
            throw new IllegalArgumentException("placed credential block ID 过长");
        }
        ResourceLocation blockId = blockText.isEmpty() ? null : ResourceLocation.tryParse(blockText);
        if (!blockText.isEmpty() && (blockId == null || !blockId.toString().equals(blockText))) {
            throw new IllegalArgumentException("placed credential block ID 无效");
        }
        int kindIndex = tag.getByte("kind");
        if (kindIndex < 0 || kindIndex >= CredentialKind.values().length) {
            throw new IllegalArgumentException("placed credential kind 无效");
        }
        UUID owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        return new CredentialSnapshot(
                owner, blockId, tag.getLong("generation"), CredentialKind.values()[kindIndex]);
    }

    @Override
    public @NotNull CompoundTag save(
            CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        int count = credentials.size();
        if (count > MAX_TRACKED_ENTRIES) {
            throw new IllegalStateException("placed tracker entries 超过有界上限");
        }

        Map<ResourceLocation, Integer> blockIndices = new HashMap<>();
        List<String> blockPalette = new ArrayList<>();
        Map<UUID, Integer> ownerIndices = new HashMap<>();
        List<UUID> ownerPalette = new ArrayList<>();
        long[] positions = new long[count];
        int[] encodedBlocks = new int[count];
        int[] encodedOwners = new int[count];
        long[] generations = new long[count];
        byte[] kinds = new byte[count];

        int entryIndex = 0;
        for (var entry : credentials.long2ObjectEntrySet()) {
            CredentialSnapshot snapshot = entry.getValue();
            positions[entryIndex] = entry.getLongKey();
            generations[entryIndex] = snapshot.generation();
            kinds[entryIndex] = (byte) snapshot.kind().ordinal();
            encodedBlocks[entryIndex] = snapshot.blockId() == null
                    ? -1 : blockIndices.computeIfAbsent(snapshot.blockId(), id -> {
                        blockPalette.add(id.toString());
                        return blockPalette.size() - 1;
                    });
            encodedOwners[entryIndex] = snapshot.owner() == null
                    ? -1 : ownerIndices.computeIfAbsent(snapshot.owner(), owner -> {
                        ownerPalette.add(owner);
                        return ownerPalette.size() - 1;
                    });
            entryIndex++;
        }
        if (blockPalette.size() > MAX_PALETTE_ENTRIES
                || ownerPalette.size() > MAX_PALETTE_ENTRIES) {
            throw new IllegalStateException("placed tracker palette 超过有界上限");
        }

        tag.putInt(KEY_SCHEMA, SCHEMA_VERSION);
        // 兼容周期内继续写 shadow；旧版本只会看到坐标，不会因缺键丢失全部位置。
        tag.putLongArray(KEY_PLACED_SHADOW, positions.clone());
        tag.putLongArray(KEY_POSITIONS, positions);
        ListTag blocks = new ListTag();
        for (String id : blockPalette) blocks.add(net.minecraft.nbt.StringTag.valueOf(id));
        tag.put(KEY_BLOCK_PALETTE, blocks);
        tag.putIntArray(KEY_BLOCK_INDICES, encodedBlocks);
        long[] owners = new long[ownerPalette.size() * 2];
        for (int i = 0; i < ownerPalette.size(); i++) {
            UUID owner = ownerPalette.get(i);
            owners[i * 2] = owner.getMostSignificantBits();
            owners[i * 2 + 1] = owner.getLeastSignificantBits();
        }
        tag.putLongArray(KEY_OWNER_PALETTE, owners);
        tag.putIntArray(KEY_OWNER_INDICES, encodedOwners);
        tag.putLongArray(KEY_GENERATIONS, generations);
        tag.putByteArray(KEY_KINDS, kinds);
        tag.putLong(KEY_NEXT_GENERATION, nextGeneration);
        return tag;
    }

    static PlacedBlockTrackerData load(
            CompoundTag tag, HolderLookup.Provider registries) {
        PlacedBlockTrackerData data = new PlacedBlockTrackerData();
        try {
            if (tag == null) return data;
            if (tag.contains(KEY_SCHEMA, Tag.TAG_INT)) {
                if (tag.getInt(KEY_SCHEMA) != SCHEMA_VERSION) return data;
                data.loadV2(tag);
            } else {
                data.loadLegacy(tag);
            }
        } catch (RuntimeException invalidData) {
            // 损坏的 tracker 不能降级为 shadow legacy，否则会重新授予错误位置回收资格。
            return new PlacedBlockTrackerData();
        }
        return data;
    }

    private void loadLegacy(CompoundTag tag) {
        if (!tag.contains(KEY_PLACED_SHADOW, Tag.TAG_LONG_ARRAY)) return;
        long[] positions = tag.getLongArray(KEY_PLACED_SHADOW);
        if (positions.length > MAX_TRACKED_ENTRIES) throw new IllegalArgumentException("legacy tracker 越界");
        LongOpenHashSet seen = new LongOpenHashSet(Math.min(positions.length, 16_384));
        for (int i = 0; i < positions.length; i++) {
            long packed = positions[i];
            if (!seen.add(packed)) throw new IllegalArgumentException("legacy tracker 坐标重复");
            credentials.put(packed, CredentialSnapshot.legacy(null, nextGeneration++));
        }
    }

    private void loadV2(CompoundTag tag) {
        require(tag, KEY_POSITIONS, Tag.TAG_LONG_ARRAY);
        require(tag, KEY_BLOCK_PALETTE, Tag.TAG_LIST);
        require(tag, KEY_BLOCK_INDICES, Tag.TAG_INT_ARRAY);
        require(tag, KEY_OWNER_PALETTE, Tag.TAG_LONG_ARRAY);
        require(tag, KEY_OWNER_INDICES, Tag.TAG_INT_ARRAY);
        require(tag, KEY_GENERATIONS, Tag.TAG_LONG_ARRAY);
        require(tag, KEY_KINDS, Tag.TAG_BYTE_ARRAY);

        long[] positions = tag.getLongArray(KEY_POSITIONS);
        int[] blocks = tag.getIntArray(KEY_BLOCK_INDICES);
        int[] owners = tag.getIntArray(KEY_OWNER_INDICES);
        long[] generations = tag.getLongArray(KEY_GENERATIONS);
        byte[] kinds = tag.getByteArray(KEY_KINDS);
        if (positions.length > MAX_TRACKED_ENTRIES
                || blocks.length != positions.length
                || owners.length != positions.length
                || generations.length != positions.length
                || kinds.length != positions.length) {
            throw new IllegalArgumentException("placed tracker V2 数组长度不一致");
        }
        if (tag.contains(KEY_PLACED_SHADOW, Tag.TAG_LONG_ARRAY)
                && (!Arrays.equals(tag.getLongArray(KEY_PLACED_SHADOW), positions))) {
            throw new IllegalArgumentException("placed tracker shadow 不匹配");
        }

        List<String> blockPalette = readBlockPalette(tag.getList(KEY_BLOCK_PALETTE, Tag.TAG_STRING));
        long[] ownerPalette = tag.getLongArray(KEY_OWNER_PALETTE);
        if (ownerPalette.length > MAX_PALETTE_ENTRIES * 2L || (ownerPalette.length & 1) != 0) {
            throw new IllegalArgumentException("placed tracker owner palette 越界");
        }
        int ownerCount = ownerPalette.length / 2;
        if (blockPalette.size() > MAX_PALETTE_ENTRIES) {
            throw new IllegalArgumentException("placed tracker block palette 越界");
        }

        LongOpenHashSet seen = new LongOpenHashSet(Math.min(positions.length, 16_384));
        long maxGeneration = 0L;
        for (int i = 0; i < positions.length; i++) {
            if (!seen.add(positions[i])) throw new IllegalArgumentException("placed tracker 坐标重复");
            int blockIndex = blocks[i];
            int ownerIndex = owners[i];
            int kindIndex = kinds[i];
            long generation = generations[i];
            if (generation <= 0L || kindIndex < 0 || kindIndex >= CredentialKind.values().length
                    || blockIndex < -1 || blockIndex >= blockPalette.size()
                    || ownerIndex < -1 || ownerIndex >= ownerCount) {
                throw new IllegalArgumentException("placed tracker credential 索引无效");
            }
            CredentialKind kind = CredentialKind.values()[kindIndex];
            UUID owner = ownerIndex < 0 ? null
                    : new UUID(ownerPalette[ownerIndex * 2], ownerPalette[ownerIndex * 2 + 1]);
            ResourceLocation blockId = blockIndex < 0 ? null : ResourceLocation.tryParse(blockPalette.get(blockIndex));
            if (blockIndex >= 0 && blockId == null) throw new IllegalArgumentException("placed tracker block ID 无效");
            if (kind == CredentialKind.V2 && (owner == null || blockId == null)) {
                throw new IllegalArgumentException("V2 credential 缺少 owner 或 block ID");
            }
            if (kind == CredentialKind.LEGACY && owner != null) {
                throw new IllegalArgumentException("legacy credential 不得包含 owner");
            }
            credentials.put(positions[i], new CredentialSnapshot(owner, blockId, generation, kind));
            maxGeneration = Math.max(maxGeneration, generation);
        }
        long persistedNext = tag.contains(KEY_NEXT_GENERATION, Tag.TAG_LONG)
                ? tag.getLong(KEY_NEXT_GENERATION) : 0L;
        nextGeneration = persistedNext > maxGeneration ? persistedNext : incrementGeneration(maxGeneration);
    }

    private static List<String> readBlockPalette(ListTag encoded) {
        if (encoded.size() > MAX_PALETTE_ENTRIES
                || (encoded.getElementType() != Tag.TAG_STRING && !encoded.isEmpty())) {
            throw new IllegalArgumentException("placed tracker block palette 类型无效");
        }
        List<String> palette = new ArrayList<>(encoded.size());
        for (int i = 0; i < encoded.size(); i++) {
            String id = encoded.getString(i);
            if (id.isEmpty() || id.length() > MAX_BLOCK_ID_LENGTH) {
                throw new IllegalArgumentException("placed tracker block palette 值无效");
            }
            ResourceLocation parsed = ResourceLocation.tryParse(id);
            if (parsed == null || !parsed.toString().equals(id)) {
                throw new IllegalArgumentException("placed tracker block palette ID 无效");
            }
            palette.add(id);
        }
        return palette;
    }

    private static void require(CompoundTag tag, String key, int type) {
        if (!tag.contains(key, type)) throw new IllegalArgumentException("placed tracker 缺少 " + key);
    }

    private long allocateGeneration() {
        long allocated = nextGeneration;
        if (allocated <= 0L || allocated == Long.MAX_VALUE) {
            throw new IllegalStateException("placed tracker generation 已耗尽");
        }
        nextGeneration++;
        return allocated;
    }

    private void reserveAfter(long generation) {
        if (generation >= nextGeneration) nextGeneration = incrementGeneration(generation);
    }

    private static long incrementGeneration(long generation) {
        if (generation >= Long.MAX_VALUE - 1L) return Long.MAX_VALUE;
        return Math.max(1L, generation + 1L);
    }

    public enum CredentialKind {
        V2,
        LEGACY
    }

    /** 不可变凭据快照；LEGACY 明确允许 owner 为空，V2 不允许。 */
    public record CredentialSnapshot(
            @Nullable UUID owner,
            @Nullable ResourceLocation blockId,
            long generation,
            CredentialKind kind) {
        public CredentialSnapshot {
            Objects.requireNonNull(kind, "kind");
            if (generation <= 0L) throw new IllegalArgumentException("generation 必须为正数");
            if (kind == CredentialKind.V2 && (owner == null || blockId == null)) {
                throw new IllegalArgumentException("V2 credential 必须包含 owner 与 block ID");
            }
            if (kind == CredentialKind.LEGACY && owner != null) {
                throw new IllegalArgumentException("LEGACY credential owner 必须为空");
            }
        }

        public static CredentialSnapshot legacy(@Nullable ResourceLocation blockId, long generation) {
            return new CredentialSnapshot(null, blockId, generation, CredentialKind.LEGACY);
        }

        private CredentialSnapshot asLegacyForBlock(ResourceLocation actualBlockId) {
            return new CredentialSnapshot(null, actualBlockId, generation, CredentialKind.LEGACY);
        }
    }

    public enum RecoveryStatus {
        NOT_TRACKED,
        MATCH,
        LEGACY_MATCH,
        OWNER_MISMATCH,
        BLOCK_ID_MISMATCH,
        AIR,
        NOT_LOADED
    }

    /** 结构化回收匹配结果，调用方可区分清陈旧记录与保留 owner 不匹配记录。 */
    public record RecoveryCheck(
            RecoveryStatus status,
            @Nullable CredentialSnapshot credential) {
        public boolean canRecover() {
            return status == RecoveryStatus.MATCH || status == RecoveryStatus.LEGACY_MATCH;
        }
    }
}
