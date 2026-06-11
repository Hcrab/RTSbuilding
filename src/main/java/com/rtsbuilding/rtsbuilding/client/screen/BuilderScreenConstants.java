package com.rtsbuilding.rtsbuilding.client.screen;


import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * BuilderScreen 浣跨敤鐨勫竷灞€甯搁噺瀹氫箟銆?
 * <p>
 * 鎵€鏈変笌灞忓箷甯冨眬銆侀潰鏉垮昂瀵搞€侀棿璺濈浉鍏崇殑甯搁噺闆嗕腑鍦ㄦ锛?
 * 渚夸簬缁熶竴璋冩暣 UI 甯冨眬鍙傛暟銆?
 */
public final class BuilderScreenConstants {

    // ======================== 椤堕儴鏍?========================
    /** 椤堕儴鏍忛珮搴?*/
    public static final int TOP_H = 52;
    /** 椤堕儴鎸夐挳闂磋窛 */
    public static final int TOP_BUTTON_GAP = 5;
    /** 椤堕儴鎸夐挳楂樺害 */
    public static final int TOP_BUTTON_H = 24;
    /** 椤堕儴鎸夐挳鏈€灏忓搴?*/
    public static final int MIN_TOP_BUTTON_W = 28;
    /** 妯″紡鎸夐挳瀹藉害 */
    public static final int TOP_MODE_BUTTON_W = 32;
    /** 鍥炬爣鎸夐挳瀹藉害 */
    public static final int TOP_ICON_BUTTON_W = 32;

    // ======================== 搴曢儴闈㈡澘 ========================
    /** 榛樿搴曢儴闈㈡澘楂樺害 */
    public static final int DEFAULT_BOTTOM_H = 110;
    /** 搴曢儴闈㈡澘鏈€灏忛珮搴?*/
    public static final int MIN_BOTTOM_H = 72;
    /** 搴曢儴闈㈡澘鏈€澶ч珮搴?*/
    public static final int MAX_BOTTOM_H = 320;
    /** 搴曢儴闈㈡澘鍐呰竟璺?*/
    public static final int BOTTOM_PANEL_PADDING = 8;
    /** 搴曢儴闈㈡澘鏍囬鏍忛珮搴?*/
    public static final int BOTTOM_PANEL_HEADER_H = 18;
    /** 瀛樺偍缃戞牸鏈€灏忚鏁?*/
    public static final int MIN_STORAGE_GRID_ROWS = 2;
    /** 缃戞牸搴曢儴鍐呰竟璺?*/
    public static final int GRID_BOTTOM_PADDING = 4;

    // ======================== 妲戒綅/鏍煎瓙 ========================
    /** 瀛樺偍缃戞牸鍗曚釜鏍煎瓙澶у皬 */
    public static final int SLOT = 22;
    /** 蹇嵎鏍忓崟涓牸瀛愬ぇ灏?*/
    public static final int HOTBAR_SLOT = 18;
    /** 蹇嵎鏍忔牸瀛愰棿璺?*/
    public static final int HOTBAR_PITCH = 20;
    /** 宸ュ叿蹇嵎鏍忕墿鍝佹Ы浣嶆暟 */
    public static final int TOOL_HOTBAR_ITEM_SLOTS = 9;
    /** 绌烘墜鎸夐挳绱㈠紩锛堢9鏍硷級 */
    public static final int EMPTY_HAND_BUTTON_INDEX = TOOL_HOTBAR_ITEM_SLOTS;
    /** 宸ュ叿鍖哄煙楂樺害 */
    public static final int TOOL_AREA_H = HOTBAR_SLOT;

    // ======================== 鎼滅储/鎺掑簭 ========================
    /** 鎼滅储娓呴櫎鎸夐挳澶у皬 */
    public static final int SEARCH_CLEAR_SIZE = 12;
    /** 鎺掑簭鎸夐挳澶у皬 */
    public static final int SORT_BUTTON_SIZE = 16;

    // ======================== 鍚堟垚闈㈡澘 ========================
    /** 鍚堟垚闈㈡澘瀹藉害 */
    public static final int CRAFT_PANEL_W = 126;
    /** 鍚堟垚闈㈡澘涓庡瓨鍌ㄧ綉鏍肩殑闂磋窛 */
    public static final int CRAFT_PANEL_GAP = 6;
    /** 鍚堟垚闈㈡澘鍒楁暟 */
    public static final int CRAFT_PANEL_COLS = 4;
    /** 鍚堟垚闈㈡澘妲戒綅澶у皬 */
    public static final int CRAFT_PANEL_SLOT = 18;
    /** 鍚堟垚闈㈡澘琛岄棿璺?*/
    public static final int CRAFT_PANEL_PITCH = 20;
    /** 鍚堟垚鎼滅储妗嗛珮搴?*/
    public static final int CRAFT_PANEL_SEARCH_H = 12;
    /** 鍚堟垚搴旂敤鎸夐挳瀹藉害 */
    public static final int CRAFT_PANEL_APPLY_W = 18;
    /** 鍚堟垚鍒囨崲鎸夐挳瀹藉害 */
    public static final int CRAFT_PANEL_TOGGLE_W = 38;
    /** 鍚堟垚搴曞骇涓ぎ鎸夐挳澶у皬 */
    public static final int CRAFT_DOCK_C_SIZE = 18;
    /** 鍚堟垚搴曞骇妲戒綅澶у皬 */
    public static final int CRAFT_DOCK_SLOT_SIZE = 10;
    /** 鍚堟垚搴曞骇闂磋窛 */
    public static final int CRAFT_DOCK_GAP = 2;
    /** 瀛樺偍涓庢渶杩戠墿鍝侀棿璺?*/
    public static final int STORAGE_RECENT_GAP = 6;

    // ======================== 鍒嗙被闈㈡澘 ========================
    /** 鍒嗙被闈㈡澘瀹藉害 */
    public static final int CATEGORY_W = 124;
    /** 鍒嗙被琛岄珮 */
    public static final int CATEGORY_ROW_H = 11;
    /** 鍒嗙被鏂囧瓧缂╂斁姣斾緥 */
    public static final float CATEGORY_TEXT_SCALE = 0.84F;

    // ======================== 浜や簰杞洏 ========================
    /** 浜や簰杞洏鍗曢〉澶у皬 */
    public static final int INTERACT_WHEEL_PAGE_SIZE = 10;
    /** 浜や簰杞洏鍗婂緞 */
    public static final int INTERACT_WHEEL_RADIUS = 68;
    /** 浜や簰杞洏妲戒綅澶у皬 */
    public static final int INTERACT_WHEEL_SLOT = 18;
    /** 浜や簰杞洏妲戒綅鍗婇暱 */
    public static final int INTERACT_WHEEL_SLOT_HALF = INTERACT_WHEEL_SLOT / 2;

    // ======================== 蹇€熷缓閫犻潰鏉?========================
    /** 蹇€熷缓閫犻潰鏉垮搴?*/
    public static final int QUICK_BUILD_PANEL_W = 188;
    /** 蹇€熷缓閫犻潰鏉块珮搴?*/
    public static final int QUICK_BUILD_PANEL_H = 216;
    /** 蹇€熷缓閫犻潰鏉挎渶灏忛珮搴?*/
    public static final int QUICK_BUILD_PANEL_MIN_H = 156;
    /** 蹇€熷缓閫犲舰鐘舵Ы浣嶅ぇ灏?*/
    public static final int QUICK_BUILD_SHAPE_SLOT = 32;
    /** 蹇€熷缓閫犲舰鐘堕棿璺?*/
    public static final int QUICK_BUILD_SHAPE_GAP = 8;
    /** 蹇€熷缓閫犻娇杞彍鍗曞搴?*/
    public static final int QUICK_BUILD_GEAR_MENU_W = 148;
    /** 蹇€熷缓閫犻娇杞楂?*/
    public static final int QUICK_BUILD_GEAR_ROW_H = 18;

    // ======================== 杩為攣鎸栨帢闈㈡澘 ========================
    /** 杩為攣鎸栨帢闈㈡澘瀹藉害 */
    public static final int ULTIMINE_PANEL_W = 238;
    /** 杩為攣鎸栨帢闈㈡澘楂樺害 */
    public static final int ULTIMINE_PANEL_H = 122;
    /** 杩為攣鎸栨帢鏈€灏忛檺鍒?*/
    public static final int ULTIMINE_MIN_LIMIT = 1;
    /** 杩為攣鎸栨帢鏈€澶ч檺鍒?*/
    public static final int ULTIMINE_MAX_LIMIT = 256;

    // ======================== 褰㈢姸杞洏 ========================
    /** 褰㈢姸杞洏鍗婂緞 */
    public static final int SHAPE_WHEEL_RADIUS = 52;
    /** 褰㈢姸杞洏妲戒綅澶у皬 */
    public static final int SHAPE_WHEEL_SLOT = 22;
    /** 褰㈢姸鏈€澶у昂瀵?*/
    public static final int SHAPE_MAX_DIMENSION = 32;
    /** 褰㈢姸鏈€澶у亸绉?*/
    public static final int SHAPE_MAX_OFFSET = SHAPE_MAX_DIMENSION - 1;
    /** 褰㈢姸鏈€澶у崐寰?*/
    public static final int SHAPE_MAX_RADIUS = 32;
    /** 褰㈢姸鏃嬭浆姝ヨ繘瑙掑害 */
    public static final int SHAPE_ROTATE_STEP_DEGREES = 15;
    /** 褰㈢姸鍘嗗彶璁板綍涓婇檺 */
    public static final int SHAPE_HISTORY_LIMIT = 24;

    // ======================== 褰㈢姸涓婁笅鏂囬潰鏉?========================
    /** 褰㈢姸涓婁笅鏂囬潰鏉垮搴?*/
    public static final int SHAPE_CONTEXT_PANEL_W = 148;
    /** 褰㈢姸涓婁笅鏂囬潰鏉?X 杈硅窛 */
    public static final int SHAPE_CONTEXT_PANEL_X_MARGIN = 10;
    /** 褰㈢姸涓婁笅鏂囬潰鏉?Y 鍧愭爣 */
    public static final int SHAPE_CONTEXT_PANEL_Y = TOP_H + 10;
    /** 褰㈢姸涓婁笅鏂囪楂?*/
    public static final int SHAPE_CONTEXT_ROW_H = 14;

    // ======================== 婕忔枟缂撳啿闈㈡澘 ========================
    /** 婕忔枟缂撳啿闈㈡澘瀹藉害 */
    public static final int FUNNEL_BUFFER_PANEL_W = 132;
    /** 婕忔枟缂撳啿琛岄珮 */
    public static final int FUNNEL_BUFFER_ROW_H = 22;
    /** 婕忔枟缂撳啿鍒囨崲鎸夐挳瀹藉害 */
    public static final int FUNNEL_BUFFER_TOGGLE_W = 60;
    /** 婕忔枟缂撳啿鍒囨崲鎸夐挳楂樺害 */
    public static final int FUNNEL_BUFFER_TOGGLE_H = 16;

    // ======================== 榻胯疆鑿滃崟锛堣缃級 ========================
    /** 榻胯疆鑿滃崟楂樺害 */
    public static final int GEAR_MENU_H = 520;
    /** 榻胯疆鑿滃崟鏈€灏忛珮搴?*/
    public static final int GEAR_MENU_MIN_H = 220;
    /** 榻胯疆鑿滃崟鍐呭楂樺害 */
    public static final int GEAR_MENU_CONTENT_H = 724;

    // ======================== 浠诲姟妫€娴嬪脊绐?========================
    /** 浠诲姟妫€娴嬪脊绐楀搴?*/
    public static final int QUEST_DETECT_POPUP_W = 178;
    /** 浠诲姟妫€娴嬪脊绐楅珮搴?*/
    public static final int QUEST_DETECT_POPUP_H = 48;

    // ======================== 瀛樺偍鎵弿寮圭獥 ========================
    /** 瀛樺偍鎵弿寮圭獥瀹藉害 */
    public static final int STORAGE_SCAN_POPUP_W = 150;
    /** 瀛樺偍鎵弿寮圭獥楂樺害 */
    public static final int STORAGE_SCAN_POPUP_H = 30;

    // ======================== 杈撳叆 / 娓叉煋鎺у埗 ========================
    /** 涓敭鎷栨嫿闃堝€硷紙鍍忕礌锛?*/
    public static final double MIDDLE_CLICK_DRAG_THRESHOLD = 1.5D;
    /** 榛樿 RTS UI 缂╂斁 */
    public static final double DEFAULT_RTS_GUI_SCALE = 2.0D;
    /** 鏈€灏?RTS UI 缂╂斁 */
    public static final double MIN_RTS_GUI_SCALE = 1.0D;
    /** 鏈€澶?RTS UI 缂╂斁 */
    public static final double MAX_RTS_GUI_SCALE = 4.0D;
    /** RTS UI 缂╂斁姝ヨ繘 */
    public static final double RTS_GUI_SCALE_STEP = 0.5D;
    /** 妯℃€佸眰 Z 杞村悜娣卞害 */
    public static final float RTS_MODAL_LAYER_Z = 400.0F;
    /** 鍙椾激闂厜鎸佺画鏃堕棿锛堟绉掞級 */
    public static final long DAMAGE_FLASH_DURATION_MS = 300L;

    // ======================== 鏉傞」 ========================
    /** 婕忔枟鍏夋爣浣跨敤鐨勭墿鍝佸浘鏍囷紙婕忔枟锛?*/
    public static final ItemStack FUNNEL_CURSOR_STACK = new ItemStack(net.minecraft.world.item.Items.HOPPER);
    /** "鍏ㄩ儴"鍒嗙被鏍囪 */
    public static final String CATEGORY_ALL = "all";
    /** Mod 鍒嗙被鍓嶇紑 */
    public static final String CATEGORY_MOD_PREFIX = "mod|";
    /** Tab 鍒嗙被鍓嶇紑 */
    public static final String CATEGORY_TAB_PREFIX = "tab|";

    public static final ResourceLocation QUICK_BUILD_SINGLE_BLOCK = quickBuildTexture("single_block");
    public static final ResourceLocation QUICK_BUILD_LINE_BLOCK = quickBuildTexture("line_block");
    public static final ResourceLocation QUICK_BUILD_SQUARE_BLOCK = quickBuildTexture("square_block");
    public static final ResourceLocation QUICK_BUILD_WALL_BLOCK = quickBuildTexture("wall_block");
    public static final ResourceLocation QUICK_BUILD_CIRCLE_BLOCK = quickBuildTexture("circle_block");
    public static final ResourceLocation QUICK_BUILD_BOX_BLOCK = quickBuildTexture("box_block");

    // ======================== 绾圭悊璧勬簮 ========================
    /** 褰㈢姸绾圭悊锛氭柟鍧楋紙闈炴椿璺冿級 */
    public static final ResourceLocation SHAPE_BLOCK_INACTIVE = quickBuildTexture("shape_block_inactive");
    /** 褰㈢姸绾圭悊锛氭柟鍧楋紙鎮仠锛?*/
    public static final ResourceLocation SHAPE_BLOCK_HOVER = quickBuildTexture("shape_block_hover");
    /** 褰㈢姸绾圭悊锛氭柟鍧楋紙娲昏穬锛?*/
    public static final ResourceLocation SHAPE_BLOCK_ACTIVE = quickBuildTexture("shape_block_active");
    /** 褰㈢姸绾圭悊锛氱嚎鏉★紙闈炴椿璺冿級 */
    public static final ResourceLocation SHAPE_LINE_INACTIVE = quickBuildTexture("shape_line_inactive");
    /** 褰㈢姸绾圭悊锛氱嚎鏉★紙鎮仠锛?*/
    public static final ResourceLocation SHAPE_LINE_HOVER = quickBuildTexture("shape_line_hover");
    /** 褰㈢姸绾圭悊锛氱嚎鏉★紙娲昏穬锛?*/
    public static final ResourceLocation SHAPE_LINE_ACTIVE = quickBuildTexture("shape_line_active");
    /** 褰㈢姸绾圭悊锛氭鏂瑰舰锛堥潪娲昏穬锛?*/
    public static final ResourceLocation SHAPE_SQUARE_INACTIVE = quickBuildTexture("shape_square_inactive");
    /** 褰㈢姸绾圭悊锛氭鏂瑰舰锛堟偓鍋滐級 */
    public static final ResourceLocation SHAPE_SQUARE_HOVER = quickBuildTexture("shape_square_hover");
    /** 褰㈢姸绾圭悊锛氭鏂瑰舰锛堟椿璺冿級 */
    public static final ResourceLocation SHAPE_SQUARE_ACTIVE = quickBuildTexture("shape_square_active");
    /** 褰㈢姸绾圭悊锛氬澹侊紙闈炴椿璺冿級 */
    public static final ResourceLocation SHAPE_WALL_INACTIVE = quickBuildTexture("shape_wall_inactive");
    /** 褰㈢姸绾圭悊锛氬澹侊紙鎮仠锛?*/
    public static final ResourceLocation SHAPE_WALL_HOVER = quickBuildTexture("shape_wall_hover");
    /** 褰㈢姸绾圭悊锛氬澹侊紙娲昏穬锛?*/
    public static final ResourceLocation SHAPE_WALL_ACTIVE = quickBuildTexture("shape_wall_active");
    /** 褰㈢姸绾圭悊锛氬渾锛堥潪娲昏穬锛?*/
    public static final ResourceLocation SHAPE_CIRCLE_INACTIVE = quickBuildTexture("shape_circle_inactive");
    /** 褰㈢姸绾圭悊锛氬渾锛堟偓鍋滐級 */
    public static final ResourceLocation SHAPE_CIRCLE_HOVER = quickBuildTexture("shape_circle_hover");
    /** 褰㈢姸绾圭悊锛氬渾锛堟椿璺冿級 */
    public static final ResourceLocation SHAPE_CIRCLE_ACTIVE = quickBuildTexture("shape_circle_active");
    /** 褰㈢姸绾圭悊锛氱珛鏂逛綋锛堥潪娲昏穬锛?*/
    public static final ResourceLocation SHAPE_BOX_INACTIVE = quickBuildTexture("shape_box_inactive");
    /** 褰㈢姸绾圭悊锛氱珛鏂逛綋锛堟偓鍋滐級 */
    public static final ResourceLocation SHAPE_BOX_HOVER = quickBuildTexture("shape_box_hover");
    /** 褰㈢姸绾圭悊锛氱珛鏂逛綋锛堟椿璺冿級 */
    public static final ResourceLocation SHAPE_BOX_ACTIVE = quickBuildTexture("shape_box_active");
    /** Quick Build Range Destroy chain shape texture. */
    public static final ResourceLocation QUICK_BUILD_CHAIN_BLOCK = quickBuildTexture("chain_block");

    // ======================== 椤堕儴鏍忔ā寮忕汗鐞?========================
    /** 浜や簰妯″紡锛堥潪娲昏穬锛?*/
    public static final ResourceLocation TOPBAR_INTERACT_INACTIVE = topbarTexture("mode_interact_inactive");
    /** 浜や簰妯″紡锛堟偓鍋滐級 */
    public static final ResourceLocation TOPBAR_INTERACT_HOVER = topbarTexture("mode_interact_hover");
    /** 浜や簰妯″紡锛堟椿璺冿級 */
    public static final ResourceLocation TOPBAR_INTERACT_ACTIVE = topbarTexture("mode_interact_active");
    /** 浜や簰妯″紡锛堟寜涓嬶級 */
    public static final ResourceLocation TOPBAR_INTERACT_PRESSED = topbarTexture("mode_interact_pressed");

    /** 閾炬帴妯″紡锛堥潪娲昏穬锛?*/
    public static final ResourceLocation TOPBAR_LINK_INACTIVE = topbarTexture("mode_link_inactive");
    /** 閾炬帴妯″紡锛堟偓鍋滐級 */
    public static final ResourceLocation TOPBAR_LINK_HOVER = topbarTexture("mode_link_hover");
    /** 閾炬帴妯″紡锛堟椿璺冿級 */
    public static final ResourceLocation TOPBAR_LINK_ACTIVE = topbarTexture("mode_link_active");
    /** 閾炬帴妯″紡锛堟寜涓嬶級 */
    public static final ResourceLocation TOPBAR_LINK_PRESSED = topbarTexture("mode_link_pressed");

    /** 婕忔枟妯″紡锛堥潪娲昏穬锛?*/
    public static final ResourceLocation TOPBAR_FUNNEL_INACTIVE = topbarTexture("mode_funnel_inactive");
    /** 婕忔枟妯″紡锛堟偓鍋滐級 */
    public static final ResourceLocation TOPBAR_FUNNEL_HOVER = topbarTexture("mode_funnel_hover");
    /** 婕忔枟妯″紡锛堟椿璺冿級 */
    public static final ResourceLocation TOPBAR_FUNNEL_ACTIVE = topbarTexture("mode_funnel_active");
    /** 婕忔枟妯″紡锛堟寜涓嬶級 */
    public static final ResourceLocation TOPBAR_FUNNEL_PRESSED = topbarTexture("mode_funnel_pressed");

    /** 鏃嬭浆妯″紡锛堥潪娲昏穬锛?*/
    public static final ResourceLocation TOPBAR_ROTATE_INACTIVE = topbarTexture("mode_rotate_inactive");
    /** 鏃嬭浆妯″紡锛堟偓鍋滐級 */
    public static final ResourceLocation TOPBAR_ROTATE_HOVER = topbarTexture("mode_rotate_hover");
    /** 鏃嬭浆妯″紡锛堟椿璺冿級 */
    public static final ResourceLocation TOPBAR_ROTATE_ACTIVE = topbarTexture("mode_rotate_active");
    /** 鏃嬭浆妯″紡锛堟寜涓嬶級 */
    public static final ResourceLocation TOPBAR_ROTATE_PRESSED = topbarTexture("mode_rotate_pressed");

    /** 蹇€熷缓閫狅紙闈炴椿璺冿級 */
    public static final ResourceLocation TOPBAR_QUICK_BUILD_INACTIVE = topbarTexture("quick_build_inactive");
    /** 蹇€熷缓閫狅紙鎮仠锛?*/
    public static final ResourceLocation TOPBAR_QUICK_BUILD_HOVER = topbarTexture("quick_build_hover");
    /** 蹇€熷缓閫狅紙娲昏穬锛?*/
    public static final ResourceLocation TOPBAR_QUICK_BUILD_ACTIVE = topbarTexture("quick_build_active");
    /** 蹇€熷缓閫狅紙鎸変笅锛?*/
    public static final ResourceLocation TOPBAR_QUICK_BUILD_PRESSED = topbarTexture("quick_build_pressed");

    /** 杩為攣鎸栨帢锛堥潪娲昏穬锛?*/
    public static final ResourceLocation TOPBAR_ULTIMINE_INACTIVE = topbarTexture("ultimine_inactive");
    /** 杩為攣鎸栨帢锛堟偓鍋滐級 */
    public static final ResourceLocation TOPBAR_ULTIMINE_HOVER = topbarTexture("ultimine_hover");
    /** 杩為攣鎸栨帢锛堟椿璺冿級 */
    public static final ResourceLocation TOPBAR_ULTIMINE_ACTIVE = topbarTexture("ultimine_active");
    /** 杩為攣鎸栨帢锛堟寜涓嬶級 */
    public static final ResourceLocation TOPBAR_ULTIMINE_PRESSED = topbarTexture("ultimine_pressed");

    /** 鍖哄潡瑙嗗浘锛堥潪娲昏穬锛?*/
    public static final ResourceLocation TOPBAR_CHUNK_VIEW_INACTIVE = topbarTexture("chunk_view_inactive");
    /** 鍖哄潡瑙嗗浘锛堟偓鍋滐級 */
    public static final ResourceLocation TOPBAR_CHUNK_VIEW_HOVER = topbarTexture("chunk_view_hover");
    /** 鍖哄潡瑙嗗浘锛堟椿璺冿級 */
    public static final ResourceLocation TOPBAR_CHUNK_VIEW_ACTIVE = topbarTexture("chunk_view_active");
    /** 鍖哄潡瑙嗗浘锛堟寜涓嬶級 */
    public static final ResourceLocation TOPBAR_CHUNK_VIEW_PRESSED = topbarTexture("chunk_view_pressed");

    /** 璁剧疆榻胯疆锛堥潪娲昏穬锛?*/
    public static final ResourceLocation TOPBAR_GEAR_INACTIVE = topbarTexture("settings_gear_inactive");
    /** 璁剧疆榻胯疆锛堟偓鍋滐級 */
    public static final ResourceLocation TOPBAR_GEAR_HOVER = topbarTexture("settings_gear_hover");
    /** 璁剧疆榻胯疆锛堟椿璺冿級 */
    public static final ResourceLocation TOPBAR_GEAR_ACTIVE = topbarTexture("settings_gear_active");
    /** 璁剧疆榻胯疆锛堟寜涓嬶級 */
    public static final ResourceLocation TOPBAR_GEAR_PRESSED = topbarTexture("settings_gear_pressed");

    /** 浠诲姟妫€娴嬶紙闈炴椿璺冿級 */
    public static final ResourceLocation TOPBAR_QUEST_DETECT_INACTIVE = topbarTexture("quest_detect_inactive");
    /** 浠诲姟妫€娴嬶紙鎮仠锛?*/
    public static final ResourceLocation TOPBAR_QUEST_DETECT_HOVER = topbarTexture("quest_detect_hover");
    /** 浠诲姟妫€娴嬶紙娲昏穬锛?*/
    public static final ResourceLocation TOPBAR_QUEST_DETECT_ACTIVE = topbarTexture("quest_detect_active");
    /** 浠诲姟妫€娴嬶紙鎸変笅锛?*/
    public static final ResourceLocation TOPBAR_QUEST_DETECT_PRESSED = topbarTexture("quest_detect_pressed");

    // ======================== 杈呭姪鏂规硶 ========================

    /** 鏋勫缓蹇€熷缓閫犵汗鐞嗚矾寰?*/
    private static ResourceLocation quickBuildTexture(String key) {
        ResourceLocation id = ResourceLocation.tryParse("rtsbuilding:textures/gui/quickbuild/" + key + ".png");
        return id == null ? new ResourceLocation("minecraft", "missingno") : id;
    }

    /** 鏋勫缓椤堕儴鏍忕汗鐞嗚矾寰?*/
    private static ResourceLocation topbarTexture(String key) {
        ResourceLocation id = ResourceLocation.tryParse("rtsbuilding:textures/gui/topbar/" + key + ".png");
        return id == null ? new ResourceLocation("minecraft", "missingno") : id;
    }

    private BuilderScreenConstants() {
        // 宸ュ叿绫伙紝绂佹瀹炰緥鍖?
    }
}
