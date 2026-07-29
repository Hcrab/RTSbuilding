package com.rtsbuilding.rtsbuilding.client.screen.layout;

/**
 * 生产分类注册表整理阶段使用的行模型容器。
 *
 * <p>本类型只承载从 NeoForge/Minecraft 注册表整理出的 token、显示名和树层级，不负责
 * 绘制或命中。绘制与输入已经统一消费 Java 8 {@code BottomPanelCategoryLayout} 和
 * Core {@code BottomBarUiCategory}，因此这里不再保留第二套点击结果模型。</p>
 */
public final class CategoryTypes {

    /**
     * 底栏分类树的一行生产数据。
     *
     * @param token 用于筛选的稳定分类标识
     * @param label 已翻译或回退整理后的显示名
     * @param depth 树深度，0 为模组根行，1 为创造标签子行
     * @param expandable 是否存在可展开子行
     * @param expanded 当前是否展开
     * @param modNamespace 所属模组命名空间；“全部”行为空
     */
    public record CategoryRow(
            String token,
            String label,
            int depth,
            boolean expandable,
            boolean expanded,
            String modNamespace) {}

    private CategoryTypes() {}
}
