package com.rtsbuilding.rtsbuilding.server.data;

import net.minecraft.nbt.CompoundTag;

/**
 * 鐜╁绾у埆鐨勯潪浼氳瘽 {@link DataComponent} 娉ㄥ唽琛ㄣ€?
 *
 * <p>涓?{@link SessionComponents} 涓嶅悓锛屾澶勭殑缁勪欢涓嶅綊灞炰簬瀛樺偍浼氳瘽锛?
 * 鑰屾槸姣忎釜鐜╁鐙珛鐨勬瑕佹暟鎹紙濡傛彃浠躲€佽澶囨爮銆佽繘搴︾瓑锛夈€?
 * 鎵€鏈夌粍浠跺啓鍏ュ悓涓€浠?{@code session.dat}锛岀粺涓€鐢?{@link SaveScheduler} 绠＄悊銆?
 */
public final class PlayerComponents {

    /** 宸插畨瑁呮彃浠跺垪琛ㄢ€斺€擟ompoundTag 妗ユ帴锛屽吋瀹?{@code RtsPluginPersistence} 鏍煎紡 */
    public static final DataComponent<CompoundTag> PLUGINS = bridge("plugins");

    /** 鎸栨帢瑁呭鏍忕粦瀹氣€斺€擟ompoundTag 妗ユ帴锛屽吋瀹?{@code MiningLoadoutState} 鏍煎紡 */
    public static final DataComponent<CompoundTag> MINING_LOADOUT = bridge("mining_loadout");

    /** 鐜╁杩涘害鏁版嵁鈥斺€擟ompoundTag 妗ユ帴锛屽吋瀹?{@code RtsProgressionPersistence} 鏍煎紡 */
    public static final DataComponent<CompoundTag> PROGRESSION = bridge("progression");

    /** 鎸夌淮搴︿繚瀛樼殑瀹㈡埛绔寖鍥村墧闄ょ洅锛涙枃浠舵湰韬殢鐜╁涓庡瓨妗ｉ殧绂汇€?*/
    public static final DataComponent<CompoundTag> CULLING = bridge("culling");

    /** 鍒涘缓鐩撮€氭ˉ鎺ョ粍浠?*/
    private static DataComponent<CompoundTag> bridge(String key) {
        return new DataComponent<>(
                key,
                NbtCodec.of(
                        tag -> tag,
                        (tag, v) -> {
                            for (String k : v.getAllKeys()) {
                                tag.put(k, v.get(k));
                            }
                        }
                ),
                CompoundTag::new
        );
    }

    private PlayerComponents() {
    }
}

