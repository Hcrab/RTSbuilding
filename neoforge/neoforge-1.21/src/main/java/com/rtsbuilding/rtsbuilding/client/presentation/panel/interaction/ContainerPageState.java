package com.rtsbuilding.rtsbuilding.client.presentation.panel.interaction;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * 容器页状态机：管理"等待打开 → 已打开 / 超时 / 关闭"的容器页生命周期。
 *
 * <p>状态迁移：</p>
 * <ul>
 *   <li>{@link #openRequested(Object)}：发起打开请求（调用方须先关闭旧容器），进入等待打开态。</li>
 *   <li>{@link #opened(Object)}：容器打开成功（key 为 null 表示外部打开的容器），进入打开态。</li>
 *   <li>{@link #tickPending(boolean)}：等待期间逐帧推进；探测到容器已打开或超时则退出等待。</li>
 *   <li>{@link #cancelPending()} / {@link #closed()}：取消等待 / 关闭容器页。</li>
 * </ul>
 *
 * <p>该状态机不依赖 Minecraft 世界状态，探测结果由宿主（{@link InteractionPanel}）在
 * tick 中喂入，便于单测与状态推演。</p>
 */
public final class ContainerPageState {

    /** 等待服务端打开容器的超时 tick 数（约 2 秒）。 */
    public static final int PENDING_OPEN_TIMEOUT_TICKS = 40;

    /** 逐帧推进结果：无等待 / 等待中 / 已打开 / 超时。 */
    public enum TickResult {
        NONE, PENDING, OPENED, TIMED_OUT
    }

    private boolean pageOpen;
    @Nullable
    private Object activeId;
    @Nullable
    private Object pendingId;
    private int pendingTicks;

    public boolean isPageOpen() {
        return pageOpen;
    }

    public boolean hasPending() {
        return pendingId != null;
    }

    /** 当前打开的容器归一化键；外部打开的容器为 null。 */
    @Nullable
    public Object getActiveId() {
        return activeId;
    }

    /** 等待打开中的容器归一化键；无等待时为 null。 */
    @Nullable
    public Object getPendingId() {
        return pendingId;
    }

    /** 判断给定归一化键是否为当前打开的容器。 */
    public boolean sameAsActive(Object key) {
        return pageOpen && Objects.equals(activeId, key);
    }

    /** 判断给定归一化键是否正在等待打开。 */
    public boolean sameAsPending(Object key) {
        return Objects.equals(pendingId, key);
    }

    /** 发起打开请求：进入等待打开态（调用方须先关闭旧容器）。 */
    public void openRequested(Object key) {
        this.pendingId = key;
        this.pendingTicks = 0;
        this.activeId = null;
        this.pageOpen = true;
    }

    /**
     * 容器打开成功：进入打开态并清除等待。
     *
     * @param key 打开的容器归一化键；外部打开的容器传 null（活动标签由"外部容器"标签展示）。
     */
    public void opened(@Nullable Object key) {
        this.activeId = key;
        this.pendingId = null;
        this.pendingTicks = 0;
        this.pageOpen = true;
    }

    /** 取消等待中的打开请求（不影响已打开状态）。 */
    public void cancelPending() {
        this.pendingId = null;
        this.pendingTicks = 0;
    }

    /** 关闭容器页：清空全部状态。 */
    public void closed() {
        this.pageOpen = false;
        this.activeId = null;
        this.pendingId = null;
        this.pendingTicks = 0;
    }

    /**
     * 等待期逐帧推进，由宿主面板在 tick 中调用。
     *
     * @param containerOpenDetected 服务端容器是否已实际打开（如玩家 containerId 非 0）。
     * @return {@link TickResult#PENDING} 继续等待；{@link TickResult#OPENED} 表示等待结束
     *         （pending 已清除，active 将由随后的 {@link #opened} 设置）；
     *         {@link TickResult#TIMED_OUT} 表示打开超时，宿主应关闭面板。
     */
    public TickResult tickPending(boolean containerOpenDetected) {
        if (pendingId == null) return TickResult.NONE;
        if (containerOpenDetected) {
            cancelPending();
            return TickResult.OPENED;
        }
        if (++pendingTicks > PENDING_OPEN_TIMEOUT_TICKS) {
            cancelPending();
            return TickResult.TIMED_OUT;
        }
        return TickResult.PENDING;
    }

    /** 整体重置（等价于 {@link #closed()}）。 */
    public void reset() {
        closed();
    }
}
