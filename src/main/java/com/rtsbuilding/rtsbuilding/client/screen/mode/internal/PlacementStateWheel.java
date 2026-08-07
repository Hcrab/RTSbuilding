package com.rtsbuilding.rtsbuilding.client.screen.mode.internal;

import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.client.util.RtsGuiVectorRenderer;
import com.rtsbuilding.rtsbuilding.uikit.theme.ModeWheelStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
 * 为下一次 RTS 放置选择完整 BlockState 的客户端轮盘。
 *
 * <p>26.1 的 GUI 提取器不再允许屏幕直接向共享世界缓冲提交方块模型，因此此实现用
 * 属性短标签表达每个候选状态。候选组合、命中、分页和最终预设仍与主线一致；它不修改
 * 世界中的既有方块，也不自行发送网络请求。</p>
 */
public final class PlacementStateWheel {
    private static final int PAGE_SIZE = 8;
    private static final int CHOICE_LIMIT = 128;
    private static final int OPTION_DISTANCE = 60;
    private static final int OPTION_RADIUS = 16;
    private static final int OPTION_HIT_RADIUS = 21;
    private static final int PAGE_BUTTON_OFFSET = 92;
    private static final int PAGE_BUTTON_RADIUS = 15;
    private static final int EDGE_PADDING = 118;

    private final List<RotationProperty> properties = new ArrayList<>();
    private final List<BlockState> placementChoices = new ArrayList<>();
    private boolean open;
    private int centerX;
    private int centerY;
    private int placementPage;

    public boolean isOpen() {
        return this.open;
    }

    /**
     * 以当前幽灵方块状态打开轮盘；没有可安全切换的属性时返回 false。
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
            reset();
            return false;
        }
        this.centerX = clampCenter(mouseX, screenWidth);
        this.centerY = clampCenter(mouseY, screenHeight);
        this.placementPage = 0;
        this.open = true;
        return true;
    }

    public void close() {
        reset();
    }

    public void closeImmediately() {
        reset();
    }

    public PlacementChoice hoveredChoice(double mouseX, double mouseY) {
        int index = hoveredPlacementIndex(mouseX, mouseY);
        return index < 0 ? null : new PlacementChoice(this.placementChoices.get(index));
    }

    public boolean cyclePlacementPage(int delta) {
        int pageCount = placementPageCount();
        if (!this.open || pageCount <= 1 || delta == 0) {
            return false;
        }
        this.placementPage = Math.floorMod(
                this.placementPage + Integer.signum(delta), pageCount);
        return true;
    }

    public boolean handlePlacementPageClick(double mouseX, double mouseY) {
        if (!this.open || placementPageCount() <= 1) {
            return false;
        }
        if (insideCircle(mouseX, mouseY,
                this.centerX - PAGE_BUTTON_OFFSET, this.centerY,
                PAGE_BUTTON_RADIUS + 3)) {
            return cyclePlacementPage(-1);
        }
        if (insideCircle(mouseX, mouseY,
                this.centerX + PAGE_BUTTON_OFFSET, this.centerY,
                PAGE_BUTTON_RADIUS + 3)) {
            return cyclePlacementPage(1);
        }
        return false;
    }

    public void render(
            GuiGraphicsExtractor graphics,
            Font font,
            int mouseX,
            int mouseY) {
        if (!this.open) {
            return;
        }
        int hoveredIndex = hoveredPlacementIndex(mouseX, mouseY);
        int pageStart = this.placementPage * PAGE_SIZE;
        int optionCount = placementPageSize();

        RtsGuiVectorRenderer.drawRing(
                graphics, this.centerX, this.centerY, 45.0F, 1.5F,
                ModeWheelStyle.PLACEMENT_TRACK.toArgb());

        for (int localIndex = 0; localIndex < optionCount; localIndex++) {
            int choiceIndex = pageStart + localIndex;
            double angle = optionAngle(localIndex, optionCount);
            int optionX = this.centerX
                    + (int) Math.round(Math.cos(angle) * OPTION_DISTANCE);
            int optionY = this.centerY
                    + (int) Math.round(Math.sin(angle) * OPTION_DISTANCE);
            boolean hovered = choiceIndex == hoveredIndex;
            boolean current = choiceIndex == 0;
            int border = ModeWheelStyle.optionBorder(current, hovered ? 1.0D : 0.0D)
                    .toArgb();
            int background = ModeWheelStyle.optionBackground(current, hovered ? 1.0D : 0.0D)
                    .toArgb();
            RtsGuiVectorRenderer.fillDisc(
                    graphics, optionX, optionY, OPTION_RADIUS + 1.25F, border);
            RtsGuiVectorRenderer.fillDisc(
                    graphics, optionX, optionY, OPTION_RADIUS - 1.25F, background);
            String shortLabel = shortChoiceLabel(
                    this.placementChoices.get(choiceIndex));
            RtsClientUiUtil.drawCenteredStringNoShadow(
                    graphics, font, shortLabel, optionX, optionY - 4,
                    ModeWheelStyle.LABEL_TEXT.toArgb());
        }

        int pageCount = placementPageCount();
        if (pageCount > 1) {
            drawPageButton(graphics, font, this.centerX - PAGE_BUTTON_OFFSET,
                    this.centerY, "<", insideCircle(mouseX, mouseY,
                            this.centerX - PAGE_BUTTON_OFFSET, this.centerY,
                            PAGE_BUTTON_RADIUS + 3));
            drawPageButton(graphics, font, this.centerX + PAGE_BUTTON_OFFSET,
                    this.centerY, ">", insideCircle(mouseX, mouseY,
                            this.centerX + PAGE_BUTTON_OFFSET, this.centerY,
                            PAGE_BUTTON_RADIUS + 3));
        }

        String label = hoveredIndex >= 0
                ? placementChoiceLabel(this.placementChoices.get(hoveredIndex))
                : Component.translatable(
                        "screen.rtsbuilding.placement_state_wheel.all_properties")
                        .getString();
        if (pageCount > 1) {
            label += "  " + (this.placementPage + 1) + "/" + pageCount;
        }
        drawLabelPill(graphics, font, label, this.centerX, this.centerY + 88);
        RtsClientUiUtil.drawCenteredStringNoShadow(
                graphics, font,
                Component.translatable(pageCount > 1
                        ? "screen.rtsbuilding.placement_state_wheel.hint_paged"
                        : "screen.rtsbuilding.rotation_wheel.hint"),
                this.centerX, this.centerY + 107,
                ModeWheelStyle.HINT_TEXT.toArgb());
    }

    private void reset() {
        this.open = false;
        this.properties.clear();
        this.placementChoices.clear();
        this.placementPage = 0;
    }

    private int hoveredPlacementIndex(double mouseX, double mouseY) {
        if (!this.open) {
            return -1;
        }
        int pageStart = this.placementPage * PAGE_SIZE;
        int optionCount = placementPageSize();
        for (int localIndex = 0; localIndex < optionCount; localIndex++) {
            double angle = optionAngle(localIndex, optionCount);
            double optionX = this.centerX + Math.cos(angle) * OPTION_DISTANCE;
            double optionY = this.centerY + Math.sin(angle) * OPTION_DISTANCE;
            if (insideCircle(mouseX, mouseY, optionX, optionY, OPTION_HIT_RADIUS)) {
                return pageStart + localIndex;
            }
        }
        return -1;
    }

    private int placementPageCount() {
        return Math.max(1, PlacementStateCombinationPlan.pageCount(
                this.placementChoices.size(), PAGE_SIZE));
    }

    private int placementPageSize() {
        int remaining = this.placementChoices.size() - this.placementPage * PAGE_SIZE;
        return Mth.clamp(remaining, 0, PAGE_SIZE);
    }

    private static int clampCenter(double coordinate, int size) {
        if (size <= EDGE_PADDING * 2) {
            return Math.max(0, size / 2);
        }
        return Mth.clamp(
                (int) Math.round(coordinate), EDGE_PADDING, size - EDGE_PADDING);
    }

    private static double optionAngle(int index, int optionCount) {
        return -Math.PI / 2.0D + Math.PI * 2.0D * index / optionCount;
    }

    private static void drawPageButton(
            GuiGraphicsExtractor graphics,
            Font font,
            int centerX,
            int centerY,
            String label,
            boolean hovered) {
        RtsGuiVectorRenderer.fillDisc(
                graphics, centerX, centerY, PAGE_BUTTON_RADIUS + 1.25F,
                ModeWheelStyle.pageBorder(hovered).toArgb());
        RtsGuiVectorRenderer.fillDisc(
                graphics, centerX, centerY, PAGE_BUTTON_RADIUS - 1.25F,
                ModeWheelStyle.pageBackground(hovered).toArgb());
        RtsClientUiUtil.drawCenteredStringNoShadow(
                graphics, font, label, centerX, centerY - 4,
                ModeWheelStyle.LABEL_TEXT.toArgb());
    }

    private static void drawLabelPill(
            GuiGraphicsExtractor graphics,
            Font font,
            String label,
            int centerX,
            int centerY) {
        String safe = label == null ? "" : label;
        int width = font.width(safe) + 14;
        int left = centerX - width / 2;
        int right = centerX + (width + 1) / 2;
        RtsGuiVectorRenderer.fillCapsule(
                graphics, left, right, centerY + 4.5F, 15.0F,
                ModeWheelStyle.LABEL_BACKGROUND.toArgb());
        RtsClientUiUtil.drawCenteredStringNoShadow(
                graphics, font, safe, centerX, centerY,
                ModeWheelStyle.LABEL_TEXT.toArgb());
    }

    private String shortChoiceLabel(BlockState state) {
        if (state == null) {
            return "?";
        }
        for (RotationProperty property : this.properties) {
            Comparable<?> value = state.getValue(property.property());
            if (value instanceof Direction direction) {
                return direction.getName().substring(0, 1).toUpperCase(Locale.ROOT);
            }
            if (value instanceof Direction.Axis axis) {
                return axis.getName().toUpperCase(Locale.ROOT);
            }
        }
        return Integer.toString(this.placementChoices.indexOf(state) + 1);
    }

    private String placementChoiceLabel(BlockState state) {
        List<String> parts = new ArrayList<>(this.properties.size());
        for (RotationProperty property : this.properties) {
            Comparable<?> value = state.getValue(property.property());
            parts.add(property.propertyName() + "="
                    + valueName(property.property(), value));
        }
        return String.join(" · ", parts);
    }

    private <T extends Comparable<T>> void addProperty(
            BlockState state,
            Property<T> property) {
        if (!state.hasProperty(property)
                || this.properties.stream().anyMatch(
                        entry -> entry.propertyName().equals(property.getName()))) {
            return;
        }
        T current = state.getValue(property);
        List<PropertyOption> options = new ArrayList<>();
        options.add(new PropertyOption(current));
        for (T value : property.getPossibleValues()) {
            if (value.equals(current)
                    || property == BlockStateProperties.SLAB_TYPE
                    && value == SlabType.DOUBLE) {
                continue;
            }
            options.add(new PropertyOption(value));
        }
        if (options.size() > 1) {
            this.properties.add(new RotationProperty(
                    property.getName(), property, List.copyOf(options)));
        }
    }

    private static List<BlockState> buildPlacementStates(
            BlockState base,
            List<RotationProperty> properties) {
        List<BlockState> states = new ArrayList<>();
        List<Integer> optionCounts = properties.stream()
                .map(property -> property.options().size())
                .toList();
        for (int[] indices : PlacementStateCombinationPlan.combinations(
                optionCounts, CHOICE_LIMIT)) {
            BlockState state = base;
            for (int propertyIndex = 0; propertyIndex < properties.size();
                    propertyIndex++) {
                RotationProperty property = properties.get(propertyIndex);
                state = setValue(
                        state,
                        property.property(),
                        property.options().get(indices[propertyIndex]).value());
            }
            if (!states.contains(state)) {
                states.add(state);
            }
        }
        return List.copyOf(states);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState setValue(
            BlockState state,
            Property property,
            Comparable value) {
        return state.setValue(property, value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String valueName(Property property, Comparable value) {
        return property.getName(value);
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

    record RotationProperty(
            String propertyName,
            Property<?> property,
            List<PropertyOption> options) {
    }

    private record PropertyOption(Comparable<?> value) {
    }

    /** 确认项携带轮盘实际渲染的完整状态，避免属性在放置前重新漂移。 */
    public record PlacementChoice(BlockState state) {
    }
}
