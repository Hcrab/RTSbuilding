package com.rtsbuilding.rtsbuilding.client.screen.mode;

import com.google.common.base.Optional;
import com.rtsbuilding.rtsbuilding.client.input.overlay.LegacyGuiGraphics;
import com.rtsbuilding.rtsbuilding.uikit.theme.ModeWheelStyle;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 下一次 RTS 放置所使用的完整 1.12 {@link IBlockState} 选择轮盘。
 *
 * <p>本类只负责安全属性候选、分页、命中与预览，不修改世界，也不发送网络请求。
 * 确认后的完整状态由 BuilderScreen 转换为受限放置预设。</p>
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
    private static final int LABEL_MAX_WIDTH = 218;
    private static final long OPEN_DURATION_MS = 180L;
    private static final long CLOSE_DURATION_MS = 130L;
    private static final float HOVER_SPEED_PER_SECOND = 14.0F;
    private static final String[] PROPERTY_ORDER = {
            "facing", "horizontal_facing", "axis", "horizontal_axis",
            "half", "face", "attach_face", "rotation"
    };

    private final List<RotationProperty<?>> properties = new ArrayList<RotationProperty<?>>();
    private final List<IBlockState> placementChoices = new ArrayList<IBlockState>();
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

    public boolean isOpen() { return this.open; }

    /** 打开“下一次放置”轮盘；没有可预选属性时返回 false。 */
    public boolean open(IBlockState state, double mouseX, double mouseY, int screenWidth,
            int screenHeight, float cameraYaw, float cameraPitch) {
        reset();
        if (state == null || state.getMaterial() == net.minecraft.block.material.Material.AIR) return false;

        for (String propertyName : PROPERTY_ORDER) addPropertyByName(state, propertyName);
        if (this.properties.isEmpty()) return false;
        this.placementChoices.addAll(buildPlacementStates(state, this.properties));
        if (this.placementChoices.size() <= 1) {
            reset();
            return false;
        }

        this.centerX = clampCenter(mouseX, screenWidth, EDGE_PADDING);
        this.centerY = clampCenter(mouseY, screenHeight, EDGE_PADDING);
        this.cameraYaw = cameraYaw;
        this.cameraPitch = cameraPitch;
        this.placementPage = 0;
        this.open = true;
        this.closing = false;
        this.transitionStartedAtMs = now();
        this.lastRenderAtMs = this.transitionStartedAtMs;
        this.hoverProgress = new float[placementPageSize()];
        return true;
    }

    public void close() {
        if (!this.open || this.closing) return;
        long timestamp = now();
        this.closingStartedAtProgress = animationProgress(timestamp);
        this.transitionStartedAtMs = timestamp;
        this.closing = true;
    }

    /** 屏幕生命周期结束后没有后续帧，因此直接清理而不播放退场动画。 */
    public void closeImmediately() { reset(); }

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
        int index = hoveredPlacementIndex(mouseX, mouseY, now());
        return index < 0 ? null : new PlacementChoice(this.placementChoices.get(index));
    }

    public boolean cyclePlacementPage(int delta) {
        int count = placementPageCount();
        if (!this.open || this.closing || count <= 1 || delta == 0) return false;
        this.placementPage = floorMod(this.placementPage + Integer.signum(delta), count);
        this.hoverProgress = new float[placementPageSize()];
        return true;
    }

    public boolean handlePlacementPageClick(double mouseX, double mouseY) {
        if (!this.open || this.closing || placementPageCount() <= 1) return false;
        if (insideCircle(mouseX, mouseY, this.centerX - PAGE_BUTTON_OFFSET,
                this.centerY, PAGE_BUTTON_RADIUS + 3)) {
            cyclePlacementPage(-1);
            return true;
        }
        if (insideCircle(mouseX, mouseY, this.centerX + PAGE_BUTTON_OFFSET,
                this.centerY, PAGE_BUTTON_RADIUS + 3)) {
            cyclePlacementPage(1);
            return true;
        }
        return false;
    }

    public void render(LegacyGuiGraphics graphics, FontRenderer font, int mouseX, int mouseY) {
        if (!this.open || this.properties.isEmpty()) return;
        long timestamp = now();
        float progress = animationProgress(timestamp);
        if (this.closing && progress <= 0.001F) {
            reset();
            return;
        }
        float deltaSeconds = Math.min(0.05F, Math.max(0L, timestamp - this.lastRenderAtMs) / 1000.0F);
        this.lastRenderAtMs = timestamp;
        renderPlacementPage(graphics, font, mouseX, mouseY, timestamp, progress, deltaSeconds);
    }

    private void renderPlacementPage(LegacyGuiGraphics graphics, FontRenderer font, int mouseX,
            int mouseY, long timestamp, float progress, float deltaSeconds) {
        int hoveredIndex = hoveredPlacementIndex(mouseX, mouseY, timestamp);
        int pageStart = this.placementPage * PLACEMENT_PAGE_SIZE;
        int optionCount = placementPageSize();
        updatePlacementHoverAnimations(hoveredIndex < 0 ? -1 : hoveredIndex - pageStart, deltaSeconds);
        float alpha = clamp(progress, 0.0F, 1.0F);
        float distance = optionDistance(progress);

        drawRing(graphics, this.centerX, this.centerY, Math.round(lerp(progress, 22.0F, RING_RADIUS)),
                1.25F, color(ModeWheelStyle.PLACEMENT_TRACK.toArgb(), alpha));
        for (int localIndex = 0; localIndex < optionCount; localIndex++) {
            int choiceIndex = pageStart + localIndex;
            double angle = optionAngle(localIndex, optionCount);
            int x = this.centerX + (int) Math.round(Math.cos(angle) * distance);
            int y = this.centerY + (int) Math.round(Math.sin(angle) * distance);
            drawOption(graphics, this.placementChoices.get(choiceIndex), choiceIndex == 0,
                    x, y, this.hoverProgress[localIndex], alpha, progress);
        }
        drawCenterBrackets(graphics, alpha);

        int pageCount = placementPageCount();
        if (pageCount > 1) {
            drawPageButton(graphics, font, this.centerX - PAGE_BUTTON_OFFSET, this.centerY, "<",
                    insideCircle(mouseX, mouseY, this.centerX - PAGE_BUTTON_OFFSET,
                            this.centerY, PAGE_BUTTON_RADIUS + 3), alpha);
            drawPageButton(graphics, font, this.centerX + PAGE_BUTTON_OFFSET, this.centerY, ">",
                    insideCircle(mouseX, mouseY, this.centerX + PAGE_BUTTON_OFFSET,
                            this.centerY, PAGE_BUTTON_RADIUS + 3), alpha);
        }

        String label = hoveredIndex >= 0
                ? placementChoiceLabel(this.placementChoices.get(hoveredIndex))
                : tr("screen.rtsbuilding.placement_state_wheel.all_properties");
        if (pageCount > 1) label += "  " + (this.placementPage + 1) + "/" + pageCount;
        drawLabelPill(graphics, font, label, this.centerX, this.centerY + 88, alpha);
        graphics.drawCenteredString(font, tr(pageCount > 1
                        ? "screen.rtsbuilding.placement_state_wheel.hint_paged"
                        : "screen.rtsbuilding.rotation_wheel.hint"),
                this.centerX, this.centerY + 107,
                color(ModeWheelStyle.HINT_TEXT.toArgb(), alpha * 0.9F));
    }

    private int hoveredPlacementIndex(double mouseX, double mouseY, long timestamp) {
        if (!this.open || this.closing || animationProgress(timestamp) < 0.28F) return -1;
        int pageStart = this.placementPage * PLACEMENT_PAGE_SIZE;
        int count = placementPageSize();
        float distance = optionDistance(animationProgress(timestamp));
        for (int localIndex = 0; localIndex < count; localIndex++) {
            double angle = optionAngle(localIndex, count);
            double x = this.centerX + Math.cos(angle) * distance;
            double y = this.centerY + Math.sin(angle) * distance;
            if (insideCircle(mouseX, mouseY, x, y, OPTION_HIT_RADIUS)) return pageStart + localIndex;
        }
        return -1;
    }

    private void updatePlacementHoverAnimations(int hoveredIndex, float deltaSeconds) {
        int count = placementPageSize();
        if (this.hoverProgress.length != count) this.hoverProgress = new float[count];
        float amount = clamp(deltaSeconds * HOVER_SPEED_PER_SECOND, 0.0F, 1.0F);
        for (int i = 0; i < count; i++) {
            this.hoverProgress[i] = lerp(amount, this.hoverProgress[i],
                    i == hoveredIndex && !this.closing ? 1.0F : 0.0F);
        }
    }

    private int placementPageCount() {
        return Math.max(1, PlacementStateCombinationPlan.pageCount(
                this.placementChoices.size(), PLACEMENT_PAGE_SIZE));
    }

    private int placementPageSize() {
        int remaining = this.placementChoices.size() - this.placementPage * PLACEMENT_PAGE_SIZE;
        return clamp(remaining, 0, PLACEMENT_PAGE_SIZE);
    }

    private void drawOption(LegacyGuiGraphics graphics, IBlockState state, boolean current,
            int centerX, int centerY, float hover, float alpha, float openingProgress) {
        float scale = (0.72F + openingProgress * 0.28F) * (1.0F + hover * 0.12F);
        int radius = Math.max(6, Math.round(OPTION_RADIUS * scale));
        fillDisc(graphics, centerX, centerY, radius + 1.25F,
                color(ModeWheelStyle.optionBorder(current, hover).toArgb(), alpha));
        fillDisc(graphics, centerX, centerY, Math.max(4.0F, radius - 1.25F),
                color(ModeWheelStyle.optionBackground(current, hover).toArgb(), alpha));

        ItemStack stack = stackForState(state);
        if (stack.isEmpty()) return;
        GlStateManager.pushMatrix();
        GlStateManager.translate(centerX, centerY + 1, 180.0F);
        GlStateManager.scale(scale, scale, 1.0F);
        GlStateManager.rotate(placementPreviewYaw(this.cameraYaw), 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(-clamp(this.cameraPitch, -45.0F, 45.0F) * 0.12F, 1.0F, 0.0F, 0.0F);
        try {
            graphics.renderItem(stack, -8, -8);
        } catch (RuntimeException ignored) {
            // 损坏的第三方模型不应让整个 RTS 屏幕失效。
        } finally {
            GlStateManager.popMatrix();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private static ItemStack stackForState(IBlockState state) {
        try {
            Block block = state.getBlock();
            Item item = Item.getItemFromBlock(block);
            if (item == null) return ItemStack.EMPTY;
            int metadata = block.getMetaFromState(state);
            return new ItemStack(item, 1, metadata);
        } catch (RuntimeException ignored) {
            return ItemStack.EMPTY;
        }
    }

    private static void drawPageButton(LegacyGuiGraphics graphics, FontRenderer font,
            int centerX, int centerY, String text, boolean hovered, float alpha) {
        fillDisc(graphics, centerX, centerY, PAGE_BUTTON_RADIUS + 1.25F,
                color(ModeWheelStyle.pageBorder(hovered).toArgb(), alpha));
        fillDisc(graphics, centerX, centerY, PAGE_BUTTON_RADIUS - 1.25F,
                color(ModeWheelStyle.pageBackground(hovered).toArgb(), alpha));
        graphics.drawCenteredString(font, text, centerX, centerY - 4,
                color(ModeWheelStyle.LABEL_TEXT.toArgb(), alpha));
    }

    private float animationProgress(long timestamp) {
        if (!this.open) return 0.0F;
        if (this.closing) {
            float raw = clamp((timestamp - this.transitionStartedAtMs) / (float) CLOSE_DURATION_MS, 0.0F, 1.0F);
            float smooth = raw * raw * (3.0F - 2.0F * raw);
            return this.closingStartedAtProgress * (1.0F - smooth);
        }
        float raw = clamp((timestamp - this.transitionStartedAtMs) / (float) OPEN_DURATION_MS, 0.0F, 1.0F);
        float remaining = 1.0F - raw;
        return 1.0F - remaining * remaining * remaining;
    }

    private void drawCenterBrackets(LegacyGuiGraphics graphics, float alpha) {
        int radius = Math.round(lerp(alpha, 13.0F, 23.0F));
        int length = 5;
        int c = color(ModeWheelStyle.CENTER_BRACKET.toArgb(), alpha * 0.72F);
        graphics.fill(this.centerX - radius, this.centerY - radius, this.centerX - radius + length, this.centerY - radius + 1, c);
        graphics.fill(this.centerX - radius, this.centerY - radius, this.centerX - radius + 1, this.centerY - radius + length, c);
        graphics.fill(this.centerX + radius - length, this.centerY - radius, this.centerX + radius, this.centerY - radius + 1, c);
        graphics.fill(this.centerX + radius - 1, this.centerY - radius, this.centerX + radius, this.centerY - radius + length, c);
        graphics.fill(this.centerX - radius, this.centerY + radius - 1, this.centerX - radius + length, this.centerY + radius, c);
        graphics.fill(this.centerX - radius, this.centerY + radius - length, this.centerX - radius + 1, this.centerY + radius, c);
        graphics.fill(this.centerX + radius - length, this.centerY + radius - 1, this.centerX + radius, this.centerY + radius, c);
        graphics.fill(this.centerX + radius - 1, this.centerY + radius - length, this.centerX + radius, this.centerY + radius, c);
    }

    private static void drawLabelPill(LegacyGuiGraphics graphics, FontRenderer font, String text,
            int centerX, int centerY, float alpha) {
        String clipped = trimToWidth(font, text, LABEL_MAX_WIDTH - 14);
        int width = Math.min(LABEL_MAX_WIDTH, font.getStringWidth(clipped) + 14);
        fillCapsule(graphics, centerX - width / 2, centerX + (width + 1) / 2, centerY,
                15.0F, color(ModeWheelStyle.LABEL_BACKGROUND.toArgb(), alpha * 0.86F));
        graphics.drawCenteredString(font, clipped, centerX, centerY - 4,
                color(ModeWheelStyle.LABEL_TEXT.toArgb(), alpha));
    }

    private void addPropertyByName(IBlockState state, String name) {
        for (IProperty<?> property : state.getPropertyKeys()) {
            if (name.equals(property.getName()) && isSafeProperty(state, property)) {
                addProperty(state, property);
                return;
            }
        }
    }

    private static boolean isSafeProperty(IBlockState state, IProperty<?> property) {
        String name = property.getName();
        Class<?> type = property.getValueClass();
        if (("facing".equals(name) || "horizontal_facing".equals(name))
                && EnumFacing.class.isAssignableFrom(type)) return true;
        if (("axis".equals(name) || "horizontal_axis".equals(name))
                && EnumFacing.Axis.class.isAssignableFrom(type)) return true;
        if ("rotation".equals(name) && Integer.class.isAssignableFrom(type)) {
            for (Object value : property.getAllowedValues()) {
                int angle = ((Integer) value).intValue();
                if (angle < 0 || angle >= 16) return false;
            }
            return true;
        }
        if ("half".equals(name)) {
            if (state.getBlock() instanceof BlockSlab && ((BlockSlab) state.getBlock()).isDouble()) return false;
            for (Object value : property.getAllowedValues()) {
                String valueName = propertyValueNameUnchecked(property, (Comparable<?>) value);
                if (!"top".equals(valueName) && !"bottom".equals(valueName)) return false;
            }
            return true;
        }
        if ("face".equals(name) || "attach_face".equals(name)) {
            for (Object value : property.getAllowedValues()) {
                String valueName = propertyValueNameUnchecked(property, (Comparable<?>) value);
                if (!"floor".equals(valueName) && !"wall".equals(valueName) && !"ceiling".equals(valueName)) return false;
            }
            return true;
        }
        return false;
    }

    private <T extends Comparable<T>> void addProperty(IBlockState state, IProperty<T> property) {
        for (RotationProperty<?> existing : this.properties) {
            if (existing.propertyName().equals(property.getName())) return;
        }
        T current = state.getValue(property);
        List<PropertyOption<T>> options = new ArrayList<PropertyOption<T>>();
        options.add(new PropertyOption<T>(current));
        for (T value : property.getAllowedValues()) {
            if (!value.equals(current)) options.add(new PropertyOption<T>(value));
        }
        if (options.size() > 1) {
            this.properties.add(new RotationProperty<T>(property.getName(), property,
                    Collections.unmodifiableList(options)));
        }
    }

    private static List<IBlockState> buildPlacementStates(IBlockState base,
            List<RotationProperty<?>> properties) {
        List<Integer> counts = new ArrayList<Integer>(properties.size());
        for (RotationProperty<?> property : properties) counts.add(Integer.valueOf(property.options().size()));
        List<IBlockState> states = new ArrayList<IBlockState>();
        for (int[] indices : PlacementStateCombinationPlan.combinations(counts, PLACEMENT_CHOICE_LIMIT)) {
            IBlockState state = base;
            for (int i = 0; i < properties.size(); i++) state = applyOption(state, properties.get(i), indices[i]);
            if (!states.contains(state)) states.add(state);
        }
        return Collections.unmodifiableList(states);
    }

    private static <T extends Comparable<T>> IBlockState applyOption(IBlockState state,
            RotationProperty<T> property, int optionIndex) {
        T original = property.options().get(optionIndex).value();
        String serialized = property.property().getName(original);
        Optional<T> parsed = property.property().parseValue(serialized);
        if (!parsed.isPresent() || !property.property().getAllowedValues().contains(parsed.get())) return state;
        return state.withProperty(property.property(), parsed.get());
    }

    private String placementChoiceLabel(IBlockState state) {
        List<String> parts = new ArrayList<String>(this.properties.size());
        for (RotationProperty<?> property : this.properties) {
            Comparable<?> value = getValueUnchecked(state, property.property());
            parts.add(tr(propertyLabelKey(property.propertyName())) + " " + optionLabel(property.property(), value));
        }
        return join(parts, " · ");
    }

    static float placementPreviewYaw(float cameraYaw) { return 180.0F - cameraYaw; }

    private static String propertyLabelKey(String name) {
        if (name == null) return "screen.rtsbuilding.rotation_wheel.facing";
        if (name.indexOf("axis") >= 0) return "screen.rtsbuilding.rotation_wheel.axis";
        if ("half".equals(name)) return "screen.rtsbuilding.rotation_wheel.half";
        if ("face".equals(name) || "attach_face".equals(name)) return "screen.rtsbuilding.rotation_wheel.attach_face";
        if ("rotation".equals(name)) return "screen.rtsbuilding.rotation_wheel.rotation";
        return "screen.rtsbuilding.rotation_wheel.facing";
    }

    private static String optionLabel(IProperty<?> property, Comparable<?> value) {
        String serialized = propertyValueNameUnchecked(property, value).toLowerCase(Locale.ROOT);
        if (value instanceof EnumFacing) return tr("screen.rtsbuilding.rotation_wheel.direction." + serialized);
        if (value instanceof EnumFacing.Axis) return tr("screen.rtsbuilding.rotation_wheel.axis." + serialized);
        if (value instanceof Enum<?>) return tr("screen.rtsbuilding.rotation_wheel.value." + serialized);
        return serialized;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Comparable<?> getValueUnchecked(IBlockState state, IProperty property) { return state.getValue(property); }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String propertyValueNameUnchecked(IProperty property, Comparable value) { return property.getName(value); }

    private static boolean insideCircle(double x, double y, double cx, double cy, double radius) {
        double dx = x - cx, dy = y - cy;
        return dx * dx + dy * dy <= radius * radius;
    }

    private static int clampCenter(double coordinate, int size, int padding) {
        if (size <= padding * 2) return Math.max(0, size / 2);
        return clamp((int) Math.round(coordinate), padding, size - padding);
    }
    private static float optionDistance(float progress) { return lerp(progress, OPTION_START_DISTANCE, OPTION_DISTANCE); }
    private static double optionAngle(int index, int count) { return -Math.PI / 2.0D + Math.PI * 2.0D * index / count; }
    private static String tr(String key) { return I18n.format(key); }
    private static long now() { return System.currentTimeMillis(); }
    private static int floorMod(int value, int divisor) { int result = value % divisor; return result < 0 ? result + divisor : result; }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
    private static float lerp(float amount, float from, float to) { return from + (to - from) * amount; }
    private static int color(int argb, float multiplier) {
        int alpha = Math.round(((argb >>> 24) & 255) * clamp(multiplier, 0.0F, 1.0F));
        return new com.rtsbuilding.rtsbuilding.uikit.theme.UiColor(argb).withAlpha(alpha).toArgb();
    }

    private static String trimToWidth(FontRenderer font, String text, int maxWidth) {
        String safe = text == null ? "" : text;
        if (font.getStringWidth(safe) <= maxWidth) return safe;
        String suffix = "...";
        int available = Math.max(0, maxWidth - font.getStringWidth(suffix));
        int end = safe.length();
        while (end > 0 && font.getStringWidth(safe.substring(0, end)) > available) end--;
        return safe.substring(0, end) + suffix;
    }

    private static String join(List<String> values, String separator) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() > 0) result.append(separator);
            result.append(value);
        }
        return result.toString();
    }

    private static void fillDisc(LegacyGuiGraphics graphics, float cx, float cy, float radius, int color) {
        int r = Math.max(1, Math.round(radius));
        int r2 = r * r;
        for (int y = -r; y <= r; y++) {
            int half = (int) Math.floor(Math.sqrt(Math.max(0, r2 - y * y)));
            graphics.fill(Math.round(cx) - half, Math.round(cy) + y,
                    Math.round(cx) + half + 1, Math.round(cy) + y + 1, color);
        }
    }

    private static void drawRing(LegacyGuiGraphics graphics, float cx, float cy,
            float radius, float thickness, int color) {
        int outer = Math.max(1, Math.round(radius));
        int inner = Math.max(0, Math.round(radius - thickness));
        int outer2 = outer * outer, inner2 = inner * inner;
        for (int y = -outer; y <= outer; y++) {
            int oh = (int) Math.floor(Math.sqrt(Math.max(0, outer2 - y * y)));
            if (inner == 0 || Math.abs(y) >= inner) {
                graphics.fill(Math.round(cx) - oh, Math.round(cy) + y,
                        Math.round(cx) + oh + 1, Math.round(cy) + y + 1, color);
            } else {
                int ih = (int) Math.floor(Math.sqrt(Math.max(0, inner2 - y * y)));
                graphics.fill(Math.round(cx) - oh, Math.round(cy) + y,
                        Math.round(cx) - ih, Math.round(cy) + y + 1, color);
                graphics.fill(Math.round(cx) + ih + 1, Math.round(cy) + y,
                        Math.round(cx) + oh + 1, Math.round(cy) + y + 1, color);
            }
        }
    }

    private static void fillCapsule(LegacyGuiGraphics graphics, int left, int right,
            float centerY, float height, int color) {
        float radius = height * 0.5F;
        int innerLeft = Math.round(left + radius), innerRight = Math.round(right - radius);
        graphics.fill(innerLeft, Math.round(centerY - radius), innerRight, Math.round(centerY + radius), color);
        fillDisc(graphics, innerLeft, centerY, radius, color);
        fillDisc(graphics, innerRight, centerY, radius, color);
    }

    static final class RotationProperty<T extends Comparable<T>> {
        private final String propertyName;
        private final IProperty<T> property;
        private final List<PropertyOption<T>> options;
        RotationProperty(String propertyName, IProperty<T> property, List<PropertyOption<T>> options) {
            this.propertyName = propertyName; this.property = property; this.options = options;
        }
        String propertyName() { return this.propertyName; }
        IProperty<T> property() { return this.property; }
        List<PropertyOption<T>> options() { return this.options; }
    }

    static final class PropertyOption<T extends Comparable<T>> {
        private final T value;
        PropertyOption(T value) { this.value = value; }
        T value() { return this.value; }
    }

    /** 确认项携带轮盘实际预览的完整状态，避免多个属性在放置前漂移。 */
    public static final class PlacementChoice {
        private final IBlockState state;
        public PlacementChoice(IBlockState state) { this.state = state; }
        public IBlockState state() { return this.state; }
        @Override public boolean equals(Object other) {
            return this == other || other instanceof PlacementChoice
                    && this.state.equals(((PlacementChoice) other).state);
        }
        @Override public int hashCode() { return this.state.hashCode(); }
        @Override public String toString() { return "PlacementChoice[state=" + this.state + "]"; }
    }
}
