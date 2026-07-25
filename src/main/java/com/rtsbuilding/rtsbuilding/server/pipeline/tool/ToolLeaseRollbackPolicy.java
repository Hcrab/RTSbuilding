package com.rtsbuilding.rtsbuilding.server.pipeline.tool;

/**
 * 绠￠亾澶辫触鏃剁殑绾伐鍏锋墍鏈夋潈鍐崇瓥銆?
 *
 * <p>璇ョ被涓嶈闂帺瀹躲€佷細璇濇垨 Minecraft 瀵硅薄锛屽彧鎶婂悓姝ョ閬撹褰曠殑浜嬪疄杞崲鎴?
 * 涓や釜鏄庣‘鍔ㄤ綔銆備换鍔℃挙閿€涓庡伐鍏峰綊杩樺繀椤诲垎寮€鍒ゆ柇锛氶槦鍒椾换鍔￠渶瑕佹挙閿€锛屼絾骞舵湭
 * 鍊熷叆鏂板伐鍏凤紱闈為槦鍒椾换鍔℃棦闇€瑕佹挙閿€锛屼篃宸茬粡鎶婂伐鍏锋墍鏈夋潈绉讳氦缁欎换鍔°€?/p>
 */
public final class ToolLeaseRollbackPolicy {
    private ToolLeaseRollbackPolicy() {
    }

    public static Decision decide(boolean taskSubmitted, boolean leaseTransferred,
            boolean leaseReturned, boolean pipelineLeasePresent) {
        return new Decision(
                taskSubmitted,
                pipelineLeasePresent && !leaseTransferred && !leaseReturned);
    }

    public record Decision(boolean cancelSubmittedTask, boolean returnPipelineLease) {
    }
}

