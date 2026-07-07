package com.rtsbuilding.rtsbuilding.client.render.pass;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rtsbuilding.rtsbuilding.client.render.RenderPass;
import com.rtsbuilding.rtsbuilding.client.render.util.CornerBracketRenderer;
import com.rtsbuilding.rtsbuilding.client.render.util.CursorRaycaster;
import com.rtsbuilding.rtsbuilding.client.screen.panel.background.ScreenBackgroundPanel;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import net.minecraft.client.Minecraft;

/**
 * 交互目标渲染 pass——基于鼠标光标的射线检测，用角支架高亮悬停方块或实体。
 * 坐标换算委托 {@link CursorRaycaster}，各渲染/交互逻辑统一复用。
 */
public final class InteractionTargetPass implements RenderPass {

    private static final double LINE_OFFSET = 0.01D;

    private static final CornerBracketRenderer.SmoothTarget smoothTarget = new CornerBracketRenderer.SmoothTarget();

    // ======================== ARGB 颜色缓存 ========================

    private static final CornerBracketRenderer.Rgb blockColor = new CornerBracketRenderer.Rgb();
    private static final CornerBracketRenderer.Rgb entityColor = new CornerBracketRenderer.Rgb();

    // ======================== 可自定义颜色 ========================

    /** 方块交互目标角支架颜色（ARGB），默认橙金 #F69C31 */
    public static int blockTargetColor = 0xFFF69C31;
    /** 实体交互目标角支架颜色（ARGB），默认青蓝 #4D99FF */
    public static int entityTargetColor = 0xFF4D99FF;

    @Override
    public boolean shouldRender(Minecraft mc) {
        return mc.screen instanceof BuilderScreen;
    }

    @Override
    public void render(Minecraft mc, BufferAllocator alloc, PoseStack poseStack, float partialTick, int frameIndex) {
        if (mc.level == null || mc.getCameraEntity() == null) return;
        if (!(mc.screen instanceof BuilderScreen screen)) return;
        // 仅当左边栏 click_button 选中时渲染交互目标高亮
        // （bind_button 模式下由 LinkedStoragePass 处理容器线框，不重复渲染）
        if (!screen.isClickButtonSelected()) return;

        // 鼠标在 UI 区域内时不渲染（UI 覆盖世界画面）
        if (!isMouseInContentArea(mc, screen)) return;
        if (screen.isMouseOverUI(guiMouseX(mc, screen), guiMouseY(mc, screen))) return;

        // 使用 RenderPipeline 统一计算的缓存射线，避免每帧重复计算
        var ray = alloc.cursorRay();
        if (ray == null) return;

        var hit = ray.raycastNearest(mc);

        // 计算目标包围盒
        boolean isBlock;
        double tMinX, tMinY, tMinZ, tMaxX, tMaxY, tMaxZ;

        if (hit.hasEntity()) {
            // 实体优先——RTS 视角下优先选中生物而非其脚下的方块
            if (hit.entityHit() == null) return;
            isBlock = false;
            var entity = hit.entityHit().getEntity();
            var bounds = entity.getBoundingBox().inflate(0.03D);
            tMinX = bounds.minX; tMinY = bounds.minY; tMinZ = bounds.minZ;
            tMaxX = bounds.maxX; tMaxY = bounds.maxY; tMaxZ = bounds.maxZ;
        } else if (hit.hasBlock()) {
            if (hit.blockHit() == null) return;
            isBlock = true;
            var pos = hit.blockHit().getBlockPos();
            double off = LINE_OFFSET;
            tMinX = pos.getX() - off; tMinY = pos.getY() - off; tMinZ = pos.getZ() - off;
            tMaxX = pos.getX() + 1 + off; tMaxY = pos.getY() + 1 + off; tMaxZ = pos.getZ() + 1 + off;
        } else {
            return;
        }

        // 平滑过渡到目标包围盒
        smoothTarget.update(tMinX, tMinY, tMinZ, tMaxX, tMaxY, tMaxZ);

        // 根据动画后的中心计算距离（厚度缩放）
        double distance = smoothTarget.centerDistanceTo(ray.origin());

        // 选择颜色：从 ARGB 缓存读取 float 分量，避免每帧位运算
        blockColor.update(blockTargetColor);
        entityColor.update(entityTargetColor);
        float r = isBlock ? blockColor.r : entityColor.r;
        float g = isBlock ? blockColor.g : entityColor.g;
        float b = isBlock ? blockColor.b : entityColor.b;

        // 深度检测层
        CornerBracketRenderer.renderCornerBrackets(poseStack, alloc.brackets(),
                smoothTarget.minX(), smoothTarget.minY(), smoothTarget.minZ(),
                smoothTarget.maxX(), smoothTarget.maxY(), smoothTarget.maxZ(),
                r, g, b, 0.9f, distance);
        // 半透明无深度层（穿墙可见）——深度测试开启时渲染
        if (BoxSelectionPass.depthTestEnabled) {
            CornerBracketRenderer.renderCornerBrackets(poseStack, alloc.noDepth(),
                    smoothTarget.minX(), smoothTarget.minY(), smoothTarget.minZ(),
                    smoothTarget.maxX(), smoothTarget.maxY(), smoothTarget.maxZ(),
                    r, g, b, CornerBracketRenderer.DEFAULT_NO_DEPTH_ALPHA, distance);
        }
    }

    @Override
    public int requiredBuffers() {
        return 4 | 8; // BRACKET_QUADS | TARGET_NO_DEPTH
    }

    // ======================== 鼠标位置工具 ========================

    private static double guiMouseX(Minecraft mc, BuilderScreen screen) {
        return mc.mouseHandler.xpos() / screen.getRtsGuiScale();
    }

    private static double guiMouseY(Minecraft mc, BuilderScreen screen) {
        return mc.mouseHandler.ypos() / screen.getRtsGuiScale();
    }

    private static boolean isMouseInContentArea(Minecraft mc, BuilderScreen screen) {
        double rtsScale = screen.getRtsGuiScale();
        var win = mc.getWindow();
        int virtualW = (int) Math.round(win.getScreenWidth() / rtsScale);
        int virtualH = (int) Math.round(win.getScreenHeight() / rtsScale);
        double mx = guiMouseX(mc, screen);
        double my = guiMouseY(mc, screen);
        int left = screen.getLeftSidebarWidth();
        int top = ScreenBackgroundPanel.BACKGROUND_TOP_Y;
        int right = virtualW - screen.getRightSidebarWidth();
        int bottom = virtualH - screen.getDownSidebarHeight();
        return mx >= left && mx < right && my >= top && my < bottom;
    }
}
