package com.rtsbuilding.rtsbuilding.client.kernel;

/**
 * 模块 ID 常量——替代散落在各处的魔字符串 {@code kernel.module("camera")}。
 *
 * <p>所有 Feature Module 的 ID 集中在此定义，IDE 重构时可安全重命名。</p>
 */
public final class ModuleIds {

    private ModuleIds() {}

    public static final String CAMERA = "camera";
    public static final String STORAGE = "storage";
    public static final String BUILDING = "building";
    public static final String MINING = "mining";
    public static final String BLUEPRINT = "blueprint";
    public static final String WORKFLOW = "workflow";
    public static final String PLUGIN = "plugin";
    public static final String PROGRESSION = "progression";
    public static final String OVERLAY = "overlay";
    public static final String REMOTE_MENU = "remote_menu";
}
