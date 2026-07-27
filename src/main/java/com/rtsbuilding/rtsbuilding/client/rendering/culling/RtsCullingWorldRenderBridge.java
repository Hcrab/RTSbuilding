package com.rtsbuilding.rtsbuilding.client.rendering.culling;

/**
 * 迁移早期使用过的临时世界渲染桥。
 *
 * <p>事件订阅已经统一交给 {@code RtsVisualOverlayRenderer}；保留这个无订阅类只为
 * 避免旧引用在移植期间突然失效。它不再注册事件，也不会造成剔除框和蓝图捕获框双绘。</p>
 */
@Deprecated
public final class RtsCullingWorldRenderBridge {
    private RtsCullingWorldRenderBridge() {
    }
}
