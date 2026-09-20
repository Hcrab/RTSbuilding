package com.rtsbuilding.rtsbuilding.common.config;

import java.util.List;

/** G06/网络保存入口使用的 typed 变更批次；请求不携带任意文件路径或整份 TOML。 */
public record RtsServerConfigUpdateRequest(int expectedRevision,
                                           List<RtsServerConfigChange> changes) {
    public RtsServerConfigUpdateRequest {
        changes = changes == null ? List.of() : List.copyOf(changes);
        if (changes.size() > 64) {
            throw new IllegalArgumentException("too many configuration changes");
        }
    }
}
