package com.rtsbuilding.rtsbuilding.client.screen.panel.component;

import com.rtsbuilding.rtsbuilding.client.util.state.HoverStateManager;
import com.rtsbuilding.rtsbuilding.client.util.NineSliceRegion;
import com.rtsbuilding.rtsbuilding.client.util.TextureInfo;
import com.rtsbuilding.rtsbuilding.client.util.ThemeManager;
import com.rtsbuilding.rtsbuilding.client.util.render.CrossFadeRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.SpriteRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.TextRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.function.IntConsumer;

/**
 * Hex/Dec 颜色值文本输入组件——提供输入框、模式切换按钮、键盘/剪贴板输入支持。
 *
 * <p>独立无状态外观组件，不依赖面板框架，调用方传入坐标和当前颜色值即可使用。
 * 内部管理编辑状态、输入缓冲区、光标位置、光标闪烁和悬浮动画。</p>
 *
 * <p>支持光标定位、方向键导航、插入/删除、剪贴板粘贴等现代文本输入行为。</p>
 */
public class HexInputComponent {

    // ======================== 布局常量 ========================

    /** 输入行高度 */
    public static final int INPUT_H = 18;
    /** 标签与输入框的间距 */
    private static final int LABEL_GAP = 4;
    /** 模式按钮文字水平内边距（左右各6px） */
    public static final int MODE_BTN_HPAD = 6;
    /** 输入框与模式按钮的间距 */
    private static final int MODE_GAP = 4;
    /** 光标闪烁周期（毫秒） */
    private static final long CURSOR_BLINK_MS = 600;
    /** 输入框左右内边距 */
    private static final int INPUT_PAD = 4;

    // ======================== 输入框贴图 ========================

    private static final ResourceLocation INPUT_BOX_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/input_box.png");
    private static final int INPUT_BOX_TEX_W = 32;
    private static final int INPUT_BOX_TEX_H = 32;
    private static final int INPUT_BOX_STATE_H = 16;
    private static final int INPUT_BOX_BORDER = 4;
    private static final TextureInfo INPUT_BOX_TEX_INFO = new TextureInfo(
            INPUT_BOX_TEXTURE, INPUT_BOX_TEX_W, INPUT_BOX_TEX_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR, TextureInfo.FilterMode.PIXEL);
    private static final NineSliceRegion INPUT_BOX_NINE_SLICE = NineSliceRegion.fullTheme(
            INPUT_BOX_TEX_INFO, INPUT_BOX_STATE_H, INPUT_BOX_BORDER);

    // ======================== 模式按钮贴图 ========================

    private static final ResourceLocation MODE_BTN_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/fold_ui.png");
    private static final int MODE_BTN_TEX_W = 32;
    private static final int MODE_BTN_TEX_H = 32;
    private static final int MODE_BTN_STATE_H = 16;
    private static final int MODE_BTN_BORDER = 4;
    private static final TextureInfo MODE_BTN_TEX_INFO = new TextureInfo(
            MODE_BTN_TEXTURE, MODE_BTN_TEX_W, MODE_BTN_TEX_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR, TextureInfo.FilterMode.PIXEL);
    private static final NineSliceRegion MODE_BTN_NINE_SLICE = NineSliceRegion.fullTheme(
            MODE_BTN_TEX_INFO, MODE_BTN_STATE_H, MODE_BTN_BORDER);

    // ======================== 实例状态 ========================

    /** 是否处于 Hex/Dec 手动输入模式 */
    private boolean hexEditMode;
    /** 输入缓冲区 */
    private final StringBuilder hexEditBuffer = new StringBuilder();
    /** 光标在缓冲区中的位置（0 = 文本最左端） */
    private int cursorPos;
    /** 进入编辑模式时的时间戳，用于光标闪烁 */
    private long hexEditStartTime;
    /** 颜色显示模式：true=16进制(#RRGGBB)，false=10进制(0-16777215) */
    private boolean hexDisplayMode = true;
    /** 模式切换按钮悬浮状态管理 */
    private final HoverStateManager modeBtnHoverState = new HoverStateManager();
    /** 是否首次输入（首次输入时清空预填内容） */
    private boolean hexEditFirstInput;
    /** 防重入标记——handleKeyPressed 已处理字符时设置，阻止 charTyped 重复 */
    private boolean hexKeyAlreadyProcessed;
    /** 文本水平滚动偏移（像素），使光标保持在可视区域 */
    private int scrollOffset;

    // ======================== 回调 ========================

    @Nullable
    private IntConsumer onColorParsed;

    public void setOnColorParsed(@Nullable IntConsumer callback) {
        this.onColorParsed = callback;
    }

    // ======================== 渲染 ========================

    /** 渲染整个输入行：标签 + 输入框背景 + 颜色文本 + 闪烁光标 + 模式切换按钮。 */
    public void render(GuiGraphics g, int mouseX, int mouseY,
                       int previewX, int previewW, int inputY, int currentColor) {
        Font font = Minecraft.getInstance().font;

        String inputText;
        if (hexEditMode) {
            inputText = hexEditBuffer.toString();
        } else if (hexDisplayMode) {
            inputText = String.format("#%06X", currentColor & 0xFFFFFF);
        } else {
            inputText = String.valueOf(currentColor & 0xFFFFFF);
        }

        int inputTextColor = ThemeManager.getTextColor();
        String inputLabel = Component.translatable("screen.rtsbuilding.color_picker.input_label").getString();
        int labelW = font.width(inputLabel);
        Component modeText = Component.translatable(hexDisplayMode
                ? "screen.rtsbuilding.color_picker.mode.hex"
                : "screen.rtsbuilding.color_picker.mode.dec");
        String modeTextStr = modeText.getString();
        int modeTextW = font.width(modeTextStr);
        int modeBtnW = modeTextW + MODE_BTN_HPAD * 2;
        int inputW = previewW - labelW - LABEL_GAP - modeBtnW - MODE_GAP;
        int inputX = previewX + labelW + LABEL_GAP;

        NineSliceRegion inputSpec = hexEditMode
                ? INPUT_BOX_NINE_SLICE.withVOffset(INPUT_BOX_STATE_H)
                : INPUT_BOX_NINE_SLICE;
        SpriteRenderer.drawNineSlice(g, inputSpec.withTheme(), inputX, inputY, inputW, INPUT_H);

        TextRenderer.draw(g, inputLabel, previewX,
                inputY + (INPUT_H - font.lineHeight) / 2, inputTextColor);

        int contentAreaW = inputW - INPUT_PAD * 2;
        int textBaselineX = inputX + INPUT_PAD;
        int textY = inputY + (INPUT_H - font.lineHeight) / 2;

        if (hexEditMode) {
            updateScrollOffset(font, inputText, contentAreaW);
            int drawOffset = scrollOffset;
            String fullText = inputText;

            // 找到可见文本起始索引
            int startIdx = 0;
            if (drawOffset < 0) {
                int accumulated = 0;
                for (int i = 0; i < fullText.length(); i++) {
                    int cw = font.width(String.valueOf(fullText.charAt(i)));
                    if (accumulated + cw > -drawOffset) { startIdx = i; break; }
                    accumulated += cw;
                }
            }

            // 找到可见文本结束索引
            int endIdx = fullText.length();
            int visibleLimit = -drawOffset + contentAreaW;
            if (font.width(fullText) > visibleLimit) {
                int accumulated = 0;
                for (int i = 0; i < fullText.length(); i++) {
                    accumulated += font.width(String.valueOf(fullText.charAt(i)));
                    if (accumulated > visibleLimit) { endIdx = i; break; }
                }
            }

            String visibleText = fullText.substring(startIdx, endIdx);
            int drawX = textBaselineX + Math.max(0, drawOffset);
            if (!visibleText.isEmpty()) {
                g.drawString(font, visibleText, drawX, textY, inputTextColor, false);
            }

            // 绘制光标（可见区域内）
            String beforeCursor = fullText.substring(0, Math.min(cursorPos, fullText.length()));
            int cursorGlobalX = font.width(beforeCursor) + drawOffset;
            if ((System.currentTimeMillis() / CURSOR_BLINK_MS) % 2 == 0
                    && cursorGlobalX >= 0 && cursorGlobalX < contentAreaW) {
                g.fill(textBaselineX + cursorGlobalX, textY,
                        textBaselineX + cursorGlobalX + 1, textY + font.lineHeight, 0xFFFFFFFF);
            }
        } else {
            String displayText = TextRenderer.trimToWidth(font, inputText, Math.max(8, inputW - 6));
            int textX = textBaselineX + (contentAreaW - font.width(displayText)) / 2;
            g.drawString(font, displayText, textX, textY, inputTextColor, false);
        }

        // 进制模式切换按钮
        int btnX = previewX + previewW - modeBtnW;
        int btnY = inputY;
        boolean modeBtnHovered = mouseX >= btnX && mouseX < btnX + modeBtnW
                && mouseY >= btnY && mouseY < btnY + INPUT_H;
        float modeBtnT = this.modeBtnHoverState.update(modeBtnHovered);
        CrossFadeRenderer.render(modeBtnT,
                () -> SpriteRenderer.drawNineSlice(g,
                        MODE_BTN_NINE_SLICE.withTheme(), btnX, btnY, modeBtnW, INPUT_H),
                () -> SpriteRenderer.drawNineSlice(g,
                        MODE_BTN_NINE_SLICE.withTheme().withVOffset(MODE_BTN_STATE_H),
                        btnX, btnY, modeBtnW, INPUT_H));
        int modeTextColor = ThemeManager.getTextColor();
        TextRenderer.draw(g, modeText,
                btnX + (modeBtnW - modeTextW) / 2,
                btnY + (INPUT_H - font.lineHeight) / 2, modeTextColor);
    }

    /** 更新滚动偏移，使光标始终可见 */
    private void updateScrollOffset(Font font, String text, int contentAreaW) {
        String beforeCursor = text.substring(0, Math.min(cursorPos, text.length()));
        int cursorVisualX = font.width(beforeCursor);
        if (cursorVisualX + scrollOffset > contentAreaW) scrollOffset = contentAreaW - cursorVisualX;
        if (cursorVisualX + scrollOffset < 0) scrollOffset = -cursorVisualX;
        int totalW = font.width(text);
        if (totalW + scrollOffset < contentAreaW && scrollOffset < 0) {
            scrollOffset = Math.max(contentAreaW - totalW, 0);
            if (scrollOffset > 0) scrollOffset = 0;
        }
    }

    // ======================== 点击检测 ========================

    /** 处理输入行区域的点击事件。 @return true 表示事件已消费 */
    public boolean handleClick(double mouseX, double mouseY,
                               int hexInputY, int previewX, int previewW,
                               int currentColor) {
        Font font = Minecraft.getInstance().font;
        String inputLabel = Component.translatable("screen.rtsbuilding.color_picker.input_label").getString();
        int labelW = font.width(inputLabel);
        int inputX = previewX + labelW + LABEL_GAP;
        Component modeText = Component.translatable(hexDisplayMode
                ? "screen.rtsbuilding.color_picker.mode.hex"
                : "screen.rtsbuilding.color_picker.mode.dec");
        String modeTextStr = modeText.getString();
        int modeBtnW = font.width(modeTextStr) + MODE_BTN_HPAD * 2;
        int inputW = previewW - labelW - LABEL_GAP - modeBtnW - MODE_GAP;
        int btnX = previewX + previewW - modeBtnW;

        // 模式切换按钮
        if (mouseX >= btnX && mouseX < btnX + modeBtnW
                && mouseY >= hexInputY && mouseY < hexInputY + INPUT_H) {
            if (hexEditMode) applyHexInput();
            this.hexDisplayMode = !this.hexDisplayMode;
            return true;
        }

        // 输入框
        if (mouseX >= inputX && mouseX < inputX + inputW
                && mouseY >= hexInputY && mouseY < hexInputY + INPUT_H) {
            if (!hexEditMode) {
                hexEditBuffer.setLength(0);
                hexEditBuffer.append(hexDisplayMode
                        ? String.format("%06X", currentColor & 0xFFFFFF)
                        : String.valueOf(currentColor & 0xFFFFFF));
                hexEditMode = true;
                hexEditStartTime = System.currentTimeMillis();
                hexEditFirstInput = true;
                hexKeyAlreadyProcessed = false;
                cursorPos = hexEditBuffer.length();
                scrollOffset = 0;
            } else {
                int textStartX = inputX + INPUT_PAD + scrollOffset;
                int clickOffsetX = (int) Math.round(mouseX) - textStartX;
                cursorPos = cursorPosFromClickX(font, hexEditBuffer.toString(), clickOffsetX);
            }
            return true;
        } else if (hexEditMode) {
            applyHexInput();
        }
        return false;
    }

    /** 根据点击 X 偏移计算光标位置 */
    private static int cursorPosFromClickX(Font font, String text, int clickOffsetX) {
        if (clickOffsetX <= 0) return 0;
        int accumulated = 0;
        for (int i = 0; i < text.length(); i++) {
            int cw = font.width(String.valueOf(text.charAt(i)));
            if (accumulated + cw / 2 >= clickOffsetX) return i;
            accumulated += cw;
        }
        return text.length();
    }

    // ======================== 键盘事件 ========================

    /**
     * 处理按键按下事件——仅处理功能键，字符键消费以阻止快捷键但交给 charTyped 输入。
     * @return true 表示事件已消费
     */
    public boolean handleKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (!hexEditMode) return false;

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            applyHexInput();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            cancelHexEdit();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (cursorPos > 0 && hexEditBuffer.length() > 0) {
                hexEditBuffer.deleteCharAt(cursorPos - 1);
                cursorPos--;
                hexEditFirstInput = false;
                tryParseHexInput();
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            if (cursorPos < hexEditBuffer.length()) {
                hexEditBuffer.deleteCharAt(cursorPos);
                hexEditFirstInput = false;
                tryParseHexInput();
            }
            return true;
        }
        // 方向键导航
        if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_RIGHT
                || keyCode == GLFW.GLFW_KEY_HOME || keyCode == GLFW.GLFW_KEY_END) {
            handleCursorKey(keyCode, modifiers);
            return true;
        }
        // Ctrl+V 粘贴
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && keyCode == GLFW.GLFW_KEY_V) {
            pasteFromClipboard();
            return true;
        }
        // Ctrl+A 全选
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && keyCode == GLFW.GLFW_KEY_A) {
            cursorPos = hexEditBuffer.length();
            return true;
        }
        // 在 keyPressed 中直接处理 hex 字符（0-9, A-F 和 '#'），
        // 确保不受 charTyped 是否被调用的影响
        // 特殊处理 '#'（Shift+3，大部分键盘布局）
        if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 && keyCode == GLFW.GLFW_KEY_3) {
            System.out.println("[HexInput] keyPressed: Shift+3 -> '#'");
            if (hexEditFirstInput) {
                hexEditBuffer.setLength(0);
                cursorPos = 0;
                hexEditFirstInput = false;
                System.out.println("[HexInput] keyPressed: cleared initial buffer");
            }
            hexEditBuffer.insert(cursorPos, '#');
            cursorPos++;
            hexKeyAlreadyProcessed = true;
            System.out.println("[HexInput] keyPressed: buffer=[" + hexEditBuffer + "] cursor=" + cursorPos);
            tryParseHexInput();
            return true;
        }
        char hexChar = keyCodeToHexChar(keyCode, modifiers);
        if (hexChar != '\0') {
            if (hexEditFirstInput) {
                hexEditBuffer.setLength(0);
                cursorPos = 0;
                hexEditFirstInput = false;
                System.out.println("[HexInput] keyPressed: cleared initial buffer");
            }
            hexEditBuffer.insert(cursorPos, hexChar);
            cursorPos++;
            hexKeyAlreadyProcessed = true;
            System.out.println("[HexInput] keyPressed: char='" + hexChar + "' buffer=\"" + hexEditBuffer + "\" cursor=" + cursorPos);
            tryParseHexInput();
            return true;
        }
        // Tab 阻隔
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            return true;
        }
        return false;
    }

    /** 将 GLFW keyCode 转换为 hex 字符，Shift+A-F 产生大写，否则小写，Shift+数字和 G-Z 返回 \0。 */
    private static char keyCodeToHexChar(int keyCode, int modifiers) {
        if (keyCode >= GLFW.GLFW_KEY_0 && keyCode <= GLFW.GLFW_KEY_9) {
            if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) return '\0';
            return (char) ('0' + (keyCode - GLFW.GLFW_KEY_0));
        }
        if (keyCode >= GLFW.GLFW_KEY_A && keyCode <= GLFW.GLFW_KEY_F) {
            boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
            return shift ? (char) ('A' + (keyCode - GLFW.GLFW_KEY_A))
                    : (char) ('a' + (keyCode - GLFW.GLFW_KEY_A));
        }
        return '\0';
    }

    /** 处理光标导航键 */
    private void handleCursorKey(int keyCode, int modifiers) {
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT -> cursorPos = ctrl ? 0 : Math.max(0, cursorPos - 1);
            case GLFW.GLFW_KEY_RIGHT -> cursorPos = ctrl ? hexEditBuffer.length() : Math.min(hexEditBuffer.length(), cursorPos + 1);
            case GLFW.GLFW_KEY_HOME -> cursorPos = 0;
            case GLFW.GLFW_KEY_END -> cursorPos = hexEditBuffer.length();
        }
    }

    /**
     * 处理字符输入事件——所有字符（0-9, A-F, a-f, #）在此处按 GLFW 提供的正确大小写插入。
     */
    public boolean handleCharTyped(char codePoint, int modifiers) {
        if (!hexEditMode) return false;
        System.out.println("[HexInput] charTyped: codePoint='" + codePoint + "' (0x" + Integer.toHexString(codePoint) + ")");
        if (hexKeyAlreadyProcessed) {
            hexKeyAlreadyProcessed = false;
            System.out.println("[HexInput] charTyped: skipped (already processed by keyPressed)");
            return true;
        }
        if (hexEditFirstInput) {
            hexEditBuffer.setLength(0);
            cursorPos = 0;
            hexEditFirstInput = false;
            System.out.println("[HexInput] charTyped: cleared initial buffer");
        }
        boolean valid;
        if (hexDisplayMode) {
            valid = (codePoint >= '0' && codePoint <= '9')
                    || (codePoint >= 'A' && codePoint <= 'F')
                    || (codePoint >= 'a' && codePoint <= 'f')
                    || codePoint == '#';
        } else {
            valid = codePoint >= '0' && codePoint <= '9';
        }
        if (valid) {
            hexEditBuffer.insert(cursorPos, codePoint);
            cursorPos++;
            System.out.println("[HexInput] charTyped: inserted, buffer=[" + hexEditBuffer + "] cursor=" + cursorPos);
            tryParseHexInput();
        }
        return true;
    }

    // ======================== 颜色管理 ========================

    public boolean isHexDisplayMode() { return hexDisplayMode; }
    public boolean isEditMode() { return hexEditMode; }
    public void applyEdit() { applyHexInput(); }
    public void cancelEdit() { cancelHexEdit(); }
    public void syncColor(int color) {}

    // ======================== 内部方法 ========================

    private void tryParseHexInput() {
        String text = hexEditBuffer.toString().trim();
        System.out.println("[HexInput] tryParseHexInput: text=[" + text + "]");
        if (text.isEmpty()) { System.out.println("[HexInput] tryParseHexInput: empty, skip"); return; }
        int color = parseColorText(text);
        System.out.println("[HexInput] tryParseHexInput: parseColorText returned 0x" + Integer.toHexString(color));
        if (color != -1 && onColorParsed != null) {
            System.out.println("[HexInput] tryParseHexInput: calling onColorParsed.accept(0x" + Integer.toHexString(color) + ")");
            onColorParsed.accept(color);
        }
    }

    private void applyHexInput() {
        if (!hexEditMode) {
            System.out.println("[HexInput] applyHexInput: not in edit mode, skip");
            return;
        }
        String text = hexEditBuffer.toString().trim();
        System.out.println("[HexInput] applyHexInput: text=[" + text + "]");
        if (!text.isEmpty()) {
            int color = parseColorText(text);
            System.out.println("[HexInput] applyHexInput: parseColorText returned 0x" + Integer.toHexString(color));
            if (color != -1 && onColorParsed != null) {
                System.out.println("[HexInput] applyHexInput: calling onColorParsed.accept(0x" + Integer.toHexString(color) + ")");
                onColorParsed.accept(color);
            } else {
                System.out.println("[HexInput] applyHexInput: NOT calling callback (color=" + color + ", callback=" + (onColorParsed != null) + ")");
            }
        }
        hexEditMode = false;
    }

    private void cancelHexEdit() {
        if (!hexEditMode) return;
        hexEditMode = false;
    }

    private void pasteFromClipboard() {
        String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
        if (clip == null || clip.isEmpty()) return;
        if (hexEditFirstInput) {
            hexEditBuffer.setLength(0);
            cursorPos = 0;
            hexEditFirstInput = false;
        }
        for (int i = 0; i < clip.length(); i++) {
            char ch = clip.charAt(i);
            boolean valid = hexDisplayMode
                    ? (ch >= '0' && ch <= '9') || (ch >= 'A' && ch <= 'F') || (ch >= 'a' && ch <= 'f') || ch == '#'
                    : (ch >= '0' && ch <= '9');
            if (valid) {
                hexEditBuffer.insert(cursorPos, ch);
                cursorPos++;
            }
        }
        tryParseHexInput();
    }

    // ======================== 解析 ========================

    /** 解析颜色文本，支持 #RRGGBB、#RGB、0xRRGGBB、RRGGBB、rgb(r,g,b)、十进制 */
    public static int parseColorText(String text) {
        if (text == null || text.isEmpty()) return -1;
        String trimmed = text.trim();
        try {
            if (trimmed.startsWith("#")) {
                String hex = trimmed.substring(1);
                if (hex.length() == 6 && hex.matches("[0-9A-Fa-f]{6}"))
                    return 0xFF000000 | Integer.parseInt(hex, 16);
                if (hex.length() == 3 && hex.matches("[0-9A-Fa-f]{3}")) {
                    int r = Integer.parseInt(hex.substring(0, 1), 16) * 17;
                    int g = Integer.parseInt(hex.substring(1, 2), 16) * 17;
                    int b = Integer.parseInt(hex.substring(2, 3), 16) * 17;
                    return 0xFF000000 | (r << 16) | (g << 8) | b;
                }
                return -1;
            }
            if ((trimmed.startsWith("0x") || trimmed.startsWith("0X")) && trimmed.length() == 8) {
                String hex = trimmed.substring(2);
                if (hex.matches("[0-9A-Fa-f]{6}")) return 0xFF000000 | Integer.parseInt(hex, 16);
                return -1;
            }
            if (trimmed.toLowerCase().startsWith("rgb(") && trimmed.endsWith(")")) {
                String[] parts = trimmed.substring(4, trimmed.length() - 1).split(",");
                if (parts.length == 3) {
                    int r = Integer.parseInt(parts[0].trim());
                    int g = Integer.parseInt(parts[1].trim());
                    int b = Integer.parseInt(parts[2].trim());
                    if (r >= 0 && r <= 255 && g >= 0 && g <= 255 && b >= 0 && b <= 255)
                        return 0xFF000000 | (r << 16) | (g << 8) | b;
                }
                return -1;
            }
            if (trimmed.matches("\\d{1,8}")) {
                int val = Integer.parseInt(trimmed);
                if (val >= 0 && val <= 0xFFFFFF) return 0xFF000000 | val;
                return -1;
            }
            if (trimmed.length() <= 6 && trimmed.matches("[0-9A-Fa-f]{1,6}"))
                return 0xFF000000 | Integer.parseInt(trimmed, 16);
        } catch (NumberFormatException e) { return -1; }
        return -1;
    }

    // ======================== 尺寸计算 ========================

    /** 计算输入行所需的最小内容宽度。 */
    public int computeInputLineWidth() {
        Font font = Minecraft.getInstance().font;
        String label = Component.translatable("screen.rtsbuilding.color_picker.input_label").getString();
        int labelW = font.width(label);
        String hexText = Component.translatable("screen.rtsbuilding.color_picker.mode.hex").getString();
        String decText = Component.translatable("screen.rtsbuilding.color_picker.mode.dec").getString();
        int modeBtnW = Math.max(font.width(hexText), font.width(decText)) + MODE_BTN_HPAD * 2;
        int minInputW = Math.max(font.width("#FFCC00"), font.width("16777215")) + 6;
        return 12 + labelW + LABEL_GAP + minInputW + MODE_GAP + modeBtnW;
    }
}
