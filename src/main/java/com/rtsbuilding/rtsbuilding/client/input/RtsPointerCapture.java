package com.rtsbuilding.rtsbuilding.client.input;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 记录“按下时由谁拥有这根指针”，并保证拖动和释放只回到同一所有者。
 *
 * <p>它不认识 Minecraft 窗口、镜头或玩法动作，只管理 button 到 owner 的关系。
 * 因此 1.21.1、1.20.1 以及后续 Loader 端口都可以复用同一套输入语义。</p>
 */
public final class RtsPointerCapture<T> {
    private final Map<Integer, T> ownersByButton = new HashMap<>();

    public void capture(int button, T owner) {
        if (owner == null) {
            ownersByButton.remove(button);
        } else {
            ownersByButton.put(button, owner);
        }
    }

    public Optional<T> owner(int button) {
        return Optional.ofNullable(ownersByButton.get(button));
    }

    /**
     * 取出并释放指定按键的所有者。其他同时按下的鼠标键不受影响。
     */
    public Optional<T> release(int button) {
        return Optional.ofNullable(ownersByButton.remove(button));
    }

    public boolean hasCapture(int button) {
        return ownersByButton.containsKey(button);
    }

    public boolean hasAnyCapture() {
        return !ownersByButton.isEmpty();
    }

    public void clear() {
        ownersByButton.clear();
    }
}
