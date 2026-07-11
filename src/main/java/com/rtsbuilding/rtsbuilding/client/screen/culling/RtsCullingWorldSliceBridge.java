package com.rtsbuilding.rtsbuilding.client.screen.culling;

/**
 * 标记 Embeddium {@code WorldSlice} 已经接入 RTS 方块剔除。
 *
 * <p>接口本身不承载行为；它让启动校验能够检查真实的 Mixin 转换结果，避免配置未注册时静默退化。
 */
public interface RtsCullingWorldSliceBridge {
}
