package com.rtsbuilding.rtsbuilding.client.screen.guide;

import com.rtsbuilding.rtsbuilding.RtsCommunityLinks;

/**
 * 生成可直接粘贴给通用 AI 的 RTSBuilding 求助文本。
 *
 * <p>本类只负责稳定的文本结构，不读取 Minecraft 状态，也不操作剪贴板。
 * 这样版本、模式与教程来源可以在生产适配器中替换，同时让结尾契约能被普通单元测试覆盖。
 */
public final class RtsAiHelpPrompt {
    private RtsAiHelpPrompt() {
    }

    public static String compose(boolean chinese,
                                 String modVersion,
                                 String minecraftVersion,
                                 String loaderVersion,
                                 String language,
                                 String mode,
                                 String tutorial,
                                 String latestLogLines,
                                 String rtsLogLines,
                                 boolean logAvailable) {
        int logLength = safeLength(latestLogLines) + safeLength(rtsLogLines);
        StringBuilder text = new StringBuilder(Math.max(8192, tutorial.length() + logLength + 2200));
        if (chinese) {
            text.append("""
                    你是 RTSBuilding 模组的教程助手。请仅依据下面提供的当前游戏信息和教程回答玩家的问题。

                    回答规则：
                    1. 先直接告诉玩家下一步该怎么做。
                    2. 普通操作问题以教程为准，并写出相关章节名称；教程没有说明时，明确说明这一点。
                    3. 如果用户反馈疑似 bug，不要只回答“教程中没有说明”。先用一句话归纳现象，再把当前版本的技术信息附录作为上下文，与最近日志逐段对照，结合当前版本、模式和教程进行合理推断，按可能性从高到低给出 2～5 条彼此不同、玩家可以立即尝试的排查路径。普通操作问题按教程回答，不要深入服务端实现或日志分析。
                    4. 每条排查路径都要写清“检查什么、怎么操作、什么结果说明这条路径成立”。教程没有直接依据的内容必须标注为“推测”，不能把推测写成已确认原因。
                    5. 优先检查当前模式、所需插件、选中工具或物品、储存绑定、生存平衡、家园/会话范围、按键方式和相关设置；仅在确有必要时再建议重启、查看日志或反馈 issue。
                    6. 如果信息不足，先提供安全且可逆的初步检查，再追问最能区分原因的 1～3 项信息。优先询问复现步骤、当前模式/插件/工具状态、是否关闭生存平衡后恢复，以及日志中的首个相关错误。
                    7. 把排障视为多轮对话：提出问题后等待用户回答；收到新信息后排除不符合的路径、重新排序剩余可能性，并继续追问下一项真正必要的信息。不要为了显得完整而一次性索要整份日志或模组列表。
                    8. 回答简洁，优先写操作步骤，少写内部实现和防误解式补充。不要建议删除存档、配置或模组文件等破坏性操作。
                    9. 文末附带了 latest.log 最后 200 行和最近 200 行 RTSBuilding 相关记录；用户反馈 bug 时先按时间顺序找首个异常，再用同一条 `trace` 关联 `seq`、`op`、`workflow`、`task`，检查 `[RTS-TRACE]`、`[RTS-DIAG]`、`[RTS-SERVER-HEALTH]`、`BEGIN/RESULT/FILTER/TERMINAL` 以及异常堆栈。先区分客户端输入/网络、服务端拒绝、任务等待资源、任务终态和后续连锁错误；日志不够时标注未知，不要把最后一条错误自动当成根因。普通操作问题或日志无关时直接忽略。
                    10. 日志只用于建立可验证的时间线：说明“看到的证据、支持的结论、仍是推测的部分”，不要为了使用日志而强行解读，也不要要求玩家上传完整日志或整个模组列表。
                    11. 每次回答结尾只提示：如果需要 GitHub、Discord、QQ 等反馈渠道，请回复“反馈渠道”；只有玩家明确回复或询问反馈渠道时，才提供这些信息。

                    当前游戏信息：
                    """);
            appendInfo(text, "RTSBuilding 版本", modVersion);
            appendInfo(text, "Minecraft 版本", minecraftVersion);
            appendInfo(text, "NeoForge 版本", loaderVersion);
            appendInfo(text, "游戏语言", language);
            appendInfo(text, "当前 RTS 模式", mode);
            text.append("\n求助链接：\n");
            appendLinks(text);
            text.append("\n以下是完整教程：\n\n").append(tutorial.strip());
            appendLogs(text, true, latestLogLines, rtsLogLines, logAvailable);
            text.append("\n\n用户的问题是：");
        } else {
            text.append("""
                    You are the tutorial assistant for the RTSBuilding mod. Answer only from the current game information and tutorial provided below.

                    Rules:
                    1. Tell the player what to do next before adding details.
                    2. For normal how-to questions, follow the tutorial and name the relevant section. Clearly say when the tutorial does not cover something.
                    3. When the user reports a possible bug, do not stop at "The tutorial does not explain this." Summarize the symptom in one sentence, then use this version's Technical Information Appendix as context and compare it step by step with the recent logs. Use the version, current mode, and tutorial to provide 2–5 distinct troubleshooting paths ranked by likelihood. For normal how-to questions, follow the tutorial without diving into server internals or log analysis.
                    4. For every path, state what to check, the exact action to try, and what result would support that diagnosis. Label anything not directly supported by the tutorial as an "inference"; never present an inference as a confirmed cause.
                    5. Check the current mode, required plugins, selected tool or item, storage link, survival progression, home/session range, input method, and relevant settings before suggesting a restart, logs, or an issue report.
                    6. If key information is missing, first give safe and reversible checks, then ask only 1–3 questions that best distinguish the causes. Prioritize reproduction steps, current mode/plugin/tool state, whether disabling survival progression changes the result, and the first relevant log error.
                    7. Treat troubleshooting as a multi-turn conversation: wait for the user's answers, eliminate paths that no longer fit, rerank the remaining causes, and ask for the next genuinely necessary detail. Do not request an entire log or mod list merely to appear thorough.
                    8. Keep the answer concise and action-oriented. Avoid unnecessary implementation details, and never recommend destructive actions such as deleting saves, configs, or mod files.
                    9. The end of this prompt includes the last 200 lines of latest.log and the latest 200 RTSBuilding-related lines. For a bug report, first find the earliest abnormal event in time order, then correlate the same `trace` across `seq`, `op`, `workflow`, and `task`. Check `[RTS-TRACE]`, `[RTS-DIAG]`, `[RTS-SERVER-HEALTH]`, `BEGIN/RESULT/FILTER/TERMINAL`, and the first relevant stack trace. Separate client input/network failure, server rejection, resource waiting, task terminal state, and later cascade errors; do not treat the last error as the root cause automatically. Ignore logs for normal how-to questions or unrelated logs.
                    10. Use logs to build a verifiable timeline: distinguish observed evidence, supported conclusions, and remaining inferences. Never force an interpretation merely because logs are present, and do not request the entire log or mod list by default.
                    11. End every answer with only this offer: if the player needs GitHub, Discord, QQ, or other feedback channels, ask them to reply “反馈渠道”. Provide those channels only after the player explicitly asks for them.

                    Current game information:
                    """);
            appendInfo(text, "RTSBuilding version", modVersion);
            appendInfo(text, "Minecraft version", minecraftVersion);
            appendInfo(text, "NeoForge version", loaderVersion);
            appendInfo(text, "Game language", language);
            appendInfo(text, "Current RTS mode", mode);
            text.append("\nSupport links:\n");
            appendLinks(text);
            text.append("\nComplete tutorial:\n\n").append(tutorial.strip());
            appendLogs(text, false, latestLogLines, rtsLogLines, logAvailable);
            text.append("\n\nThe user's question is:");
        }
        return text.toString();
    }

    private static void appendInfo(StringBuilder text, String label, String value) {
        text.append("- ").append(label).append(": ").append(safe(value)).append('\n');
    }

    private static void appendLinks(StringBuilder text) {
        text.append("- Website: ").append(RtsCommunityLinks.WEBSITE).append('\n');
        text.append("- GitHub: ").append(RtsCommunityLinks.GITHUB_REPOSITORY).append('\n');
        text.append("- Discord: ").append(RtsCommunityLinks.DISCORD_INVITE).append('\n');
        text.append("- QQ group: ").append(RtsCommunityLinks.QQ_GROUP).append('\n');
    }

    private static void appendLogs(StringBuilder text,
                                   boolean chinese,
                                   String latestLogLines,
                                   String rtsLogLines,
                                   boolean available) {
        if (!available) {
            text.append(chinese
                    ? "\n\n## 诊断日志\nlatest.log 当前不可用；不影响回答普通操作问题。"
                    : "\n\n## Diagnostic logs\nlatest.log is currently unavailable. This does not affect normal how-to answers.");
            return;
        }
        text.append(chinese
                ? "\n\n## latest.log 最后 200 行\n"
                : "\n\n## Last 200 lines of latest.log\n");
        text.append(safeLog(latestLogLines));
        text.append(chinese
                ? "\n\n## 最近 200 行 RTSBuilding 相关日志\n"
                : "\n\n## Latest 200 RTSBuilding-related log lines\n");
        text.append(safeLog(rtsLogLines));
    }

    private static String safeLog(String value) {
        return value == null || value.isBlank() ? "(none)" : value.strip();
    }

    private static int safeLength(String value) {
        return value == null ? 0 : value.length();
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value.strip();
    }
}
