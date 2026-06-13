package com.rtsbuilding.rtsbuilding.client.screen.blueprint;


import com.rtsbuilding.rtsbuilding.blueprint.client.BlueprintPanel;

import java.util.List;

/**
 * 钃濆浘铏氬奖棰勮鏁版嵁??
 * <p>
 * 鐢ㄤ簬鍦ㄤ笘鐣屼笂娓叉煋钃濆浘鏀剧疆鐨勯瑙堟柟鍧楋??
 * 鍖呭惈棰勮鏂瑰潡鍒楄〃銆佹潗鏂欐槸鍚﹀氨缁€佹槸鍚﹁鎴柇绛変俊鎭€?
 */
public record BlueprintGhostPreview(List<BlueprintPanel.BlueprintGhostBlock> blocks, boolean materialsReady, boolean truncated) {
    /** 绌洪瑙堝父??*/
    public static final BlueprintGhostPreview EMPTY = new BlueprintGhostPreview(List.of(), false, false);
}
