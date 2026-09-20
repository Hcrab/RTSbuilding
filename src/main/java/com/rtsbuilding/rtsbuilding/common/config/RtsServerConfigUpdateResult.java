package com.rtsbuilding.rtsbuilding.common.config;

/** 服务端验证/落盘结果；失败也携带当前权威视图和原因。 */
public record RtsServerConfigUpdateResult(Status status, RtsServerConfigView view, String message) {
    public RtsServerConfigUpdateResult {
        status = status == null ? Status.INVALID : status;
        view = view == null ? RtsServerConfigView.defaults().readOnly() : view;
        message = message == null ? "" : message;
    }
    public boolean succeeded() { return status == Status.APPLIED || status == Status.NO_CHANGE; }
    public static RtsServerConfigUpdateResult applied(RtsServerConfigView view) { return new RtsServerConfigUpdateResult(Status.APPLIED, view, "applied"); }
    public static RtsServerConfigUpdateResult notEditable(RtsServerConfigView view) { return new RtsServerConfigUpdateResult(Status.NOT_EDITABLE, view, "server configuration is read-only"); }
    public static RtsServerConfigUpdateResult invalid(RtsServerConfigView view, String message) { return new RtsServerConfigUpdateResult(Status.INVALID, view, message); }
    public static RtsServerConfigUpdateResult revisionConflict(RtsServerConfigView view) { return new RtsServerConfigUpdateResult(Status.REVISION_CONFLICT, view, "configuration revision changed"); }
    public static RtsServerConfigUpdateResult saveFailed(RtsServerConfigView view, String message) { return new RtsServerConfigUpdateResult(Status.SAVE_FAILED, view, message); }
    public enum Status { APPLIED, NO_CHANGE, NOT_EDITABLE, REVISION_CONFLICT, INVALID, SAVE_FAILED }
}
