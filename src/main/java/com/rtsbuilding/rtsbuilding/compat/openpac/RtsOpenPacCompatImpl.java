package com.rtsbuilding.rtsbuilding.compat.openpac;

/**
 * Open Parties and Claims 的 1.12 占位状态。
 *
 * <p>官方 OpenPAC 最低支持 1.18.2，没有可核验的 1.12 API。因此这里故意不保存任何
 * Xaero/OpenPAC 类型、类名或反射签名，避免把现代 API 伪装成旧版支持。</p>
 */
final class RtsOpenPacCompatImpl {
    private RtsOpenPacCompatImpl() {
    }

    static String unavailableReason() {
        return "Open Parties and Claims has no official Minecraft 1.12.2 release or API";
    }
}
