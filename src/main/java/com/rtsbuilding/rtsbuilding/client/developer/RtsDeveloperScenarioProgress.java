package com.rtsbuilding.rtsbuilding.client.developer;

import java.util.HashMap;
import java.util.Map;

/**
 * 开发者场景的纯事件计数器。
 *
 * <p>它不读取游戏实例、不写日志，也不假设请求与服务端确认严格交替，因而可以覆盖
 * 多人延迟下“先连续发出请求、随后批量收到确认”的真实顺序。</p>
 */
final class RtsDeveloperScenarioProgress {
    private final Map<String, Integer> required;
    private final Map<String, Integer> observed = new HashMap<>();

    RtsDeveloperScenarioProgress(Map<String, Integer> required) {
        this.required = Map.copyOf(required);
    }

    void record(String event) {
        if (required.containsKey(event)) {
            observed.merge(event, 1, Integer::sum);
        }
    }

    boolean isComplete() {
        return required.entrySet().stream()
                .allMatch(entry -> observed.getOrDefault(entry.getKey(), 0) >= entry.getValue());
    }

    int completedEvents() {
        return required.entrySet().stream()
                .mapToInt(entry -> Math.min(entry.getValue(), observed.getOrDefault(entry.getKey(), 0)))
                .sum();
    }

    int requiredEvents() {
        return required.values().stream().mapToInt(Integer::intValue).sum();
    }
}
