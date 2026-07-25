package com.rtsbuilding.rtsbuilding.uicore.registry;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class UiOrderedRegistryTest {
    @Test
    void 默认按分组权重和注册顺序稳定排序() {
        UiOrderedRegistry<String> registry = new UiOrderedRegistry<String>();
        registry.register(reg("b", "a_top", 20));
        registry.register(reg("a", "a_top", 10));
        registry.register(reg("c", "b_tools", 0));
        assertEquals(Arrays.asList("a", "b", "c"), ids(registry.snapshot()));
    }

    @Test
    void before覆盖普通权重() {
        UiOrderedRegistry<String> registry = new UiOrderedRegistry<String>();
        registry.register(new UiRegistration<String>("late", "top", 100,
                Collections.singletonList("early"), Collections.<String>emptyList(), "late"));
        registry.register(reg("early", "top", 0));
        assertEquals(Arrays.asList("late", "early"), ids(registry.snapshot()));
    }

    @Test
    void after覆盖普通权重() {
        UiOrderedRegistry<String> registry = new UiOrderedRegistry<String>();
        registry.register(reg("anchor", "top", 100));
        registry.register(new UiRegistration<String>("follower", "top", 0,
                Collections.<String>emptyList(), Collections.singletonList("anchor"), "follower"));
        assertEquals(Arrays.asList("anchor", "follower"), ids(registry.snapshot()));
    }

    @Test
    void 缺失可选目标不会阻止快照() {
        UiOrderedRegistry<String> registry = new UiOrderedRegistry<String>();
        registry.register(new UiRegistration<String>("only", "top", 0,
                Collections.singletonList("optional_mod:missing"),
                Collections.<String>emptyList(), "only"));
        assertEquals(Collections.singletonList("only"), ids(registry.snapshot()));
    }

    @Test
    void 重复id立即失败() {
        UiOrderedRegistry<String> registry = new UiOrderedRegistry<String>();
        registry.register(reg("same", "top", 0));
        assertThrows(IllegalArgumentException.class, () -> registry.register(reg("same", "top", 1)));
    }

    @Test
    void 排序循环提供涉及id() {
        UiOrderedRegistry<String> registry = new UiOrderedRegistry<String>();
        registry.register(new UiRegistration<String>("a", "top", 0,
                Collections.singletonList("b"), Collections.<String>emptyList(), "a"));
        registry.register(new UiRegistration<String>("b", "top", 0,
                Collections.singletonList("a"), Collections.<String>emptyList(), "b"));
        IllegalStateException error = assertThrows(IllegalStateException.class, registry::snapshot);
        assertTrue(error.getMessage().contains("a"));
        assertTrue(error.getMessage().contains("b"));
    }

    @Test
    void 快照后禁止渲染期注册() {
        UiOrderedRegistry<String> registry = new UiOrderedRegistry<String>();
        registry.register(reg("a", "top", 0));
        registry.snapshot();
        assertTrue(registry.isFrozen());
        assertThrows(IllegalStateException.class, () -> registry.register(reg("b", "top", 0)));
    }

    @Test
    void id强制稳定小写格式() {
        assertThrows(IllegalArgumentException.class,
                () -> new UiRegistration<String>("Bad ID", "top", 0,
                        Collections.<String>emptyList(), Collections.<String>emptyList(), "bad"));
    }

    private static UiRegistration<String> reg(String id, String group, int weight) {
        return new UiRegistration<String>(id, group, weight,
                Collections.<String>emptyList(), Collections.<String>emptyList(), id);
    }

    private static List<String> ids(List<UiRegistration<String>> registrations) {
        return registrations.stream().map(UiRegistration::getId).collect(Collectors.toList());
    }
}
