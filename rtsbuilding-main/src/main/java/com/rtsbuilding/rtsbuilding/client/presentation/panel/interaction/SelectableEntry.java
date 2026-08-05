package com.rtsbuilding.rtsbuilding.client.presentation.panel.interaction;

public sealed interface SelectableEntry permits EntityEntry, BlockEntry {

    /** 显示名称（标签标题用）。 */
    String displayName();

    /**
     * 归一化标识：用于标签去重、高亮匹配与容器打开状态跟踪的唯一键。
     * 多方块共用一个 GUI 的条目（如大箱子左右两半）必须返回同一键，
     * 见 {@link ContainerGroupResolver}。
     */
    Object identifier();
}
