package com.rtsbuilding.rtsbuilding.server.history;

import com.rtsbuilding.rtsbuilding.common.RtsHistoryConstants;
import com.rtsbuilding.rtsbuilding.common.mining.MiningLimits;
import com.rtsbuilding.rtsbuilding.server.task.persistence.NbtStringLimits;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Forge 1.20.1 紧凑破坏历史的增量预算摘要。
 *
 * <p>它只认识 Forge 当前 schema3 的位置数组、state palette、credential 列和 sparse
 * extras，不读取世界，也不负责把记录写入撤回栈。切片之间复用摘要，使任务能够在修改
 * 世界前拒绝超过 TaskCodec 字节/节点预算的候选历史。</p>
 */
public final class HistoryBudget {
    /** task codec 的硬字节上限；这里不能依赖 task codec 的私有测量器。 */
    public static final long MAX_TASK_PAYLOAD_BYTES =
            com.rtsbuilding.rtsbuilding.server.task.persistence.TaskCodec.MAX_TASK_PAYLOAD_BYTES;
    /** 与 TaskCodec 的 payload 节点边界保持一致。 */
    public static final int MAX_TASK_PAYLOAD_NODES = 1_000_000;
    /** 为 gzip 头、根元数据和不同 NBT writer 留出的 BE 安全余量。 */
    private static final long BLOCK_ENTITY_SAFETY_MARGIN = 64L * 1024L;
    public static final long MAX_BLOCK_ENTITY_LOGICAL_BYTES =
            (long) RtsHistoryConstants.MAX_COMPRESSED_NBT_BYTES_PER_ENTRY
                    - BLOCK_ENTITY_SAFETY_MARGIN;
    private static final long FIXED_PAYLOAD_BYTES = 4_096L;
    private static final int FIXED_PAYLOAD_NODES = 128;
    private static final int MAX_NBT_DEPTH = 64;

    private final int targetCount;
    private final int recordCount;
    private final long paletteBytes;
    private final int paletteNodes;
    private final long extrasBytes;
    private final int extrasNodes;
    private final long blockEntityBytes;
    private final Map<CompoundTag, Integer> palette;
    /** 按字段区分的凭据模板；generation 独立计入列式原始数组。 */
    private final Map<CredentialTemplate, Boolean> credentialTemplates;

    private HistoryBudget(
            int targetCount, int recordCount, long paletteBytes, int paletteNodes,
            long extrasBytes, int extrasNodes, long blockEntityBytes,
            Map<CompoundTag, Integer> palette,
            Map<CredentialTemplate, Boolean> credentialTemplates) {
        this.targetCount = targetCount;
        this.recordCount = recordCount;
        this.paletteBytes = paletteBytes;
        this.paletteNodes = paletteNodes;
        this.extrasBytes = extrasBytes;
        this.extrasNodes = extrasNodes;
        this.blockEntityBytes = blockEntityBytes;
        this.palette = Collections.unmodifiableMap(palette);
        this.credentialTemplates = Collections.unmodifiableMap(credentialTemplates);
    }

    /** 从持久化解码或任务创建时建立一次完整摘要。 */
    public static HistoryBudget forTargets(int targetCount, List<CompoundTag> records) {
        Objects.requireNonNull(records, "records");
        validateTargetCount(targetCount);
        return empty(targetCount).append(records, 0);
    }

    /** 建立没有任何历史的摘要。 */
    public static HistoryBudget empty(int targetCount) {
        validateTargetCount(targetCount);
        return new HistoryBudget(targetCount, 0, 0L, 0, 0L, 0, 0L,
                Map.of(), Map.of());
    }

    /** 只把 {@code records[fromIndex..]} 计入摘要，保留旧前缀的派生成本。 */
    public HistoryBudget append(List<CompoundTag> records, int fromIndex) {
        Objects.requireNonNull(records, "records");
        if (fromIndex < 0 || fromIndex > records.size()) {
            throw new IllegalArgumentException("history 增量起点越界");
        }
        long nextRecordCount = (long) recordCount + records.size() - fromIndex;
        if (nextRecordCount > maxRecordCount(targetCount)) {
            throw new IllegalArgumentException("history 记录数超过任务上限");
        }

        Map<CompoundTag, Integer> nextPalette = palette;
        boolean paletteCopied = false;
        Map<CredentialTemplate, Boolean> nextCredentials = credentialTemplates;
        boolean credentialsCopied = false;
        long nextPaletteBytes = paletteBytes;
        int nextPaletteNodes = paletteNodes;
        long nextExtrasBytes = extrasBytes;
        int nextExtrasNodes = extrasNodes;
        long nextBlockEntityBytes = blockEntityBytes;
        for (int i = fromIndex; i < records.size(); i++) {
            CompoundTag record = requireRecord(records.get(i));
            CompoundTag state = record.getCompound("state");
            if (!nextPalette.containsKey(state)) {
                if (!paletteCopied) {
                    nextPalette = new LinkedHashMap<>(nextPalette);
                    paletteCopied = true;
                }
                nextPalette.put(state.copy(), nextPalette.size());
                NbtCost stateCost = measure(state, 2);
                nextPaletteBytes = saturatedAdd(nextPaletteBytes, stateCost.bytes());
                nextPaletteNodes = saturatedAddInt(nextPaletteNodes, stateCost.nodes());
            }
            for (String key : HistoryCredentialCodec.FIELDS) {
                if (!record.contains(key)) continue;
                CredentialTemplate template = new CredentialTemplate(
                        key, HistoryCredentialCodec.template(record.get(key)));
                // record index (int) + generation (long) + template index (int)。
                nextExtrasBytes = saturatedAdd(nextExtrasBytes, 16L);
                if (!nextCredentials.containsKey(template)) {
                    if (!credentialsCopied) {
                        nextCredentials = new LinkedHashMap<>(nextCredentials);
                        credentialsCopied = true;
                    }
                    nextCredentials.put(template, Boolean.TRUE);
                    NbtCost templateCost = measure(template.value(), 4);
                    nextExtrasBytes = saturatedAdd(nextExtrasBytes, templateCost.bytes());
                    nextExtrasNodes = saturatedAddInt(nextExtrasNodes, templateCost.nodes());
                }
            }
            NbtCost extrasCost = measureSparseExtras(record);
            if (extrasCost != null) {
                nextExtrasBytes = saturatedAdd(nextExtrasBytes, extrasCost.bytes());
                nextExtrasNodes = saturatedAddInt(nextExtrasNodes, extrasCost.nodes());
            }
            nextBlockEntityBytes = saturatedAdd(nextBlockEntityBytes, measureBlockEntityFields(record));
        }
        return new HistoryBudget(
                targetCount, (int) nextRecordCount, nextPaletteBytes, nextPaletteNodes,
                nextExtrasBytes, nextExtrasNodes, nextBlockEntityBytes,
                nextPalette, nextCredentials);
    }

    /** 候选记录能否在不改变当前摘要的前提下安全加入。 */
    public boolean canAppend(List<CompoundTag> candidateRecords) {
        if (candidateRecords == null) return false;
        try {
            return append(candidateRecords, 0).accepts();
        } catch (RuntimeException invalidCandidate) {
            return false;
        }
    }

    /** 返回加入候选后的摘要；调用方必须先确认 {@link #canAppend(List)}。 */
    public HistoryBudget appended(List<CompoundTag> candidateRecords) {
        Objects.requireNonNull(candidateRecords, "candidateRecords");
        HistoryBudget result = append(candidateRecords, 0);
        if (!result.accepts()) throw new IllegalArgumentException("history 候选超出容量预算");
        return result;
    }

    public int targetCount() { return targetCount; }
    public int recordCount() { return recordCount; }
    public int stateCount() { return palette.size(); }

    public long estimatedPayloadBytes() {
        long bytes = FIXED_PAYLOAD_BYTES;
        // destruction 同时保留 targets 与 destroyedPositions；mining 按较大值预留。
        bytes = saturatedAdd(bytes, 16L * targetCount);
        bytes = saturatedAdd(bytes, 8L * recordCount);
        bytes = saturatedAdd(bytes, 4L * recordCount);
        bytes = saturatedAdd(bytes, paletteBytes);
        bytes = saturatedAdd(bytes, extrasBytes);
        return bytes;
    }

    public int estimatedPayloadNodes() {
        long nodes = FIXED_PAYLOAD_NODES + (long) paletteNodes + extrasNodes;
        return nodes >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) nodes;
    }

    public long estimatedBlockEntityBytes() { return blockEntityBytes; }

    public boolean accepts() {
        return recordCount <= maxRecordCount(targetCount)
                && estimatedPayloadBytes() <= MAX_TASK_PAYLOAD_BYTES
                && estimatedPayloadNodes() <= MAX_TASK_PAYLOAD_NODES
                && blockEntityBytes <= MAX_BLOCK_ENTITY_LOGICAL_BYTES;
    }

    public static int maxRecordCount(int targetCount) {
        validateTargetCount(targetCount);
        long result = (long) targetCount * RtsHistoryConstants.MAX_HISTORY_RECORDS_PER_TARGET;
        return result >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) result;
    }

    private static CompoundTag requireRecord(CompoundTag record) {
        if (record == null || !record.contains("pos", Tag.TAG_LONG)
                || !record.contains("state", Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("history record 缺少规范 pos/state");
        }
        return record;
    }

    private static NbtCost measureSparseExtras(CompoundTag record) {
        boolean hasExtras = false;
        long bytes = 8L;
        int nodes = 1;
        bytes = saturatedAdd(bytes, 3L
                + NbtStringLimits.modifiedUtfBytes(HistoryRecordCodec.EXTRA_INDEX_KEY) + 4L);
        nodes++;
        long valuesBytes = 8L;
        int valuesNodes = 1;
        for (String key : record.getAllKeys()) {
            if (key.equals("pos") || key.equals("state") || HistoryCredentialCodec.isCredential(key)) continue;
            hasExtras = true;
            int keyBytes = NbtStringLimits.requireWritable(key, "history key");
            valuesBytes = saturatedAdd(valuesBytes, 3L + keyBytes);
            NbtCost cost = measure(record.get(key), 4);
            valuesBytes = saturatedAdd(valuesBytes, cost.bytes());
            valuesNodes = saturatedAddInt(valuesNodes, cost.nodes());
        }
        if (hasExtras) {
            bytes = saturatedAdd(bytes, 3L
                    + NbtStringLimits.modifiedUtfBytes(HistoryRecordCodec.EXTRA_VALUES_KEY));
            bytes = saturatedAdd(bytes, valuesBytes);
            nodes = saturatedAddInt(nodes, valuesNodes);
        }
        return hasExtras ? new NbtCost(bytes, nodes) : null;
    }

    private static long measureBlockEntityFields(CompoundTag record) {
        long bytes = 0L;
        for (String key : record.getAllKeys()) {
            if (!isBlockEntityKey(key)) continue;
            Tag value = record.get(key);
            if (value == null || value.getId() != Tag.TAG_COMPOUND) {
                throw new IllegalArgumentException("history block entity 必须是 CompoundTag");
            }
            NbtCost cost = measure(value, 4);
            bytes = saturatedAdd(bytes, 3L
                    + NbtStringLimits.modifiedUtfBytes(key) + cost.bytes());
        }
        return bytes;
    }

    private static boolean isBlockEntityKey(String key) {
        return key.equals("block_entity") || key.equals("blockEntity")
                || key.equals("after_block_entity") || key.equals("afterBlockEntity");
    }

    /** 与 Forge TaskCodec 的节点、数组原始字节和 Modified UTF key 成本保持同一口径。 */
    private static NbtCost measure(Tag tag, int depth) {
        if (tag == null) throw new IllegalArgumentException("history NBT 不能包含 null");
        if (depth > MAX_NBT_DEPTH) throw new IllegalArgumentException("history NBT 嵌套过深");
        long bytes;
        int nodes = 1;
        switch (tag.getId()) {
            case Tag.TAG_END -> bytes = 1L;
            case Tag.TAG_BYTE -> bytes = 1L;
            case Tag.TAG_SHORT -> bytes = 2L;
            case Tag.TAG_INT, Tag.TAG_FLOAT -> bytes = 4L;
            case Tag.TAG_LONG, Tag.TAG_DOUBLE -> bytes = 8L;
            case Tag.TAG_BYTE_ARRAY -> bytes = ((net.minecraft.nbt.ByteArrayTag) tag).getAsByteArray().length;
            case Tag.TAG_INT_ARRAY -> bytes = ((net.minecraft.nbt.IntArrayTag) tag).getAsIntArray().length * 4L;
            case Tag.TAG_LONG_ARRAY -> bytes = ((net.minecraft.nbt.LongArrayTag) tag).getAsLongArray().length * 8L;
            case Tag.TAG_STRING -> bytes = 2L + NbtStringLimits.requireWritable(tag.getAsString(), "history string");
            case Tag.TAG_LIST -> {
                net.minecraft.nbt.ListTag list = (net.minecraft.nbt.ListTag) tag;
                bytes = 8L;
                for (int i = 0; i < list.size(); i++) {
                    NbtCost child = measure(list.get(i), depth + 1);
                    bytes = saturatedAdd(bytes, child.bytes());
                    nodes = saturatedAddInt(nodes, child.nodes());
                }
            }
            case Tag.TAG_COMPOUND -> {
                CompoundTag compound = (CompoundTag) tag;
                bytes = 8L;
                for (String key : compound.getAllKeys()) {
                    int keyBytes = NbtStringLimits.requireWritable(key, "history key");
                    bytes = saturatedAdd(bytes, 3L + keyBytes);
                    NbtCost child = measure(compound.get(key), depth + 1);
                    bytes = saturatedAdd(bytes, child.bytes());
                    nodes = saturatedAddInt(nodes, child.nodes());
                }
            }
            default -> throw new IllegalArgumentException("未知 history NBT 类型: " + tag.getId());
        }
        return new NbtCost(bytes, nodes);
    }

    private static int saturatedAddInt(int left, int right) {
        long result = (long) left + right;
        return result >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) result;
    }

    private static long saturatedAdd(long left, long right) {
        if (right <= 0L) return left;
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static void validateTargetCount(int targetCount) {
        if (targetCount < 0 || targetCount > MiningLimits.MAX_VOLUME) {
            throw new IllegalArgumentException("history target 数量越界");
        }
    }

    private record NbtCost(long bytes, int nodes) {
    }

    private record CredentialTemplate(String field, CompoundTag value) {
    }
}
