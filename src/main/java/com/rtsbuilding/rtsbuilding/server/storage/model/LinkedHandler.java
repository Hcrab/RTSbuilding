package com.rtsbuilding.rtsbuilding.server.storage.model;

import com.rtsbuilding.rtsbuilding.server.storage.port.RtsItemStorage;
import net.minecraft.core.BlockPos;

/**
 * 已解析的链接物品处理器——将链接存储引用与其对应的物品处理器绑定。
 *
 * <p>封装了物品处理器的身份引用、显示名称、是否允许存入以及优先级。
 *
 * @param ref        链接存储引用
 * @param name       显示名称
 * @param handler    Loader 无关的物品储存端口
 * @param allowStore 是否允许存入物品（false = 仅提取模式）
 * @param priority   优先级（AE 风格，影响插入顺序）
 */
public record LinkedHandler(LinkedStorageRef ref, String name, RtsItemStorage handler,
                            boolean allowStore, int priority) {
    public BlockPos pos() {
        return this.ref.pos();
    }
}
