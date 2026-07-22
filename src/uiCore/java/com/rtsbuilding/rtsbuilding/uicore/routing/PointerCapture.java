package com.rtsbuilding.rtsbuilding.uicore.routing;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 按鼠标键分别记录 press 所有者。
 *
 * <p>此类只管理所有权，不调用控件，也不猜测平台按钮编号。平台路由器必须把
 * drag/release 送回同一个所有者，并在 release 后释放对应按钮。</p>
 */
public final class PointerCapture<T> {
    private final Map<Integer, T> ownersByButton = new LinkedHashMap<Integer, T>();

    public boolean capture(int button, T owner) {
        if (button < 0 || owner == null) {
            throw new IllegalArgumentException("button and owner must be valid");
        }
        T existing = ownersByButton.get(button);
        if (existing != null && existing != owner) {
            return false;
        }
        ownersByButton.put(button, owner);
        return true;
    }

    public T ownerOf(int button) {
        return ownersByButton.get(button);
    }

    public boolean isCaptured(int button) {
        return ownersByButton.containsKey(button);
    }

    public T release(int button) {
        return ownersByButton.remove(button);
    }

    public int releaseOwner(T owner) {
        int released = 0;
        Integer[] buttons = ownersByButton.keySet().toArray(new Integer[ownersByButton.size()]);
        for (Integer button : buttons) {
            if (ownersByButton.get(button) == owner) {
                ownersByButton.remove(button);
                released++;
            }
        }
        return released;
    }

    public void clear() {
        ownersByButton.clear();
    }

    public Map<Integer, T> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<Integer, T>(ownersByButton));
    }
}
