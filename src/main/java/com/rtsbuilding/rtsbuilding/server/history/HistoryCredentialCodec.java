package com.rtsbuilding.rtsbuilding.server.history;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 历史凭据的列式存储，仅压缩表示，不决定方块所有权或回收权限。
 *
 * <p>generation 每块唯一，不能把整个凭据当作 palette，也不能为每块保留一个 extras
 * compound。这里用三个原始数组保存记录索引、generation 和模板索引，其余完整字段按值
 * 共享模板，兼容挖掘和拆除现有两种字段命名。解码还原原记录，不修改 tracker 的格式。</p>
 */
final class HistoryCredentialCodec {
    static final String KEY = "history_credentials";
    static final List<String> FIELDS = List.of(
            "credential_before", "credential_after", "credentialBefore", "credentialAfter");
    private static final Set<String> COLUMN_FIELDS = Set.of("records", "generations", "indices", "templates");

    private HistoryCredentialCodec() { }

    static boolean isCredential(String key) {
        return FIELDS.contains(key);
    }

    /** 返回完整模板副本，只抽离 generation；未知扩展字段原样保留并参与预算。 */
    static CompoundTag template(Tag raw) {
        if (!(raw instanceof CompoundTag credential)
                || !credential.contains("generation", Tag.TAG_LONG)
                || credential.getLong("generation") <= 0) {
            throw new IllegalArgumentException("history credential 缺少有效 generation");
        }
        CompoundTag template = credential.copy();
        template.remove("generation");
        return template;
    }

    static void encode(CompoundTag parent, List<CompoundTag> records) {
        CompoundTag columns = new CompoundTag();
        for (String field : FIELDS) {
            int count = 0;
            for (CompoundTag record : records) if (record.contains(field)) count++;
            if (count == 0) continue;
            int[] rows = new int[count], indices = new int[count];
            long[] generations = new long[count];
            Map<CompoundTag, Integer> palette = new LinkedHashMap<>();
            ListTag templates = new ListTag();
            int cursor = 0;
            for (int row = 0; row < records.size(); row++) {
                CompoundTag record = records.get(row);
                if (!record.contains(field)) continue;
                CompoundTag template = template(record.get(field));
                Integer index = palette.get(template);
                if (index == null) {
                    index = palette.size();
                    palette.put(template, index);
                    templates.add(template);
                }
                rows[cursor] = row;
                indices[cursor] = index;
                generations[cursor++] = record.getCompound(field).getLong("generation");
            }
            CompoundTag column = new CompoundTag();
            column.putIntArray("records", rows);
            column.putLongArray("generations", generations);
            column.putIntArray("indices", indices);
            column.put("templates", templates);
            columns.put(field, column);
        }
        // 空记录不带额外字段；复用 parent 时移除上一轮遗留的列。
        if (columns.isEmpty()) parent.remove(KEY);
        else parent.put(KEY, columns);
    }

    static void decodeInto(CompoundTag parent, List<CompoundTag> records) {
        if (!parent.contains(KEY)) return;
        if (!parent.contains(KEY, Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("history credentials 类型无效");
        }
        CompoundTag columns = parent.getCompound(KEY);
        for (String field : columns.getAllKeys()) {
            if (!isCredential(field) || !columns.contains(field, Tag.TAG_COMPOUND)) {
                throw new IllegalArgumentException("history credential 列无效");
            }
            CompoundTag column = columns.getCompound(field);
            if (!column.getAllKeys().equals(COLUMN_FIELDS)
                    || !column.contains("records", Tag.TAG_INT_ARRAY)
                    || !column.contains("generations", Tag.TAG_LONG_ARRAY)
                    || !column.contains("indices", Tag.TAG_INT_ARRAY)
                    || !(column.get("templates") instanceof ListTag templates)
                    || templates.isEmpty() || templates.getElementType() != Tag.TAG_COMPOUND) {
                throw new IllegalArgumentException("history credential 列不完整");
            }
            int[] rows = column.getIntArray("records"), indices = column.getIntArray("indices");
            long[] generations = column.getLongArray("generations");
            if (rows.length == 0 || rows.length > records.size()
                    || rows.length != indices.length || rows.length != generations.length
                    || templates.size() > rows.length) {
                throw new IllegalArgumentException("history credential 数组长度不一致");
            }
            boolean[] used = new boolean[templates.size()];
            int previous = -1;
            for (int i = 0; i < rows.length; i++) {
                int row = rows[i], index = indices[i];
                if (row <= previous || row >= records.size() || index < 0 || index >= templates.size()
                        || generations[i] <= 0 || records.get(row).contains(field)) {
                    throw new IllegalArgumentException("history credential 索引/重复字段无效");
                }
                previous = row;
                used[index] = true;
                CompoundTag credential = templates.getCompound(index).copy();
                if (credential.contains("generation")) {
                    throw new IllegalArgumentException("history credential 模板覆盖 generation");
                }
                credential.putLong("generation", generations[i]);
                records.get(row).put(field, credential);
            }
            for (boolean value : used) if (!value) {
                throw new IllegalArgumentException("history credential 含未使用模板");
            }
        }
    }
}
