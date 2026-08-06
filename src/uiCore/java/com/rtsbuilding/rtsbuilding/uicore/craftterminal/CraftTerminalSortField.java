package com.rtsbuilding.rtsbuilding.uicore.craftterminal;

/**
 * 合成终端允许玩家直接切换的两个排序字段。
 *
 * <p>该枚举只表达终端 UI 的产品语义，不依赖 Minecraft、网络分页或具体纹理。
 * 底层储存系统仍可保留更多排序方式；终端只暴露名称与数量，避免把共享枚举的
 * 所有内部选项泄漏到这个紧凑控件中。</p>
 */
public enum CraftTerminalSortField {
    NAME,
    QUANTITY;

    /** 两个用户可见字段之间确定性切换。 */
    public CraftTerminalSortField next() {
        return this == NAME ? QUANTITY : NAME;
    }
}
