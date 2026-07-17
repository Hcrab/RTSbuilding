package com.rtsbuilding.rtsbuilding.client.screen.standalone;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * 无工具破坏确认弹窗。当玩家背包中缺少可正确采集范围内方块的工具时弹出。
 */
public class NoToolConfirmScreen extends Screen {

    private static final int DIALOG_W = 280;
    private static final int DIALOG_H = 180;

    private final Screen parent;
    private final Runnable onConfirm;
    private final List<BlockEntry> missingBlocks;
    private final Component titleComponent;
    private final Component descComponent;
    private final Component cancelText;
    private final Component continueText;

    public NoToolConfirmScreen(Screen parent, Runnable onConfirm, List<BlockEntry> missingBlocks,
                               Component titleComponent, Component descComponent,
                               Component cancelText, Component continueText) {
        super(titleComponent);
        this.parent = parent;
        this.onConfirm = onConfirm;
        this.missingBlocks = missingBlocks;
        this.titleComponent = titleComponent;
        this.descComponent = descComponent;
        this.cancelText = cancelText;
        this.continueText = continueText;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int dialogTop = (this.height - DIALOG_H) / 2;
        int dialogLeft = centerX - DIALOG_W / 2;

        int btnY = dialogTop + DIALOG_H - 28;

        // 取消按钮
        this.addRenderableWidget(Button.builder(cancelText, btn -> {
            if (this.minecraft != null) {
                this.minecraft.setScreen(parent);
            }
        }).pos(dialogLeft + 30, btnY).size(100, 20).build());

        // 仍然继续按钮
        this.addRenderableWidget(Button.builder(continueText, btn -> {
            if (this.minecraft != null) {
                this.minecraft.setScreen(parent);
                onConfirm.run();
            }
        }).pos(dialogLeft + DIALOG_W - 130, btnY).size(100, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 半透明暗色遮罩
        g.fill(0, 0, this.width, this.height, 0xC0101010);

        int centerX = this.width / 2;
        int dialogTop = (this.height - DIALOG_H) / 2;
        int dialogLeft = centerX - DIALOG_W / 2;

        // 对话框背景
        g.fill(dialogLeft, dialogTop, dialogLeft + DIALOG_W, dialogTop + DIALOG_H, 0xC0101010);
        g.renderOutline(dialogLeft, dialogTop, DIALOG_W, DIALOG_H, 0xFF647B92);

        // 标题
        g.drawCenteredString(this.font, titleComponent, centerX, dialogTop + 10, 0xFFFF5555);

        // 描述文字
        g.drawCenteredString(this.font, descComponent, centerX, dialogTop + 30, 0xFFCCCCCC);

        // 缺失工具方块列表
        int listY = dialogTop + 48;
        int entriesPerCol = 3;
        int maxEntries = entriesPerCol * 2;
        int shown = Math.min(missingBlocks.size(), maxEntries);
        int leftX = dialogLeft + 20;
        int rightX = dialogLeft + DIALOG_W / 2 + 10;

        for (int i = 0; i < shown; i++) {
            BlockEntry entry = missingBlocks.get(i);
            int col = i / entriesPerCol;
            int row = i % entriesPerCol;
            int entryX = col == 0 ? leftX : rightX;
            int entryY = listY + row * 14;

            // 方块图标
            ItemStack icon = new ItemStack(entry.block().asItem());
            g.renderItem(icon, entryX, entryY - 2);

            // 方块名称 + 数量
            String line = entry.displayName() + " ×" + entry.count();
            g.drawString(this.font, line, entryX + 18, entryY + 1, 0xFFFFFFFF);
        }

        // 如果超出显示，显示省略提示
        if (missingBlocks.size() > maxEntries) {
            g.drawCenteredString(this.font,
                    Component.translatable("screen.rtsbuilding.adv_destroy_no_tool.more",
                            missingBlocks.size() - maxEntries),
                    centerX, listY + entriesPerCol * 14 + 2, 0xFF888888);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
        // 空实现：禁止 NeoForge 自动施加的背景模糊
    }

    /**
     * 缺少工具可采集的方块条目。
     */
    public record BlockEntry(Block block, String displayName, int count) {
    }
}
