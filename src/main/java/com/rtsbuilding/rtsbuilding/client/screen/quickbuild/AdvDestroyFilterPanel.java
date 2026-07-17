package com.rtsbuilding.rtsbuilding.client.screen.quickbuild;

import com.rtsbuilding.rtsbuilding.client.widget.WindowButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 高级过滤二级面板。由 QuickBuildPanel 管理生命周期。
 * <p>
 * 显示过滤器列表，支持添加/删除 ItemStack / ModID / Tag 过滤器。
 */
public final class AdvDestroyFilterPanel {

    private static final int PANEL_W = 150;
    private static final int PANEL_H = 170;
    private static final int ENTRY_H = 20;
    private static final int DELETE_BTN_W = 14;

    private boolean visible;
    private int panelX, panelY;

    // 添加过滤器状态机
    private enum AddState { NONE, CHOOSE_TYPE, INPUT_MOD_ID, INPUT_TAG }
    private AddState addState = AddState.NONE;

    private final List<WindowButton> deleteButtons = new ArrayList<>();
    private WindowButton addButton;
    private WindowButton chooseItemStackBtn, chooseModIdBtn, chooseTagBtn;
    private WindowButton confirmInputBtn;

    private EditBox inputField;
    private String inputPlaceholder = "";

    // 数据源（当前过滤器列表 + 回调）
    private final Consumer<Runnable> onChanged; // 通知外部数据变化 + 持久化

    public AdvDestroyFilterPanel(Consumer<Runnable> onChanged) {
        this.onChanged = onChanged;
    }

    public boolean isVisible() {
        return visible;
    }

    /** 切换面板可见性，面板居中在父面板内部。 */
    public void toggle(int parentX, int parentY, int parentWidth, int parentHeight) {
        this.visible = !this.visible;
        if (visible) {
            // 居中在 QuickBuild 面板内部
            this.panelX = parentX + (parentWidth - PANEL_W) / 2;
            this.panelY = parentY + (parentHeight - PANEL_H) / 2;
            // 约束在屏幕范围内
            var mc = Minecraft.getInstance();
            this.panelX = Math.max(4, Math.min(mc.getWindow().getGuiScaledWidth() - PANEL_W - 4, this.panelX));
            this.panelY = Math.max(4, Math.min(mc.getWindow().getGuiScaledHeight() - PANEL_H - 4, this.panelY));
            this.addState = AddState.NONE;
            this.inputField = null;
        }
    }

    public void close() {
        this.visible = false;
        this.addState = AddState.NONE;
        this.inputField = null;
    }

    // ======================== 渲染 ========================

    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick,
                       List<AdvDestroyFilter> filters) {
        if (!visible) return;

        // 面板背景
        g.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, 0xCC1E2A3A);
        g.fill(panelX + 1, panelY + 1, panelX + PANEL_W - 1, panelY + PANEL_H - 1, 0xCC2D3D4E);
        // 边框
        g.fill(panelX, panelY, panelX + PANEL_W, panelY + 1, 0xFF5FE36C);
        g.fill(panelX, panelY + PANEL_H - 1, panelX + PANEL_W, panelY + PANEL_H, 0xFF5FE36C);

        var font = Minecraft.getInstance().font;
        // 标题
        g.drawString(font, Component.translatable("screen.rtsbuilding.quick_build.adv_filter_title"),
                panelX + 4, panelY + 4, 0xFFEAF4FF, false);

        // 关闭按钮 (X)
        int closeX = panelX + PANEL_W - 14;
        g.fill(closeX, panelY + 3, closeX + 10, panelY + 13, 0x66444444);
        g.drawString(font, "x", closeX + 2, panelY + 4, 0xFFAA5555, false);

        // 分隔线
        g.fill(panelX + 4, panelY + 16, panelX + PANEL_W - 4, panelY + 17, 0x55647B92);

        // 过滤器列表
        int entryY = panelY + 22;
        deleteButtons.clear();
        for (int i = 0; i < filters.size(); i++) {
            AdvDestroyFilter filter = filters.get(i);
            if (entryY + ENTRY_H > panelY + PANEL_H - 30) break; // 超出面板

            // 类型图标
            String icon = switch (filter.getType()) {
                case ITEM_STACK -> "[▣]";
                case MOD_ID -> "[M]";
                case TAG -> "[#]";
            };
            g.drawString(font, icon, panelX + 4, entryY + 2, 0xFFC9D8E8, false);

            // 显示名称（截断）
            String name = filter.getDisplayName();
            if (font.width(name) > PANEL_W - 60) {
                name = font.plainSubstrByWidth(name, PANEL_W - 60) + "..";
            }
            g.drawString(font, name, panelX + 28, entryY + 2, 0xFFEAF4FF, false);

            // 删除按钮（通过引用删除，避免索引偏移问题）
            AdvDestroyFilter filterToDelete = filter;
            WindowButton delBtn = new WindowButton(panelX + PANEL_W - 18, entryY + 1, DELETE_BTN_W, 14,
                    Component.literal("x"), btn -> {
                filters.remove(filterToDelete);
                onChanged.accept(() -> {});
            });
            delBtn.render(g, mouseX, mouseY, partialTick);
            deleteButtons.add(delBtn);

            entryY += ENTRY_H;
        }

        // 添加按钮区域
        int addY = panelY + PANEL_H - 24;
        if (addState == AddState.NONE) {
            if (addButton == null) {
                addButton = new WindowButton(panelX + 4, addY, PANEL_W - 8, 16,
                        Component.translatable("screen.rtsbuilding.quick_build.adv_filter_add"),
                        btn -> { addState = AddState.CHOOSE_TYPE; });
            } else {
                addButton.setX(panelX + 4);
                addButton.setY(addY);
            }
            addButton.render(g, mouseX, mouseY, partialTick);
        } else if (addState == AddState.CHOOSE_TYPE) {
            // 选择过滤器类型
            int btnW = (PANEL_W - 12) / 3;
            chooseItemStackBtn = new WindowButton(panelX + 4, addY, btnW, 16,
                    Component.translatable("screen.rtsbuilding.quick_build.adv_filter_item"),
                    btn -> startAddItemStack());
            chooseModIdBtn = new WindowButton(panelX + 4 + btnW + 2, addY, btnW, 16,
                    Component.translatable("screen.rtsbuilding.quick_build.adv_filter_mod"),
                    btn -> startAddModId());
            chooseTagBtn = new WindowButton(panelX + 4 + 2 * (btnW + 2), addY, btnW, 16,
                    Component.translatable("screen.rtsbuilding.quick_build.adv_filter_tag"),
                    btn -> startAddTag());
            chooseItemStackBtn.render(g, mouseX, mouseY, partialTick);
            chooseModIdBtn.render(g, mouseX, mouseY, partialTick);
            chooseTagBtn.render(g, mouseX, mouseY, partialTick);
        } else if (addState == AddState.INPUT_MOD_ID || addState == AddState.INPUT_TAG) {
            // 文本输入框
            if (inputField == null) {
                inputField = new EditBox(font, panelX + 4, addY, PANEL_W - 38, 14,
                        Component.literal(inputPlaceholder));
                inputField.setMaxLength(64);
                inputField.setFocused(true);
            }
            inputField.render(g, mouseX, mouseY, partialTick);
            // 确认按钮
            if (confirmInputBtn == null) {
                confirmInputBtn = new WindowButton(panelX + PANEL_W - 30, addY, 24, 14,
                        Component.literal("\u2713"), // ✓
                        btn -> confirmInput(filters));
            } else {
                confirmInputBtn.setX(panelX + PANEL_W - 30);
                confirmInputBtn.setY(addY);
            }
            confirmInputBtn.render(g, mouseX, mouseY, partialTick);
        }
    }

    // ======================== 鼠标事件 ========================

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;

        // 点击外部关闭
        if (mouseX < panelX || mouseX > panelX + PANEL_W || mouseY < panelY || mouseY > panelY + PANEL_H) {
            close();
            return true; // 吞掉事件
        }

        // 关闭按钮
        int closeX = panelX + PANEL_W - 14;
        if (mouseX >= closeX && mouseX <= closeX + 10 && mouseY >= panelY + 3 && mouseY <= panelY + 13) {
            close();
            return true;
        }

        // 删除按钮
        for (WindowButton delBtn : deleteButtons) {
            if (delBtn.mouseClicked(mouseX, mouseY, button)) return true;
        }

        // 添加按钮（取决于当前状态）
        if (addState == AddState.NONE && addButton != null && addButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (addState == AddState.CHOOSE_TYPE) {
            if (chooseItemStackBtn != null && chooseItemStackBtn.mouseClicked(mouseX, mouseY, button)) return true;
            if (chooseModIdBtn != null && chooseModIdBtn.mouseClicked(mouseX, mouseY, button)) return true;
            if (chooseTagBtn != null && chooseTagBtn.mouseClicked(mouseX, mouseY, button)) return true;
        }
        if ((addState == AddState.INPUT_MOD_ID || addState == AddState.INPUT_TAG)) {
            if (confirmInputBtn != null && confirmInputBtn.mouseClicked(mouseX, mouseY, button)) return true;
            if (inputField != null) {
                return inputField.mouseClicked(mouseX, mouseY, button);
            }
        }

        return true; // 面板内其他区域吞掉事件
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!visible) return false;
        if (inputField != null) {
            return inputField.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;
        if (addState == AddState.CHOOSE_TYPE && keyCode == 256) { // ESC
            addState = AddState.NONE;
            return true;
        }
        if (inputField != null) {
            if (keyCode == 257 || keyCode == 335) { // Enter or numpad Enter
                if (inputField.getValue().trim().isEmpty()) return true;
                // confirm is handled separately
                return false;
            }
            if (keyCode == 256) { // ESC
                addState = AddState.NONE;
                inputField = null;
                return true;
            }
            return inputField.keyPressed(keyCode, scanCode, modifiers);
        }
        return false;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (!visible) return false;
        if (inputField != null) {
            return inputField.charTyped(codePoint, modifiers);
        }
        return false;
    }

    // ======================== 内部逻辑 ========================

    private void startAddItemStack() {
        // 关闭面板，用户通过"挖掘方块"槽位添加 ItemStackFilter
        // 此时 QuickBuildPanel 应处于 "添加ItemStack" 模式，即槽位操作会向列表添加而非替换第一个
        addState = AddState.NONE;
        // 外部通过 addState 或标记来处理
    }

    private void startAddModId() {
        addState = AddState.INPUT_MOD_ID;
        inputPlaceholder = "mod id...";
        inputField = null;
    }

    private void startAddTag() {
        addState = AddState.INPUT_TAG;
        inputPlaceholder = "c:ores...";
        inputField = null;
    }

    private void confirmInput(List<AdvDestroyFilter> filters) {
        if (inputField == null) return;
        String value = inputField.getValue().trim();
        if (value.isEmpty()) return;

        switch (addState) {
            case INPUT_MOD_ID -> filters.add(new AdvDestroyModIdFilter(value));
            case INPUT_TAG -> {
                ResourceLocation id = ResourceLocation.tryParse(value);
                if (id != null) {
                    filters.add(new AdvDestroyTagFilter(id));
                }
            }
        }
        addState = AddState.NONE;
        inputField = null;
        onChanged.accept(() -> {});
    }

    /** 当前是否正在等待用户选择 ItemStack 添加过滤器 */
    public boolean isWaitingForItemStack() {
        // 如果 addState == CHOOSE_TYPE（且上次点击了 ItemStack），标记已由外部处理
        return false;
    }

    /** 从外部向过滤器列表添加一个 ItemStack 过滤器（由"挖掘方块"槽位触发） */
    public void addItemStackFilter(List<AdvDestroyFilter> filters, ResourceLocation itemId) {
        filters.add(new AdvDestroyItemStackFilter(itemId));
        onChanged.accept(() -> {});
    }

    public AddState getAddState() {
        return addState;
    }

    public void cancelAddState() {
        addState = AddState.NONE;
        inputField = null;
    }
}
