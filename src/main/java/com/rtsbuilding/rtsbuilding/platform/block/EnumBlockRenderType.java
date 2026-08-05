package com.rtsbuilding.rtsbuilding.platform.block;

/** 共享渲染器只需要区分普通方块模型、不可见方块和 TileEntity 动画。 */
public enum EnumBlockRenderType {
    INVISIBLE,
    MODEL,
    ENTITYBLOCK_ANIMATED
}
