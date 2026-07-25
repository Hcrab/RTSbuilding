package com.rtsbuilding.rtsbuilding.client.screen;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;

/**
 * Forge 1.20.1 的旧包名兼容入口。
 *
 * <p>生产实现统一由 {@code screen.standalone.BuilderScreen} 持有；本类只保留
 * 旧版本事件、渲染兼容和第三方集成已经引用的二进制/源码入口，不复制任何界面状态或行为。</p>
 */
public final class BuilderScreen
        extends com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen {
    public BuilderScreen(ClientRtsController controller) {
        super(controller);
    }
}
