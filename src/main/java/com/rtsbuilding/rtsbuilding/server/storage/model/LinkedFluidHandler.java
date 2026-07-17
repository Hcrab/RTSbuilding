package com.rtsbuilding.rtsbuilding.server.storage.model;

import net.minecraft.core.BlockPos;
import com.rtsbuilding.rtsbuilding.server.storage.port.RtsFluidStorage;

/**
 * 已解析的链接流体处理器——将链接存储引用与其对应的流体处理器绑定。
 *
 * <p>封装了流体处理器的身份引用、显示名称、是否允许存入以及优先级。
 *
 * @param ref        链接存储引用
 * @param name       显示名称
 * @param handler    流体处理器
 * @param allowStore 是否允许存入流体（false = 仅提取模式）
 * @param priority   优先级（AE 风格，影响插入顺序）
 */
public record LinkedFluidHandler(
        LinkedStorageRef ref, String name, RtsFluidStorage handler, boolean allowStore, int priority) {
    public BlockPos pos() {
        return this.ref.pos();
    }
}
