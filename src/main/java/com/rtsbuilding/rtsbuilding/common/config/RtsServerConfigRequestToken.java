package com.rtsbuilding.rtsbuilding.common.config;

/** 设置页将 typed ACK 对应到当前会话和保存基准的请求身份。 */
public record RtsServerConfigRequestToken(long sessionId, int requestId, int expectedRevision) {
    public RtsServerConfigRequestToken {
        if (sessionId == 0L || requestId <= 0) {
            throw new IllegalArgumentException("invalid configuration request token");
        }
    }
}
