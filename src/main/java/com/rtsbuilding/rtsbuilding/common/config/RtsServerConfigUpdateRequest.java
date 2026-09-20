package com.rtsbuilding.rtsbuilding.common.config;

import java.util.List;

/** 带预期修订号的白名单 typed 更新请求。 */
public record RtsServerConfigUpdateRequest(int expectedRevision, List<RtsServerConfigChange> changes) {
    public RtsServerConfigUpdateRequest {
        changes = changes == null ? List.of() : List.copyOf(changes);
        if (changes.size() > 64) throw new IllegalArgumentException("too many configuration changes");
    }
}
