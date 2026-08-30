package com.rtsbuilding.rtsbuilding.uicore.topbar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 主线顶栏的权威纯状态。
 *
 * <p>这里只保存玩家能看到的语义和按钮状态，不保存 Minecraft 文本、纹理或控制器。
 * 生产与离屏各自用同一正式语言包把这些语义翻译成两行状态文字。</p>
 */
public final class TopBarUiState {
    public enum Mode {
        INTERACT, LINK_STORAGE, FUNNEL, CAMERA, ROTATE, IDLE
    }

    public final List<TopBarUiButton> buttons;
    public final Mode mode;
    public final boolean storageLinked;
    public final String linkedStorageName;
    public final boolean autoStoreMinedDrops;
    public final boolean funnelEnabled;
    public final String shapeStatus;
    public final int pendingGuiBindSlot;
    public final boolean blueprintPlacementLocked;

    public TopBarUiState(List<TopBarUiButton> buttons, Mode mode,
                         boolean storageLinked, String linkedStorageName,
                         boolean autoStoreMinedDrops, boolean funnelEnabled,
                         String shapeStatus, int pendingGuiBindSlot,
                         boolean blueprintPlacementLocked) {
        if (mode == null) throw new IllegalArgumentException("mode");
        this.buttons = Collections.unmodifiableList(new ArrayList<TopBarUiButton>(
                buttons == null ? Collections.<TopBarUiButton>emptyList() : buttons));
        this.mode = mode;
        this.storageLinked = storageLinked;
        this.linkedStorageName = safe(linkedStorageName);
        this.autoStoreMinedDrops = autoStoreMinedDrops;
        this.funnelEnabled = funnelEnabled;
        this.shapeStatus = safe(shapeStatus);
        this.pendingGuiBindSlot = pendingGuiBindSlot;
        this.blueprintPlacementLocked = blueprintPlacementLocked;
    }

    public TopBarUiButton button(TopBarUiButtonId id) {
        for (TopBarUiButton button : buttons) {
            if (button.id == id) return button;
        }
        return null;
    }

    public TopBarUiState withMode(Mode nextMode) {
        List<TopBarUiButton> next = new ArrayList<TopBarUiButton>();
        for (TopBarUiButton button : buttons) {
            boolean active = button.id.modeButton && matches(button.id, nextMode);
            next.add(button.id.modeButton ? button.withActive(active) : button);
        }
        return copy(next, nextMode);
    }

    public TopBarUiState toggle(TopBarUiButtonId id) {
        List<TopBarUiButton> next = new ArrayList<TopBarUiButton>();
        for (TopBarUiButton button : buttons) {
            next.add(button.id == id ? button.withActive(!button.active) : button);
        }
        return copy(next, mode);
    }

    private TopBarUiState copy(List<TopBarUiButton> nextButtons, Mode nextMode) {
        return new TopBarUiState(nextButtons, nextMode, storageLinked, linkedStorageName,
                autoStoreMinedDrops, funnelEnabled, shapeStatus, pendingGuiBindSlot,
                blueprintPlacementLocked);
    }

    private static boolean matches(TopBarUiButtonId id, Mode mode) {
        return (id == TopBarUiButtonId.INTERACT && mode == Mode.INTERACT)
                || (id == TopBarUiButtonId.LINK && mode == Mode.LINK_STORAGE)
                || (id == TopBarUiButtonId.FUNNEL && mode == Mode.FUNNEL)
                || (id == TopBarUiButtonId.ROTATE && mode == Mode.ROTATE);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
