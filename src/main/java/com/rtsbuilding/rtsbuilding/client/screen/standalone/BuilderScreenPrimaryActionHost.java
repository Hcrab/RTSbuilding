package com.rtsbuilding.rtsbuilding.client.screen.standalone;

/**
 * 主操作路由可见的 BuilderScreen 窄接口适配器。
 *
 * <p>它刻意不暴露 Screen 的面板集合、渲染生命周期或任意公共方法，只提供主操作优先级
 * 真正需要的坐标判断和少量平台动作。这样 PrimaryActionRouter 无法逐渐吸收整个
 * BuilderScreen，也为旧版本用不同 Screen 实现同一操作顺序保留了薄插头。</p>
 */
final class BuilderScreenPrimaryActionHost {
    private final BuilderScreen screen;

    BuilderScreenPrimaryActionHost(BuilderScreen screen) {
        this.screen = screen;
    }

    void enforceBlueprintPlacementModeLock() {
        screen.enforceBlueprintPlacementModeLock();
    }

    int pendingGuiBindSlot() {
        return screen.getPendingGuiBindSlot();
    }

    boolean isWorldArea(double mouseX, double mouseY) {
        return screen.isWorldArea(mouseX, mouseY);
    }

    boolean isInsideBottomPanel(double mouseX, double mouseY) {
        return screen.isInsideBottomPanel(mouseX, mouseY);
    }

    boolean handleRangeCullingWorldAction(double mouseX, double mouseY) {
        return screen.handleRangeCullingWorldAction(mouseX, mouseY);
    }

    boolean isRangeDestroyMode() {
        return screen.isQuickBuildRangeDestroyMode();
    }

    boolean isAdvancedShapeMode() {
        return screen.isAdvancedShapeMode();
    }

    boolean tryUseMainHandItemInAir() {
        return screen.tryUseMainHandItemInAir();
    }

    boolean canUseToolSlotShapeSource() {
        return screen.canUseToolSlotShapeSource();
    }

    int selectedToolSlot() {
        return screen.getSelectedToolSlot();
    }

    boolean hasMainHandItem() {
        return screen.hasMainHandItem();
    }
}
