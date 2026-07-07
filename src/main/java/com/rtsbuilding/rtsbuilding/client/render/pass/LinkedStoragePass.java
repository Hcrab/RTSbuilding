package com.rtsbuilding.rtsbuilding.client.render.pass;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.module.storage.StorageModule;
import com.rtsbuilding.rtsbuilding.client.record.LinkedStorageEntry;
import com.rtsbuilding.rtsbuilding.client.render.RenderPass;
import com.rtsbuilding.rtsbuilding.client.render.util.CornerBracketRenderer;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * 已链接存储方块的角支架线框渲染 pass。
 *
 * <p>为每个已绑定（链接）到存储系统的方块绘制彩色角支架线框：
 * <ul>
 *   <li><b>蓝色</b> — 双向模式（可存可取）</li>
 *   <li><b>粉色</b> — 仅提取模式</li>
 * </ul>
 *
 * <p>支持双箱子合并为一个包围盒，以及绑定/解绑展开收缩动画和模式切换色彩过渡。
 * 移植自 {@code client_old/rendering/overlay/StorageRenderer}。
 */
public final class LinkedStoragePass implements RenderPass {

    private static final double LINE_OFFSET = 0.002D;

    /** 双向模式颜色 ARGB（默认绿色 #4CAF50），由渲染设置面板控制 */
    public static int bidirectionalColor = 0xFF4CAF50;
    /** 仅提取模式颜色 ARGB（默认粉色 #FF4CD1），由渲染设置面板控制 */
    public static int extractOnlyColor = 0xFFFF4CD1;

    // ======================== ARGB 颜色缓存 ========================

    private static final CornerBracketRenderer.Rgb biColor = new CornerBracketRenderer.Rgb();
    private static final CornerBracketRenderer.Rgb extColor = new CornerBracketRenderer.Rgb();

    /** 深度层透明度 */
    private static final float DEPTH_ALPHA = 0.70F;
    /** 无深度穿墙层透明度 */
    private static final float NO_DEPTH_ALPHA = 0.25F;
    /** 雾面透明度 */
    private static final float FOG_ALPHA = 0.10F;

    /** 动画持续时间（毫秒） */
    private static final long ANIM_DURATION_MS = 300L;

    // ======================== 动画状态 ========================

    /** 每个已绑定方块位置的动画状态（实例级，与 pass 生命周期一致） */
    private final Map<BlockPos, AnimState> animStates = new HashMap<>();
    /** 上一帧的已绑定位置集合（用于检测新增/移除） */
    private Set<BlockPos> prevPositions = Collections.emptySet();
    /** 是否已初始化（首帧跳过动画） */
    private boolean initialized = false;

    /** 每个链接方块的动画状态 */
    private static final class AnimState {
        enum Phase { BINDING, BOUND, UNBINDING }

        Phase phase;
        long startTime;

        /** 冻结的包围盒（UNBINDING 时快照）。BINDING/BOUND 时可能动态更新。 */
        AABB bounds;

        /** 目标 RGB 颜色（双向蓝/仅提取粉） */
        float targetR, targetG, targetB;
        /** 当前实际渲染的 RGB 颜色（通过指数平滑逼近目标） */
        float currentR, currentG, currentB;
        /** 是否已写入过颜色首次值（用于跳过首帧颜色突变） */
        boolean colorsSet;

        AnimState(Phase phase, long now) {
            this.phase = phase;
            this.startTime = now;
        }

        float progress(long now) {
            return Math.min(1.0F, (float) (now - startTime) / (float) ANIM_DURATION_MS);
        }
    }

    // ======================== 渲染接口 ========================

    @Override
    public boolean shouldRender(Minecraft mc) {
        // 仅当绑定容器模式（bind_button）激活时才渲染已绑定方块线框
        if (!(mc.screen instanceof BuilderScreen screen)) return false;
        return screen.isBindModeActive();
    }

    @Override
    public void render(Minecraft mc, BufferAllocator alloc, PoseStack poseStack,
                       float partialTick, int frameIndex) {
        if (mc.level == null || mc.getCameraEntity() == null) return;

        RtsClientKernel kernel = RtsClientKernel.get();
        StorageModule sm = kernel.module(StorageModule.class);
        if (sm == null) return;

        var entries = sm.getLinkedStorageEntries();
        if (entries == null) return;

        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        long now = System.currentTimeMillis();

        // ── 1. 检测新增/移除 ─────────────────────────────────
        Set<BlockPos> currPositions = new HashSet<>();
        for (LinkedStorageEntry e : entries) {
            if (e.worldAvailable() && e.pos() != null) currPositions.add(e.pos());
        }

        if (!initialized) {
            // 首帧：全部初始化为 BOUND，无动画
            for (BlockPos p : currPositions) {
                if (!mc.level.hasChunk(p.getX() >> 4, p.getZ() >> 4)) continue;
                BlockState st = mc.level.getBlockState(p);
                if (st.isAir()) continue;
                AnimState a = new AnimState(AnimState.Phase.BOUND, now);
                a.bounds = computeStorageBounds(mc.level, p, st);
                animStates.put(p, a);
            }
            prevPositions = new HashSet<>(currPositions);
            initialized = true;
            return; // 首帧跳过渲染，下一帧再绘制
        }

        // 移除 → 启动 UNBINDING 动画
        for (BlockPos p : prevPositions) {
            if (!currPositions.contains(p)) {
                AnimState existing = animStates.get(p);
                if (existing != null && existing.bounds != null) {
                    AnimState ub = new AnimState(AnimState.Phase.UNBINDING, now);
                    ub.bounds = existing.bounds;
                    ub.targetR = existing.targetR;
                    ub.targetG = existing.targetG;
                    ub.targetB = existing.targetB;
                    ub.currentR = existing.currentR;
                    ub.currentG = existing.currentG;
                    ub.currentB = existing.currentB;
                    ub.colorsSet = existing.colorsSet;
                    animStates.put(p, ub);
                }
            }
        }

        // 新增 → 启动 BINDING 动画
        for (LinkedStorageEntry e : entries) {
            if (!e.worldAvailable()) continue;
            BlockPos p = e.pos();
            if (p == null || prevPositions.contains(p)) continue;
            AnimState existing = animStates.get(p);
            if (existing != null && existing.phase == AnimState.Phase.UNBINDING) {
                // 解绑动画还没放完又被绑定了 → 重新展开
                animStates.put(p, new AnimState(AnimState.Phase.BINDING, now));
            } else if (!animStates.containsKey(p)) {
                animStates.put(p, new AnimState(AnimState.Phase.BINDING, now));
            }
        }

        prevPositions = new HashSet<>(currPositions);

        // ── 2. 推进动画状态 ─────────────────────────────────
        for (Iterator<Map.Entry<BlockPos, AnimState>> it = animStates.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<BlockPos, AnimState> e = it.next();
            AnimState a = e.getValue();
            BlockPos p = e.getKey();

            switch (a.phase) {
                case BINDING:
                    // BINDING 期间持续更新包围盒（方块可能变化）
                    if (currPositions.contains(p) && mc.level.hasChunk(p.getX() >> 4, p.getZ() >> 4)) {
                        BlockState st = mc.level.getBlockState(p);
                        if (!st.isAir()) {
                            a.bounds = computeStorageBounds(mc.level, p, st);
                        }
                    }
                    if (a.progress(now) >= 1.0F) {
                        a.phase = AnimState.Phase.BOUND;
                        a.startTime = now;
                    }
                    break;
                case UNBINDING:
                    if (a.progress(now) >= 1.0F) {
                        // 动画播放完毕 → 移除
                        it.remove();
                    }
                    break;
                case BOUND:
                    if (!currPositions.contains(p)) {
                        it.remove();
                    }
                    break;
            }
        }

        // ── 3. 渲染当前链接条目（BINDING/BOUND） ──────────────
        for (LinkedStorageEntry entry : entries) {
            if (!entry.worldAvailable()) continue;
            BlockPos pos = entry.pos();
            if (pos == null || !mc.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) continue;

            BlockState state = mc.level.getBlockState(pos);
            if (state.isAir()) continue;

            AABB fullBounds = computeStorageBounds(mc.level, pos, state);

            // 确定目标颜色（从 ARGB 缓存读取 float 分量，颜色不变时零分配）
            biColor.update(bidirectionalColor);
            extColor.update(extractOnlyColor);
            float targetR = entry.isExtractOnly() ? extColor.r : biColor.r;
            float targetG = entry.isExtractOnly() ? extColor.g : biColor.g;
            float targetB = entry.isExtractOnly() ? extColor.b : biColor.b;

            AnimState a = animStates.get(pos);
            if (a != null) {
                if (!a.colorsSet) {
                    // 首次写入颜色
                    a.currentR = a.targetR = targetR;
                    a.currentG = a.targetG = targetG;
                    a.currentB = a.targetB = targetB;
                    a.colorsSet = true;
                } else {
                    // 指数平滑：每帧向目标颜色靠近（约 20 帧收敛到 95%）
                    float lerpSpeed = 0.15F;
                    a.currentR += (targetR - a.currentR) * lerpSpeed;
                    a.currentG += (targetG - a.currentG) * lerpSpeed;
                    a.currentB += (targetB - a.currentB) * lerpSpeed;
                    a.targetR = targetR;
                    a.targetG = targetG;
                    a.targetB = targetB;
                }
            }

            float renderR = a != null ? a.currentR : targetR;
            float renderG = a != null ? a.currentG : targetG;
            float renderB = a != null ? a.currentB : targetB;

            // 计算动画包围盒（BINDING 阶段从中心展开）
            AABB renderBounds = getAnimatedBounds(pos, fullBounds, now);

            double distance = cameraPos.distanceTo(renderBounds.getCenter());

            // 雾面层——在方块六个面上渲染半透明层，形成柔和光晕
            CornerBracketRenderer.renderFilledFaces(alloc.brackets(), poseStack,
                    renderBounds.minX, renderBounds.minY, renderBounds.minZ,
                    renderBounds.maxX, renderBounds.maxY, renderBounds.maxZ,
                    renderR, renderG, renderB, FOG_ALPHA);

            // 深度检测层
            CornerBracketRenderer.renderCornerBrackets(poseStack, alloc.brackets(),
                    renderBounds.minX - LINE_OFFSET, renderBounds.minY - LINE_OFFSET, renderBounds.minZ - LINE_OFFSET,
                    renderBounds.maxX + LINE_OFFSET, renderBounds.maxY + LINE_OFFSET, renderBounds.maxZ + LINE_OFFSET,
                    renderR, renderG, renderB, DEPTH_ALPHA, distance);

            // 无深度穿墙层
            CornerBracketRenderer.renderCornerBrackets(poseStack, alloc.noDepth(),
                    renderBounds.minX - LINE_OFFSET, renderBounds.minY - LINE_OFFSET, renderBounds.minZ - LINE_OFFSET,
                    renderBounds.maxX + LINE_OFFSET, renderBounds.maxY + LINE_OFFSET, renderBounds.maxZ + LINE_OFFSET,
                    renderR, renderG, renderB, NO_DEPTH_ALPHA, distance);
        }

        // ── 4. 渲染 UNBINDING 动画中的条目（收缩到中心） ────────
        for (Map.Entry<BlockPos, AnimState> ae : animStates.entrySet()) {
            AnimState a = ae.getValue();
            if (a.phase != AnimState.Phase.UNBINDING) continue;

            // t 从 1 → 0，包围盒从全尺寸收缩到中心点
            float t = 1.0F - a.progress(now);
            AABB renderBounds = expandBoundsFromCenter(a.bounds, t);

            double distance = cameraPos.distanceTo(renderBounds.getCenter());

            // 雾面
            CornerBracketRenderer.renderFilledFaces(alloc.brackets(), poseStack,
                    renderBounds.minX, renderBounds.minY, renderBounds.minZ,
                    renderBounds.maxX, renderBounds.maxY, renderBounds.maxZ,
                    a.currentR, a.currentG, a.currentB, FOG_ALPHA);

            // 深度层
            CornerBracketRenderer.renderCornerBrackets(poseStack, alloc.brackets(),
                    renderBounds.minX - LINE_OFFSET, renderBounds.minY - LINE_OFFSET, renderBounds.minZ - LINE_OFFSET,
                    renderBounds.maxX + LINE_OFFSET, renderBounds.maxY + LINE_OFFSET, renderBounds.maxZ + LINE_OFFSET,
                    a.currentR, a.currentG, a.currentB, DEPTH_ALPHA, distance);

            // 穿墙层
            CornerBracketRenderer.renderCornerBrackets(poseStack, alloc.noDepth(),
                    renderBounds.minX - LINE_OFFSET, renderBounds.minY - LINE_OFFSET, renderBounds.minZ - LINE_OFFSET,
                    renderBounds.maxX + LINE_OFFSET, renderBounds.maxY + LINE_OFFSET, renderBounds.maxZ + LINE_OFFSET,
                    a.currentR, a.currentG, a.currentB, NO_DEPTH_ALPHA, distance);
        }
    }

    @Override
    public int requiredBuffers() {
        return 4 | 8; // BRACKET_QUADS | TARGET_NO_DEPTH
    }

    // ======================== 外部生命周期 ========================

    /**
     * 清除所有动画状态——在 RTS 关闭、维度切换、连接断开时调用，
     * 防止跨会话残留旧动画条目导致内存泄漏。
     * <p>由 {@link com.rtsbuilding.rtsbuilding.client.kernel.StateEvent.RtsToggled} 事件触发。
     */
    public void clearAnimationState() {
        this.animStates.clear();
        this.prevPositions = Collections.emptySet();
        this.initialized = false;
    }

    // ======================== 包围盒动画工具 ========================

    /**
     * 根据动画阶段返回渲染包围盒：
     * <ul>
     *   <li>BINDING → 从方块中心 cubic ease-out 展开到全尺寸</li>
     *   <li>BOUND / 无动画 → 全尺寸</li>
     * </ul>
     */
    private AABB getAnimatedBounds(BlockPos pos, AABB fullBounds, long now) {
        AnimState a = animStates.get(pos);
        if (a == null || a.phase != AnimState.Phase.BINDING) return fullBounds;
        return expandBoundsFromCenter(fullBounds, a.progress(now));
    }

    /**
     * 从中心点扩大/收缩包围盒。
     *
     * @param bounds 目标包围盒（全尺寸）
     * @param t      插值因子 [0,1]，0=中心点，1=全尺寸
     * @return 插值后的包围盒
     */
    private static AABB expandBoundsFromCenter(AABB bounds, float t) {
        float clamped = Math.min(1.0F, Math.max(0.0F, t));
        double s = 1.0 - Math.pow(1.0 - clamped, 3); // cubic ease-out
        double cx = (bounds.minX + bounds.maxX) * 0.5;
        double cy = (bounds.minY + bounds.maxY) * 0.5;
        double cz = (bounds.minZ + bounds.maxZ) * 0.5;
        return new AABB(
                cx + (bounds.minX - cx) * s,
                cy + (bounds.minY - cy) * s,
                cz + (bounds.minZ - cz) * s,
                cx + (bounds.maxX - cx) * s,
                cy + (bounds.maxY - cy) * s,
                cz + (bounds.maxZ - cz) * s
        );
    }

    /**
     * 计算存储方块的包围盒。
     * <p>如果是双箱子（{@link ChestType#LEFT} 或 {@link ChestType#RIGHT}），
     * 合并两个半箱为一个包围盒。</p>
     */
    private static AABB computeStorageBounds(Level level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof ChestBlock) {
            ChestType chestType = state.getValue(ChestBlock.TYPE);
            if (chestType != ChestType.SINGLE) {
                var connectedDir = ChestBlock.getConnectedDirection(state);
                BlockPos connectedPos = pos.relative(connectedDir);
                if (level.hasChunk(connectedPos.getX() >> 4, connectedPos.getZ() >> 4)) {
                    BlockState connectedState = level.getBlockState(connectedPos);
                    if (!connectedState.isAir() && connectedState.getBlock() instanceof ChestBlock) {
                        double minX = Math.min(pos.getX(), connectedPos.getX());
                        double minY = Math.min(pos.getY(), connectedPos.getY());
                        double minZ = Math.min(pos.getZ(), connectedPos.getZ());
                        double maxX = Math.max(pos.getX(), connectedPos.getX()) + 1;
                        double maxY = Math.max(pos.getY(), connectedPos.getY()) + 1;
                        double maxZ = Math.max(pos.getZ(), connectedPos.getZ()) + 1;
                        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
                    }
                }
            }
        }
        double x = pos.getX(), y = pos.getY(), z = pos.getZ();
        return new AABB(x, y, z, x + 1.0D, y + 1.0D, z + 1.0D);
    }
}
