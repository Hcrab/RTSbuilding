package com.rtsbuilding.rtsbuilding.uikit.theme;

/**
 * 主题纹理的显式渲染轨道。
 *
 * <p>渲染器只能读取该值进行路由，不能通过文件名、像素颜色或资源包来源猜测轨道。</p>
 */
public enum UiThemeRenderMode {
    /** 直接使用公开的 inactive / hover / active / pressed 四状态完整纹理。 */
    LEGACY_DIRECT,
    /** 使用单源 alpha mask / 索引纹理，并由当前色板生成状态纹理。 */
    PALETTE
}
