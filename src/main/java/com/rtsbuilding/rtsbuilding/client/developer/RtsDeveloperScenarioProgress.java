package com.rtsbuilding.rtsbuilding.client.developer;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

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
        Objects.requireNonNull(required, "required");
        Map<String, Integer> snapshot = new LinkedHashMap<String, Integer>();
        for (Map.Entry<String, Integer> entry : required.entrySet()) {
            snapshot.put(
                    Objects.requireNonNull(entry.getKey(), "required event"),
                    Objects.requireNonNull(entry.getValue(), "required count"));
        }
        this.required = Collections.unmodifiableMap(snapshot);
    }

    synchronized void record(String event) {
        if (required.containsKey(event)) {
            Integer previous = observed.get(event);
            observed.put(event, previous == null ? 1 : previous + 1);
        }
    }

    synchronized boolean isComplete() {
        for (Map.Entry<String, Integer> entry : required.entrySet()) {
            Integer count = observed.get(entry.getKey());
            if ((count == null ? 0 : count) < entry.getValue()) return false;
        }
        return true;
    }

    synchronized int completedEvents() {
        int completed = 0;
        for (Map.Entry<String, Integer> entry : required.entrySet()) {
            Integer count = observed.get(entry.getKey());
            completed += Math.min(entry.getValue(), count == null ? 0 : count);
        }
        return completed;
    }

    int requiredEvents() {
        int total = 0;
        for (Integer count : required.values()) total += count;
        return total;
    }
}
