package com.rtsbuilding.rtsbuilding.compat;

import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;

/**
 * 大型整合包 GUI 矩阵的集成服 ACK 通道。
 *
 * <p>它只在显式配置矩阵报告时记录状态，并且不发送额外网络包。1.12 的集成服务端和
 * 客户端共享同一个类加载器，因此服务端完成方块布置/生产交互后写入的事件，可以让
 * 客户端探针摆脱“等固定几个客户端 tick”的错误时钟假设。普通客户端与专服不会启用
 * 这条诊断路径，也不负责任何生产菜单状态。</p>
 */
public final class RtsGuiCompatMatrixSync {
    private static final String MATRIX_REPORT_PROPERTY = "rtsbuilding.guiCompatMatrixReport";
    private static final String MATRIX_REPORT_ENV = "RTSBUILDING_GUI_COMPAT_MATRIX_REPORT";

    private static long setupSequence;
    private static BlockPos setupPos;
    private static String setupBlockId = "";
    private static int setupMeta = -1;
    private static String setupFailure = "";

    private static long interactionSequence;
    private static BlockPos interactionPos;
    private static String interactionFailure = "";

    private RtsGuiCompatMatrixSync() {
    }

    public static synchronized long setupSequence() {
        return setupSequence;
    }

    public static synchronized void markSetupComplete(BlockPos pos, String blockId, int meta) {
        markSetup(pos, blockId, meta, "");
    }

    public static synchronized void markSetupFailed(BlockPos pos, String blockId, int meta,
            String failure) {
        markSetup(pos, blockId, meta, isBlank(failure) ? "unknown setup failure" : failure);
    }

    private static void markSetup(BlockPos pos, String blockId, int meta, String failure) {
        if (!isEnabled()) return;
        setupPos = pos == null ? null : pos.toImmutable();
        setupBlockId = blockId == null ? "" : blockId;
        setupMeta = meta;
        setupFailure = failure == null ? "" : failure;
        setupSequence++;
    }

    public static synchronized boolean isSetupAcknowledgedAfter(long baseline, BlockPos pos,
            String blockId, int meta) {
        return setupSequence > baseline && matchesSetup(pos, blockId, meta);
    }

    public static synchronized boolean isSetupCompleteAfter(long baseline, BlockPos pos,
            String blockId, int meta) {
        return setupSequence > baseline && setupFailure.isEmpty()
                && matchesSetup(pos, blockId, meta);
    }

    /** 只应在 {@link #isSetupAcknowledgedAfter} 返回 true 后读取。空串表示成功。 */
    public static synchronized String setupFailureAfter(long baseline, BlockPos pos,
            String blockId, int meta) {
        return setupSequence > baseline && matchesSetup(pos, blockId, meta) ? setupFailure : "";
    }

    private static boolean matchesSetup(BlockPos pos, String blockId, int meta) {
        return setupMeta == meta
                && setupBlockId.equals(blockId == null ? "" : blockId)
                && setupPos != null
                && setupPos.equals(pos);
    }

    public static synchronized long interactionSequence() {
        return interactionSequence;
    }

    public static synchronized void markInteractionProcessed(BlockPos pos) {
        markInteraction(pos, "");
    }

    public static synchronized void markInteractionFailed(BlockPos pos, Throwable failure) {
        String detail = failure == null ? "unknown interaction failure"
                : failure.getClass().getName() + ": " + String.valueOf(failure.getMessage());
        markInteraction(pos, detail);
    }

    private static void markInteraction(BlockPos pos, String failure) {
        if (!isEnabled()) return;
        interactionPos = pos == null ? null : pos.toImmutable();
        interactionFailure = failure == null ? "" : failure;
        interactionSequence++;
    }

    public static synchronized boolean isInteractionAcknowledgedAfter(long baseline, BlockPos pos) {
        return interactionSequence > baseline
                && interactionPos != null
                && interactionPos.equals(pos);
    }

    public static synchronized boolean isInteractionProcessedAfter(long baseline, BlockPos pos) {
        return isInteractionAcknowledgedAfter(baseline, pos) && interactionFailure.isEmpty();
    }

    /** 只应在 {@link #isInteractionAcknowledgedAfter} 返回 true 后读取。空串表示交互正常完成。 */
    public static synchronized String interactionFailureAfter(long baseline, BlockPos pos) {
        return isInteractionAcknowledgedAfter(baseline, pos) ? interactionFailure : "";
    }

    public static boolean isEnabled() {
        return !isBlank(System.getProperty(MATRIX_REPORT_PROPERTY))
                || !isBlank(System.getenv(MATRIX_REPORT_ENV));
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
