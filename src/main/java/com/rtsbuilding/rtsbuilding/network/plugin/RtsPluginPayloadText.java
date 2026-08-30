package com.rtsbuilding.rtsbuilding.network.plugin;

/** 插件状态网络包的文本边界保护，所有外部队伍/玩家名称都必须经过这里。 */
final class RtsPluginPayloadText {
    private RtsPluginPayloadText() {
    }

    static String fit(String value, int maxChars) {
        if (value == null || maxChars <= 0) {
            return "";
        }
        if (value.length() <= maxChars) {
            return value;
        }
        int end = maxChars;
        // 不在代理对中间截断，避免生成无效的 UTF-16 字符串。
        if (end > 0 && Character.isHighSurrogate(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end);
    }
}
