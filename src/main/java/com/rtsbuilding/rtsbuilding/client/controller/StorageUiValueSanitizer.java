package com.rtsbuilding.rtsbuilding.client.controller;

import net.minecraft.util.Mth;

import java.util.Locale;

/**
 * 储存界面输入值的纯校验器。
 *
 * <p>该类只规范化面板比例和分类 token，不读取控制器状态、不发包，也不保存玩家
 * 偏好。把这些纯规则移出 {@link StorageStateManager}，可防止储存状态编排器重新越过
 * 项目的 800 行硬门禁，并让分类协议规则有单一落点。</p>
 */
final class StorageUiValueSanitizer {
    private static final String CATEGORY_ALL = "all";
    private static final String CATEGORY_MOD_PREFIX = "mod|";
    private static final String CATEGORY_TAB_PREFIX = "tab|";

    private StorageUiValueSanitizer() {
    }

    static double clampPanelNormalized(double value) {
        if (!Double.isFinite(value)) {
            return 0.0D;
        }
        return Mth.clamp(value, 0.0D, 1.0D);
    }

    static String normalizeCategory(String category) {
        if (category == null) {
            return CATEGORY_ALL;
        }
        String value = category.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty() || CATEGORY_ALL.equals(value)) {
            return CATEGORY_ALL;
        }
        if (value.startsWith(CATEGORY_MOD_PREFIX) || value.startsWith(CATEGORY_TAB_PREFIX)) {
            return value;
        }
        return CATEGORY_MOD_PREFIX + value;
    }
}
