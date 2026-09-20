package com.rtsbuilding.rtsbuilding.client.network;

import com.rtsbuilding.rtsbuilding.common.config.RtsServerConfigChange;
import com.rtsbuilding.rtsbuilding.common.config.RtsServerConfigAckStore;
import com.rtsbuilding.rtsbuilding.common.config.RtsServerConfigClientSession;
import com.rtsbuilding.rtsbuilding.common.config.RtsServerConfigUpdateResult;
import com.rtsbuilding.rtsbuilding.common.config.RtsServerConfigView;
import com.rtsbuilding.rtsbuilding.common.config.RtsServerConfigWire;
import com.rtsbuilding.rtsbuilding.common.config.RtsServerConfigRequestToken;
import com.rtsbuilding.rtsbuilding.network.config.C2SRtsServerConfigQueryPayload;
import com.rtsbuilding.rtsbuilding.network.config.C2SRtsServerConfigUpdatePayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/** G06 设置页直接调用的客户端查询/读取/保存 API；不写本地 SERVER/COMMON 文件。 */
public final class RtsClientServerConfigNetwork {
    private static final RtsServerConfigClientSession SESSION = new RtsServerConfigClientSession();
    /** 独立保存 ACK；查询响应和 requestId=0 广播永远不能覆盖它。 */
    private static final RtsServerConfigAckStore SAVE_ACK = new RtsServerConfigAckStore();

    private RtsClientServerConfigNetwork() {
    }

    public static synchronized void beginSession() {
        SESSION.begin();
        clearLastResults();
    }

    public static synchronized void clearSession() {
        SESSION.clear();
        clearLastResults();
    }

    public static synchronized RtsServerConfigRequestToken requestCurrent() {
        if (SESSION.sessionId() == 0L) {
            SESSION.begin();
            clearLastResults();
        }
        PacketDistributor.sendToServer(new C2SRtsServerConfigQueryPayload(SESSION.query()));
        return new RtsServerConfigRequestToken(SESSION.sessionId(), SESSION.lastIssuedRequestId(),
                SESSION.current().revision());
    }

    public static synchronized RtsServerConfigRequestToken save(List<RtsServerConfigChange> changes) {
        return save(current().revision(), changes);
    }

    /** 使用 UI 已确认基准 revision 保存；服务端仍会重新做权限与 revision 校验。 */
    public static synchronized RtsServerConfigRequestToken save(
            int expectedRevision, List<RtsServerConfigChange> changes) {
        if (SESSION.sessionId() == 0L) {
            SESSION.begin();
            clearLastResults();
        }
        PacketDistributor.sendToServer(new C2SRtsServerConfigUpdatePayload(
                SESSION.update(expectedRevision, changes)));
        return new RtsServerConfigRequestToken(SESSION.sessionId(), SESSION.lastIssuedRequestId(),
                expectedRevision);
    }

    public static RtsServerConfigView current() {
        return SESSION.current();
    }

    public static boolean canEdit() {
        return current().editable();
    }

    /** 返回最近一次保存 ACK；查询/广播不会消耗 UI 等待的保存结果。 */
    public static synchronized RtsServerConfigUpdateResult lastResult() {
        return SAVE_ACK.result();
    }

    public static synchronized long lastResultSessionId() {
        return SAVE_ACK.sessionId();
    }

    public static synchronized int lastResultRequestId() {
        return SAVE_ACK.requestId();
    }

    public static synchronized RtsServerConfigUpdateResult lastSaveResult() {
        return SAVE_ACK.result();
    }

    public static synchronized long lastSaveResultSessionId() {
        return SAVE_ACK.sessionId();
    }

    public static synchronized int lastSaveResultRequestId() {
        return SAVE_ACK.requestId();
    }

    public static synchronized long sessionId() {
        return SESSION.sessionId();
    }

    public static synchronized void receive(byte[] data) {
        RtsServerConfigWire.Response response = RtsServerConfigWire.decodeResponse(data);
        if (SESSION.accept(response)) {
            // 只有当前会话最近一次 update request 才能写入保存结果；query 与广播只更新 current。
            SAVE_ACK.accept(response, SESSION.sessionId(), SESSION.lastIssuedUpdateRequestId());
            // 远端客户端需要内存投影；集成服的客户端和服务端共享静态 Config，跳过
            // 回写可以避免迟到广播把正在运行的本地主机规则改回旧快照。
            if (!Minecraft.getInstance().hasSingleplayerServer()) {
                com.rtsbuilding.rtsbuilding.Config.applySynchronizedServerView(SESSION.current());
            }
        }
    }

    private static void clearLastResults() {
        SAVE_ACK.clear();
    }
}
