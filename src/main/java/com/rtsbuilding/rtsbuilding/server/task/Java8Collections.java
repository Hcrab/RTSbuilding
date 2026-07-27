package com.rtsbuilding.rtsbuilding.server.task;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Java 8 下的防御性不可变集合工厂。
 *
 * <p>本类只替代 Java 9/10 引入的集合工厂，不改变任务快照原有的不可变语义。
 * 它不负责领域校验；元素是否允许为 {@code null} 仍由各值对象自行决定。</p>
 */
public final class Java8Collections {
    private Java8Collections() {
    }

    public static <T> List<T> copyList(Collection<? extends T> values) {
        return Collections.unmodifiableList(new ArrayList<T>(values));
    }

    @SafeVarargs
    public static <T> List<T> listOf(T... values) {
        ArrayList<T> copy = new ArrayList<T>(values.length);
        Collections.addAll(copy, values);
        return Collections.unmodifiableList(copy);
    }

    public static <T> Set<T> copySet(Collection<? extends T> values) {
        return Collections.unmodifiableSet(new LinkedHashSet<T>(values));
    }

    @SafeVarargs
    public static <T> Set<T> setOf(T... values) {
        LinkedHashSet<T> copy = new LinkedHashSet<T>();
        Collections.addAll(copy, values);
        return Collections.unmodifiableSet(copy);
    }
}
