package com.rtsbuilding.rtsbuilding.server.workflow.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 固定成功与取消两条终态路径的玩家可见语义。
 *
 * <p>成功工作流必须立即退出面板；取消或失败记录仍可短暂保留，方便玩家理解
 * 为什么任务停止。这个契约防止后续 UI 调整重新把成功卡片保留三十秒。</p>
 */
class RtsWorkflowCompletionContractTest {
    @Test
    void completionPublishesFinalEventBeforeRemovingEntry() throws IOException {
        String body = methodBody("complete", "cancel");
        int terminal = body.indexOf("entry.markTerminal()");
        int event = body.indexOf("engine.fireEvent(WorkflowEventType.COMPLETED");
        int removal = body.indexOf("engine.removeEntry(playerId, dimension, entryId)");

        assertTrue(terminal >= 0, "完成事件必须使用最终状态快照");
        assertTrue(event > terminal, "COMPLETED 事件必须在终态标记之后发出");
        assertTrue(removal > event, "必须先发布最终事件，再释放工作流槽位");
        assertFalse(body.contains("engine.notifyPlayer"),
                "removeEntry 已负责合并客户端同步，完成路径不应重复通知");
    }

    @Test
    void cancellationStillKeepsAVisibleTerminalRecord() throws IOException {
        String body = methodBody("cancel", "// ─");

        assertTrue(body.contains("entry.markTerminal()"));
        assertTrue(body.contains("engine.fireEvent(WorkflowEventType.CANCELLED"));
        assertTrue(body.contains("engine.notifyPlayer(playerId, dimension)"));
        assertFalse(body.contains("engine.removeEntry("),
                "取消记录仍需短暂展示，不能与成功完成共用立即删除语义");
    }

    private static String methodBody(String methodName, String nextMarker) throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/workflow/core/RtsWorkflowToken.java"));
        int start = source.indexOf("public void " + methodName + "()");
        int end = source.indexOf(nextMarker, start + 1);
        assertTrue(start >= 0 && end > start, "无法定位 " + methodName + " 方法");
        return source.substring(start, end);
    }
}
