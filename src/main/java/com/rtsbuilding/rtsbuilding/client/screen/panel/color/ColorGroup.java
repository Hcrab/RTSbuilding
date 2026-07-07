package com.rtsbuilding.rtsbuilding.client.screen.panel.color;

import java.util.Collections;
import java.util.List;

/**
 * 颜色组——将多个相关的颜色条目打包，供调色盘面板统一编辑。
 *
 * <p>当设置项包含多个相关颜色（如一套配色方案的主色/辅色/边框色）时，
 * 使用此类将它们绑定在一起。调色盘面板打开后会显示所有条目的色块，
 * 点击即可切换编辑目标，无需为每个颜色单独开一个面板。</p>
 *
 * <p>用法：</p>
 * <pre>{@code
 * ColorGroup group = new ColorGroup(List.of(
 *     new ColorSlot("主色", primarySource),
 *     new ColorSlot("辅色", secondarySource),
 *     new ColorSlot("边框", borderSource)
 * ));
 * colorPickerButton.setColorGroup(group);
 * }</pre>
 */
public class ColorGroup {

    private final String groupDisplayName;
    private final List<ColorSlot> slots;

    /**
     * @param groupDisplayName 所属设置的显示名称（如"渲染设置"），显示在调色盘面板标题
     * @param slots            该设置下所有的颜色条目
     */
    public ColorGroup(String groupDisplayName, List<ColorSlot> slots) {
        if (slots == null || slots.isEmpty()) {
            throw new IllegalArgumentException("ColorGroup 至少需要一个 ColorSlot");
        }
        this.groupDisplayName = groupDisplayName;
        this.slots = List.copyOf(slots);
    }

    /** 所属设置的显示名称（如"渲染设置"） */
    public String groupDisplayName() { return groupDisplayName; }

    /** 组内所有颜色条目（不可变列表） */
    public List<ColorSlot> slots() { return slots; }

    /** 组内颜色条目数量 */
    public int size() { return slots.size(); }

    /** 获取指定索引的条目 */
    public ColorSlot slot(int index) { return slots.get(index); }

    /**
     * 创建仅包含单个条目的颜色组（兼容单色使用场景）。
     *
     * @param groupDisplayName 所属设置的显示名称
     * @param slotDisplayName   条目显示名称
     * @param source           颜色源
     */
    public static ColorGroup single(String groupDisplayName, String slotDisplayName, ColorSource source) {
        return new ColorGroup(groupDisplayName, Collections.singletonList(new ColorSlot(slotDisplayName, source)));
    }
}
