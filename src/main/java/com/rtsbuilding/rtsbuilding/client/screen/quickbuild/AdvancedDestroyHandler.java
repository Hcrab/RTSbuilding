package com.rtsbuilding.rtsbuilding.client.screen.quickbuild;

import com.rtsbuilding.rtsbuilding.common.shape.model.ShapeFillMode;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * 高级破坏锚点系统 + 形状位置计算 + 预览数据生成。
 * <p>
 * 预览更新采用双节流：参数脏标记 + 时间冷却，避免大范围计算卡顿。
 * 锚点设立后中心固定，但仍可同步滑条更改调整大小。
 */
public final class AdvancedDestroyHandler {

    private static final long COMPUTE_COOLDOWN_MS = 100;

    // ===== 树木识别标签 =====
    private static final TagKey<Block> TAG_LOGS = TagKey.create(BuiltInRegistries.BLOCK.key(), ResourceLocation.parse("c:logs"));
    private static final TagKey<Block> TAG_LEAVES = TagKey.create(BuiltInRegistries.BLOCK.key(), ResourceLocation.parse("c:leaves"));

    private final AdvancedDestroyOptions options;

    private BlockPos anchorPos;
    private BlockPos cursorTarget;
    private Direction hitFace = Direction.DOWN;
    private boolean anchored;

    /** 当前计算出的预览方块列表（缓存） */
    private List<BlockPos> previewPositions = List.of();
    /** 伐木扫描结果（缓存） */
    private LumberScanResult lumberResult;

    // ===== 脏标记（参数变化检测） =====
    private BlockPos lastCenter;
    private AdvancedDestroySubMode lastSubMode;
    private int lastRectPx, lastRectNx, lastRectPy, lastRectNy, lastRectPz, lastRectNz;
    private ShapeFillMode lastRectFill;
    private int lastCylRadius, lastCylPh, lastCylNh;
    private ShapeFillMode lastCylFill;
    private int lastStairsCount, lastStairsRotation;
    private boolean lastStairsSymmetric;
    private int lastLumberLimit;
    private boolean lastLumberStrongMan, lastLumberAllowPlayerBlocks;

    // ===== 时间冷却 =====
    private long lastComputeTimeMs;

    public AdvancedDestroyHandler(AdvancedDestroyOptions options) {
        this.options = options;
    }

    // ======================== 锚点管理 ========================

    public boolean isAnchored() {
        return anchored;
    }

    public BlockPos getAnchorPos() {
        return anchorPos;
    }

    public void setCursorTarget(BlockPos pos) {
        this.cursorTarget = pos;
    }

    public void setHitFace(Direction face) {
        this.hitFace = face != null ? face : Direction.DOWN;
    }

    /** 设立锚点，中心冻结但预览仍可随参数变化更新 */
    public void anchor() {
        if (cursorTarget != null) {
            this.anchorPos = cursorTarget.immutable();
            this.anchored = true;
            this.lastCenter = null; // 触发立即重算
            this.lastComputeTimeMs = 0;
        }
    }

    /** 取消锚点 */
    public void clearAnchor() {
        this.anchorPos = null;
        this.anchored = false;
        this.previewPositions = List.of();
        this.lastCenter = null;
        this.lastComputeTimeMs = 0;
    }

    /** 返回当前计算中心：锚定时为 anchorPos，否则为实时命中坐标 */
    private BlockPos effectiveCenter() {
        return anchored && anchorPos != null ? anchorPos : cursorTarget;
    }

    // ======================== 预览 / 破坏数据 ========================

    /** 更新预览（节流：只在参数变化且超过冷却期时重算） */
    public void tick() {
        BlockPos center = effectiveCenter();
        if (center == null) {
            this.previewPositions = List.of();
            return;
        }

        // 检查是否有变化需要重算
        if (!needsRecompute(center)) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastComputeTimeMs < COMPUTE_COOLDOWN_MS) {
            return;
        }

        lastComputeTimeMs = now;
        snapshotParams(center);
        this.previewPositions = computePositions(center);
    }

    /** 标记需要重算（滑条变化时外部调用） */
    public void markDirty() {
        this.lastCenter = null;
        this.lastComputeTimeMs = 0;
    }

    private boolean needsRecompute(BlockPos center) {
        if (lastCenter == null || !lastCenter.equals(center)) return true;
        if (lastSubMode != options.getSubMode()) return true;
        return switch (options.getSubMode()) {
            case RECTANGLE -> lastRectPx != options.getRectPlusX() || lastRectNx != options.getRectMinusX()
                    || lastRectPy != options.getRectPlusY() || lastRectNy != options.getRectMinusY()
                    || lastRectPz != options.getRectPlusZ() || lastRectNz != options.getRectMinusZ()
                    || lastRectFill != options.getRectFillMode();
            case CYLINDER -> lastCylRadius != options.getCylinderRadius()
                    || lastCylPh != options.getCylinderPlusH() || lastCylNh != options.getCylinderMinusH()
                    || lastCylFill != options.getCylinderFillMode();
            case STAIRS -> lastStairsCount != options.getStairsCount()
                    || lastStairsRotation != options.getStairsRotation()
                    || lastStairsSymmetric != options.isStairsSymmetric();
            case LUMBER -> lastLumberLimit != options.getLumberLimit()
                    || lastLumberStrongMan != options.isLumberStrongMan()
                    || lastLumberAllowPlayerBlocks != options.isLumberAllowPlayerBlocks();
        };
    }

    private void snapshotParams(BlockPos center) {
        this.lastCenter = center;
        this.lastSubMode = options.getSubMode();
        switch (options.getSubMode()) {
            case RECTANGLE -> {
                lastRectPx = options.getRectPlusX(); lastRectNx = options.getRectMinusX();
                lastRectPy = options.getRectPlusY(); lastRectNy = options.getRectMinusY();
                lastRectPz = options.getRectPlusZ(); lastRectNz = options.getRectMinusZ();
                lastRectFill = options.getRectFillMode();
            }
            case CYLINDER -> {
                lastCylRadius = options.getCylinderRadius();
                lastCylPh = options.getCylinderPlusH(); lastCylNh = options.getCylinderMinusH();
                lastCylFill = options.getCylinderFillMode();
            }
            case STAIRS -> {
                lastStairsCount = options.getStairsCount();
                lastStairsRotation = options.getStairsRotation();
                lastStairsSymmetric = options.isStairsSymmetric();
            }
            case LUMBER -> {
                lastLumberLimit = options.getLumberLimit();
                lastLumberStrongMan = options.isLumberStrongMan();
                lastLumberAllowPlayerBlocks = options.isLumberAllowPlayerBlocks();
            }
        }
    }

    public List<BlockPos> getPreviewPositions() {
        return previewPositions;
    }

    public List<BlockPos> getDestroyPositions() {
        BlockPos center = effectiveCenter();
        if (center == null) return List.of();
        return computePositions(center);
    }

    public int getBlockCount() {
        return previewPositions.size();
    }

    public boolean hasValidPositions() {
        return !previewPositions.isEmpty();
    }

    /** 伐木扫描结果（仅 LUMBER 模式有效） */
    public LumberScanResult getLumberResult() {
        return lumberResult;
    }

    // ======================== 清理 ========================

    public void clear() {
        clearAnchor();
        this.cursorTarget = null;
        this.previewPositions = List.of();
        this.lastCenter = null;
        this.lumberResult = null;
    }

    // ======================== 形状位置计算 ========================

    private List<BlockPos> computePositions(BlockPos center) {
        return switch (options.getSubMode()) {
            case RECTANGLE -> computeRectPositions(center);
            case CYLINDER -> computeCylinderPositions(center);
            case STAIRS -> computeStairsPositions(center);
            case LUMBER -> computeLumberPositions(center);
        };
    }

    // ===== 矩形 =====

    private List<BlockPos> computeRectPositions(BlockPos center) {
        int px = options.getRectPlusX();
        int nx = options.getRectMinusX();
        int py = options.getRectPlusY();
        int ny = options.getRectMinusY();
        int pz = options.getRectPlusZ();
        int nz = options.getRectMinusZ();

        int minX = center.getX() - nx;
        int maxX = center.getX() + px;
        int minY = center.getY() - ny;
        int maxY = center.getY() + py;
        int minZ = center.getZ() - nz;
        int maxZ = center.getZ() + pz;

        List<BlockPos> all = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    all.add(new BlockPos(x, y, z));
                }
            }
        }
        return filterByFillMode(all, minX, maxX, minY, maxY, minZ, maxZ, options.getRectFillMode());
    }

    // ===== 圆柱 =====

    private List<BlockPos> computeCylinderPositions(BlockPos center) {
        int r = options.getCylinderRadius();
        int ph = options.getCylinderPlusH();
        int nh = options.getCylinderMinusH();
        int minY = center.getY() - nh;
        int maxY = center.getY() + ph;
        int r2 = r * r;
        int cx = center.getX();
        int cz = center.getZ();

        List<BlockPos> all = new ArrayList<>();
        for (int y = minY; y <= maxY; y++) {
            for (int x = cx - r; x <= cx + r; x++) {
                for (int z = cz - r; z <= cz + r; z++) {
                    int dx = x - cx;
                    int dz = z - cz;
                    if (dx * dx + dz * dz <= r2) {
                        all.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        return filterCylinderByFillMode(all, cx, cz, r2, minY, maxY, options.getCylinderFillMode());
    }

    // ===== 楼梯 =====

    private List<BlockPos> computeStairsPositions(BlockPos start) {
        int count = options.getStairsCount();
        int rotation = options.getStairsRotation();
        boolean symmetric = options.isStairsSymmetric();

        // 根据旋转确定方向向量（4向）
        int dx = 0;
        int dz = 0;
        switch (rotation) {
            case 0 -> { dx = 0;  dz = -1; }
            case 90 -> { dx = -1; dz = 0; }
            case 180 -> { dx = 0;  dz = 1; }
            case 270 -> { dx = 1;  dz = 0; }
        }
        // 每步在水平方向偏移 (dx, dz)，垂直方向偏移 -1（或 +1，对称）
        int dy = symmetric ? 1 : -1;

        List<BlockPos> result = new ArrayList<>(count);
        BlockPos current = start;
        for (int i = 0; i < count; i++) {
            result.add(current);
            current = current.offset(dx, dy, dz);
        }
        return result;
    }

    // ===== 伐木 (LUMBER) =====

    /** 伐木扫描结果 */
    public record LumberScanResult(List<BlockPos> all, int logCount, int leafCount, int mushroomCount, boolean hasPlayerBlocks, boolean exceeded) {}

    private List<BlockPos> computeLumberPositions(BlockPos center) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            this.lumberResult = new LumberScanResult(List.of(), 0, 0, 0, false, false);
            return List.of();
        }

        BlockState hitState = level.getBlockState(center);
        boolean hitIsLog = isLog(hitState);
        boolean hitIsLeaf = isLeaves(hitState);
        boolean hitIsMushroom = isMushroomBlock(hitState);

        if (!hitIsLog && !hitIsLeaf && !hitIsMushroom) {
            this.lumberResult = new LumberScanResult(List.of(), 0, 0, 0, false, false);
            return List.of();
        }

        // 如果命中树叶/菌菇，先找连通的树干
        BlockPos trunkStart = center;
        if (hitIsLeaf || hitIsMushroom) {
            trunkStart = findConnectedLog(level, center);
            if (trunkStart == null) {
                this.lumberResult = new LumberScanResult(List.of(), 0, 0, 0, false, false);
                return List.of();
            }
        }

        // 从树干开始全量 BFS
        int limit = options.effectiveLumberLimit();
        boolean allowPlayer = options.isLumberAllowPlayerBlocks();
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(trunkStart);
        visited.add(trunkStart);

        List<BlockPos> logs = new ArrayList<>();
        List<BlockPos> leaves = new ArrayList<>();
        List<BlockPos> mushrooms = new ArrayList<>();
        boolean hasPlayerPlaced = false;
        boolean exceeded = false;

        // BFS: 从原木出发，沿所有连通的原木/树叶/菌菇方块扩散
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            BlockState state = level.getBlockState(pos);

            if (isLog(state)) {
                logs.add(pos.immutable());
            } else if (isLeaves(state)) {
                leaves.add(pos.immutable());
                // 玩家放置检测：persistent=true 的树叶
                if (!allowPlayer && state.hasProperty(LeavesBlock.PERSISTENT) && state.getValue(LeavesBlock.PERSISTENT)) {
                    hasPlayerPlaced = true;
                }
            } else if (isMushroomBlock(state)) {
                mushrooms.add(pos.immutable());
            } else {
                continue;
            }

            if (visited.size() >= limit) {
                exceeded = true;
                break;
            }

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        BlockPos neighbor = pos.offset(dx, dy, dz);
                        if (!visited.contains(neighbor) && level.isLoaded(neighbor)) {
                            visited.add(neighbor);
                            BlockState nState = level.getBlockState(neighbor);
                            if (isLog(nState) || isLeaves(nState) || isMushroomBlock(nState)) {
                                queue.add(neighbor);
                            }
                        }
                    }
                }
            }
        }

        List<BlockPos> all = new ArrayList<>();
        all.addAll(logs);
        all.addAll(leaves);
        all.addAll(mushrooms);

        this.lumberResult = new LumberScanResult(all, logs.size(), leaves.size(), mushrooms.size(), hasPlayerPlaced, exceeded);
        return all;
    }

    /** 从树叶/菌菇出发，寻找连通的原木（3×3×3 范围搜索） */
    private static BlockPos findConnectedLog(Level level, BlockPos start) {
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        BlockPos neighbor = pos.offset(dx, dy, dz);
                        if (visited.contains(neighbor) || !level.isLoaded(neighbor)) continue;
                        visited.add(neighbor);
                        BlockState state = level.getBlockState(neighbor);
                        if (isLog(state)) return neighbor;
                        if (isLeaves(state) || isMushroomBlock(state)) {
                            queue.add(neighbor);
                        }
                    }
                }
            }
        }
        return null;
    }

    private static boolean isLog(BlockState state) {
        return state.is(TAG_LOGS) || state.is(BlockTags.LOGS);
    }

    private static boolean isLeaves(BlockState state) {
        return state.is(TAG_LEAVES) || state.is(BlockTags.LEAVES);
    }

    private static boolean isMushroomBlock(BlockState state) {
        return state.is(Blocks.MUSHROOM_STEM)
                || state.is(Blocks.RED_MUSHROOM_BLOCK)
                || state.is(Blocks.BROWN_MUSHROOM_BLOCK)
                || state.getBlock() instanceof MushroomBlock;
    }

    // ======================== 填充模式过滤 ========================

    private static List<BlockPos> filterByFillMode(
            List<BlockPos> all, int minX, int maxX, int minY, int maxY, int minZ, int maxZ,
            ShapeFillMode mode) {
        if (mode == ShapeFillMode.FILL) return all;
        if (mode == ShapeFillMode.SKELETON) return filterSkeleton(all, minX, maxX, minY, maxY, minZ, maxZ);
        // HOLLOW: 仅表面
        List<BlockPos> result = new ArrayList<>();
        for (BlockPos p : all) {
            int x = p.getX();
            int y = p.getY();
            int z = p.getZ();
            if (x == minX || x == maxX || y == minY || y == maxY || z == minZ || z == maxZ) {
                result.add(p);
            }
        }
        return result;
    }

    /** SKELETON 模式：仅保留 12 条棱线 */
    private static List<BlockPos> filterSkeleton(
            List<BlockPos> all, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        Set<BlockPos> skeleton = new LinkedHashSet<>();
        for (BlockPos p : all) {
            int x = p.getX();
            int y = p.getY();
            int z = p.getZ();
            boolean onEdgeX = (x == minX || x == maxX);
            boolean onEdgeY = (y == minY || y == maxY);
            boolean onEdgeZ = (z == minZ || z == maxZ);
            int edgeCount = (onEdgeX ? 1 : 0) + (onEdgeY ? 1 : 0) + (onEdgeZ ? 1 : 0);
            if (edgeCount >= 2) {
                skeleton.add(p);
            }
        }
        return new ArrayList<>(skeleton);
    }

    private static List<BlockPos> filterCylinderByFillMode(
            List<BlockPos> all, int cx, int cz, int r2, int minY, int maxY,
            ShapeFillMode mode) {
        if (mode == ShapeFillMode.FILL) return all;
        // HOLLOW: 侧面 + 顶面 + 底面
        List<BlockPos> result = new ArrayList<>();
        int r = (int) Math.sqrt(r2);
        for (BlockPos p : all) {
            int y = p.getY();
            int dx = p.getX() - cx;
            int dz = p.getZ() - cz;
            int dist2 = dx * dx + dz * dz;
            // 顶面/底面：保留该层所有圆盘内方块
            if (y == minY || y == maxY) {
                result.add(p);
                continue;
            }
            // 中间层：仅保留侧面（边缘环），即从外数1格以内
            if (dist2 > (r - 1) * (r - 1)) {
                result.add(p);
            }
        }
        return result;
    }
}
