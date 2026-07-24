package com.rtsbuilding.rtsbuilding.client.presentation.plugin.binding;

import com.rtsbuilding.rtsbuilding.client.infrastructure.di.CompositionRoot;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.storage.StorageModule;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.client.util.animate.EasingFunctions;
import com.rtsbuilding.rtsbuilding.client.util.animate.FloatAnimation;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public final class PriorityEditController {

    private int editingIndex = -1;
    private final StringBuilder editBuffer = new StringBuilder();
    private long editStartTime;
    private boolean isEditing;

    private final FloatAnimation priorityBoxAnim = FloatAnimation.builder()
            .from(0f).to(0f)
            .duration(100L)
            .easing(EasingFunctions.EASE_OUT_QUAD)
            .startFromCurrent(true)
            .build();
    private int lastAnimRow = -1;
    private float lastAnimBaseW;

    private final List<RowLayout> rowLayouts;

    public PriorityEditController(List<RowLayout> rowLayouts) {
        this.rowLayouts = rowLayouts;
    }

    public void beginEdit(int rowIndex, int priority) {
        editingIndex = rowIndex;
        isEditing = true;
        editBuffer.setLength(0);
        editBuffer.append(priority);
        editStartTime = System.currentTimeMillis();
        lastAnimRow = rowIndex;
        lastAnimBaseW = net.minecraft.client.Minecraft.getInstance().font.width(String.valueOf(priority)) + 4 * 2;
        priorityBoxAnim.start(1f);
    }

    public void tryCommit() {
        if (!isEditing) return;
        String text = editBuffer.toString().trim();
        if (!text.isEmpty()) {
            try {
                int newPriority = Mth.clamp(Integer.parseInt(text), 0, 100);
                StorageModule sm = CompositionRoot.get().module(StorageModule.class);
                if (sm != null && editingIndex >= 0 && editingIndex < rowLayouts.size()) {
                    var entries = sm.getLinkedStorageEntries();
                    RowLayout rl = rowLayouts.get(editingIndex);
                    if (rl.originalIndex >= 0 && rl.originalIndex < entries.size()) {
                        var entry = entries.get(rl.originalIndex);
                        RtsClientPacketGateway.sendUpdateLinkedStorage(
                                entry.pos(), entry.isExtractOnly(), newPriority);
                    }
                }
            } catch (NumberFormatException ignored) {}
        }
        doCancel();
    }

    public void doCancel() {
        if (editingIndex >= 0 && editingIndex < rowLayouts.size()) {
            lastAnimRow = editingIndex;
            lastAnimBaseW = rowLayouts.get(editingIndex).priorityW;
        }
        isEditing = false;
        editingIndex = -1;
        editBuffer.setLength(0);
        priorityBoxAnim.start(0f);
    }

    public void tick(int count) {
        priorityBoxAnim.tick();
        if (!priorityBoxAnim.isRunning() && !isEditing) {
            lastAnimRow = -1;
        }
        if (isEditing && editingIndex >= count) {
            doCancel();
        }
    }

    public boolean isEditing() { return isEditing; }

    public boolean isEditingRow(int rowIndex) { return isEditing && rowIndex == editingIndex; }

    public int getEditingIndex() { return editingIndex; }

    public String getBufferText() { return editBuffer.toString(); }

    public int getBufferLength() { return editBuffer.length(); }

    public long getStartTime() { return editStartTime; }

    public float getAnimValue() { return priorityBoxAnim.getValue(); }

    public float computePriorityBoxWidth(int normalW, boolean isEditingRow, int rowIndex) {
        boolean applyAnim = isEditingRow || (rowIndex == lastAnimRow && lastAnimRow >= 0);
        if (!applyAnim) return normalW;
        float baseW = isEditingRow ? normalW : lastAnimBaseW;
        return baseW + (40 - baseW) * priorityBoxAnim.getValue();
    }

    public boolean isClickOnEditBox(int mx, int my, int parentX, int parentY, int scroll) {
        int editBoxX = parentX + 5 + 14 + 2;
        int editBoxY = parentY + 2 + editingIndex * 20 - scroll + 20 / 2;
        int boxTop = editBoxY - 13 / 2;
        return inRect(mx, my, editBoxX, boxTop, 40, 13);
    }

    public boolean handleKeyPressed(int keyCode) {
        if (!isEditing) return false;

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            tryCommit();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            doCancel();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (editBuffer.length() > 0) {
                editBuffer.deleteCharAt(editBuffer.length() - 1);
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            return true;
        }
        return false;
    }

    public boolean handleCharTyped(char codePoint) {
        if (!isEditing) return false;
        if (codePoint >= '0' && codePoint <= '9') {
            editBuffer.append(codePoint);
            return true;
        }
        return false;
    }

    private static boolean inRect(int px, int py, int rx, int ry, int rw, int rh) {
        return px >= rx && px < rx + rw && py >= ry && py < ry + rh;
    }
}
