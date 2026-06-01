package com.rtsbuilding.rtsbuilding.client.screen.interaction;

/**
 * 交互轮盘中的单个槽位（包含选项及其屏幕位置）。
 */
public record InteractionWheelSlot(InteractionOption option, int x, int y) {
}
