package com.rtsbuilding.rtsbuilding.client.screen.panel.handler;

import com.rtsbuilding.rtsbuilding.client.pathfinding.RtsClientPathfinding;
import com.rtsbuilding.rtsbuilding.client.render.util.CursorRaycaster;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 玩家移动处理器——处理 Alt+右键单击/双击的寻路逻辑。
 *
 * <p>从 {@link BuilderScreen} 提取，保持屏幕类专注于 UI 编排。
 * 所有移动相关的状态和逻辑集中在此，便于测试和复用。</p>
 */
public final class BuilderScreenMovementHandler {

    /** 上一次 Alt+右键的时刻（ms），用于双击检测以触发「飞到目标上方」。 */
    private long lastCtrlRightClickTime = 0;

    /** Alt+右键双击时间阈值（ms）。 */
    private static final long CTRL_DOUBLE_CLICK_THRESHOLD_MS = 300;

    /**
     * 处理 Alt+右键移动玩家到光标指向的方块。
     * <p>根据是否双击决定移动行为：
     * <ul>
     *   <li>单击 → 移动到目标位置（水平到达即停）</li>
     *   <li>双击 → 飞到目标上方后精准降落（含 Y 轴到达判定）</li>
     * </ul>
     *
     * @param screen 当前 BuilderScreen 实例，用于射线检测
     * @return true 表示事件已消费
     */
    public boolean handleMovePlayerActionAt(BuilderScreen screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.getCameraEntity() == null) {
            return true;
        }
        var ray = CursorRaycaster.computeCursorRay(mc, screen);
        if (ray == null) {
            return true;
        }
        BlockHitResult hit = ray.raycastBlock(mc);
        if (hit == null) {
            return true;
        }

        long now = System.currentTimeMillis();
        boolean isDoubleClick = (now - this.lastCtrlRightClickTime) < CTRL_DOUBLE_CLICK_THRESHOLD_MS;
        this.lastCtrlRightClickTime = now;

        if (isDoubleClick) {
            this.lastCtrlRightClickTime = 0;
            RtsClientPathfinding.goToAbove(hit.getBlockPos(), 1);
        } else {
            RtsClientPathfinding.goTo(hit.getBlockPos());
        }
        return true;
    }
}
