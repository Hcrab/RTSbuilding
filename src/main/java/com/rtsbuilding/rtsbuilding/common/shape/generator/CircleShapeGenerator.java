package com.rtsbuilding.rtsbuilding.common.shape.generator;

import com.rtsbuilding.rtsbuilding.common.shape.model.AreaShapeInput;
import com.rtsbuilding.rtsbuilding.common.shape.model.ShapeFillMode;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * 鍦嗗舰褰㈢姸鐢熸垚鍣紙鍦?XZ 骞抽潰涓婄殑鍗曞眰鍦嗭級銆?
 * <p>
 * 鍗婂緞鐢辫捣鐐瑰埌缁堢偣鍦?XZ 骞抽潰涓婄殑鎶曞奖璺濈鍐冲畾銆?
 * 鏀寔 FILL锛堝疄蹇冨渾锛夊拰 HOLLOW锛堢┖蹇冨渾鐜級涓ょ妯″紡銆?
 * FILL 妯″紡浼氫娇鐢ㄦ椽姘村～鍏咃紙Flood-Fill锛夊～琛ュ唴閮ㄧ┖娲炪€?
 */
public class CircleShapeGenerator extends AreaShapeGenerator {

    @Override
    public String getName() {
        return "circle";
    }

    @Override
    public List<BlockPos> generatePositions(AreaShapeInput input, ShapeFillMode fillMode) {
        // 璁＄畻 XZ 骞抽潰涓婄殑鍋忕Щ閲?
        int dx = input.end().getX() - input.start().getX();
        int dz = input.end().getZ() - input.start().getZ();

        // 璁＄畻鍗婂緞骞堕檺鍒舵渶澶у€?
        double radius = Math.sqrt((dx * (double) dx) + (dz * (double) dz));
        int r = Math.max(0, (int) Math.round(radius));
        r = Math.min(r, 64);

        int outer2 = r * r;
        int inner = Math.max(0, r - 1);
        int inner2 = inner * inner;

        // 閬嶅巻鍖呭洿鐩掑唴鐨勬墍鏈?XZ 鍧愭爣锛岀瓫閫夊嚭鍦嗗舰鑼冨洿鍐呯殑浣嶇疆
        List<BlockPos> result = new ArrayList<>();
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                int dist2 = x * x + z * z;
                boolean inOuter = dist2 <= outer2;
                boolean inInner = dist2 < inner2;
                // 绌哄績妯″紡璺宠繃鍐呴儴鐐?
                if (!inOuter || (fillMode != ShapeFillMode.FILL && inInner)) {
                    continue;
                }
                result.add(input.start().offset(x, 0, z));
            }
        }

        // 瀹炲績妯″紡闇€瑕佸～琛ユ爡鏍煎寲浜х敓鐨勫唴閮ㄧ┖娲?
        if (fillMode == ShapeFillMode.FILL) {
            result = fillInternalHoles(result);
        }

        return result;
    }

    /**
     * 浣跨敤娲按濉厖鏂规硶濉ˉ鍐呴儴绌烘礊銆?
     * <p>
     * 鍦ㄦ姇褰辩殑 2D 缃戞牸涓婃墽琛岋紝澶勭悊鍦嗗舰鏍呮牸鍖栬繃绋嬩腑浜х敓鐨勯棿闅欍€?
     * 鍘熺悊锛氫粠杈圭晫澶栧紑濮嬫爣璁版墍鏈?澶栭儴"鍖哄煙锛?
     * 鍓╀笅鐨勬湭鏍囪浣嶇疆鍗充负闇€瑕佸～鍏呯殑鍐呴儴绌烘礊銆?
     *
     * @param positions 褰撳墠鐢熸垚鐨勪綅缃垪琛?
     * @return 濉ˉ绌烘礊鍚庣殑瀹屾暣浣嶇疆鍒楄〃
     */
    private static List<BlockPos> fillInternalHoles(List<BlockPos> positions) {
        if (positions.isEmpty()) return positions;

        // 纭畾鍖呭洿鐩掕竟鐣?
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : positions) {
            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
            minZ = Math.min(minZ, pos.getZ());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        // 纭畾涓€涓唬琛ㄦ€х殑 Y 灞?
        int yLevel = positions.get(0).getY();

        java.util.Set<BlockPos> filled = new java.util.HashSet<>(positions);
        java.util.Set<BlockPos> outside = new java.util.HashSet<>();
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();

        // 灏嗗寘鍥寸洅杈圭晫澶栫殑鎵€鏈夋牸瀛愭爣璁颁负"澶栭儴"绉嶅瓙
        for (int x = minX - 1; x <= maxX + 1; x++) {
            tryEnqueue(new BlockPos(x, yLevel, minZ - 1), filled, outside, queue, minX - 1, maxX + 1, minZ - 1, maxZ + 1, yLevel);
            tryEnqueue(new BlockPos(x, yLevel, maxZ + 1), filled, outside, queue, minX - 1, maxX + 1, minZ - 1, maxZ + 1, yLevel);
        }
        for (int z = minZ; z <= maxZ; z++) {
            tryEnqueue(new BlockPos(minX - 1, yLevel, z), filled, outside, queue, minX - 1, maxX + 1, minZ - 1, maxZ + 1, yLevel);
            tryEnqueue(new BlockPos(maxX + 1, yLevel, z), filled, outside, queue, minX - 1, maxX + 1, minZ - 1, maxZ + 1, yLevel);
        }

        // 娲按濉厖锛氫粠杈圭晫鍚戝鎵╂暎锛屾爣璁版墍鏈夊彲杈剧殑"澶栭儴"鍖哄煙
        while (!queue.isEmpty()) {
            BlockPos cur = queue.removeFirst();
            tryEnqueue(cur.east(), filled, outside, queue, minX - 1, maxX + 1, minZ - 1, maxZ + 1, yLevel);
            tryEnqueue(cur.west(), filled, outside, queue, minX - 1, maxX + 1, minZ - 1, maxZ + 1, yLevel);
            tryEnqueue(cur.north(), filled, outside, queue, minX - 1, maxX + 1, minZ - 1, maxZ + 1, yLevel);
            tryEnqueue(cur.south(), filled, outside, queue, minX - 1, maxX + 1, minZ - 1, maxZ + 1, yLevel);
        }

        // 鏀堕泦鏃笉鍦?filled 涔熶笉鍦?outside 涓殑浣嶇疆锛堝嵆鍐呴儴绌烘礊锛?
        List<BlockPos> dense = new ArrayList<>(positions);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos pos = new BlockPos(x, yLevel, z);
                if (!filled.contains(pos) && !outside.contains(pos)) {
                    dense.add(pos);
                }
            }
        }
        return dense;
    }

    /**
     * 灏濊瘯灏嗕綅缃姞鍏ュ閮ㄥ尯鍩熼槦鍒椼€?
     * <p>
     * 濡傛灉璇ヤ綅缃湪杈圭晫鑼冨洿鍐呫€佷笖涓嶈 filled 鎴?outside 鍖呭惈锛屽垯鍔犲叆闃熷垪銆?
     */
    private static void tryEnqueue(BlockPos pos, java.util.Set<BlockPos> filled,
                                    java.util.Set<BlockPos> outside, java.util.ArrayDeque<BlockPos> queue,
                                    int minX, int maxX, int minZ, int maxZ, int yLevel) {
        if (pos.getX() < minX || pos.getX() > maxX || pos.getZ() < minZ || pos.getZ() > maxZ) return;
        if (pos.getY() != yLevel) return;
        if (filled.contains(pos) || outside.contains(pos)) return;
        outside.add(pos);
        queue.addLast(pos);
    }
}
