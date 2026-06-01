package com.rtsbuilding.rtsbuilding.client.screen.shape;

import com.rtsbuilding.rtsbuilding.client.ClientRtsController;

/**
 * 形状轮盘槽位。
 */
public record ShapeWheelSlot(ClientRtsController.BuildShape shape, int x, int y) {
}
