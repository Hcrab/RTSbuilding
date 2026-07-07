package com.rtsbuilding.rtsbuilding.client.util.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.rtsbuilding.rtsbuilding.client.util.render.model.TextureInfo;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 渲染指令批处理器——收集精灵图/九宫格的绘制指令，按贴图分组后批量提交。
 *
 * <p><b>为什么需要批处理？</b></p>
 * <p>每次调用 {@code g.blit()} 前，OpenGL 需要执行贴图绑定、过滤参数设置、
 * Blend 状态检查等操作。当大量小尺寸的九宫格瓷砖逐一提交时，
 * 状态切换的开销远超实际绘制的开销。</p>
 *
 * <p><b>批处理策略：</b></p>
 * <ol>
 *   <li>收集所有绘制指令到缓冲区</li>
 *   <li>{@link #flush} 时按贴图分组，同贴图的指令连续提交</li>
 *   <li>每组只设置一次过滤参数和 Blend 状态</li>
 *   <li>排序保证渲染顺序正确（按 Z 值升序）</li>
 * </ol>
 *
 * <p><b>用法：</b></p>
 * <pre>{@code
 * BatchCollector batch = new BatchCollector();
 *
 * // 收集指令（自动去重状态设置）
 * batch.nineSlice(tex, u, v, w, h, border, dstX, dstY, dstW, dstH);
 * batch.sprite(tex, u, v, rw, rh, dstX, dstY, dstW, dstH);
 *
 * // 帧末尾统一提交
 * batch.flush(guiGraphics);
 * }</pre>
 */
public final class BatchCollector {

    // ======================== 绘制指令记录 ========================

    /**
     * 单次 blit 调用所需的所有参数。
     * <p>使用 record 确保不可变性，内部无装箱/拆箱开销。</p>
     */
    record BlitCmd(
            ResourceLocation texture,
            TextureInfo texInfo,        // 用于过滤参数设置
            int dstX, int dstY, int dstW, int dstH,
            int srcX, int srcY, int srcW, int srcH,
            int texW, int texH,
            int z,                  // 预留 Z 顺序排序
            float tintR, float tintG, float tintB, float tintA  // 色调（捕获时的 shader color）
    ) {}

    // ======================== 内部状态 ========================

    /** 指令缓冲区（预分配 256 条避免频繁扩容） */
    private final List<BlitCmd> commands = new ArrayList<>(256);

    /** 缓冲区是否已启用（由 {@link #beginFrame}/{@link #endFrame} 控制） */
    private boolean frameActive;

    // ======================== 帧生命周期 ========================

    /** 开始收集当前帧的绘制指令。必须在帧渲染开始时调用。 */
    public void beginFrame() {
        this.commands.clear();
        this.frameActive = true;
    }

    /**
     * 结束当前帧的指令收集并提交所有缓冲指令。
     * <p>所有通过此收集器提交的绘制指令在此方法中真正执行。</p>
     */
    public void endFrame(GuiGraphics g) {
        if (!this.frameActive) return;
        this.frameActive = false;
        flush(g);
    }

    /**
     * 手动提交所有缓冲指令（不结束帧）。
     * <p>在帧中间需要确保指令已提交时调用（如裁剪区域切换前）。</p>
     */
    public void flush(GuiGraphics g) {
        if (commands.isEmpty()) return;

        // 按贴图分组排序（同贴图的指令连续提交，减少纹理绑定切换）
        commands.sort(Comparator.comparing(BlitCmd::texture)
                .thenComparingInt(cmd -> cmd.texInfo.filterMode().ordinal())
                .thenComparingInt(BlitCmd::z));

        try (BlendScope blend = BlendScope.normal()) {
            ResourceLocation currentTex = null;
            TextureInfo currentTexInfo = null;

            for (BlitCmd cmd : commands) {
                // 贴图变化时更新绑定 + 过滤参数
                if (cmd.texture != currentTex || cmd.texInfo != currentTexInfo) {
                    if (cmd.texInfo != currentTexInfo) {
                        currentTexInfo = cmd.texInfo;
                        FilterState.getInstance().apply(cmd.texInfo);
                    }
                    currentTex = cmd.texture;
                }

                // 色调变化时更新 shader color
                RenderSystem.setShaderColor(cmd.tintR, cmd.tintG, cmd.tintB, cmd.tintA);

                // 执行 blit
                g.blit(currentTex,
                        cmd.dstX, cmd.dstY, cmd.dstW, cmd.dstH,
                        cmd.srcX, cmd.srcY, cmd.srcW, cmd.srcH,
                        cmd.texW, cmd.texH);
            }

            // 恢复默认色调
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        }

        commands.clear();
    }

    // ======================== 指令收集 ========================

    /** 捕获当前 shader color 作为色调。 */
    private static float[] captureTint() {
        return RenderSystem.getShaderColor();
    }

    /**
     * 添加一条精灵图绘制指令。
     *
     * @param texInfo 贴图元数据（用于过滤参数和尺寸）
     * @param u       源图 X
     * @param v       源图 Y
     * @param srcW    源图宽度
     * @param srcH    源图高度
     * @param dstX    目标 X
     * @param dstY    目标 Y
     * @param dstW    目标宽度
     * @param dstH    目标高度
     */
    public void sprite(TextureInfo texInfo, int u, int v, int srcW, int srcH,
                       int dstX, int dstY, int dstW, int dstH) {
        if (!frameActive) return;
        float[] tint = captureTint();
        commands.add(new BlitCmd(
                texInfo.location(), texInfo,
                dstX, dstY, dstW, dstH,
                u, v, srcW, srcH,
                texInfo.fullWidth(), texInfo.fullHeight(),
                0, tint[0], tint[1], tint[2], tint[3]));
    }

    /**
     * 添加一条九宫格九块瓷砖的绘制指令（展开为最多 9 条 BlitCmd）。
     *
     * @param texInfo 贴图元数据
     * @param u       源区域 X
     * @param v       源区域 Y
     * @param regionW 源区域宽度
     * @param regionH 源区域高度
     * @param border  九宫格边框宽度
     * @param dstX    目标 X
     * @param dstY    目标 Y
     * @param dstW    目标宽度
     * @param dstH    目标高度
     */
    public void nineSlice(TextureInfo texInfo, int u, int v, int regionW, int regionH, int border,
                          int dstX, int dstY, int dstW, int dstH) {
        if (!frameActive) return;

        ResourceLocation tex = texInfo.location();
        int texW = texInfo.fullWidth();
        int texH = texInfo.fullHeight();
        float[] tint = captureTint();

        // 九宫格九个分区
        int srcLeft = u;
        int srcRight = u + regionW - border;
        int srcTop = v;
        int srcBottom = v + regionH - border;

        int dstLeft = dstX;
        int dstRight = dstX + dstW - border;
        int dstTop = dstY;
        int dstBottom = dstY + dstH - border;

        // 内选区尺寸
        int srcCenterW = regionW - border * 2;
        int srcCenterH = regionH - border * 2;
        int dstCenterW = dstW - border * 2;
        int dstCenterH = dstH - border * 2;

        // 按行遍历九宫格：top / center / bottom
        // 每行三个分区：left / center(平铺或拉伸) / right

        // === 第一行：top-left, top-center, top-right ===
        // top-left (四角，不缩放)
        commands.add(new BlitCmd(tex, texInfo, dstLeft, dstTop, border, border,
                srcLeft, srcTop, border, border, texW, texH, 0, tint[0], tint[1], tint[2], tint[3]));

        // top-center (水平拉伸/平铺)
        if (dstCenterW > 0 && srcCenterW > 0) {
            int tiledSrcW = Math.min(srcCenterW, dstCenterW);
            commands.add(new BlitCmd(tex, texInfo, dstLeft + border, dstTop, dstCenterW, border,
                    srcLeft + border, srcTop, tiledSrcW, border, texW, texH, 0, tint[0], tint[1], tint[2], tint[3]));
        }

        // top-right
        commands.add(new BlitCmd(tex, texInfo, dstRight, dstTop, border, border,
                srcRight, srcTop, border, border, texW, texH, 0, tint[0], tint[1], tint[2], tint[3]));

        // === 第二行：center-left, center-center, center-right ===
        if (dstCenterH > 0) {
            // center-left
            commands.add(new BlitCmd(tex, texInfo, dstLeft, dstTop + border, border, dstCenterH,
                    srcLeft, srcTop + border, border, srcCenterH, texW, texH, 0, tint[0], tint[1], tint[2], tint[3]));

            // center-center (水平和垂直都拉伸/平铺)
            if (dstCenterW > 0 && srcCenterW > 0) {
                int tiledSrcW2 = Math.min(srcCenterW, dstCenterW);
                int tiledSrcH2 = Math.min(srcCenterH, dstCenterH);
                commands.add(new BlitCmd(tex, texInfo,
                        dstLeft + border, dstTop + border, dstCenterW, dstCenterH,
                        srcLeft + border, srcTop + border, tiledSrcW2, tiledSrcH2, texW, texH, 0, tint[0], tint[1], tint[2], tint[3]));
            }

            // center-right
            commands.add(new BlitCmd(tex, texInfo, dstRight, dstTop + border, border, dstCenterH,
                    srcRight, srcTop + border, border, srcCenterH, texW, texH, 0, tint[0], tint[1], tint[2], tint[3]));
        }

        // === 第三行：bottom-left, bottom-center, bottom-right ===
        // bottom-left
        commands.add(new BlitCmd(tex, texInfo, dstLeft, dstBottom, border, border,
                srcLeft, srcBottom, border, border, texW, texH, 0, tint[0], tint[1], tint[2], tint[3]));

        // bottom-center
        if (dstCenterW > 0 && srcCenterW > 0) {
            int tiledSrcW3 = Math.min(srcCenterW, dstCenterW);
            commands.add(new BlitCmd(tex, texInfo, dstLeft + border, dstBottom, dstCenterW, border,
                    srcLeft + border, srcBottom, tiledSrcW3, border, texW, texH, 0, tint[0], tint[1], tint[2], tint[3]));
        }

        // bottom-right
        commands.add(new BlitCmd(tex, texInfo, dstRight, dstBottom, border, border,
                srcRight, srcBottom, border, border, texW, texH, 0, tint[0], tint[1], tint[2], tint[3]));
    }

    /** 当前缓冲区中的指令数量。 */
    public int size() {
        return commands.size();
    }

    /** 当前是否处于帧收集状态。 */
    public boolean isFrameActive() {
        return frameActive;
    }
}
