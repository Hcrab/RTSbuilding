package com.rtsbuilding.rtsbuilding.platform.chat;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.IChatComponent;

/**
 * 隔离各 Minecraft 版本的玩家提示消息差异。
 *
 * <p>1.7.10 没有后续版本稳定公开的状态栏消息入口，因此状态提示先安全降级到聊天栏。
 * 业务代码仍保留 {@code actionBar} 意图，未来若 GTNH 环境提供更可靠的客户端显示通道，
 * 只需要修改这里，不需要再次触碰工作流、建造、挖掘和储存代码。</p>
 */
public final class ChatMessages {
    private ChatMessages() {
    }

    public static void sendStatus(EntityPlayer player, IChatComponent message, boolean actionBar) {
        if (player == null || message == null) return;
        player.addChatMessage(message);
    }
}
