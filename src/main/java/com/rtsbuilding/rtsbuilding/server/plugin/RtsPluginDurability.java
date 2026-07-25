package com.rtsbuilding.rtsbuilding.server.plugin;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.data.SaveScheduler;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * 鎻掍欢瀹夎銆佸嵏杞戒笌杩佺Щ瀹屾垚鍚庣殑鍗虫椂鑰愪箙鍖栨鏌ョ偣銆? *
 * <p>鏅€氱帺瀹舵暟鎹粛鐢?{@link SaveScheduler} 鎵归噺淇濆瓨锛涙湰绫诲彧鏈嶅姟浜庝綆棰戜絾涓嶅彲涓㈠け鐨勬彃浠跺彉鏇淬€? * 瀹冨厛钀界洏鎻掍欢鐘舵€侊紝鍐嶄繚瀛樺寘鍚彃浠剁墿鍝佸鍑忕殑鐜╁鑳屽寘锛屼粠鑰屾妸寮哄埗缁撴潫鏈嶅姟绔椂鐨勪涪澶辩獥鍙? * 浠庤嚜鍔ㄤ繚瀛樺懆鏈熺缉鐭埌鏈鎿嶄綔杩斿洖涔嬪墠銆傛湰绫讳笉璐熻矗鍒ゅ畾鎻掍欢鏄惁鍚堟硶锛屼篃涓嶄慨鏀规彃浠跺垪琛ㄣ€? */
final class RtsPluginDurability {
    private RtsPluginDurability() {
    }

    static boolean checkpoint(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }

        try {
            // 涓汉鎻掍欢涓庨槦浼嶈縼绉诲悗鐨勪釜浜烘畫鐣欓兘浣嶄簬鐜╁ session.dat銆?            if (!SaveScheduler.INSTANCE.player(player).flush()) {
                RtsbuildingMod.LOGGER.error(
                        "鎻掍欢鍙樻洿鍗虫椂淇濆瓨澶辫触锛氱帺瀹?{} 鐨?RTS 鏁版嵁灏氭湭钀界洏锛屽皢淇濈暀鑴忔暟鎹瓑寰呴噸璇?,
                        player.getGameProfile().getName());
                return false;
            }

            // 闃熶紞鍏变韩鎻掍欢浣跨敤 SavedData锛泂ave() 鍙彁浜ゅ紓姝ヤ换鍔★紝蹇呴』绛?IO worker 鐪熸瀹屾垚銆?            String sharedKey = RtsProgressionManager.sharedProgressionKey(player);
            if (!sharedKey.isBlank()) {
                ServerLevel storageLevel = server.getLevel(Level.OVERWORLD);
                if (storageLevel == null) {
                    storageLevel = player.serverLevel();
                }
                storageLevel.getDataStorage().save();
            }

            // 鎻掍欢鐗╁搧宸茬粡浠庤儗鍖呮墸闄ゆ垨閫€鍥烇紱鍚屼竴妫€鏌ョ偣淇濆瓨鐜╁鏂囦欢锛岄伩鍏嶇姸鎬佷笌鐗╁搧鍙瓨涓€杈广€?            server.getPlayerList().saveAll();
            return true;
        } catch (RuntimeException exception) {
            RtsbuildingMod.LOGGER.error(
                    "鎻掍欢鍙樻洿鍗虫椂淇濆瓨寮傚父锛氱帺瀹?{}锛屽皢鐢卞悗缁嚜鍔ㄤ繚瀛樼户缁噸璇?,
                    player.getGameProfile().getName(),
                    exception);
            return false;
        }
    }
}
