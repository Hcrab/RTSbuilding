package com.rtsbuilding.rtsbuilding.client.presentation.plugin.binding;

import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.storage.StorageModule;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.client.util.animate.AnimFloat;
import com.rtsbuilding.rtsbuilding.client.util.animate.Easing;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public final class PriorityEditController {

    private int editingIndex = -1;
    private final StringBuilder editBuffer = new StringBuilder();
    private long editStartTime;
    private boolean isEditing;

    private final Map<Integer, AnimFloat> rowAnims = new HashMap<>();
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
        rowAnims.computeIfAbsent(rowIndex, k -> AnimFloat.of(0f, 100L, Easing.EASE_OUT_QUAD)).target(1f);
    }

    public void tryCommit() {
        if (!isEditing) return;
        String text = editBuffer.toString().trim();
        if (!text.isEmpty()) {
            try {
                int newPriority = Mth.clamp(Integer.parseInt(text), 0, 100);
                StorageModule sm = RtsClientKernel.get().module(StorageModule.class);
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
        if (editingIndex >= 0) {
            AnimFloat anim = rowAnims.get(editingIndex);
            if (anim != null) {
                anim.target(0f);
            } else {
                anim = AnimFloat.of(1f, 100L, Easing.EASE_OUT_QUAD);
                anim.target(0f);
                rowAnims.put(editingIndex, anim);
            }
        }
        isEditing = false;
        editingIndex = -1;
        editBuffer.setLength(0);
    }

    public void tick(int count) {
        if (isEditing && editingIndex >= count) {
            doCancel();
        }
        rowAnims.values().removeIf(anim -> !anim.isAnimating() && anim.get() < 0.01f);
    }

    public boolean isEditing() { return isEditing; }

    public boolean isEditingRow(int rowIndex) { return isEditing && rowIndex == editingIndex; }

    public int getEditingIndex() { return editingIndex; }

    public String getBufferText() { return editBuffer.toString(); }

    public int getBufferLength() { return editBuffer.length(); }

    public long getStartTime() { return editStartTime; }

    public float getAnimValue(int rowIndex) {
        AnimFloat anim = rowAnims.get(rowIndex);
        return anim != null ? anim.get() : 0f;
    }

    public float computePriorityBoxWidth(int normalW, boolean isEditingRow, int rowIndex) {
        float t = getAnimValue(rowIndex);
        if (t < 0.01f && !isEditingRow) return normalW;
        return normalW + (40 - normalW) * t;
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
