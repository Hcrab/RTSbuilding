package com.rtsbuilding.rtsbuilding.client.render.pass;

import com.rtsbuilding.rtsbuilding.client.render.util.CursorRaycaster;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;

/**
 * 框选状态管理器——管理三点框选（A、B、C）的完整生命周期。
 *
 * <p>流程：</p>
 * <ol>
 *   <li><b>IDLE</b>（无选中点）——鼠标悬浮显示预览方块框</li>
 *   <li><b>AWAITING_B</b>（已选 A 点）——右键点击方块设定 A，进入此状态，显示 A→光标预览</li>
 *   <li><b>AWAITING_C</b>（已选 A+B）——右键设定 B，进入此状态，显示 AB 矩形 + 高度预览，可用滚轮调整高度</li>
 *   <li><b>COMPLETE</b>（三点全选）——右键设定 C（高度），显示完整体积框</li>
 * </ol>
 */
public final class BoxSelector {

    /** 选择阶段 */
    public enum Phase {
        IDLE,
        AWAITING_B,
        AWAITING_C,
        COMPLETE
    }

    private Phase phase = Phase.IDLE;
    private BlockPos pointA;
    private BlockPos pointB;
    private BlockPos pointC;

    /** 当前鼠标悬浮的方块位置，由每帧更新传入 */
    private BlockPos hoverPos;

    /** AWAITING_C 阶段滚轮高度偏移（格），正=向上延伸，负=向下收缩 */
    private int scrollHeightOffset;

    // ======================== 查询 ========================

    public Phase getPhase() { return phase; }
    public BlockPos getPointA() { return pointA; }
    public BlockPos getPointB() { return pointB; }
    public BlockPos getPointC() { return pointC; }
    public BlockPos getHoverPos() { return hoverPos; }
    public int getScrollHeightOffset() { return scrollHeightOffset; }

    /** 获取当前已确定的最小角（A、B、C 中最小的 x/y/z） */
    public BlockPos getMinCorner() {
        if (pointA == null) return null;
        int minX = pointA.getX();
        int minY = pointA.getY();
        int minZ = pointA.getZ();
        if (pointB != null) {
            minX = Math.min(minX, pointB.getX());
            minY = Math.min(minY, pointB.getY());
            minZ = Math.min(minZ, pointB.getZ());
        }
        if (pointC != null) {
            minY = Math.min(minY, pointC.getY());
        }
        return new BlockPos(minX, minY, minZ);
    }

    /** 获取当前已确定的最大角 */
    public BlockPos getMaxCorner() {
        if (pointA == null) return null;
        int maxX = pointA.getX() + 1;
        int maxY = pointA.getY() + 1;
        int maxZ = pointA.getZ() + 1;
        if (pointB != null) {
            maxX = Math.max(maxX, pointB.getX() + 1);
            maxY = Math.max(maxY, pointB.getY() + 1);
            maxZ = Math.max(maxZ, pointB.getZ() + 1);
        }
        if (pointC != null) {
            maxY = Math.max(maxY, pointC.getY() + 1);
        }
        return new BlockPos(maxX, maxY, maxZ);
    }

    // ======================== BuilderScreen 调用入口 ========================

    /**
     * 每帧由 BuilderScreen 调用，自动进行射线检测并更新 hoverPos。
     * Ctrl 按下时使用面偏移一格的位置。
     */
    public void updateHoverFromScreen(Minecraft mc, BuilderScreen screen, boolean ctrlDown) {
        var ray = CursorRaycaster.computeCursorRay(mc, screen);
        if (ray != null) {
            var blockHit = ray.raycastBlock(mc);
            if (blockHit != null) {
                setHoverPos(ctrlDown
                        ? blockHit.getBlockPos().relative(blockHit.getDirection())
                        : blockHit.getBlockPos());
                return;
            }
        }
        setHoverPos(null);
    }

    /**
     * 处理右键点击，自动从 hoverPos 取点击位置。
     * COMPLETE 阶段：点击框选范围外的方块才取消选择，范围内的保留（避免打断拖拽摄像机）。
     */
    public void handleRightClickWithHover() {
        if (this.hoverPos == null) return;

        if (phase == Phase.COMPLETE) {
            // 点击范围外的方块时取消选择
            if (isOutsideSelection(this.hoverPos)) {
                reset();
            }
            // 点击范围内的方块不处理（留给摄像机拖拽）
            return;
        }

        handleRightClick(this.hoverPos);
    }

    /** 判断指定方块位置是否位于当前框选范围之外 */
    private boolean isOutsideSelection(BlockPos pos) {
        BlockPos min = getMinCorner();
        BlockPos max = getMaxCorner();
        if (min == null || max == null) return true;
        return pos.getX() < min.getX() || pos.getX() >= max.getX()
                || pos.getY() < min.getY() || pos.getY() >= max.getY()
                || pos.getZ() < min.getZ() || pos.getZ() >= max.getZ();
    }

    /**
     * 处理滚轮输入，在 AWAITING_C 阶段调整高度偏移。
     *
     * @return true 表示事件被消费
     */
    public boolean handleScroll(double scrollY) {
        if (phase == Phase.AWAITING_C) {
            int delta = scrollY > 0 ? 1 : (scrollY < 0 ? -1 : 0);
            if (delta != 0) {
                adjustHeight(delta);
                return true;
            }
        }
        return false;
    }

    // ======================== 内部操作 ========================

    /** 每帧更新鼠标悬浮的方块位置 */
    private void setHoverPos(@Nullable BlockPos pos) {
        this.hoverPos = pos;
    }

    /** 滚轮调整高度偏移（仅在 AWAITING_C 阶段有效） */
    private void adjustHeight(int delta) {
        if (phase == Phase.AWAITING_C) {
            this.scrollHeightOffset += delta;
        }
    }

    /**
     * 右键点击处理——推进选择阶段。
     * 在 AWAITING_C 阶段，C 点 Y 根据当前滚轮偏移计算。
     */
    private boolean handleRightClick(BlockPos clicked) {
        if (clicked == null) return false;

        switch (phase) {
            case IDLE:
                pointA = clicked.immutable();
                phase = Phase.AWAITING_B;
                scrollHeightOffset = 0;
                return true;
            case AWAITING_B:
                pointB = clicked.immutable();
                phase = Phase.AWAITING_C;
                scrollHeightOffset = 0;
                return true;
            case AWAITING_C:
                // C 点高度 = AB 中最高 Y + 滚轮偏移
                if (pointA != null && pointB != null) {
                    int baseTopY = Math.max(pointA.getY(), pointB.getY());
                    pointC = new BlockPos(clicked.getX(), baseTopY + scrollHeightOffset, clicked.getZ());
                } else {
                    pointC = clicked.immutable();
                }
                phase = Phase.COMPLETE;
                return true;
            case COMPLETE:
                return false;
        }
        return false;
    }

    /** 重置所有选择 */
    public void reset() {
        phase = Phase.IDLE;
        pointA = null;
        pointB = null;
        pointC = null;
        hoverPos = null;
        scrollHeightOffset = 0;
    }
}
