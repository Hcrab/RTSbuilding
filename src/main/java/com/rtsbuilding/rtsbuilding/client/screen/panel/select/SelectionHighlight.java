package com.rtsbuilding.rtsbuilding.client.screen.panel.select;

import com.rtsbuilding.rtsbuilding.client.render.pass.EntitySelectHighlightPass;
import com.rtsbuilding.rtsbuilding.client.render.util.CornerBracketRenderer;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * 选择面板高亮状态——在 {@link SelectPanel} 与渲染管线之间传递当前悬停目标的帧级状态。
 *
 * <p>替代此前 {@code SelectPanel} 中的 {@code static} 字段方案，
 * 由 {@link BuilderScreen} 创建并注入到 {@link SelectPanel}（写入端）
 * 和 {@link EntitySelectHighlightPass}（读取端），
 * 消除全局可变共享状态的隐患。</p>
 *
 * <p>内置 {@link CornerBracketRenderer.SmoothTarget}，
 * 使角支架线框在悬停条目切换时平滑过渡到新目标包围盒，
 * 提升视觉体验。</p>
 *
 * <p>使用方式：</p>
 * <ul>
 *   <li>{@link SelectPanel#renderContent} 每帧开头调用 {@link #clear()}，</li>
 *   <li>检测到悬停时调用 {@link #set(Entity, BlockHitResult)}，</li>
 *   <li>{@link EntitySelectHighlightPass#render} 通过 {@link #getEntity()} / {@link #getBlockHit()} 读取，</li>
 *   <li>并通过 {@link #updateAndGetSmoothBounds()} 获取平滑后的包围盒。</li>
 * </ul>
 */
public final class SelectionHighlight {

    /** 包围盒外扩量（世界单位），所有角支架高亮统一使用此值 */
    public static final double INFLATE = 0.03D;

    @Nullable
    private Entity entity;
    @Nullable
    private BlockHitResult blockHit;

    /** 包围盒平滑过渡目标——帧间指数插值，避免角支架瞬间跳跃 */
    private final CornerBracketRenderer.SmoothTarget smoothTarget = new CornerBracketRenderer.SmoothTarget();

    // ======================== 写入端（SelectPanel 调用）=======================

    /** 清除当前帧的高亮状态（每帧渲染开始时调用） */
    public void clear() {
        this.entity = null;
        this.blockHit = null;
    }

    /** 设置高亮目标（实体优先于方块） */
    public void set(@Nullable Entity entity, @Nullable BlockHitResult blockHit) {
        if (entity != null) {
            this.entity = entity;
            this.blockHit = null; // 实体优先
        } else if (blockHit != null) {
            this.blockHit = blockHit;
        }
    }

    // ======================== 读取端（EntitySelectHighlightPass 调用）=======================

    @Nullable
    public Entity getEntity() {
        return this.entity;
    }

    @Nullable
    public BlockHitResult getBlockHit() {
        return this.blockHit;
    }

    // ======================== 平滑过渡（EntitySelectHighlightPass 调用）=======================

    /**
     * 推进平滑过渡动画——基于当前高亮目标的包围盒做帧间插值。
     * <p>无高亮目标时重置平滑状态，下次有目标时直接跳入无过渡。</p>
     *
     * @return 当前帧需要渲染的平滑后包围盒，无目标时返回 null
     */
    @Nullable
    public AABB updateAndGetSmoothBounds() {
        if (this.entity != null) {
            AABB bounds = this.entity.getBoundingBox().inflate(INFLATE);
            smoothTarget.update(
                    bounds.minX, bounds.minY, bounds.minZ,
                    bounds.maxX, bounds.maxY, bounds.maxZ);
            return new AABB(smoothTarget.minX(), smoothTarget.minY(), smoothTarget.minZ(),
                    smoothTarget.maxX(), smoothTarget.maxY(), smoothTarget.maxZ());
        }
        if (this.blockHit != null) {
            var pos = this.blockHit.getBlockPos();
            double off = INFLATE;
            smoothTarget.update(
                    pos.getX() - off, pos.getY() - off, pos.getZ() - off,
                    pos.getX() + 1 + off, pos.getY() + 1 + off, pos.getZ() + 1 + off);
            return new AABB(smoothTarget.minX(), smoothTarget.minY(), smoothTarget.minZ(),
                    smoothTarget.maxX(), smoothTarget.maxY(), smoothTarget.maxZ());
        }
        // 无目标 → 重置平滑状态，下次直接跳入
        smoothTarget.reset();
        return null;
    }

    /** 计算平滑后包围盒中心到指定点的距离，用于角支架厚度缩放 */
    public double smoothCenterDistanceTo(Vec3 point) {
        return smoothTarget.centerDistanceTo(point);
    }
}
