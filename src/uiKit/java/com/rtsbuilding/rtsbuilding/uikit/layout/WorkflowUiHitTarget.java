package com.rtsbuilding.rtsbuilding.uikit.layout;

import com.rtsbuilding.rtsbuilding.uicore.workflow.WorkflowUiRow;

import java.util.List;
import java.util.Objects;

/**
 * 工作流最后绘制快照的身份命中助手。
 *
 * <p>它只把已经绘制的绝对行几何解析为 entryId；不读取最新服务端列表，也不决定
 * 该 entryId 是否仍可操作。生产窗口先使用本助手锁定屏幕上真正看到的条目，再用
 * 最新快照按 entryId 做资格校验，避免列表刷新后把同一像素位置误发给替补任务。</p>
 */
public final class WorkflowUiHitTarget {
    private WorkflowUiHitTarget() {
    }

    public static Target resolve(
            WorkflowWindowLayout.Geometry geometry,
            List<WorkflowUiRow> drawnRows,
            double mouseX,
            double mouseY) {
        if (geometry == null || drawnRows == null) {
            return Target.none();
        }
        WorkflowWindowLayout.Hit control = geometry.hitAt(mouseX, mouseY);
        if (control != null) {
            return fromRow(geometry, drawnRows, control.rowIndex, control.control);
        }
        int rowIndex = geometry.rowAt(mouseX, mouseY);
        return rowIndex < 0
                ? Target.none()
                : fromRow(geometry, drawnRows, rowIndex, null);
    }

    /** 用最新业务快照按稳定 ID 查找资格，不按旧屏幕行号替换目标。 */
    public static WorkflowUiRow findByEntryId(List<WorkflowUiRow> currentRows, int entryId) {
        if (currentRows == null || entryId < 0) {
            return null;
        }
        for (WorkflowUiRow row : currentRows) {
            if (row != null && row.entryId == entryId) {
                return row;
            }
        }
        return null;
    }

    private static Target fromRow(
            WorkflowWindowLayout.Geometry geometry,
            List<WorkflowUiRow> drawnRows,
            int rowIndex,
            WorkflowWindowLayout.Control control) {
        if (rowIndex < 0 || rowIndex >= drawnRows.size()
                || geometry.rowGeometryAt(rowIndex) == null) {
            return Target.none();
        }
        WorkflowUiRow row = drawnRows.get(rowIndex);
        return row == null
                ? Target.none()
                : new Target(true, row.entryId, rowIndex, control);
    }

    /** Java 8 可编译的不可变命中结果，保留 record 原有 accessor 形状。 */
    public static final class Target {
        private final boolean hit;
        private final int entryId;
        private final int rowIndex;
        private final WorkflowWindowLayout.Control control;

        public Target(boolean hit, int entryId, int rowIndex,
                      WorkflowWindowLayout.Control control) {
            this.hit = hit;
            this.entryId = entryId;
            this.rowIndex = rowIndex;
            this.control = control;
        }

        public boolean hit() {
            return this.hit;
        }

        public int entryId() {
            return this.entryId;
        }

        public int rowIndex() {
            return this.rowIndex;
        }

        public WorkflowWindowLayout.Control control() {
            return this.control;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Target)) return false;
            Target that = (Target) other;
            return this.hit == that.hit
                    && this.entryId == that.entryId
                    && this.rowIndex == that.rowIndex
                    && Objects.equals(this.control, that.control);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.hit, this.entryId, this.rowIndex, this.control);
        }

        @Override
        public String toString() {
            return "Target[hit=" + this.hit + ", entryId=" + this.entryId
                    + ", rowIndex=" + this.rowIndex + ", control=" + this.control + "]";
        }

        public static Target none() {
            return new Target(false, -1, -1, null);
        }

        public boolean isBody() {
            return hit && control == null;
        }
    }
}
