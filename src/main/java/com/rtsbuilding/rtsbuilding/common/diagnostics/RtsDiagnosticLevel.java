package com.rtsbuilding.rtsbuilding.common.diagnostics;

/**
 * RTSBuilding 诊断详细程度。
 *
 * <p>该枚举只控制记录量，不得参与任何玩法、调度、准入或恢复决策。</p>
 */
public enum RtsDiagnosticLevel {
    OFF,
    BASIC,
    VERBOSE
}
