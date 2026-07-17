package com.rtsbuilding.rtsbuilding.client.input;

import java.util.Optional;

/**
 * 与鼠标捕获分离的键盘/文本焦点所有者。
 */
public final class RtsKeyboardFocus<T> {
    private T owner;

    public void focus(T newOwner) {
        this.owner = newOwner;
    }

    public Optional<T> owner() {
        return Optional.ofNullable(owner);
    }

    public void blur() {
        this.owner = null;
    }

    public boolean isFocused(T candidate) {
        return owner == candidate;
    }
}
