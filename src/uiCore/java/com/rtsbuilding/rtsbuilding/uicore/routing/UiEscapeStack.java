package com.rtsbuilding.rtsbuilding.uicore.routing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Escape 的单层退出栈。
 *
 * <p>每次 {@link #popTop()} 最多返回一个所有者；关闭窗口、取消工作流等副作用
 * 留给平台适配器执行。</p>
 */
public final class UiEscapeStack<T> {
    private final List<T> owners = new ArrayList<T>();

    public void push(T owner) {
        if (owner == null) {
            throw new IllegalArgumentException("owner must not be null");
        }
        remove(owner);
        owners.add(owner);
    }

    public boolean remove(T owner) {
        for (int i = owners.size() - 1; i >= 0; i--) {
            if (owners.get(i) == owner) {
                owners.remove(i);
                return true;
            }
        }
        return false;
    }

    public T peek() {
        return owners.isEmpty() ? null : owners.get(owners.size() - 1);
    }

    public T popTop() {
        return owners.isEmpty() ? null : owners.remove(owners.size() - 1);
    }

    public int size() {
        return owners.size();
    }

    public List<T> snapshotBottomToTop() {
        return Collections.unmodifiableList(new ArrayList<T>(owners));
    }
}
