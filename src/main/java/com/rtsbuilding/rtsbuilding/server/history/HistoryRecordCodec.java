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
 * Forge 1.20.1 的 schema3 历史紧凑编码器。
 *
 * <p>位置使用并行 LongArray，方块状态使用 palette，少量 block-entity/操作后字段放入
 * sparse extras；凭据由独立列式 codec 保存。schema1/2 的逐条 history 仍由同一入口读取，
 * 旧任务不会因升级丢进度。</p>
 */
public final class HistoryRecordCodec {
    public static final String STATES_KEY = "history_states";
    public static final String STATE_INDICES_KEY = "history_state_indices";
    public static final String EXTRA_INDEX_KEY = "index";
    public static final String EXTRA_VALUES_KEY = "values";

    private HistoryRecordCodec() {
    }

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
                palette.put(state.copy(), paletteIndex);
                states.add(state.copy());
            }
            stateIndices[i] = paletteIndex;

            CompoundTag values = new CompoundTag();
            for (String key : record.getAllKeys()) {
                if (key.equals("pos") || key.equals("state") || HistoryCredentialCodec.isCredential(key)) {
                    continue;
                }
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

    public static List<CompoundTag> decode(
            CompoundTag parent, String historyKey, String positionsKey,
            int maxRecords, boolean compact) {
        Objects.requireNonNull(parent, "parent");
        if (maxRecords < 0) throw new IllegalArgumentException("history 上限不能为负数");
        int boundedMax = Math.min(maxRecords, RtsHistoryConstants.MAX_HISTORY_RECORDS_PER_ENTRY);
        return compact
                ? decodeCompact(parent, historyKey, positionsKey, boundedMax)
                : decodeLegacy(parent, historyKey, positionsKey, boundedMax);
    }

    private static List<CompoundTag> decodeCompact(
            CompoundTag parent, String historyKey, String positionsKey, int maxRecords) {
        ListTag extras = requireCompoundList(parent, historyKey, "history extras");
        if (!parent.contains(positionsKey, Tag.TAG_LONG_ARRAY)
                || !parent.contains(STATES_KEY, Tag.TAG_LIST)
                || !parent.contains(STATE_INDICES_KEY, Tag.TAG_INT_ARRAY)) {
            throw new IllegalArgumentException("紧凑 history 缺少 positions/states/indices");
        }
        long[] positions = parent.getLongArray(positionsKey);
        int[] stateIndices = parent.getIntArray(STATE_INDICES_KEY);
        if (positions.length != stateIndices.length || positions.length > maxRecords) {
            throw new IllegalArgumentException("history positions 与 state indices 数量无效");
        }
        ListTag states = requireCompoundList(parent, STATES_KEY, "history state palette");
        if (positions.length == 0 && !states.isEmpty()) {
            throw new IllegalArgumentException("空 history 不得带 state palette");
        }
        if (positions.length > 0 && states.isEmpty()) {
            throw new IllegalArgumentException("非空 history 缺少 state palette");
        }
        if (states.size() > positions.length || extras.size() > positions.length) {
            throw new IllegalArgumentException("history palette/extras 数量超过记录数");
        }

        List<CompoundTag> decoded = new ArrayList<>(positions.length);
        boolean[] used = new boolean[states.size()];
        for (int i = 0; i < positions.length; i++) {
            int stateIndex = stateIndices[i];
            if (stateIndex < 0 || stateIndex >= states.size()) {
                throw new IllegalArgumentException("history state index 越界");
            }
            used[stateIndex] = true;
            CompoundTag record = new CompoundTag();
            record.putLong("pos", positions[i]);
            record.put("state", states.getCompound(stateIndex).copy());
            decoded.add(record);
        }
        for (boolean stateUsed : used) {
            if (!stateUsed) throw new IllegalArgumentException("history state palette 含未使用项");
        }

        int previousIndex = -1;
        for (int i = 0; i < extras.size(); i++) {
            CompoundTag sparse = extras.getCompound(i);
            if (!sparse.contains(EXTRA_INDEX_KEY, Tag.TAG_INT)) {
                throw new IllegalArgumentException("history sparse extra 缺少 record index");
            }
            int recordIndex = sparse.getInt(EXTRA_INDEX_KEY);
            if (recordIndex < 0 || recordIndex >= decoded.size() || recordIndex <= previousIndex
                    || !sparse.contains(EXTRA_VALUES_KEY, Tag.TAG_COMPOUND)
                    || sparse.getAllKeys().size() != 2) {
                throw new IllegalArgumentException("history sparse extra index 无效");
            }
            previousIndex = recordIndex;
            CompoundTag values = sparse.getCompound(EXTRA_VALUES_KEY);
            if (values.isEmpty()) throw new IllegalArgumentException("history sparse extra 不能为空");
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
        if (encodedHistory.size() > maxRecords || parent.contains(positionsKey)) {
            throw new IllegalArgumentException("旧 schema history 越界或包含紧凑位置数组");
        }
        List<CompoundTag> decoded = new ArrayList<>(encodedHistory.size());
        for (int i = 0; i < encodedHistory.size(); i++) {
            decoded.add(requireRecordWithPosition(encodedHistory.getCompound(i)).copy());
        }
        return List.copyOf(decoded);
    }

    private static ListTag requireCompoundList(CompoundTag parent, String key, String field) {
        Tag raw = parent.get(key);
        if (!(raw instanceof ListTag list) || (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException(field + " 必须是 Compound ListTag");
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
        if (record == null || !record.contains("pos", Tag.TAG_LONG)) {
            throw new IllegalArgumentException("legacy history record 缺少 pos");
        }
        return record;
    }
}
