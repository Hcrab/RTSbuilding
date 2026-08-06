package com.rtsbuilding.rtsbuilding.network;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.network.builder.RtsWorkflowControlPackets;
import com.rtsbuilding.rtsbuilding.network.builder.RtsPlacementControlPackets;
import com.rtsbuilding.rtsbuilding.network.blueprint.BlueprintPayloadRegistrar;
import com.rtsbuilding.rtsbuilding.network.camera.RtsCameraPackets;
import com.rtsbuilding.rtsbuilding.network.storage.RtsStoragePackets;
import com.rtsbuilding.rtsbuilding.network.builder.RtsActionControlPackets;
import com.rtsbuilding.rtsbuilding.network.builder.RtsMiningPackets1122;
import com.rtsbuilding.rtsbuilding.network.builder.RtsSmartFillPackets1122;
import com.rtsbuilding.rtsbuilding.network.builder.RtsPlacementActionPackets1122;
import com.rtsbuilding.rtsbuilding.network.builder.RtsBlockActionSoundPackets1122;
import com.rtsbuilding.rtsbuilding.network.builder.RtsBuilderSyncPackets1122;
import com.rtsbuilding.rtsbuilding.network.pathfinding.RtsPathfindingPackets;
import com.rtsbuilding.rtsbuilding.network.plugin.RtsPluginPackets;
import com.rtsbuilding.rtsbuilding.network.craft.RtsCraftPackets;
import com.rtsbuilding.rtsbuilding.network.culling.RtsCullingPackets;
import com.rtsbuilding.rtsbuilding.network.feedback.RtsFeedbackPackets;
import com.rtsbuilding.rtsbuilding.network.progression.RtsProgressionPackets;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

import java.util.BitSet;

/**
 * 1.12.2 网络协议的唯一入口。
 *
 * <p>本类只负责通道生命周期、稳定 discriminator 和发送；各业务域继续拥有自己的
 * 消息字段与处理语义。显式编号是协议的一部分，后续迁移不得因为调整注册顺序而重排
 * 已发布编号。</p>
 */
public final class RtsPayloadRegistrar {
    public static final String PROTOCOL_VERSION = "1";
    private static final String CHANNEL_NAME = RtsbuildingMod.MODID;
    private static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL_NAME);
    private static final BitSet REGISTERED_IDS = new BitSet(256);
    private static boolean initialized;

    private RtsPayloadRegistrar() {
    }

    /** 必须在 Forge pre-init/init 的公共侧入口调用一次。 */
    public static synchronized void register() {
        if (initialized) {
            return;
        }
        RtsCameraPackets.register();
        RtsFeedbackPackets.register();
        RtsProgressionPackets.register();
        RtsBuilderSyncPackets1122.register();
        RtsCullingPackets.register();
        RtsWorkflowControlPackets.register();
        RtsPlacementControlPackets.register();
        BlueprintPayloadRegistrar.register();
        RtsStoragePackets.register();
        RtsActionControlPackets.register();
        RtsPathfindingPackets.register();
        RtsMiningPackets1122.register();
        RtsSmartFillPackets1122.register();
        RtsPlacementActionPackets1122.register();
        RtsBlockActionSoundPackets1122.register();
        RtsPluginPackets.register();
        RtsCraftPackets.register();
        initialized = true;
    }

    public static synchronized <REQ extends IMessage, REPLY extends IMessage> void registerMessage(
            int discriminator,
            Class<? extends IMessageHandler<REQ, REPLY>> handler,
            Class<REQ> message,
            Side receiveSide) {
        if (discriminator < 0 || discriminator > 255) {
            throw new IllegalArgumentException("Packet discriminator out of range: " + discriminator);
        }
        if (REGISTERED_IDS.get(discriminator)) {
            throw new IllegalStateException("Duplicate packet discriminator: " + discriminator);
        }
        CHANNEL.registerMessage(handler, message, discriminator, receiveSide);
        REGISTERED_IDS.set(discriminator);
    }

    public static void sendToServer(IMessage message) {
        requireInitialized();
        CHANNEL.sendToServer(message);
    }

    public static void sendToPlayer(EntityPlayerMP player, IMessage message) {
        requireInitialized();
        if (player == null) {
            throw new IllegalArgumentException("player");
        }
        CHANNEL.sendTo(message, player);
    }

    public static SimpleNetworkWrapper channel() {
        requireInitialized();
        return CHANNEL;
    }

    private static void requireInitialized() {
        if (!initialized) {
            throw new IllegalStateException("RTS network channel has not been registered");
        }
    }
}
