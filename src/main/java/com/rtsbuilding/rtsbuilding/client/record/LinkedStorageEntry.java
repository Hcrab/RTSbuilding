package com.rtsbuilding.rtsbuilding.client.record;

import net.minecraft.core.BlockPos;

/**
 * 已链接的存储方块客户端显示记录。
 *
 * <p>从服务端存储页面载荷解码，用于渲染已绑定容器的角支架线框。
 * 包含方块坐标、链接模式（双向/仅提取）及世界是否可用等信息。</p>
 *
 * <p>移植自 {@code client_old/record/LinkedStorageEntry}。</p>
 */
public record LinkedStorageEntry(
        BlockPos pos,
        byte mode,
        boolean worldAvailable) {

    /** 双向模式（可存可取） */
    public static final byte MODE_BIDIRECTIONAL = 0;
    /** 仅提取模式 */
    public static final byte MODE_EXTRACT_ONLY = 1;

    /** 是否为双向模式 */
    public boolean isBidirectional() {
        return mode == MODE_BIDIRECTIONAL;
    }

    /** 是否为仅提取模式 */
    public boolean isExtractOnly() {
        return mode == MODE_EXTRACT_ONLY;
    }
}
