package com.rtsbuilding.rtsbuilding.client.screen.standalone.craftterminal;

import com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort;
import com.rtsbuilding.rtsbuilding.uicore.craftterminal.CraftTerminalSortField;

/**
 * 在共享储存排序枚举与合成终端的两个用户可见字段之间做显式转换。
 *
 * <p>共享底部栏仍可使用 MOD 排序；合成终端打开时会把这个不受支持的字段
 * 规范化为数量排序。转换集中在这里，避免屏幕渲染、tooltip 和点击各自维护
 * 一套不一致的 switch。</p>
 */
public final class CraftTerminalSortAdapter {
    private CraftTerminalSortAdapter() {
    }

    public static RtsStorageSort normalize(RtsStorageSort sort) {
        return sort == RtsStorageSort.NAME || sort == RtsStorageSort.QUANTITY
                ? sort
                : RtsStorageSort.QUANTITY;
    }

    public static CraftTerminalSortField fromStorage(RtsStorageSort sort) {
        RtsStorageSort normalized = normalize(sort);
        return normalized == RtsStorageSort.NAME
                ? CraftTerminalSortField.NAME
                : CraftTerminalSortField.QUANTITY;
    }

    public static RtsStorageSort toStorage(CraftTerminalSortField field) {
        if (field == null) {
            throw new IllegalArgumentException("field");
        }
        return field == CraftTerminalSortField.NAME
                ? RtsStorageSort.NAME
                : RtsStorageSort.QUANTITY;
    }
}
