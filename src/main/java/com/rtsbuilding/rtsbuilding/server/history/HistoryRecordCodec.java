package com.rtsbuilding.rtsbuilding.server.history;

import com.rtsbuilding.rtsbuilding.common.RtsHistoryConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 撤回/重做历史的 schema3 紧凑编码器。
 *
 * <p>每条记录只在并行 LongArray 中写位置，在 palette 中按值共享 state，并以 int[] 保存
 * 每条记录对应的 palette index。逐块唯一的凭据由列式 codec 保存，其余字段放入稀疏
 * extras ListTag，并保留原 record index，因此方块实体、凭据和操作后快照不会因压缩而丢失。
 * schema1/2 的逐条内嵌 pos 格式仍由同一个 decode 入口读取。</p>
 */
public final class HistoryRecordCodec {
    /** schema3 中 state palette 和 index 数组的固定字段名。 */
    public static final String STATES_KEY = "history_states";
    public static final String STATE_INDICES_KEY = "history_state_indices";
    /** sparse extras entry 的结构索引字段。 */
    public static final String EXTRA_INDEX_KEY = "index";
    /** sparse extras 的值容器，避免用户历史恰好拥有名为 index 的业务字段时被覆盖。 */
    public static final String EXTRA_VALUES_KEY = "values";

    private HistoryRecordCodec() {
    }

    /**
     * 写入紧凑历史。{@code historyKey} 继续沿用旧字段名，但 schema3 中它表示 sparse
     * extras 列表；{@code positionsKey} 允许 mining/destruction 保留各自旧命名。
     */
    public static void encode(
            CompoundTag parent, String historyKey, String positionsKey,
            List<CompoundTag> records) {
        Objects.requireNonNull(parent, "parent");
        Objects.requireNonNull(historyKey, "historyKey");
        Objects.requireNonNull(positionsKey, "positionsKey");
        Objects.requireNonNull(records, "records");

        if (records.size() > RtsHistoryConstants.MAX_HISTORY_RECORDS_PER_ENTRY) {
            throw new IllegalArgumentException("history 记录数超过有界上限");
        }
        long[] positions = new long[records.size()];
        int[] stateIndices = new int[records.size()];
        ListTag states = new ListTag();
        ListTag extras = new ListTag();
        Map<CompoundTag, Integer> palette = new LinkedHashMap<>();

        for (int i = 0; i < records.size(); i++) {
            CompoundTag record = requireRecord(records.get(i));
            positions[i] = record.getLong("pos");

            CompoundTag state = record.getCompound("state");
            Integer paletteIndex = palette.get(state);
            if (paletteIndex == null) {
                paletteIndex = palette.size();
                // 映射键和编码值不能引用调用方持有的可变 NBT。
                palette.put(state.copy(), paletteIndex);
                states.add(state.copy());
            }
            stateIndices[i] = paletteIndex;

            CompoundTag values = new CompoundTag();
            for (String key : record.getAllKeys()) {
                if (key.equals("pos") || key.equals("state") || HistoryCredentialCodec.isCredential(key)) continue;
                values.put(key, record.get(key).copy());
            }
            if (!values.isEmpty()) {
                CompoundTag sparse = new CompoundTag();
                sparse.putInt(EXTRA_INDEX_KEY, i);
                sparse.put(EXTRA_VALUES_KEY, values);
                extras.add(sparse);
            }
        }

        parent.put(historyKey, extras);
        parent.putLongArray(positionsKey, positions);
        parent.put(STATES_KEY, states);
        parent.putIntArray(STATE_INDICES_KEY, stateIndices);
        HistoryCredentialCodec.encode(parent, records);
    }

    /**
     * 读取紧凑或旧 inline 历史。紧凑格式对数组长度、palette index、sparse index 和核心
     * 字段冲突全部 fail closed，避免损坏存档静默重排或丢失 NBT。
     */
    public static List<CompoundTag> decode(
            CompoundTag parent, String historyKey, String positionsKey,
            int maxRecords, boolean compact) {
        Objects.requireNonNull(parent, "parent");
        Objects.requireNonNull(historyKey, "historyKey");
        Objects.requireNonNull(positionsKey, "positionsKey");
        if (maxRecords < 0) throw new IllegalArgumentException("history 上限不能为负数");
        int boundedMaxRecords = Math.min(
                maxRecords, RtsHistoryConstants.MAX_HISTORY_RECORDS_PER_ENTRY);

        if (compact) return decodeCompact(parent, historyKey, positionsKey, boundedMaxRecords);
        return decodeLegacy(parent, historyKey, positionsKey, boundedMaxRecords);
    }

    private static List<CompoundTag> decodeCompact(
            CompoundTag parent, String historyKey, String positionsKey, int maxRecords) {
        // schema3 的 historyKey 是 sparse extras，不再是逐条完整记录。
        ListTag extras = requireCompoundList(parent, historyKey, "history extras");
        if (!parent.contains(positionsKey, Tag.TAG_LONG_ARRAY)
                || !parent.contains(STATES_KEY, Tag.TAG_LIST)
                || !parent.contains(STATE_INDICES_KEY, Tag.TAG_INT_ARRAY)) {
            throw new IllegalArgumentException("紧凑 history 缺少 positions/states/indices");
        }

        long[] positions = parent.getLongArray(positionsKey);
        int[] stateIndices = parent.getIntArray(STATE_INDICES_KEY);
        if (positions.length != stateIndices.length) {
            throw new IllegalArgumentException("history positions 与 state indices 数量不一致");
        }
        if (positions.length > maxRecords) {
            throw new IllegalArgumentException("history 超过有界上限");
        }

        ListTag encodedStates = requireCompoundList(parent, STATES_KEY, "history state palette");
        if (positions.length == 0 && !encodedStates.isEmpty()) {
            throw new IllegalArgumentException("空 history 不得带 state palette");
        }
        if (positions.length > 0 && encodedStates.isEmpty()) {
            throw new IllegalArgumentException("非空 history 缺少 state palette");
        }
        // 编码器每条记录最多贡献一个 palette state；先拒绝多余项，避免为损坏
        // payload 分配远大于记录数的 usedStates 数组。
        if (encodedStates.size() > positions.length) {
            throw new IllegalArgumentException("history state palette 数量超过记录数");
        }
        if (extras.size() > positions.length) {
            throw new IllegalArgumentException("history sparse extra 数量超过记录数");
        }

        List<CompoundTag> decoded = new ArrayList<>(positions.length);
        boolean[] usedStates = new boolean[encodedStates.size()];
        for (int i = 0; i < positions.length; i++) {
            int paletteIndex = stateIndices[i];
            if (paletteIndex < 0 || paletteIndex >= encodedStates.size()) {
                throw new IllegalArgumentException("history state index 越界");
            }
            usedStates[paletteIndex] = true;
            CompoundTag record = new CompoundTag();
            record.putLong("pos", positions[i]);
            record.put("state", encodedStates.getCompound(paletteIndex).copy());
            decoded.add(record);
        }
        for (boolean used : usedStates) {
            if (!used) throw new IllegalArgumentException("history state palette 含未使用项");
        }

        int previousIndex = -1;
        for (int i = 0; i < extras.size(); i++) {
            CompoundTag sparse = extras.getCompound(i);
            if (!sparse.contains(EXTRA_INDEX_KEY, Tag.TAG_INT)) {
                throw new IllegalArgumentException("history sparse extra 缺少 record index");
            }
            int recordIndex = sparse.getInt(EXTRA_INDEX_KEY);
            if (recordIndex < 0 || recordIndex >= decoded.size()
                    || recordIndex <= previousIndex) {
                throw new IllegalArgumentException("history sparse extra index 无效或重复");
            }
            previousIndex = recordIndex;
            if (!sparse.contains(EXTRA_VALUES_KEY, Tag.TAG_COMPOUND)
                    || sparse.getAllKeys().size() != 2) {
                throw new IllegalArgumentException("history sparse extra values 无效");
            }
            CompoundTag values = sparse.getCompound(EXTRA_VALUES_KEY);
            if (values.isEmpty()) {
                throw new IllegalArgumentException("history sparse extra 不能为空");
            }
            for (String key : values.getAllKeys()) {
                if (key.equals("pos") || key.equals("state")) {
                    throw new IllegalArgumentException("history sparse extra 覆盖核心字段");
                }
                decoded.get(recordIndex).put(key, values.get(key).copy());
            }
        }
        HistoryCredentialCodec.decodeInto(parent, decoded);
        return List.copyOf(decoded);
    }

    private static List<CompoundTag> decodeLegacy(
            CompoundTag parent, String historyKey, String positionsKey, int maxRecords) {
        Tag rawHistory = parent.get(historyKey);
        if (!(rawHistory instanceof ListTag encodedHistory)) {
            throw new IllegalArgumentException("history 必须是 ListTag");
        }
        if (!encodedHistory.isEmpty() && encodedHistory.getElementType() != Tag.TAG_COMPOUND) {
            throw new IllegalArgumentException("history 元素类型无效");
        }
        if (encodedHistory.size() > maxRecords) {
            throw new IllegalArgumentException("history 超过有界上限");
        }
        if (parent.contains(positionsKey)) {
            throw new IllegalArgumentException("旧 schema 不得包含紧凑 history 位置数组");
        }
        List<CompoundTag> decoded = new ArrayList<>(encodedHistory.size());
        for (int i = 0; i < encodedHistory.size(); i++) {
            CompoundTag record = requireRecordWithPosition(encodedHistory.getCompound(i));
            decoded.add(record.copy());
        }
        return List.copyOf(decoded);
    }

    private static ListTag requireCompoundList(CompoundTag parent, String key, String field) {
        Tag raw = parent.get(key);
        if (!(raw instanceof ListTag list)) {
            throw new IllegalArgumentException(field + " 必须是 ListTag");
        }
        if (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND) {
            throw new IllegalArgumentException(field + " 元素类型无效");
        }
        return list;
    }

    private static CompoundTag requireRecord(CompoundTag record) {
        if (record == null || !record.contains("pos", Tag.TAG_LONG)
                || !record.contains("state", Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("history record 缺少规范 pos/state");
        }
        return record;
    }

    private static CompoundTag requireRecordWithPosition(CompoundTag record) {
        if (!record.contains("pos", Tag.TAG_LONG)) {
            throw new IllegalArgumentException("legacy history record 缺少 pos");
        }
        return record;
    }
}
