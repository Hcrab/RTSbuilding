package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import net.minecraft.server.level.ServerPlayer;
import com.rtsbuilding.rtsbuilding.server.task.RtsEffectAccumulator;

/**
 * 服务操作模板——封装服务方法尾部常见的重复操作模式。
 *
 * <p>此类模板化了在存储操作（挖掘、放置、合成、传输等）之后需要执行的
 * 通用后续处理流程，消除各个 Service 实现类中的重复代码。
 * Forge 1.20.1 通过 {@link #INSTANCE} 复用同一个无状态实例。
 *
 * <p><b>提供的方法：</b>
 * <ul>
 *   <li>{@link #afterModification(ServerPlayer, RtsStorageSession)} —
 *       <b>完整四步操作</b>：强制刷新缓存 → 递增数据版本 → 推送页面 → 持久化会话。
 *       适用于存储数据实际变更的场景。</li>
 *   <li>{@link #simpleSave(ServerPlayer, RtsStorageSession)} —
 *       <b>简化保存</b>：无 forceRefresh，仅推送页面 + 持久化会话。
 *       适用于仅变更浏览器状态（如翻页、排序）等非存储数据场景。</li>
 *   <li>{@link #markDirty(ServerPlayer, RtsStorageSession)} —
 *       <b>标记脏数据</b>：强制刷新缓存 + 递增数据版本，不推送页面。
 *       适用于页面将在下一次 tick 或显式请求时自动刷新的场景。</li>
 *   <li>{@link #refreshPage(ServerPlayer, RtsStorageSession)} —
 *       <b>直接刷新页面</b>：不递增版本也不保存，适用于版本已由外部递增过的场景。</li>
 * </ul>
 *
 * <p><b>设计特点：</b>只编排已有静态服务，不持有玩家或存储状态。
 */
public final class ServiceOperationTemplate {

    public static final ServiceOperationTemplate INSTANCE = new ServiceOperationTemplate();

    private ServiceOperationTemplate() {
    }

    /**
     * 完整三板斧——存储变更后的标准操作。
     *
     * <ol>
     *   <li>强制刷新存储缓存（{@link RtsStorageTickService#forceRefresh}）</li>
     *   <li>递增数据版本（{@code session.transfer.pageDataVersion}）</li>
     *   <li>合并本 Tick 的页面刷新和会话持久化</li>
     * </ol>
     */
    public void afterModification(ServerPlayer player, RtsStorageSession session) {
        RtsStorageTickService.INSTANCE.forceRefresh(player);
        session.transfer.pageDataVersion.incrementAndGet();
        RtsEffectAccumulator.INSTANCE.markStorageViewDirty(player.getUUID(), player.level().dimension());
        RtsEffectAccumulator.INSTANCE.markPersistence(player.getUUID(), player.level().dimension());
    }

    /**
     * 简化的保存模式——无 forceRefresh，适用于仅变更浏览器状态等非存储数据的场景。
     */
    public void simpleSave(ServerPlayer player, RtsStorageSession session) {
        RtsEffectAccumulator.INSTANCE.markPersistence(player.getUUID(), player.level().dimension());
    }

    /**
     * 仅标记脏 + bump 数据版本——适用于不需要立即刷新页面的场景
     * （页面将在下一次 tick 或显式请求时自动刷新）。
     */
    public void markDirty(ServerPlayer player, RtsStorageSession session) {
        RtsStorageTickService.INSTANCE.forceRefresh(player);
        session.transfer.pageDataVersion.incrementAndGet();
    }

    /**
     * 轻量标脏：只递增页面数据版本，并唤醒下一次储存 tick 刷新。
     * <p>
     * 连锁挖掘、区域破坏这类批量工作不能在每个方块后同步
     * {@link RtsStorageTickService#forceRefresh(ServerPlayer)}，否则绑定大量储存时会把
     * 当前服务端 tick 拖长。批量工作中途使用此方法，完成时再调用
     * {@link #afterModification(ServerPlayer, RtsStorageSession)} 做完整刷新和持久化。
     */
    public void markDirtyDeferred(ServerPlayer player, RtsStorageSession session) {
        RtsStorageTickService.INSTANCE.alert(player.getUUID());
        session.transfer.pageDataVersion.incrementAndGet();
    }

    /**
     * 直接刷新页面——不 bump 版本也不保存，适用于页面版本已由外部递增过的场景。
     */
    public void refreshPage(ServerPlayer player, RtsStorageSession session) {
        RtsEffectAccumulator.INSTANCE.markStorageViewDirty(player.getUUID(), player.level().dimension());
    }
}
