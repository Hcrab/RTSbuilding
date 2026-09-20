package com.rtsbuilding.rtsbuilding.server.service.mining;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.common.network.RtsNetworkProtocol;
import com.rtsbuilding.rtsbuilding.server.network.RtsClientboundPackets;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.ConfigSync;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.nio.charset.StandardCharsets;

/**
 * 补齐挖掘配置在游玩阶段的同步：NeoForge 自带配置包只在登录握手阶段注册。
 *
 * <p>复用框架的配置接收入口，不另建客户端上限缓存。服务端每 tick 只比较当前有效视图的
 * 快照哈希，所有客户端实际消费的世界规则任一变化才编码已经加载的内存投影，因此同时覆盖文件重载和单人配置屏幕保存，不读盘，
 * 不启动线程，也不会把同步的服务端值保存到客户端本地文件。</p>
 */
@EventBusSubscriber(modid = RtsbuildingMod.MODID)
public final class RtsMiningConfigSync {
    private static final String CONFIG_FILE = "rts_building/rtsbuilding-server.toml";
    private static int lastSignature = Integer.MIN_VALUE;

    private RtsMiningConfigSync() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(RtsNetworkProtocol.VERSION).playToClient(Payload.TYPE, Payload.CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> ConfigSync.receiveSyncedConfig(payload.contents(), CONFIG_FILE)));
    }

    @SubscribeEvent
    public static void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Payload payload = snapshot();
            if (payload != null) {
                RtsClientboundPackets.sendToPlayer(player, payload);
                // 登录包已经是当前有效配置；记录签名避免下一 tick 对同一值重复广播。
                lastSignature = currentSignature();
            }
        }
    }

    @SubscribeEvent
    public static void afterServerTick(ServerTickEvent.Post event) {
        int signature = currentSignature();
        if (signature == lastSignature) return;
        MinecraftServer server = event.getServer();
        if (server.getPlayerList().getPlayers().isEmpty()) return;
        Payload payload = snapshot();
        if (payload == null) return;
        lastSignature = signature;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            RtsClientboundPackets.sendToPlayer(player, payload);
            RtsProgressionManager.syncToPlayer(player);
        }
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        lastSignature = Integer.MIN_VALUE;
    }

    private static Payload snapshot() {
        for (ModConfig config : ModConfigs.getConfigSet(ModConfig.Type.SERVER)) {
            if (Config.isServerSpec(config.getSpec()) && config.getLoadedConfig() != null) {
                byte[] contents = Config.clientServerConfigProjectionToml().getBytes(StandardCharsets.UTF_8);
                if (contents.length > Payload.MAX_CONFIG_BYTES) {
                    // 同步失败不能打断正在进行的世界任务；明确日志后保留登录阶段的框架同步入口。
                    RtsbuildingMod.LOGGER.warn("挖掘配置文档超过游玩阶段同步预算：{} 字节，请缩短自定义配置注释", contents.length);
                    return null;
                }
                return new Payload(contents);
            }
        }
        return null;
    }

    /** 只读内存中的有效值；服务端专有预算变化不触发客户端投影同步。 */
    private static int currentSignature() {
        return java.util.Objects.hash(
                Config.areBlueprintsEnabled(), Config.maxBlueprintBlocks(),
                Config.areaMineMaxVolume(), Config.areaMineMaxWidth(), Config.areaMineMaxHeight(),
                Config.areaMineMaxDepth(), Config.ultimineMaxBlocks(), Config.areaMineMaxHarvestTier(), Config.maxTreeBlocks(),
                Config.maxShapeDimension(), Config.maxShapeRadius(), Config.smartFillMaxBlocks(),
                Config.smartFillDefaultBlocks(), Config.smartFillMaxDiameter(), Config.smartFillDefaultDiameter(),
                Config.maxBatchBindingSelectionVolume(), Config.maxBatchBindingSizeX(),
                Config.maxBatchBindingSizeY(), Config.maxBatchBindingSizeZ());
    }

    /** 这是配置文档的字节预算，与挖掘方块数量无关；仅传输原本就由框架同步的 SERVER 配置。 */
    public record Payload(byte[] contents) implements CustomPacketPayload {
        public static final int MAX_CONFIG_BYTES = 256 * 1024;
        public static final Type<Payload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
                RtsbuildingMod.MODID, "s2c_mining_config_refresh"));
        public static final StreamCodec<FriendlyByteBuf, Payload> CODEC = StreamCodec.of(
                (buf, value) -> buf.writeByteArray(value.contents),
                buf -> new Payload(buf.readByteArray(MAX_CONFIG_BYTES)));

        public Payload {
            if (contents == null || contents.length == 0 || contents.length > MAX_CONFIG_BYTES) {
                throw new IllegalArgumentException("挖掘配置同步数据长度无效");
            }
            contents = contents.clone();
        }

        @Override
        public byte[] contents() {
            return contents.clone();
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
