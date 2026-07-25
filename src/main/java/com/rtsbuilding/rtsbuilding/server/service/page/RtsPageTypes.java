package com.rtsbuilding.rtsbuilding.server.service.page;

import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Set;

/**
 * page 子包内部共享的数据类型定义。
 *
 * <p>所有类型均为包私有 record 或 enum，不暴露在 {@code page} 包之外。
 * 按用途分为以下几组：
 *
 * <ul>
 *   <li><b>条目类型</b>— {@link Entry}（单个物品条目，含原型、ID、名称、数量）、
 *   {@link FluidEntry}（流体条目，含 ID、名称、容量）</li>
 *   <li><b>链接引用</b>— {@link LinkedRefPayload}（所有链接存储的位置、名称、模式、优先级等元数据）</li>
 *   <li><b>类别选择</b>— {@link CategorySelection}（"全部"、"模组"、"创造标签页"三种过滤模式）、
 *   {@link CategorySelectionType} 枚举</li>
 * </ul>
 */
// ---- 条目类型 -----------------------------------------------------------

record Entry(ItemStack stack, String itemId, String namespace, String path, String label, long count) {
}

record FluidEntry(String fluidId, String namespace, String path, long amount, long capacity) {
}

record LinkedRefPayload(
        List<Long> positions,
        List<String> names,
        List<Byte> modes,
        List<Integer> priorities,
        List<String> iconItemIds,
        List<Boolean> worldAvailable) {
}

// ---- 类别选择 -----------------------------------------------------

record CategorySelection(CategorySelectionType type, String namespace, String tabKey) {
    static CategorySelection all() {
        return new CategorySelection(CategorySelectionType.ALL, "", "");
    }

    static CategorySelection mod(String namespace) {
        return new CategorySelection(CategorySelectionType.MOD, namespace, "");
    }

    static CategorySelection tab(String namespace, String tabKey) {
        return new CategorySelection(CategorySelectionType.TAB, namespace, tabKey);
    }

    boolean isCreativeTab() {
        return this.type == CategorySelectionType.TAB;
    }

    boolean matches(String namespace, Set<String> tabs) {
        return switch (this.type) {
            case ALL -> true;
            case MOD -> this.namespace.equals(namespace);
            case TAB -> this.namespace.equals(namespace) && tabs.contains(this.tabKey);
        };
    }
}

enum CategorySelectionType {
    ALL,
    MOD,
    TAB
}
