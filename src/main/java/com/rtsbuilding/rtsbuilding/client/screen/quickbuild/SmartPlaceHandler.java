package com.rtsbuilding.rtsbuilding.client.screen.quickbuild;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * 智能放置模式客户端逻辑处理器。
 *
 * <p>管理 FloodFill 扫描状态、预览数据缓存，并提供扫描触发
 * 和预览查询接口。通过脏标记机制避免每帧重复扫描。</p>
 */
public final class SmartPlaceHandler {

    private final SmartPlaceOptions options = new SmartPlaceOptions();

    // 锚定状态：true 时冻结预览，不再追踪鼠标
    private boolean anchored;

    // 扫描状态
    private BlockPos scanOrigin;
    private BlockPos cursorTarget;
    private FloodFillScanner.ScanResult lastScanResult;
    private boolean needsRescan = true;

    // 脏标记比对
    private BlockPos lastCursorTarget;
    private int lastFillCount = -1;
    private int lastDiameter = -1;
    private SmartPlaceMode lastMode;

    // 扫描节流
    private long lastScanTimeMs;
    private static final long SCAN_COOLDOWN_MS = 100; // 100ms 最小扫描间隔

    public SmartPlaceHandler() {}

    // ===== 参数读写 =====

    public SmartPlaceOptions getOptions() {
        return options;
    }

    public SmartPlaceMode getMode() {
        return options.mode;
    }

    public void setMode(SmartPlaceMode mode) {
        if (this.options.mode != mode) {
            this.options.mode = mode;
            needsRescan = true;
        }
    }

    public int getFillCount() {
        return options.fillCount;
    }

    public void setFillCount(int value) {
        int clamped = options.clampFillCount(value);
        if (this.options.fillCount != clamped) {
            this.options.fillCount = clamped;
            needsRescan = true;
        }
    }

    public int getDetectionDiameter() {
        return options.detectionDiameter;
    }

    public void setDetectionDiameter(int value) {
        int clamped = options.clampDiameter(value);
        if (this.options.detectionDiameter != clamped) {
            this.options.detectionDiameter = clamped;
            needsRescan = true;
        }
    }

    // ===== 扫描 =====

    /**
     * 设置扫描起始点并触发扫描。
     *
     * @param mc    Minecraft 实例
     * @param origin 玩家点击的方块位置
     */
    public void startScan(Minecraft mc, BlockPos origin) {
        this.scanOrigin = origin;
        this.needsRescan = true;
        performScan(mc);
    }

    /**
     * 每帧调用，检查是否需要重新扫描。
     * 锚定时跳过扫描（预览冻结）；追踪态根据鼠标指向变化触发重扫。
     */
    public void tick(Minecraft mc) {
        if (mc == null || mc.level == null || mc.player == null) {
            return;
        }
        // 锚定时冻结预览，不重新扫描
        if (anchored) {
            return;
        }
        // 检查参数变化
        if (this.options.fillCount != lastFillCount
                || this.options.detectionDiameter != lastDiameter
                || this.options.mode != lastMode) {
            needsRescan = true;
            lastFillCount = this.options.fillCount;
            lastDiameter = this.options.detectionDiameter;
            lastMode = this.options.mode;
        }
        // 检查鼠标指向方块变化
        if (cursorTarget != null) {
            if (lastCursorTarget == null || !lastCursorTarget.equals(cursorTarget)) {
                needsRescan = true;
                lastCursorTarget = cursorTarget;
            }
        }
        if (needsRescan) {
            performScan(mc);
        }
    }

    private void performScan(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (now - lastScanTimeMs < SCAN_COOLDOWN_MS) {
            return;
        }
        lastScanTimeMs = now;
        needsRescan = false;

        Level level = mc.level;
        // 锚定时使用 scanOrigin，追踪态使用 cursorTarget
        BlockPos origin = anchored ? scanOrigin : cursorTarget;
        if (level == null || origin == null) {
            lastScanResult = null;
            return;
        }

        // 检测起始点对空气面的偏移
        BlockPos airNeighbor = findAirNeighbor(level, origin);
        if (airNeighbor == null) {
            lastScanResult = null;
            return;
        }

        boolean fluidMode = options.mode == SmartPlaceMode.LAKE_FILL;
        lastScanResult = FloodFillScanner.scan(
                level, airNeighbor,
                options.fillCount,
                options.detectionDiameter,
                fluidMode);
        lastFillCount = options.fillCount;
        lastDiameter = options.detectionDiameter;
        lastMode = options.mode;
    }

    /**
     * 在点击方块周围寻找空气/可替换的相邻方块作为 FloodFill 起始点。
     */
    private static BlockPos findAirNeighbor(Level level, BlockPos clicked) {
        int[][] dirs = {
                {0, 1, 0}, {0, -1, 0},
                {1, 0, 0}, {-1, 0, 0},
                {0, 0, 1}, {0, 0, -1}
        };
        for (int[] dir : dirs) {
            BlockPos neighbor = clicked.offset(dir[0], dir[1], dir[2]);
            if (FloodFillScanner.isReplaceable(level, level.getBlockState(neighbor))) {
                return neighbor;
            }
        }
        // 如果点击的方块本身可替换，直接使用
        if (FloodFillScanner.isReplaceable(level, level.getBlockState(clicked))) {
            return clicked;
        }
        return null;
    }

    // ===== 预览数据查询 =====

    /** 当前扫描到的填充位置列表 */
    public List<BlockPos> getPreviewPositions() {
        return lastScanResult != null ? lastScanResult.positions() : List.of();
    }

    /** 当前扫描结果的包围盒 */
    public AABB getBoundingBox() {
        return lastScanResult != null ? lastScanResult.boundingBox() : null;
    }

    /** 当前是否有有效扫描结果 */
    public boolean hasValidResult() {
        return lastScanResult != null && lastScanResult.isValid();
    }

    /** 扫描结果总数 */
    public int getScanCount() {
        return lastScanResult != null ? lastScanResult.scannedCount() : 0;
    }

    /** 是否达到体积上限（提示用户结果被截断） */
    public boolean reachedVolumeLimit() {
        return lastScanResult != null && lastScanResult.reachedVolumeLimit();
    }

    // ===== 状态清理 =====

    /** 清空扫描状态 */
    public void clear() {
        anchored = false;
        scanOrigin = null;
        cursorTarget = null;
        lastScanResult = null;
        needsRescan = true;
        lastCursorTarget = null;
        lastFillCount = -1;
        lastDiameter = -1;
        lastMode = null;
    }

    /** 获取扫描起始点（用于确认填充时发送给服务端） */
    public BlockPos getScanOrigin() {
        return scanOrigin;
    }

    // ===== 锚定控制 =====

    /** 设置当前鼠标指向位置，供 tick() 中的追踪扫描使用。 */
    public void setCursorTarget(BlockPos pos) {
        this.cursorTarget = pos;
    }

    /** 锚定当前预览：冻结扫描结果，停止追踪鼠标。 */
    public void anchor() {
        this.anchored = true;
        this.scanOrigin = this.cursorTarget;
    }

    /** 取消锚定：清空扫描结果并恢复追踪模式，重置节流以立即重扫。 */
    public void clearAnchor() {
        this.anchored = false;
        this.scanOrigin = null;
        this.lastScanResult = null;
        this.needsRescan = true;
        this.lastScanTimeMs = 0;
    }

    /** 当前是否处于锚定状态。 */
    public boolean isAnchored() {
        return anchored;
    }
}
