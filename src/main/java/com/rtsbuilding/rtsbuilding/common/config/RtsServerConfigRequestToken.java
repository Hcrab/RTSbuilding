package com.rtsbuilding.rtsbuilding.common.config;

/**
 * 客户端一次配置请求的身份。
 *
 * <p>设置页用它把返回的 typed ACK 和当前草稿绑定起来；session 切换或旧请求返回时，
 * 页面可以安全丢弃结果，而不把它误显示成当前保存成功。</p>
 */
public record RtsServerConfigRequestToken(long sessionId, int requestId, int expectedRevision) {
    public RtsServerConfigRequestToken {
        if (sessionId == 0L || requestId <= 0) {
            throw new IllegalArgumentException("invalid configuration request token");
        }
    }
}
