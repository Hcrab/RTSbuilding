package com.rtsbuilding.rtsbuilding.client.screen.mode;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Vector3f;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.client.util.RtsGuiVectorRenderer;
import com.rtsbuilding.rtsbuilding.uikit.theme.ModeWheelStyle;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 下一次 RTS 放置所使用的完整 BlockState 选择轮盘。
 *
 * <p>本类只负责候选组合、分页、命中和渲染，不修改世界中的已有方块，也不发送
 * 网络请求。确认后的状态由 {@code BuilderScreen} 转换为受限放置预设。</p>
 */
public final class PlacementStateWheel {
    private static final int OPTION_DISTANCE = 60;
    private static final int OPTION_START_DISTANCE = 24;
    private static final int OPTION_RADIUS = 15;
    private static final int OPTION_HIT_RADIUS = 20;
    private static final int RING_RADIUS = 45;
    private static final int EDGE_PADDING = 118;
    private static final int PLACEMENT_PAGE_SIZE = 8;
    private static final int PLACEMENT_CHOICE_LIMIT = 128;
    private static final int PAGE_BUTTON_OFFSET = 92;
    private static final int PAGE_BUTTON_RADIUS = 15;
    private static final long OPEN_DURATION_MS = 180L;
    private static final long CLOSE_DURATION_MS = 130L;
    private static final float HOVER_SPEED_PER_SECOND = 14.0F;

    private final List<RotationProperty> properties = new ArrayList<>();
    private final List<BlockState> placementChoices = new ArrayList<>();
    private boolean open;
    private boolean closing;
    private int centerX;
    private int centerY;
    private int placementPage;
    private float cameraYaw;
    private float cameraPitch;
    private long transitionStartedAtMs;
    private long lastRenderAtMs;
    private float closingStartedAtProgress;
    private float[] hoverProgress = new float[0];

    public boolean isOpen() {
        return this.open;
    }

    /**
     * 为“下一次放置”打开视觉轮盘。返回 false 表示没有可预选的安全属性。
     */
    public boolean open(
            BlockState state,
            double mouseX,
            double mouseY,
            int screenWidth,
            int screenHeight,
            float cameraYaw,
            float cameraPitch) {
        reset();
        if (state == null || state.isAir()) {
            return false;
        }

        addProperty(state, BlockStateProperties.FACING);
        addProperty(state, BlockStateProperties.FACING_HOPPER);
        addProperty(state, BlockStateProperties.HORIZONTAL_FACING);
        addProperty(state, BlockStateProperties.AXIS);
        addProperty(state, BlockStateProperties.HORIZONTAL_AXIS);
        addProperty(state, BlockStateProperties.HALF);
        addProperty(state, BlockStateProperties.SLAB_TYPE);
        addProperty(state, BlockStateProperties.ATTACH_FACE);
        addProperty(state, BlockStateProperties.ROTATION_16);
        if (this.properties.isEmpty()) {
            return false;
        }
        this.placementChoices.addAll(buildPlacementStates(state, this.properties));
        if (this.placementChoices.size() <= 1) {
            return false;
        }

        int padding = EDGE_PADDING;
        this.centerX = clampCenter(mouseX, screenWidth, padding);
        this.centerY = clampCenter(mouseY, screenHeight, padding);
        this.cameraYaw = cameraYaw;
        this.cameraPitch = cameraPitch;
        this.placementPage = 0;
        this.open = true;
        this.closing = false;
        this.transitionStartedAtMs = Util.getMillis();
        this.lastRenderAtMs = this.transitionStartedAtMs;
        this.hoverProgress = new float[placementPageSize()];
        return true;
    }

    public void close() {
        if (!this.open || this.closing) {
            return;
        }
        long now = Util.getMillis();
        this.closingStartedAtProgress = animationProgress(now);
        this.transitionStartedAtMs = now;
        this.closing = true;
    }

    /** 屏幕生命周期结束时不再有后续渲染帧，因此这里直接清理而不播放退场动画。 */
    public void closeImmediately() {
        reset();
    }

    private void reset() {
        this.open = false;
        this.closing = false;
        this.properties.clear();
        this.placementChoices.clear();
        this.placementPage = 0;
        this.hoverProgress = new float[0];
        this.closingStartedAtProgress = 0.0F;
    }

    public PlacementChoice hoveredChoice(double mouseX, double mouseY) {
        int index = hoveredPlacementIndex(mouseX, mouseY, Util.getMillis());
        return index < 0 ? null : new PlacementChoice(this.placementChoices.get(index));
    }

    /**
     * R 放置轮盘按完整状态分页，不再按属性分层。左右方向键和页码两侧按钮都调用这里。
     */
    public boolean cyclePlacementPage(int delta) {
        int pageCount = placementPageCount();
        if (!this.open || this.closing || pageCount <= 1 || delta == 0) {
            return false;
        }
        this.placementPage = Math.floorMod(this.placementPage + Integer.signum(delta), pageCount);
        this.hoverProgress = new float[placementPageSize()];
        return true;
    }

    /**
     * 命中可视翻页按钮时切页。返回 true 表示该点击已被翻页控件消费。
     */
    public boolean handlePlacementPageClick(double mouseX, double mouseY) {
        if (!this.open || this.closing || placementPageCount() <= 1) {
            return false;
        }
        if (insideCircle(mouseX, mouseY,
                this.centerX - PAGE_BUTTON_OFFSET, this.centerY, PAGE_BUTTON_RADIUS + 3)) {
            cyclePlacementPage(-1);
            return true;
        }
        if (insideCircle(mouseX, mouseY,
                this.centerX + PAGE_BUTTON_OFFSET, this.centerY, PAGE_BUTTON_RADIUS + 3)) {
            cyclePlacementPage(1);
            return true;
        }
        return false;
    }

    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        if (!this.open || this.properties.isEmpty()) {
            return;
        }
        long now = Util.getMillis();
        float progress = animationProgress(now);
        if (this.closing && progress <= 0.001F) {
            reset();
            return;
        }
        float deltaSeconds = Math.min(0.05F, Math.max(0L, now - this.lastRenderAtMs) / 1000.0F);
        this.lastRenderAtMs = now;
        renderPlacementPage(graphics, font, mouseX, mouseY, now, progress, deltaSeconds);
    }

    /**
     * R 面板每页显示最多八个完整 BlockState。楼梯因此直接得到 4 朝向 × 2 上下的
     * 八个一次点击候选；16 段角度则自然分成两页，不再出现同一方向的同心双层按钮。
     */
    private void renderPlacementPage(
            GuiGraphics graphics,
            Font font,
            int mouseX,
            int mouseY,
            long now,
            float progress,
            float deltaSeconds) {
        int hoveredIndex = hoveredPlacementIndex(mouseX, mouseY, now);
        int pageStart = this.placementPage * PLACEMENT_PAGE_SIZE;
        int optionCount = placementPageSize();
        int hoveredLocalIndex = hoveredIndex < 0 ? -1 : hoveredIndex - pageStart;
        updatePlacementHoverAnimations(hoveredLocalIndex, deltaSeconds);
        float alpha = Mth.clamp(progress, 0.0F, 1.0F);
        float distance = optionDistance(progress);

        RtsGuiVectorRenderer.drawRing(
                graphics,
                this.centerX,
                this.centerY,
                Math.round(Mth.lerp(progress, 22.0F, RING_RADIUS)),
                1.25F,
                ModeWheelStyle.multiplyAlpha(
                        ModeWheelStyle.PLACEMENT_TRACK, alpha).toArgb());
        Lighting.setupFor3DItems();
        RenderSystem.enableDepthTest();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        for (int localIndex = 0; localIndex < optionCount; localIndex++) {
            int choiceIndex = pageStart + localIndex;
            double angle = optionAngle(localIndex, optionCount);
            int optionX = this.centerX + (int) Math.round(Math.cos(angle) * distance);
            int optionY = this.centerY + (int) Math.round(Math.sin(angle) * distance);
            drawOption(
                    graphics,
                    this.placementChoices.get(choiceIndex),
                    choiceIndex == 0,
                    optionX,
                    optionY,
                    this.hoverProgress[localIndex],
                    alpha,
                    progress);
        }
        graphics.flush();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableDepthTest();
        Lighting.setupForFlatItems();
        drawCenterBrackets(graphics, alpha);

        int pageCount = placementPageCount();
        if (pageCount > 1) {
            drawPageButton(
                    graphics, font,
                    this.centerX - PAGE_BUTTON_OFFSET, this.centerY,
                    "<",
                    insideCircle(mouseX, mouseY,
                            this.centerX - PAGE_BUTTON_OFFSET, this.centerY,
                            PAGE_BUTTON_RADIUS + 3),
                    alpha);
            drawPageButton(
                    graphics, font,
                    this.centerX + PAGE_BUTTON_OFFSET, this.centerY,
                    ">",
                    insideCircle(mouseX, mouseY,
                            this.centerX + PAGE_BUTTON_OFFSET, this.centerY,
                            PAGE_BUTTON_RADIUS + 3),
                    alpha);
        }

        String label = hoveredIndex >= 0
                ? placementChoiceLabel(this.placementChoices.get(hoveredIndex))
                : Component.translatable(
                        "screen.rtsbuilding.placement_state_wheel.all_properties").getString();
        if (pageCount > 1) {
            label += "  " + (this.placementPage + 1) + "/" + pageCount;
        }
        drawLabelPill(
                graphics, font, label,
                this.centerX,
                this.centerY + 88,
                alpha);
        RtsClientUiUtil.drawCenteredStringNoShadow(
                graphics,
                font,
                Component.translatable(pageCount > 1
                        ? "screen.rtsbuilding.placement_state_wheel.hint_paged"
                        : "screen.rtsbuilding.rotation_wheel.hint").getString(),
                this.centerX,
                this.centerY + 107,
                ModeWheelStyle.multiplyAlpha(
                        ModeWheelStyle.HINT_TEXT, alpha * 0.9F).toArgb());
    }

    private int hoveredPlacementIndex(double mouseX, double mouseY, long now) {
        if (!this.open || this.closing || animationProgress(now) < 0.28F) {
            return -1;
        }
        int pageStart = this.placementPage * PLACEMENT_PAGE_SIZE;
        int optionCount = placementPageSize();
        float radius = optionDistance(animationProgress(now));
        for (int localIndex = 0; localIndex < optionCount; localIndex++) {
            double angle = optionAngle(localIndex, optionCount);
            double optionX = this.centerX + Math.cos(angle) * radius;
            double optionY = this.centerY + Math.sin(angle) * radius;
            if (insideCircle(mouseX, mouseY, optionX, optionY, OPTION_HIT_RADIUS)) {
                return pageStart + localIndex;
            }
        }
        return -1;
    }

    private void updatePlacementHoverAnimations(int hoveredIndex, float deltaSeconds) {
        int count = placementPageSize();
        if (this.hoverProgress.length != count) {
            this.hoverProgress = new float[count];
        }
        float amount = Mth.clamp(deltaSeconds * HOVER_SPEED_PER_SECOND, 0.0F, 1.0F);
        for (int i = 0; i < count; i++) {
            float target = i == hoveredIndex && !this.closing ? 1.0F : 0.0F;
            this.hoverProgress[i] = Mth.lerp(amount, this.hoverProgress[i], target);
        }
    }

    private int placementPageCount() {
        return Math.max(1, PlacementStateCombinationPlan.pageCount(
                this.placementChoices.size(), PLACEMENT_PAGE_SIZE));
    }

    private int placementPageSize() {
        int remaining = this.placementChoices.size()
                - this.placementPage * PLACEMENT_PAGE_SIZE;
        return Mth.clamp(remaining, 0, PLACEMENT_PAGE_SIZE);
    }

    private static int clampCenter(double coordinate, int size, int padding) {
        if (size <= padding * 2) {
            return Math.max(0, size / 2);
        }
        return Mth.clamp((int) Math.round(coordinate), padding, size - padding);
    }

    private void drawOption(
            GuiGraphics graphics,
            BlockState state,
            boolean current,
            int centerX,
            int centerY,
            float hover,
            float alpha,
            float openingProgress) {
        float scale = (0.72F + openingProgress * 0.28F) * (1.0F + hover * 0.12F);
        int radius = Math.max(6, Math.round(OPTION_RADIUS * scale));
        int border = ModeWheelStyle.multiplyAlpha(
                ModeWheelStyle.optionBorder(current, hover), alpha).toArgb();
        int background = ModeWheelStyle.multiplyAlpha(
                ModeWheelStyle.optionBackground(current, hover), alpha).toArgb();
        RtsGuiVectorRenderer.fillDisc(
                graphics, centerX, centerY, radius + 1.25F, border);
        RtsGuiVectorRenderer.fillDisc(
                graphics, centerX, centerY, Math.max(4.0F, radius - 1.25F),
                background);

        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(centerX, centerY + 2, 180.0F);
        float modelScale = 15.0F * scale;
        pose.scale(modelScale, -modelScale, modelScale);
        pose.mulPose(Vector3f.XP.rotationDegrees(
                25.0F - Mth.clamp(this.cameraPitch, -45.0F, 45.0F) * 0.35F));
        // Minecraft Camera 使用 PI - yaw 构造观察旋转；这里必须使用相同符号，
        // 否则玩家转过东/西方向后，轮盘模型会与实际放置结果镜像相反。
        pose.mulPose(Vector3f.YP.rotationDegrees(placementPreviewYaw(this.cameraYaw)));
        pose.translate(-0.5F, -0.5F, -0.5F);
        try {
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                    state,
                    pose,
                    graphics.bufferSource(),
                    LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY);
        } catch (RuntimeException ignored) {
            // A broken third-party model must not make the RTS screen unusable.
        } finally {
            pose.popPose();
        }
    }

    private static void drawPageButton(
            GuiGraphics graphics,
            Font font,
            int centerX,
            int centerY,
            String text,
            boolean hovered,
            float alpha) {
        int border = ModeWheelStyle.multiplyAlpha(
                ModeWheelStyle.pageBorder(hovered), alpha).toArgb();
        int background = ModeWheelStyle.multiplyAlpha(
                ModeWheelStyle.pageBackground(hovered), alpha).toArgb();
        RtsGuiVectorRenderer.fillDisc(
                graphics, centerX, centerY, PAGE_BUTTON_RADIUS + 1.25F,
                border);
        RtsGuiVectorRenderer.fillDisc(
                graphics, centerX, centerY, PAGE_BUTTON_RADIUS - 1.25F,
                background);
        RtsClientUiUtil.drawCenteredStringNoShadow(
                graphics, font, text, centerX, centerY - 4,
                ModeWheelStyle.multiplyAlpha(
                        ModeWheelStyle.LABEL_TEXT, alpha).toArgb());
    }

    private float animationProgress(long now) {
        if (!this.open) {
            return 0.0F;
        }
        if (this.closing) {
            float raw = Mth.clamp(
                    (now - this.transitionStartedAtMs) / (float) CLOSE_DURATION_MS,
                    0.0F,
                    1.0F);
            float smooth = raw * raw * (3.0F - 2.0F * raw);
            return this.closingStartedAtProgress * (1.0F - smooth);
        }
        float raw = Mth.clamp(
                (now - this.transitionStartedAtMs) / (float) OPEN_DURATION_MS,
                0.0F,
                1.0F);
        float remaining = 1.0F - raw;
        return 1.0F - remaining * remaining * remaining;
    }

    private static float optionDistance(float progress) {
        return Mth.lerp(progress, OPTION_START_DISTANCE, OPTION_DISTANCE);
    }

    private static double optionAngle(int index, int optionCount) {
        return -Math.PI / 2.0D + Math.PI * 2.0D * index / optionCount;
    }

    private void drawCenterBrackets(GuiGraphics graphics, float alpha) {
        int radius = Math.round(Mth.lerp(alpha, 13.0F, 23.0F));
        int length = 5;
        int color = ModeWheelStyle.multiplyAlpha(
                ModeWheelStyle.CENTER_BRACKET, alpha * 0.72F).toArgb();
        graphics.fill(this.centerX - radius, this.centerY - radius,
                this.centerX - radius + length, this.centerY - radius + 1, color);
        graphics.fill(this.centerX - radius, this.centerY - radius,
                this.centerX - radius + 1, this.centerY - radius + length, color);
        graphics.fill(this.centerX + radius - length, this.centerY - radius,
                this.centerX + radius, this.centerY - radius + 1, color);
        graphics.fill(this.centerX + radius - 1, this.centerY - radius,
                this.centerX + radius, this.centerY - radius + length, color);
        graphics.fill(this.centerX - radius, this.centerY + radius - 1,
                this.centerX - radius + length, this.centerY + radius, color);
        graphics.fill(this.centerX - radius, this.centerY + radius - length,
                this.centerX - radius + 1, this.centerY + radius, color);
        graphics.fill(this.centerX + radius - length, this.centerY + radius - 1,
                this.centerX + radius, this.centerY + radius, color);
        graphics.fill(this.centerX + radius - 1, this.centerY + radius - length,
                this.centerX + radius, this.centerY + radius, color);
    }

    private static void drawLabelPill(
            GuiGraphics graphics,
            Font font,
            String text,
            int centerX,
            int centerY,
            float alpha) {
        int width = font.width(text) + 14;
        int left = centerX - width / 2;
        int right = left + width;
        int background = ModeWheelStyle.multiplyAlpha(
                ModeWheelStyle.LABEL_BACKGROUND, alpha * 0.86F).toArgb();
        RtsGuiVectorRenderer.fillCapsule(
                graphics, left, right, centerY, 15.0F, background);
        RtsClientUiUtil.drawCenteredStringNoShadow(
                graphics,
                font,
                text,
                centerX,
                centerY - 4,
                ModeWheelStyle.multiplyAlpha(
                        ModeWheelStyle.LABEL_TEXT, alpha).toArgb());
    }

    private <T extends Comparable<T>> void addProperty(BlockState state, Property<T> property) {
        addProperty(this.properties, state, property);
    }

    private static <T extends Comparable<T>> void addProperty(
            List<RotationProperty> target,
            BlockState state,
            Property<T> property) {
        if (!state.hasProperty(property)) {
            return;
        }
        // FACING 与 FACING_HOPPER 的序列化名称相同；同一状态中只会命中其中之一。
        if (target.stream().anyMatch(entry -> entry.propertyName().equals(property.getName()))) {
            return;
        }
        T current = state.getValue(property);
        List<PropertyOption> options = new ArrayList<>();
        // 当前幽灵方块状态固定排在轮盘顶部，其他候选再按属性原始顺序展开。
        // 这样 R 轮盘始终以玩家此刻真正会放下的状态作为视觉基准。
        options.add(new PropertyOption(current));
        for (T value : property.getPossibleValues()) {
            if (value.equals(current)) {
                continue;
            }
            if (property == BlockStateProperties.SLAB_TYPE && value == SlabType.DOUBLE) {
                continue;
            }
            options.add(new PropertyOption(value));
        }
        if (options.size() > 1) {
            target.add(new RotationProperty(
                    property.getName(), property, List.copyOf(options)));
        }
    }

    /**
     * 把允许预设的属性做笛卡尔积，得到“一次点击即可落地”的完整状态。
     * 当前值总在每个属性的首位，因此第一项始终是打开轮盘时的幽灵状态。
     */
    private static List<BlockState> buildPlacementStates(
            BlockState base,
            List<RotationProperty> properties) {
        List<BlockState> states = new ArrayList<>();
        List<Integer> optionCounts = properties.stream()
                .map(property -> property.options().size())
                .toList();
        for (int[] indices : PlacementStateCombinationPlan.combinations(
                optionCounts, PLACEMENT_CHOICE_LIMIT)) {
            BlockState state = base;
            for (int propertyIndex = 0; propertyIndex < properties.size(); propertyIndex++) {
                RotationProperty property = properties.get(propertyIndex);
                PropertyOption option = property.options().get(indices[propertyIndex]);
                state = setValue(state, property.property(), option.value());
            }
            if (!states.contains(state)) {
                states.add(state);
            }
        }
        return List.copyOf(states);
    }

    private String placementChoiceLabel(BlockState state) {
        List<String> parts = new ArrayList<>(this.properties.size());
        for (RotationProperty property : this.properties) {
            Comparable<?> value = state.getValue(property.property());
            parts.add(Component.translatable(
                            propertyLabelKey(property.propertyName())).getString()
                    + " " + optionLabel(value).getString());
        }
        return String.join(" · ", parts);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState setValue(
            BlockState state,
            Property property,
            Comparable value) {
        return state.setValue(property, value);
    }

    private static boolean insideCircle(
            double x,
            double y,
            double centerX,
            double centerY,
            double radius) {
        double dx = x - centerX;
        double dy = y - centerY;
        return dx * dx + dy * dy <= radius * radius;
    }

    static float placementPreviewYaw(float cameraYaw) {
        return 180.0F - cameraYaw;
    }

    private static String propertyLabelKey(String propertyName) {
        if (propertyName == null) {
            return "screen.rtsbuilding.rotation_wheel.facing";
        }
        if (propertyName.contains("axis")) {
            return "screen.rtsbuilding.rotation_wheel.axis";
        }
        return switch (propertyName) {
            case "half" -> "screen.rtsbuilding.rotation_wheel.half";
            case "type" -> "screen.rtsbuilding.rotation_wheel.slab";
            case "face" -> "screen.rtsbuilding.rotation_wheel.attach_face";
            case "rotation" -> "screen.rtsbuilding.rotation_wheel.rotation";
            default -> "screen.rtsbuilding.rotation_wheel.facing";
        };
    }

    private static Component optionLabel(Comparable<?> value) {
        if (value instanceof Direction direction) {
            return Component.translatable("screen.rtsbuilding.rotation_wheel.direction."
                    + direction.getName());
        }
        if (value instanceof Direction.Axis axis) {
            return Component.translatable("screen.rtsbuilding.rotation_wheel.axis."
                    + axis.getName());
        }
        if (value instanceof Enum<?> enumValue) {
            return Component.translatable("screen.rtsbuilding.rotation_wheel.value."
                    + enumValue.name().toLowerCase(Locale.ROOT));
        }
        return Component.literal(String.valueOf(value));
    }

    record RotationProperty(
            String propertyName,
            Property<?> property,
            List<PropertyOption> options) {
    }

    private record PropertyOption(Comparable<?> value) {
    }

    /** 确认项携带轮盘实际渲染的完整状态，避免多个属性在放置前重新漂移。 */
    public record PlacementChoice(BlockState state) {
    }
}
