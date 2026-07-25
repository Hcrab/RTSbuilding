package com.rtsbuilding.rtsbuilding.server.data;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.workflow.service.RtsWorkflowSlotManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 宸ヤ綔娴佹潯鐩殑鎸佷箙鍖栧瓨鍌ㄣ€?
 *
 * <p><b>鏂扮増</b>锛氶€氳繃 {@link DataCluster} + {@link WorkflowComponents#FULL_WORKFLOW}
 * 鎸夌帺瀹舵媶鍒嗘枃浠跺瓨鍌紙{@code rtsbuilding/players/{uuid}/workflow.dat}锛夈€?
 * 浣跨敤鑴忔爣璁板欢杩熷埛鐩橈紝浠呭湪鐜╁鏈夋椿璺冨伐浣滄祦鏃舵墠鍐欏搴旀枃浠躲€?
 * DataCluster 鐢熷懡鍛ㄦ湡缁熶竴鐢?{@link SaveScheduler} 绠＄悊锛屾湰绫诲彧璐熻矗缂栬В鐮併€?
 *
 * <p><b>鏃х増</b>锛氬皢鎵€鏈夌帺瀹舵暟鎹啓鍏ュ崟涓?{@code rtsbuilding/workflow_data.dat}銆?
 * 鏃х増 API 淇濈暀鐢ㄤ簬鍔犺浇閬楃暀瀛樻。鏁版嵁鏃剁殑鍥為€€銆?
 */
public final class RtsWorkflowStore {

    // 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
    //  鏃х増甯搁噺锛堜繚鐣欑敤浜庨仐鐣欐暟鎹洖閫€锛?
    // 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

    private static final String DIRECTORY = "rtsbuilding";
    private static final String FILE_NAME = "workflow_data.dat";
    private static final String KEY_DATA_VERSION = "data_version";
    private static final String KEY_PLAYERS = "players";
    private static final int DATA_VERSION = 1;

    private static final String KEY_DIMENSIONS = "dimensions";

    private RtsWorkflowStore() {
    }

    // 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
    //  鏂扮増 API锛堥€愮帺瀹?DataCluster锛岄€氳繃 SaveScheduler 绠＄悊鐢熷懡鍛ㄦ湡锛?
    // 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

    /**
     * 淇濆瓨鎵€鏈夌帺瀹跺湪鎵€鏈夌淮搴︿笂鐨勫伐浣滄祦妲戒綅绠＄悊鍣ㄣ€?
     *
     * <p>姣忎釜鐜╁鐨勬暟鎹啓鍏?{@code rtsbuilding/players/{uuid}/workflow.dat}锛?
     * 閫氳繃 {@link DataCluster#set(DataComponent, Object)} 鏍囪鑴忋€?
     * 鍐欏叆鍚庨€氳繃 {@link SaveScheduler#flushAll()} 绔嬪嵆鍒风洏浠ョ‘淇濇暟鎹惤鐩樸€?
     * DataCluster 鐢熷懡鍛ㄦ湡鐢?{@link SaveScheduler} 缁熶竴绠＄悊銆?
     *
     * @param server   Minecraft 鏈嶅姟鍣ㄥ疄渚?
     * @param allSlots 鐜╁ UUID 鈫?缁村害 鈫?妲戒綅绠＄悊鍣ㄧ殑鏄犲皠
     */
    public static void saveAll(MinecraftServer server,
                               Map<UUID, Map<ResourceKey<Level>, RtsWorkflowSlotManager>> allSlots) {
        if (server == null || allSlots == null) return;

        for (Map.Entry<UUID, Map<ResourceKey<Level>, RtsWorkflowSlotManager>> playerEntry : allSlots.entrySet()) {
            UUID playerId = playerEntry.getKey();
            Map<ResourceKey<Level>, RtsWorkflowSlotManager> dimSlots = playerEntry.getValue();
            if (dimSlots == null || dimSlots.isEmpty()) continue;

            CompoundTag dimensions = new CompoundTag();
            boolean hasData = false;

            for (Map.Entry<ResourceKey<Level>, RtsWorkflowSlotManager> dimEntry : dimSlots.entrySet()) {
                ResourceKey<Level> dimension = dimEntry.getKey();
                RtsWorkflowSlotManager slots = dimEntry.getValue();
                if (slots == null || slots.occupiedCount() == 0) continue;

                CompoundTag slotsTag = slots.saveToNbt();
                if (slotsTag != null && !slotsTag.isEmpty()) {
                    dimensions.put(dimension.location().toString(), slotsTag);
                    hasData = true;
                }
            }

            if (hasData) {
                CompoundTag playerData = new CompoundTag();
                playerData.put(KEY_DIMENSIONS, dimensions);
                cluster(server, playerId).set(WorkflowComponents.FULL_WORKFLOW, playerData);
            }
        }

        // 鍏抽敭浜嬩欢鍚庣珛鍗冲埛鐩橈紝纭繚鏁版嵁钀界洏
        SaveScheduler.INSTANCE.flushAll();
    }

    /**
     * 浠庡瓨鍌ㄤ腑鍔犺浇鎸囧畾鐜╁鐨勫伐浣滄祦妲戒綅绠＄悊鍣ㄣ€?
     *
     * <p>浼樺厛浠庢柊鐗堢殑閫愮帺瀹?{@link DataCluster} 鍔犺浇锛?
     * 濡傛灉涓嶅瓨鍦紙棣栨杩佺Щ鍚庨仐鐣欐暟鎹級锛屽洖閫€鍒版棫鐗堝叏閲忔枃浠躲€?
     *
     * @param server   Minecraft 鏈嶅姟鍣ㄥ疄渚?
     * @param playerId 鐜╁鐨?UUID
     * @return 缁村害 鈫?妲戒綅绠＄悊鍣ㄧ殑鏄犲皠
     */
    public static Map<ResourceKey<Level>, RtsWorkflowSlotManager> loadPlayer(
            MinecraftServer server, UUID playerId) {
        // 灏濊瘯鏂扮増閫愮帺瀹舵枃浠?
        DataCluster dc = cluster(server, playerId);
        CompoundTag root = dc.get(WorkflowComponents.FULL_WORKFLOW);
        if (!root.isEmpty() && root.contains(KEY_DIMENSIONS)) {
            return deserializeDimensions(root.getCompound(KEY_DIMENSIONS));
        }

        // 鍥為€€锛氬皾璇曟棫鐗堝叏閲忔枃浠?
        return loadPlayerLegacy(server, playerId);
    }

    // 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
    //  鍐呴儴鏂规硶
    // 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

    private static DataCluster cluster(MinecraftServer server, UUID playerId) {
        // 閫氳繃 SaveScheduler 缁熶竴绠＄悊鐢熷懡鍛ㄦ湡锛岄伩鍏嶇嫭绔嬬紦瀛樺鑷寸殑鍒嗚
        return SaveScheduler.INSTANCE.dataCluster(server, playerId, "workflow");
    }

    private static Map<ResourceKey<Level>, RtsWorkflowSlotManager> deserializeDimensions(CompoundTag dimensions) {
        Map<ResourceKey<Level>, RtsWorkflowSlotManager> result = new HashMap<>();
        for (String dimKey : dimensions.getAllKeys()) {
            ResourceLocation dimLocation = ResourceLocation.tryParse(dimKey);
            if (dimLocation == null) continue;

            ResourceKey<Level> dimension = RtsDimensionKeys.create(dimLocation);
            CompoundTag slotsTag = dimensions.getCompound(dimKey);
            if (slotsTag != null && !slotsTag.isEmpty()) {
                RtsWorkflowSlotManager slots = RtsWorkflowSlotManager.loadFromNbt(slotsTag);
                if (slots.occupiedCount() > 0) {
                    result.put(dimension, slots);
                }
            }
        }
        return result;
    }

    // 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
    //  鏃х増鍏ㄩ噺鏂囦欢鍥為€€
    // 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

    private static Map<ResourceKey<Level>, RtsWorkflowSlotManager> loadPlayerLegacy(
            MinecraftServer server, UUID playerId) {
        Map<ResourceKey<Level>, RtsWorkflowSlotManager> result = new HashMap<>();
        if (server == null || playerId == null) return result;

        var legacyStore = new RtsAtomicNbtStore(server, DIRECTORY, FILE_NAME);
        RtsNbtStore.ReadResult readResult = legacyStore.readResult();
        if (readResult instanceof RtsNbtStore.ReadResult.Failed failed) {
            RtsbuildingMod.LOGGER.error("[Workflow] 鏃х増瀛樻。璇诲彇澶辫触锛屽凡淇濈暀鍘熸枃浠朵笖璺宠繃杩佺Щ: {}",
                    failed.cause().getMessage());
            return result;
        }
        if (readResult instanceof RtsNbtStore.ReadResult.Missing) return result;
        CompoundTag root = ((RtsNbtStore.ReadResult.Found) readResult).root();
        if (root.isEmpty()) return result;

        CompoundTag players = root.getCompound(KEY_PLAYERS);
        if (players.isEmpty()) return result;

        String playerKey = playerId.toString();
        if (!players.contains(playerKey)) return result;

        CompoundTag playerTag = players.getCompound(playerKey);
        CompoundTag dimensions = playerTag.getCompound(KEY_DIMENSIONS);
        result.putAll(deserializeDimensions(dimensions));

        // 杩佺Щ蹇呴』鍏堢‘璁ゆ柊鐗堢帺瀹舵枃浠惰惤鐩橈紝鍐嶆竻鐞嗘棫鏂囦欢锛涘弽杩囨潵浼氬湪绗簩娆″啓鐩樺け璐ユ椂涓㈠け鍞竴鍓湰銆?        if (!result.isEmpty()) {
            CompoundTag playerData = new CompoundTag();
            CompoundTag dims = new CompoundTag();
            for (Map.Entry<ResourceKey<Level>, RtsWorkflowSlotManager> entry : result.entrySet()) {
                CompoundTag slotsTag = entry.getValue().saveToNbt();
                if (slotsTag != null) {
                    dims.put(entry.getKey().location().toString(), slotsTag);
                }
            }
            playerData.put(KEY_DIMENSIONS, dims);
            DataCluster playerCluster = cluster(server, playerId);
            playerCluster.set(WorkflowComponents.FULL_WORKFLOW, playerData);
            if (!playerCluster.flush()) {
                RtsbuildingMod.LOGGER.error(
                        "[Workflow] 鐜╁ {} 鐨勬柊鐗堝伐浣滄祦鏂囦欢鍐欏叆澶辫触锛屾棫鐗堟暟鎹繚鎸佷笉鍙橈紝绛夊緟涓嬫閲嶈瘯",
                        playerId);
                return result;
            }

            players.remove(playerKey);
            CompoundTag migratedLegacyRoot = root.copy();
            migratedLegacyRoot.put(KEY_PLAYERS, players.isEmpty() ? new CompoundTag() : players);
            migratedLegacyRoot.putInt(KEY_DATA_VERSION, DATA_VERSION);
            if (!legacyStore.write(migratedLegacyRoot)) {
                RtsbuildingMod.LOGGER.warn(
                        "[Workflow] 鐜╁ {} 鐨勬柊鐗堟暟鎹凡钀界洏锛屼絾鏃х増绱㈠紩娓呯悊澶辫触锛涘凡淇濈暀鍙屽壇鏈紝闇€绋嶅悗娓呯悊鏃ф枃浠?,
                        playerId);
                return result;
            }

            RtsbuildingMod.LOGGER.info("[Workflow] 宸茶縼绉荤帺瀹?{} 鐨勫伐浣滄祦鏁版嵁鍒版柊鐗堟牸寮?, playerId);
        }

        return result;
    }
}
