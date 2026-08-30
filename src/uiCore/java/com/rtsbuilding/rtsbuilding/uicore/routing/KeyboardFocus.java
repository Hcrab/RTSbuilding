package com.rtsbuilding.rtsbuilding.uicore.routing;

/** 键盘焦点所有者；它与指针捕获故意分离。 */
public final class KeyboardFocus<T> {
    private T owner;

    public T getOwner() {
        return owner;
    }

    public boolean hasFocus() {
        return owner != null;
    }

    public boolean isFocused(T candidate) {
        return owner != null && owner == candidate;
    }

    public void request(T newOwner) {
        if (newOwner == null) {
            throw new IllegalArgumentException("focus owner must not be null");
        }
        owner = newOwner;
    }

    public boolean clear(T expectedOwner) {
        if (owner == null || owner != expectedOwner) {
            return false;
        }
        owner = null;
        return true;
    }

    public void clear() {
        owner = null;
    }
}
